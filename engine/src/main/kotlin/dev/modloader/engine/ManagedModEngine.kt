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
                ModArchiveUpdate.recover(home, ::validateUpdateDirectory)
                block(home)
        }
    }
    private data class Record(val dir: File, val json: JSONObject) {
        var shaMismatch: Boolean = false
        var changes: List<IntegrityChange> = emptyList()
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
    private fun read(dir: File, expectedFolder: String = dir.name): Record {
        ModFolderPolicy.validate(expectedFolder)
        val file = SafeFs.checked(dir, "state.json")
        check(file.isFile && file.length() in 1..256L * 1024) { "Mod kaydı bozuk" }
        val record = Record(dir, JSONObject(SafeFs.readText(file)))
        check(record.json.getInt("schema") == 1 && record.folder == expectedFolder)
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
        val result = names.filterNot { it.startsWith('.') }.mapNotNull { name ->
            val dir = SafeFs.checked(home, name)
            if (dir.isDirectory && SafeFs.checked(dir, "state.json").exists()) read(dir) else null
        }
        require(result.map { it.id }.distinct().size == result.size)
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
        val changes = mutableListOf<IntegrityChange>()
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
            val actual = if (target.exists() && (!target.isFile || target.length() > Limits.TOTAL_BYTES)) "unreadable"
                else SafeFs.hash(target)
            if (actual != expected) changes.add(IntegrityChange(path.removePrefix(GameTarget.RELATIVE_RESOURCES + "/"), expected, actual))
        }
        val serialized = document.toString()
        if (serialized != previous) SafeFs.writeAtomic(file, serialized)
        record.changes = changes
        record.shaMismatch = changes.isNotEmpty()
        return record.shaMismatch
    }
    private fun summary(record: Record, issue: String? = null) = JSONObject().apply {
        put("id", record.id); put("folder", record.folder); put("active", record.active)
        put("archiveSha256", record.json.getString("archiveHash"))
        put("issue", issue ?: JSONObject.NULL)
        put("shaMismatch", record.shaMismatch)
        put("changes", JSONArray().apply { record.changes.forEach { change -> put(JSONObject().apply {
            put("path", change.path); put("expected", change.expected ?: JSONObject.NULL); put("actual", change.actual ?: JSONObject.NULL)
        }) } })
        val backupAt = record.json.optLong("backupAt", 0)
        put("backupAt", backupAt)
        val originals = record.files.map { SafeFs.checked(root, it.path).let { f -> if (f.isFile) f.length() else 0L } }
        val payload = record.files.sumOf { it.size }
        // Additional free space at peak: ZIP + staging, or staging + originals + rollback reserve.
        put("requiredBytes", maxOf(record.archive.length() + payload,
            payload + originals.sum() + (originals.maxOrNull() ?: 0L)) + Limits.RESERVE_BYTES)
        put("availableBytes", record.dir.usableSpace)
    }
    fun list(): String = locked { home ->
        JSONArray().apply {
            val names = home.list() ?: error("mods klasörü okunamadı")
            val readable = mutableListOf<Record>()
            var invalidRecord = false
            names.filterNot { it.startsWith('.') }.forEach { name ->
                val dir = SafeFs.checked(home, name)
                if (!dir.isDirectory || !SafeFs.checked(dir, "state.json").exists()) return@forEach
                val record = try { read(dir) } catch (_: Exception) {
                    invalidRecord = true
                    // Listing is tolerant; mutations still use strict records() and cannot bypass unknown conflicts.
                    put(JSONObject().apply {
                        put("id", UUID.nameUUIDFromBytes(name.toByteArray(Charsets.UTF_8)).toString())
                        put("folder", name); put("active", false); put("issue", "INVALID_RECORD")
                        put("shaMismatch", false)
                    })
                    return@forEach
                }
                readable.add(record)
            }
            require(readable.map { it.id }.distinct().size == readable.size)
            readable.forEach { record ->
                var issue: String? = if (invalidRecord) "INVALID_LIBRARY" else null
                try {
                    // Unknown state may overlap a pending transaction: listing must not resume writes in that case.
                    if (!invalidRecord) reconcile(record)
                    checkSha(record)
                } catch (e: Exception) { issue = (e.message ?: "Recovery required").take(500) }
                try { put(summary(record, issue)) } catch (_: Exception) {
                    put(JSONObject().put("id", record.id).put("folder", record.folder).put("active", record.active)
                        .put("issue", "INVALID_RECORD").put("shaMismatch", record.shaMismatch))
                }
            }
        }.toString()
    }

    fun store(fd: ParcelFileDescriptor, id: String, legacyJson: String, progress: (String, Long, Long) -> Unit): String = locked { home ->
        validId(id)
        records(home).firstOrNull { it.id == id }?.let { reconcile(it); return@locked summary(it).toString() }
        require(legacyJson.length <= 2048)
        val legacyArray = JSONArray(legacyJson)
        require(legacyArray.length() <= 20)
        val legacy = (0 until legacyArray.length()).map { validId(legacyArray.getString(it)) }.distinct()
        val stat = Os.fstat(fd.fileDescriptor)
        require(OsConstants.S_ISREG(stat.st_mode) && stat.st_size in 1..Limits.ZIP_BYTES)
        if (home.usableSpace < stat.st_size + Limits.RESERVE_BYTES) throw EngineFailure(2, "NO_ARCHIVE_SPACE")
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

    private fun validateUpdateDirectory(dir: File, folder: String, id: String, hash: String) {
        val record = read(dir, folder)
        check(record.id == id && !record.active && record.json.getString("archiveHash") == hash)
        check(SafeFs.hash(record.archive) == hash) { "Update archive changed" }
    }

    fun update(fd: ParcelFileDescriptor, id: String, expectedArchiveHash: String, manifest: String,
        progress: (String, Long, Long) -> Unit): String = locked { home ->
        val release = ModUpdateJson.release(manifest)
        ModUpdatePolicy.sha256(expectedArchiveHash)
        val record = find(home, id)
        reconcile(record)
        val currentHash = record.json.getString("archiveHash")
        // A reply lost after publication must not deactivate or replace a successfully updated mod again.
        if (currentHash == release.zipSha256) {
            check(SafeFs.hash(record.archive) == currentHash)
            checkSha(record)
            return@locked summary(record).toString()
        }
        if (currentHash != expectedArchiveHash) throw EngineFailure(16, "UPDATE_PREVIEW_STALE")
        if (checkSha(record)) throw EngineFailure(7, "SHA_MISMATCH")
        check(SafeFs.hash(record.archive) == currentHash) { "Stored archive changed" }
        val previousMetadata = ModMetadataReader.read(record.archive)
        val source = previousMetadata.update ?: throw EngineFailure(16, "UPDATE_SOURCE_MISSING")
        if (source.modId != release.modId || release.versionCode <= requireNotNull(previousMetadata.versionCode))
            throw EngineFailure(16, "UPDATE_IDENTITY_OR_VERSION")
        replaceArchive(home, record, fd, release.zipSha256, release.zipSize, null, progress) { metadata ->
            if (metadata.modId != release.modId || metadata.versionCode != release.versionCode ||
                metadata.version != release.version || metadata.update != source)
                throw EngineFailure(16, "UPDATE_PACKAGE_MISMATCH")
        }
    }

    fun overwrite(fd: ParcelFileDescriptor, id: String, expectedArchiveHash: String, incomingHash: String,
        developerMode: Boolean, publicationBaseUrl: String, progress: (String, Long, Long) -> Unit): String = locked { home ->
        ModUpdatePolicy.sha256(expectedArchiveHash); ModUpdatePolicy.sha256(incomingHash)
        val base = if (developerMode) DeveloperPublicationPolicy.baseUrl(publicationBaseUrl) else null
        val record = find(home, id)
        reconcile(record)
        val currentHash = record.json.getString("archiveHash")
        check(SafeFs.hash(record.archive) == currentHash) { "Stored archive changed" }
        if (currentHash == incomingHash) {
            // Same bytes or a lost successful reply: never replace an already published version twice.
            if (base != null) writeDeveloperFiles(record, base)
            checkSha(record)
            return@locked summary(record).toString()
        }
        if (currentHash != expectedArchiveHash) throw EngineFailure(16, "OVERWRITE_PREVIEW_STALE")
        if (checkSha(record)) throw EngineFailure(7, "SHA_MISMATCH")
        val previous = ModMetadataReader.read(record.archive)
        replaceArchive(home, record, fd, incomingHash, null, base, progress) { incoming ->
            if (!ModImportPolicy.collides(incoming.name, incoming.modId, previous.name, previous.modId))
                throw EngineFailure(16, "OVERWRITE_IDENTITY_MISMATCH")
        }
    }

    fun writeDeveloperFiles(id: String, publicationBaseUrl: String): String = locked { home ->
        val record = find(home, id)
        writeDeveloperFiles(record, publicationBaseUrl)
        "Generated"
    }
    private fun writeDeveloperFiles(record: Record, publicationBaseUrl: String) {
        val hash = record.json.getString("archiveHash")
        check(SafeFs.hash(record.archive) == hash) { "Stored archive changed" }
        ModDeveloperFiles.write(record.dir, record.id, record.archive, ModMetadataReader.read(record.archive),
            record.files, hash, publicationBaseUrl)
    }

    /** Both approved imports and network updates use the same recoverable directory exchange. */
    private fun replaceArchive(home: File, record: Record, fd: ParcelFileDescriptor, incomingHash: String,
        expectedSize: Long?, developerBaseUrl: String?, progress: (String, Long, Long) -> Unit,
        validateMetadata: (ModMetadata) -> Unit): String {
        val stat = Os.fstat(fd.fileDescriptor)
        require(OsConstants.S_ISREG(stat.st_mode) && stat.st_size in 1..Limits.ZIP_BYTES)
        if (expectedSize != null && stat.st_size != expectedSize) throw EngineFailure(14, "UPDATE_SIZE_MISMATCH")
        val size = stat.st_size
        if (home.usableSpace < size + Limits.RESERVE_BYTES) throw EngineFailure(2, "NO_ARCHIVE_SPACE")
        val currentHash = record.json.getString("archiveHash")
        val operationName = ".update-${record.id}"
        val operation = SafeFs.checked(home, operationName).also(SafeFs::mkdir)
        val incoming = SafeFs.checked(operation, "new").also(SafeFs::mkdir)
        val journal = SafeFs.checked(operation, "journal.json")
        try {
            val archive = SafeFs.checked(incoming, "${record.folder}.zip")
            var done = 0L
            ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(fd.fileDescriptor)).use { input ->
                SafeFs.copy(input, archive, size) { n -> done += n; progress("Mod arşivleniyor", done, size) }
            }
            if (archive.length() != size || SafeFs.hash(archive) != incomingHash)
                throw EngineFailure(14, "UPDATE_HASH_MISMATCH")
            val metadata = ModMetadataReader.read(archive)
            validateMetadata(metadata)
            val verify = SafeFs.checked(incoming, "verify").also(SafeFs::mkdir)
            val files = ZipModParser().extract(archive, pkg, verify) { n, total -> progress("Mod doğrulanıyor", n, total) }
            SafeFs.removeTree(incoming, "verify")
            val newPaths = files.map { it.path.lowercase(Locale.ROOT) }.toSet()
            records(home).filter { it.id != record.id }.forEach { other ->
                reconcile(other)
                if (other.active && other.files.any { it.path.lowercase(Locale.ROOT) in newPaths })
                    throw conflict(other, newPaths)
            }
            if (developerBaseUrl != null)
                ModDeveloperFiles.write(incoming, record.id, archive, metadata, files, incomingHash, developerBaseUrl)
            checkpoint("UPDATE_VERIFIED")
            toggle(home, record, false, progress)
            if (checkSha(record)) throw EngineFailure(7, "SHA_MISMATCH")
            val data = JSONObject().apply {
                put("schema", 1); put("id", record.id); put("folder", record.folder); put("name", metadata.name)
                put("active", false); put("transactions", JSONArray()); put("archiveHash", incomingHash)
                put("files", JSONArray().apply { files.forEach { f -> put(JSONObject().apply {
                    put("path", f.path); put("size", f.size); put("sha256", f.sha256)
                }) } })
            }
            SafeFs.writeAtomic(SafeFs.checked(incoming, "state.json"), data.toString())
            SafeFs.mkdir(SafeFs.checked(incoming, "backup"))
            checkSha(read(incoming, record.folder))
            SafeFs.writeAtomic(journal, JSONObject().apply {
                put("schema", 1); put("id", record.id); put("folder", record.folder)
                put("oldHash", currentHash); put("newHash", incomingHash)
            }.toString())
            checkpoint("UPDATE_READY")
            ModArchiveUpdate.recover(home, ::validateUpdateDirectory, checkpoint)
            val updated = find(home, record.id)
            checkSha(updated)
            return summary(updated).toString()
        } finally {
            if (operation.exists() && !journal.exists()) SafeFs.removeTree(home, operationName)
        }
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
                    throw conflict(other, paths)
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
            record.transactions = listOf(txId)
            record.json.put("backupAt", System.currentTimeMillis())
            save(record) // Apply'dan önce kalıcı bağ.
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
                    throw conflict(other, paths)
            }
            record.transactions.asReversed().forEach { engine(record).restore(pkg, it, progress) }
            checkpoint("MOD_RESTORED")
            record.active = false; save(record)
        }
    }
    private fun conflict(other: Record, paths: Set<String>) = EngineFailure(8, "MOD_CONFLICT", listOf(ModConflict(
        try { UntrustedTextPolicy.display(other.json.getString("name"), 100) } catch (_: Exception) { other.folder },
        other.files.filter { it.path.lowercase(Locale.ROOT) in paths }
            .map { it.path.removePrefix(GameTarget.RELATIVE_RESOURCES + "/") })))
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
                if (other.active && other.files.any { it.path.lowercase(Locale.ROOT) in paths }) throw conflict(other, paths)
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
