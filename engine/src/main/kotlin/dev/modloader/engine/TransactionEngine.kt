package dev.modloader.engine

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import dev.modloader.domain.*
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

internal class TransactionEngine(private val storage: File, private val vaultRelative: String = ".backup/modloader-v1",
    private val beforeMutation: () -> Unit = {},
    private val checkpoint: (String) -> Unit = {}) {
    private fun root(pkg: String): File {
        PathPolicy.packageName(pkg)
        val root = SafeFs.checked(storage, "Android/data/$pkg")
        check(root.isDirectory) { "Hedef dizin yok; oyunu önce en az bir kez açın" }
        check(SafeFs.checked(root, GameTarget.RELATIVE_RESOURCES).isDirectory) { "Oyunun gamedata/Resources/ dizini yok" }
        return root
    }

    private fun vault(root: File): File = SafeFs.checked(root, vaultRelative).also(SafeFs::mkdir)
    private fun tx(vault: File, id: String): File {
        require(UUID.fromString(id).toString() == id) { "Geçersiz işlem kimliği" }
        return SafeFs.checked(vault, id)
    }
    private fun read(dir: File, pkg: String) = Journal.read(SafeFs.checked(dir, "journal.json"), pkg, dir.name)
    private fun save(dir: File, journal: Journal) = SafeFs.writeAtomic(SafeFs.checked(dir, "journal.json"), journal.json())

    private fun <T> locked(pkg: String, block: (File, File) -> T): T {
        val root = root(pkg)
        val vault = vault(root)
        RandomAccessFile(SafeFs.checked(vault, "engine.lock"), "rw").use { file ->
            file.channel.use { channel ->
                val lock = channel.tryLock() ?: error("Başka işlem çalışıyor")
                lock.use { return block(root, vault) }
            }
        }
    }

    /** Gerçek create + fsync + rename + read + delete testi; erişimi Android sürümünden tahmin etmez. */
    private fun probe(root: File) {
        val a = SafeFs.checked(root, "${GameTarget.RELATIVE_RESOURCES}/.modloader-probe-${UUID.randomUUID()}")
        val b = SafeFs.checked(root, "${GameTarget.RELATIVE_RESOURCES}/.modloader-probe-${UUID.randomUUID()}")
        try {
            SafeFs.writeAtomic(a, "probe")
            SafeFs.rename(a, b)
            check(SafeFs.readText(b) == "probe")
        } finally {
            if (a.exists()) check(a.delete())
            if (b.exists()) check(b.delete())
            SafeFs.syncDir(root.resolve(GameTarget.RELATIVE_RESOURCES))
        }
    }

    fun prepare(fd: ParcelFileDescriptor, pkg: String, progress: (String, Long, Long) -> Unit): String = locked(pkg) { root, vault ->
        recoverLocked(root, vault, pkg, progress)
        probe(root)
        // Önizleme paketleri de yer kaplar. Sınırsız disk büyümesini engelle.
        check((vault.listFiles()?.count { it.isDirectory } ?: 0) < 20) { "20 işlem saklanıyor; doğrulanmış yedekleri dışa aktararak arşivi temizleyin" }
        val stat = Os.fstat(fd.fileDescriptor)
        require(OsConstants.S_ISREG(stat.st_mode) && stat.st_size in 1..Limits.ZIP_BYTES) { "Yerel, sınırlı ZIP FD gerekli" }
        if (vault.usableSpace <= stat.st_size + Limits.RESERVE_BYTES) throw EngineFailure(2, "NO_ARCHIVE_SPACE")
        val dir = tx(vault, UUID.randomUUID().toString()).also(SafeFs::mkdir)
        val zip = SafeFs.checked(dir, "package.zip")
        // APK sandbox yolunu shell'e vermek yerine FD aktarılır.
        ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(fd.fileDescriptor)).use {
            var copied = 0L
            SafeFs.copy(it, zip, Limits.ZIP_BYTES) { n -> copied += n; progress("ZIP aktarımı", copied, stat.st_size) }
        }
        ModMetadataReader.read(zip) // UI kontrolü atlatılsa da manifest ve gerçek payload eşleşmeli.
        val stage = SafeFs.checked(dir, "new").also(SafeFs::mkdir)
        val files = ZipModParser().extract(zip, pkg, stage) { done, total -> progress("ZIP doğrulama", done, total) }
        // ZIP parser dosyaları fsync eder; yeni dizin girdilerini de kalıcılaştır.
        stage.walkBottomUp().filter { it.isDirectory }.forEach(SafeFs::syncDir)
        progress("Hedef dosyalar inceleniyor", 0, files.size.toLong())
        var originalBytes = 0L
        val entries = files.mapIndexed { index, mod ->
            val destination = SafeFs.checked(root, mod.path)
            originalBytes += destination.length()
            check(originalBytes <= Limits.TOTAL_BYTES) { "Orijinal dosyaların toplamı yedek sınırını aşıyor" }
            val old = SafeFs.hash(destination)
            progress("Hedef dosyalar inceleniyor", index + 1L, files.size.toLong())
            PreviewEntry(mod, old)
        }
        val journal = Journal(Plan(dir.name, pkg, entries))
        save(dir, journal)
        check(zip.delete()); SafeFs.syncDir(dir)
        journal.json()
    }

    fun apply(pkg: String, id: String, approved: Boolean, progress: (String, Long, Long) -> Unit): String = locked(pkg) { root, vault ->
        beforeMutation()
        recoverLocked(root, vault, pkg, progress)
        val dir = tx(vault, id)
        val journal = read(dir, pkg)
        if (journal.state == "COMMITTED") return@locked "Zaten tamamlandı: $id"
        check(journal.state == "PREPARED") { "İşlem uygulanabilir durumda değil: ${journal.state}" }
        require(approved || journal.plan.entries.none { it.originalHash != null }) { "Dosya ezme onayı gerekli" }
        probe(root)
        val entries = journal.plan.entries
        var backupBytes = 0L
        var largest = 0L
        entries.forEachIndexed { index, entry ->
            val target = SafeFs.checked(root, entry.file.path)
            check(SafeFs.hash(target) == entry.originalHash) { "Önizleme eskidi: ${entry.file.path}" }
            val staged = SafeFs.checked(dir, "new/${entry.file.path}")
            check(SafeFs.hash(staged) == entry.file.sha256) { "Staging bütünlüğü bozuldu" }
            if (entry.originalHash != null) {
                backupBytes += target.length(); largest = maxOf(largest, target.length())
            }
            progress("Uygulama öncesi doğrulama", index + 1L, entries.size.toLong())
        }
        check(backupBytes <= Limits.TOTAL_BYTES) { "Yedek toplam boyutu sınırı aşıldı" }
        if (vault.usableSpace < backupBytes + largest + Limits.RESERVE_BYTES) throw EngineFailure(2, "NO_BACKUP_SPACE")
        journal.state = "BACKING_UP"; save(dir, journal)
        try {
            var backedUp = 0L
            entries.filter { it.originalHash != null }.forEach { entry ->
                val source = SafeFs.checked(root, entry.file.path)
                val backup = SafeFs.checked(dir, "old/${entry.file.path}")
                SafeFs.input(source).use { input ->
                    SafeFs.copy(input, backup, Limits.TOTAL_BYTES) { n ->
                        backedUp += n; progress("Yedekleme", backedUp, backupBytes)
                    }
                }
                check(SafeFs.hash(backup) == entry.originalHash) { "Yedek alınırken hedef değişti" }
            }
            // Bütün yedekler kalıcı olmadan hiçbir hedef dosyaya dokunulmaz.
            journal.state = "APPLYING"; save(dir, journal)
            checkpoint("APPLYING")
            entries.forEachIndexed { index, entry ->
                val target = SafeFs.checked(root, entry.file.path)
                check(SafeFs.hash(target) == entry.originalHash) { "Hedef eşzamanlı değişti" }
                val staged = SafeFs.checked(dir, "new/${entry.file.path}")
                check(SafeFs.hash(staged) == entry.file.sha256) { "Staging değişti" }
                // Write-ahead intent: rename sonrası çökme de geri alınabilir.
                journal.intentCount = index + 1; save(dir, journal)
                checkpoint("INTENT:$index")
                SafeFs.rename(staged, target)
                checkpoint("RENAMED:$index")
                check(SafeFs.hash(SafeFs.checked(root, entry.file.path)) == entry.file.sha256)
                progress("Dosyalar uygulanıyor", index + 1L, entries.size.toLong())
            }
            journal.state = "COMMITTED"; save(dir, journal)
            checkpoint("COMMITTED")
            "Tamamlandı. Yedek: ${dir.path}/old"
        } catch (e: Exception) {
            if (journal.state == "APPLYING") {
                try { rollback(root, dir, journal, progress) }
                catch (rollbackError: Exception) {
                    throw IllegalStateException("RECOVERY_REQUIRED: $id; ${rollbackError.message}", e)
                }
            } else if (journal.state == "BACKING_UP") {
                journal.state = "ABORTED"; save(dir, journal)
            }
            throw e
        }
    }

    fun restore(pkg: String, id: String, progress: (String, Long, Long) -> Unit): String = locked(pkg) { root, vault ->
        recoverLocked(root, vault, pkg, progress)
        val dir = tx(vault, id)
        val journal = read(dir, pkg)
        if (journal.state in setOf("ROLLED_BACK", "ABORTED", "PREPARED")) return@locked "Mod zaten geri alınmış veya uygulanmamış."
        check(journal.state == "COMMITTED") { "Önce yarım işlem kurtarılmalı" }
        val paths = journal.plan.entries.map { it.file.path }.toSet()
        val committedAt = SafeFs.checked(dir, "journal.json").lastModified()
        // Aynı içeriği yazan sonraki bir mod bile geri alma sırasını değiştirmemeli.
        vault.listFiles().orEmpty().filter { it.name != "engine.lock" && it.name != id }.forEach { other ->
            val otherDir = tx(vault, other.name)
            val file = SafeFs.checked(otherDir, "journal.json")
            if (file.exists()) {
                val later = read(otherDir, pkg)
                check(later.state != "COMMITTED" || file.lastModified() < committedAt ||
                    later.plan.entries.none { it.file.path in paths }) {
                    "Aynı dosyalara uygulanan daha yeni mod var; önce onu Recover ile geri alın."
                }
            }
        }
        // Tüm çakışmaları ve yedekleri ilk değişiklikten önce kontrol et.
        journal.plan.entries.forEach { entry ->
            if (SafeFs.hash(SafeFs.checked(root, entry.file.path)) != entry.file.sha256)
                throw EngineFailure(7, "SHA_MISMATCH")
            entry.originalHash?.let { hash ->
                check(SafeFs.hash(SafeFs.checked(dir, "old/${entry.file.path}")) == hash) { "Yedek eksik veya bozuk" }
            }
        }
        rollback(root, dir, journal, progress)
        "Mod geri alındı; orijinal dosyalar geri yüklendi."
    }

    /** Explicit user-approved restore. Preserve observed files before durable restore intent. */
    fun restoreBase(pkg: String, id: String, progress: (String, Long, Long) -> Unit): String = locked(pkg) { root, vault ->
        recoverLocked(root, vault, pkg, progress)
        beforeMutation()
        val dir = tx(vault, id)
        val journal = read(dir, pkg)
        check(journal.state in setOf("COMMITTED", "ROLLED_BACK"))
        // A lost success reply must not replace the preserved pre-recovery snapshot.
        if (journal.state == "ROLLED_BACK" && journal.plan.entries.all {
            SafeFs.hash(SafeFs.checked(root, it.file.path)) == it.originalHash
        }) return@locked "Already restored"
        val observed = org.json.JSONObject()
        var bytes = 0L
        var largestOriginal = 0L
        journal.plan.entries.forEach { entry ->
            entry.originalHash?.let {
                val original = SafeFs.checked(dir, "old/${entry.file.path}")
                check(SafeFs.hash(original) == it)
                largestOriginal = maxOf(largestOriginal, original.length())
            }
            val target = SafeFs.checked(root, entry.file.path)
            bytes += target.length()
            require(bytes <= Limits.TOTAL_BYTES)
        }
        if (vault.usableSpace < bytes + largestOriginal + Limits.RESERVE_BYTES) throw EngineFailure(2, "SPACE")
        journal.plan.entries.forEach { entry ->
            val target = SafeFs.checked(root, entry.file.path)
            val hash = SafeFs.hash(target)
            if (hash != null) {
                val preserved = SafeFs.checked(dir, "before-recovery/${entry.file.path}")
                SafeFs.input(target).use { SafeFs.copy(it, preserved, Limits.TOTAL_BYTES) }
                check(SafeFs.hash(preserved) == hash)
            }
            observed.put(entry.file.path, hash ?: org.json.JSONObject.NULL)
        }
        SafeFs.writeAtomic(SafeFs.checked(dir, "recovery-sha.json"), observed.toString())
        journal.state = "FORCE_RESTORING"; save(dir, journal)
        checkpoint("FORCE_INTENT")
        finishBaseRestore(root, dir, journal, progress)
        "Restored"
    }

    private fun finishBaseRestore(root: File, dir: File, journal: Journal, progress: (String, Long, Long) -> Unit) {
        beforeMutation()
        val manifest = SafeFs.checked(dir, "recovery-sha.json")
        check(manifest.isFile && manifest.length() in 1..256L * 1024)
        val observed = org.json.JSONObject(SafeFs.readText(manifest))
        journal.plan.entries.forEachIndexed { index, entry ->
            check(observed.has(entry.file.path))
            val expected = if (observed.isNull(entry.file.path)) null else observed.getString(entry.file.path)
            check(expected == null || expected.matches(Regex("[0-9a-f]{64}")))
            val target = SafeFs.checked(root, entry.file.path)
            val current = SafeFs.hash(target)
            if (current != entry.originalHash) {
                if (current != expected) throw EngineFailure(6, "RECOVERY_REQUIRED")
                if (entry.originalHash == null) {
                    check(target.delete()); SafeFs.syncDir(target.parentFile!!)
                } else {
                    val source = SafeFs.checked(dir, "old/${entry.file.path}")
                    check(SafeFs.hash(source) == entry.originalHash)
                    val temp = SafeFs.checked(dir, "restore.tmp")
                    SafeFs.input(source).use { SafeFs.copy(it, temp, Limits.TOTAL_BYTES) }
                    check(SafeFs.hash(temp) == entry.originalHash)
                    check(SafeFs.hash(SafeFs.checked(root, entry.file.path)) == current)
                    SafeFs.rename(temp, target)
                }
            }
            checkpoint("FORCE_RESTORED:$index")
            progress("restore", index + 1L, journal.plan.entries.size.toLong())
        }
        journal.state = "ROLLED_BACK"; save(dir, journal)
    }

    fun recover(pkg: String, progress: (String, Long, Long) -> Unit): String = locked(pkg) { root, vault ->
        val results = recoverLocked(root, vault, pkg, progress)
        if (results.isEmpty()) "$pkg: bekleyen işlem yok."
        else "$pkg işlem durumları:\n${results.joinToString("\n")}"
    }

    private fun recoverLocked(root: File, vault: File, pkg: String, progress: (String, Long, Long) -> Unit): List<String> {
        val results = mutableListOf<String>()
        val dirs = vault.listFiles() ?: error("İşlem arşivi okunamadı")
        check(dirs.size <= 64) { "İşlem arşivi sınırı aşıldı" }
        dirs.filter { it.name != "engine.lock" }.forEach { item ->
            val dir = tx(vault, item.name)
            check(dir.isDirectory)
            val journalFile = SafeFs.checked(dir, "journal.json")
            if (!journalFile.exists()) return@forEach // PREPARING sırasında öldü; hedefe dokunulmadı.
            val journal = read(dir, pkg)
            when (journal.state) {
                "FORCE_RESTORING" -> finishBaseRestore(root, dir, journal, progress)
                "APPLYING", "ROLLING_BACK" -> rollback(root, dir, journal, progress)
                "BACKING_UP" -> { journal.state = "ABORTED"; save(dir, journal) }
            }
            val label = when (journal.state) {
                "COMMITTED" -> "Uygulandı"
                "ROLLED_BACK" -> "Geri alındı"
                "ABORTED" -> "Hedefler değiştirilmeden durduruldu"
                "PREPARED" -> "Uygulanmadı; önizleme hazırlandı"
                else -> journal.state
            }
            results.add("${dir.name}: $label")
        }
        return results
    }

    private fun rollback(root: File, dir: File, journal: Journal, progress: (String, Long, Long) -> Unit) {
        beforeMutation()
        journal.state = "ROLLING_BACK"; save(dir, journal)
        val touched = journal.plan.entries.take(journal.intentCount).asReversed()
        touched.forEachIndexed { index, entry ->
            val target = SafeFs.checked(root, entry.file.path)
            val current = SafeFs.hash(target)
            if (current != entry.originalHash) {
                check(current == entry.file.sha256) { "Geri alma çakışması: ${entry.file.path}; yedek korundu" }
                if (entry.originalHash == null) {
                    check(target.delete()); SafeFs.syncDir(target.parentFile!!)
                } else {
                    val backup = SafeFs.checked(dir, "old/${entry.file.path}")
                    check(SafeFs.hash(backup) == entry.originalHash) { "Yedek bütünlüğü bozuk" }
                    val restore = SafeFs.checked(dir, "restore.tmp")
                    SafeFs.input(backup).use { SafeFs.copy(it, restore, Limits.TOTAL_BYTES) }
                    check(SafeFs.hash(restore) == entry.originalHash)
                    // Kullanıcı/oyun kurtarma sırasında değiştirmişse ezme.
                    check(SafeFs.hash(SafeFs.checked(root, entry.file.path)) == current)
                    SafeFs.rename(restore, target)
                }
            }
            progress("Geri alma", index + 1L, touched.size.toLong())
            checkpoint("RESTORED:$index")
        }
        journal.state = "ROLLED_BACK"; save(dir, journal)
    }
}
