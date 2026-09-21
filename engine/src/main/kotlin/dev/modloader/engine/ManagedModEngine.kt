package dev.modloader.engine

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import dev.modloader.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

/** Bundles/mods/<ad>/state.json kalıcı durumdur; UI flag'i yalnızca bunun önbelleğidir. */
internal class ManagedModEngine(private val storage: File, private val beforeMutation: () -> Unit = {},
    private val checkpoint: (String) -> Unit = {}) {
    private val pkg = GameTarget.PACKAGE_NAME
    private val root get() = SafeFs.checked(storage, "Android/data/$pkg")
    private fun mods(): File {
        val gameRoot = root
        if (!gameRoot.isDirectory || !SafeFs.checked(gameRoot, GameTarget.RELATIVE_RESOURCES).isDirectory)
            throw EngineFailure(12, "GAME_DATA_MISSING")
        return SafeFs.checked(gameRoot, "${GameTarget.RELATIVE_RESOURCES}/mods").also(SafeFs::mkdir)
    }
    private fun validId(id: String): String {
        require(UUID.fromString(id).toString() == id); return id
    }
    private fun <T> locked(block: (File) -> T): T {
        val home = mods()
        return SafeFs.withFileLock(SafeFs.checked(home, ".engine.lock")) {
                // Kilit alındı: çalışan bir import yok. Yayınlanmadan kesilen staging artık güvenle temizlenebilir.
                home.list().orEmpty().filter { it.startsWith(".deleted-") || it.startsWith(".incoming-") }.forEach {
                    validId(it.removePrefix(".deleted-").removePrefix(".incoming-"))
                    SafeFs.removeTree(home, it)
                }
                block(home)
        }
    }
    private data class Record(val dir: File, val json: JSONObject) {
        var shaMismatch: Boolean = false
        val id get() = json.getString("id")
        val folder get() = json.getString("folder")
        val files: List<ModFile> get() {
            val array = json.getJSONArray("files")
            return (0 until array.length()).map { i -> array.getJSONObject(i).let {
                ModFile(it.getString("path"), it.getLong("size"), it.getString("sha256"))
            } }
        }
        var transactions: List<String>
            get() = json.getJSONArray("transactions").let { a -> (0 until a.length()).map(a::getString) }
            set(value) { json.put("transactions", JSONArray(value)) }
        var active: Boolean
            get() = json.getBoolean("active")
            set(value) { json.put("active", value) }
        val archive get() = SafeFs.checked(dir, "$folder.zip")
    }
    private fun save(record: Record) = SafeFs.writeAtomic(SafeFs.checked(record.dir, "state.json"), record.json.toString())
    private fun read(dir: File): Record {
        ModFolderPolicy.validate(dir.name)
        val file = SafeFs.checked(dir, "state.json")
        check(file.isFile && file.length() in 1..256L * 1024) { "Mod kaydı bozuk" }
        val record = Record(dir, JSONObject(SafeFs.readText(file)))
        check(record.json.getInt("schema") == 1 && record.folder == dir.name)
        validId(record.id)
        require(record.transactions.size <= 20); record.transactions.forEach(::validId)
        require(record.files.size in 1..Limits.ENTRIES)
        record.files.forEach {
            require(PathPolicy.relative(it.path, pkg) == it.path && it.path != GameTarget.RELATIVE_RESOURCES)
            require(it.size in 0..Limits.FILE_BYTES && it.sha256.matches(Regex("[0-9a-f]{64}")))
        }
        require(record.files.sumOf { it.size } <= Limits.TOTAL_BYTES)
        require(record.files.map { it.path.lowercase(Locale.ROOT) }.distinct().size == record.files.size)
        require(record.json.getString("archiveHash").matches(Regex("[0-9a-f]{64}")))
        return record
    }
    private fun records(home: File): List<Record> {
        val names = home.list() ?: error("mods klasörü okunamadı")
        require(names.size <= 100) { "mods klasörü sınırı aşıldı" }
        val result = names.filterNot { it.startsWith('.') }.mapNotNull { name ->
            val dir = SafeFs.checked(home, name)
            if (dir.isDirectory && SafeFs.checked(dir, "state.json").exists()) read(dir) else null
        }
        require(result.size <= 20 && result.map { it.id }.distinct().size == result.size)
        return result
    }
    private fun find(home: File, id: String): Record = records(home).firstOrNull { it.id == validId(id) } ?: error("Mod kaydı bulunamadı")
    private fun engine(record: Record) = TransactionEngine(storage,
        "${GameTarget.RELATIVE_RESOURCES}/mods/${record.folder}/backup", beforeMutation, checkpoint)
    private fun backup(record: Record) = SafeFs.checked(record.dir, "backup").also(SafeFs::mkdir)

    /** v0.3 kimlikli yedekleri kopyalamadan aynı disk üzerinde taşır; yarıda kalsa yeniden denenir. */
    private fun migrate(record: Record) {
        val backup = backup(record)
        record.transactions.forEach { id ->
            val target = SafeFs.checked(backup, id)
            if (!target.exists()) {
                val old = SafeFs.checked(root, ".backup/modloader-v1/$id")
                check(old.isDirectory) { "Yedek işlemi bulunamadı: $id" }
                val journal = Journal.read(SafeFs.checked(old, "journal.json"), pkg, id)
                check(journal.plan.entries.map { it.file }.toSet() == record.files.toSet()) { "Eski yedek bu mod ile eşleşmiyor" }
                SafeFs.rename(old, target)
            }
        }
    }
    private fun reconcile(record: Record) {
        migrate(record)
        engine(record).recover(pkg) { _, _, _ -> }
        val active = record.transactions.any { id ->
            Journal.read(SafeFs.checked(backup(record), "$id/journal.json"), pkg, id).state == "COMMITTED"
        }
        if (record.active != active) { record.active = active; save(record) }
    }
    /** sha.json okunabilir bir özet; geri alma yetkisi her zaman doğrulanmış journal/yedektedir. */
    private fun checkSha(record: Record): Boolean {
        val file = SafeFs.checked(record.dir, "sha.json")
        val journals = record.transactions.map { Journal.read(SafeFs.checked(backup(record), "$it/journal.json"), pkg, it) }
        // Eski sürümde üst üste aktivasyon varsa aktifken son, tamamen geri alındığında ilk orijinal esastır.
        val source = if (record.active) journals.lastOrNull { it.state == "COMMITTED" } else journals.firstOrNull()
        val previous = if (file.exists()) {
            check(file.isFile && file.length() in 1..256L * 1024)
            SafeFs.readText(file)
        } else null
        val document = if (source != null || previous == null) JSONObject().apply {
            put("schema", 1); put("modId", record.id)
            put("transactionId", source?.plan?.id ?: JSONObject.NULL)
            val originals = source?.plan?.entries?.associate { it.file.path to it.originalHash }
            put("files", JSONArray().apply {
                record.files.forEach { entry ->
                    val original = if (originals != null) {
                        check(originals.containsKey(entry.path)); originals[entry.path]
                    } else SafeFs.hash(SafeFs.checked(root, entry.path))
                    put(JSONObject().apply {
                        put("path", entry.path); put("originalSha256", original ?: JSONObject.NULL)
                        put("modifiedSha256", entry.sha256)
                    })
                }
            })
        } else JSONObject(previous)
        check(document.getInt("schema") == 1 && document.getString("modId") == record.id)
        val entries = document.getJSONArray("files")
        check(entries.length() == record.files.size)
        val expectedFiles = record.files.associateBy { it.path }
        val seen = hashSetOf<String>()
        var mismatch = false
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val path = entry.getString("path")
            val expectedFile = expectedFiles[path] ?: error("Invalid SHA manifest path")
            check(seen.add(path) && entry.getString("modifiedSha256") == expectedFile.sha256)
            val original = if (entry.isNull("originalSha256")) null else entry.getString("originalSha256")
            check(original == null || original.matches(Regex("[0-9a-f]{64}")))
            val expected = if (record.active) expectedFile.sha256 else original
            val target = SafeFs.checked(root, path)
            // Aşırı büyüyen ya da dosya yerine dizin olan hedef de değişikliktir; okuma izni hatası ise issue kalır.
            if (target.exists() && (!target.isFile || target.length() > Limits.TOTAL_BYTES)) mismatch = true
            else if (SafeFs.hash(target) != expected) mismatch = true
        }
        val serialized = document.toString()
        if (serialized != previous) SafeFs.writeAtomic(file, serialized)
        record.shaMismatch = mismatch
        return mismatch
    }
    private fun summary(record: Record, issue: String? = null) = JSONObject().apply {
        put("id", record.id); put("folder", record.folder); put("active", record.active)
        put("issue", issue ?: JSONObject.NULL)
        put("shaMismatch", record.shaMismatch)
    }
    fun list(): String = locked { home ->
        JSONArray().apply {
            records(home).forEach { record ->
                var issue: String? = null
                try { reconcile(record); checkSha(record) } catch (e: Exception) { issue = (e.message ?: "Recovery required").take(500) }
                put(summary(record, issue))
            }
        }.toString()
    }

    fun store(fd: ParcelFileDescriptor, id: String, legacyJson: String, progress: (String, Long, Long) -> Unit): String = locked { home ->
        validId(id)
        records(home).firstOrNull { it.id == id }?.let { reconcile(it); return@locked summary(it).toString() }
        check(records(home).size < 20) { "En fazla 20 mod saklanabilir" }
        require(legacyJson.length <= 2048)
        val legacyArray = JSONArray(legacyJson)
        require(legacyArray.length() <= 20)
        val legacy = (0 until legacyArray.length()).map { validId(legacyArray.getString(it)) }.distinct()
        val stat = Os.fstat(fd.fileDescriptor)
        require(OsConstants.S_ISREG(stat.st_mode) && stat.st_size in 1..Limits.ZIP_BYTES)
        check(home.usableSpace >= stat.st_size + Limits.RESERVE_BYTES) { "Mod arşivi için alan yok" }
        val incomingName = ".incoming-$id"
        SafeFs.removeTree(home, incomingName)
        val incoming = SafeFs.checked(home, incomingName).also(SafeFs::mkdir)
        try {
            val archive = SafeFs.checked(incoming, "archive.zip")
            var done = 0L
            ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(fd.fileDescriptor)).use { input ->
                SafeFs.copy(input, archive, Limits.ZIP_BYTES) { n -> done += n; progress("Mod arşivleniyor", done, stat.st_size) }
            }
            val metadata = ModMetadataReader.read(archive)
            val verify = SafeFs.checked(incoming, "verify").also(SafeFs::mkdir)
            val files = ZipModParser().extract(archive, pkg, verify) { n, total -> progress("Mod doğrulanıyor", n, total) }
            SafeFs.removeTree(incoming, "verify")
            val base = ModFolderPolicy.slug(metadata.name)
            val existingNames = home.list().orEmpty().map { it.lowercase(Locale.ROOT) }.toSet()
            val folder = if (base.lowercase(Locale.ROOT) !in existingNames) base else "$base-${id.take(8)}"
            ModFolderPolicy.validate(folder)
            check(folder.lowercase(Locale.ROOT) !in existingNames) { "Mod adı çakışması" }
            SafeFs.rename(archive, SafeFs.checked(incoming, "$folder.zip"))
            val data = JSONObject().apply {
                put("schema", 1); put("id", id); put("folder", folder); put("name", metadata.name)
                put("active", false); put("transactions", JSONArray(legacy))
                put("archiveHash", SafeFs.hash(SafeFs.checked(incoming, "$folder.zip")))
                put("files", JSONArray().apply { files.forEach { f -> put(JSONObject().apply {
                    put("path", f.path); put("size", f.size); put("sha256", f.sha256)
                }) } })
            }
            SafeFs.writeAtomic(SafeFs.checked(incoming, "state.json"), data.toString())
            SafeFs.mkdir(SafeFs.checked(incoming, "backup"))
            val destination = SafeFs.checked(home, folder)
            SafeFs.rename(incoming, destination)
            val record = read(destination)
            reconcile(record)
            checkSha(record)
            summary(record).toString()
        } finally { SafeFs.removeTree(home, incomingName) }
    }

    private fun toggle(home: File, record: Record, enabled: Boolean, progress: (String, Long, Long) -> Unit) {
        reconcile(record)
        if (record.active == enabled) return
        if (checkSha(record)) throw EngineFailure(7, "SHA_MISMATCH")
        if (enabled) {
            val paths = record.files.map { it.path.lowercase(Locale.ROOT) }.toSet()
            records(home).filter { it.id != record.id }.forEach { other ->
                reconcile(other)
                if (other.active && other.files.any { it.path.lowercase(Locale.ROOT) in paths })
                    throw EngineFailure(8, "MOD_CONFLICT")
            }
            check(SafeFs.hash(record.archive) == record.json.getString("archiveHash")) { "Saklanan ZIP değişmiş" }
            // Önceki aktivasyon geri alınmıştır. Yeni aktivasyon kendi orijinallerini yedekler.
            record.transactions = emptyList(); save(record)
            SafeFs.removeTree(record.dir, "backup"); backup(record)
            val txEngine = engine(record)
            val prepared = ParcelFileDescriptor.open(record.archive, ParcelFileDescriptor.MODE_READ_ONLY).use {
                JSONObject(txEngine.prepare(it, pkg, progress))
            }
            val txId = prepared.getString("id")
            record.transactions = listOf(txId); save(record) // Apply'dan önce kalıcı bağ.
            checkpoint("MOD_LINKED")
            txEngine.apply(pkg, txId, true, progress)
            checkpoint("MOD_COMMITTED")
            record.active = true; save(record)
        } else {
            val paths = record.files.map { it.path.lowercase(Locale.ROOT) }.toSet()
            fun lastCommit(mod: Record): Long = mod.transactions.mapNotNull { id ->
                val file = SafeFs.checked(backup(mod), "$id/journal.json")
                if (Journal.read(file, pkg, id).state == "COMMITTED") file.lastModified() else null
            }.maxOrNull() ?: 0L
            // Eski sürümden gelen üst üste uygulanmış modlar sessizce bozulmasın.
            records(home).filter { it.id != record.id }.forEach { other ->
                reconcile(other)
                if (other.active && other.files.any { it.path.lowercase(Locale.ROOT) in paths } && lastCommit(other) >= lastCommit(record))
                    throw EngineFailure(8, "MOD_CONFLICT")
            }
            record.transactions.asReversed().forEach { engine(record).restore(pkg, it, progress) }
            checkpoint("MOD_RESTORED")
            record.active = false; save(record)
        }
    }
    fun setActive(id: String, enabled: Boolean, progress: (String, Long, Long) -> Unit): String = locked { home ->
        val record = find(home, id)
        toggle(home, record, enabled, progress)
        checkSha(record)
        summary(record).toString()
    }
    fun delete(id: String, progress: (String, Long, Long) -> Unit): String = locked { home ->
        validId(id)
        val record = records(home).firstOrNull { it.id == id } ?: return@locked "Deleted"
        toggle(home, record, false, progress)
        // Rename sonrası çökme olursa sonraki açılış .deleted klasörünü temizler.
        val deleted = ".deleted-$id"
        SafeFs.rename(record.dir, SafeFs.checked(home, deleted))
        SafeFs.removeTree(home, deleted)
        "Deleted"
    }
    fun warningAction(id: String, recover: Boolean, progress: (String, Long, Long) -> Unit): String = locked { home ->
        validId(id)
        val record = records(home).firstOrNull { it.id == id } ?: return@locked "Deleted"
        if (recover) {
            reconcile(record)
            val paths = record.files.map { it.path.lowercase(Locale.ROOT) }.toSet()
            records(home).filter { it.id != id }.forEach { other ->
                reconcile(other)
                if (other.active && other.files.any { it.path.lowercase(Locale.ROOT) in paths }) throw EngineFailure(8, "MOD_CONFLICT")
            }
            if (record.transactions.isEmpty()) throw EngineFailure(11, "NO_BASE_BACKUP")
            record.transactions.asReversed().forEach { engine(record).restoreBase(pkg, it, progress) }
            reconcile(record); checkSha(record)
            summary(record).toString()
        } else {
            // Do not reconcile here: recovery would write game files before a discard.
            // Incomplete mutations must retain their recovery journal and backups.
            migrate(record)
            record.transactions.forEach { txId ->
                val journal = Journal.read(SafeFs.checked(backup(record), "$txId/journal.json"), pkg, txId)
                if (journal.state in setOf("APPLYING", "ROLLING_BACK", "FORCE_RESTORING"))
                    throw EngineFailure(6, "RECOVERY_REQUIRED")
            }
            val deleted = ".deleted-$id"
            SafeFs.rename(record.dir, SafeFs.checked(home, deleted))
            SafeFs.removeTree(home, deleted)
            "Deleted"
        }
    }
    fun openArchive(id: String): ParcelFileDescriptor = locked { home ->
        val record = find(home, id)
        check(SafeFs.hash(record.archive) == record.json.getString("archiveHash")) { "Arşiv bütünlüğü bozuk" }
        ParcelFileDescriptor.open(record.archive, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
