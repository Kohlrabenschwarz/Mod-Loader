package dev.modloader.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dev.modloader.domain.*
import dev.modloader.engine.ModMetadata
import dev.modloader.engine.ModMetadataReader
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

data class LibraryMod(val id: String, val archive: File, val metadata: ModMetadata, val icon: Bitmap?,
    val active: Boolean = false, val archived: Boolean = false, val folder: String = "", val issue: String? = null,
    val shaMismatch: Boolean = false, val warningHidden: Boolean = false,
    val damagedArchive: Boolean = false, val changes: List<IntegrityChange> = emptyList(),
    val backupAt: Long = 0, val requiredBytes: Long = 0, val availableBytes: Long = 0,
    val archiveSha256: String? = null)

class ModLibrary(private val context: Context) {
    private data class Cached(val length: Long, val modified: Long, val mod: LibraryMod)
    private val cache = mutableMapOf<String, Cached>()
    private val directory = File(context.filesDir, "mods").apply { check(mkdirs() || isDirectory) }
    private val staging = File(context.cacheDir, "mod-imports").apply { check(mkdirs() || isDirectory) }
    fun archiveLocation(id: String): File {
        require(UUID.fromString(id).toString() == id)
        return File(directory, "$id.zip")
    }
    private fun read(file: File): LibraryMod {
        val metadata = ModMetadataReader.read(file)
        val icon = metadata.icon?.let { bytes ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (options.outWidth !in 1..2048 || options.outHeight !in 1..2048) throw EngineFailure(9, "ICON_DIMENSIONS")
            options.inJustDecodeBounds = false
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 256) options.inSampleSize *= 2
            requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) { "İkon çözümlenemedi" }
        }
        return LibraryMod(file.nameWithoutExtension, file, metadata.copy(icon = null), icon,
            archiveSha256 = ModUpdatePolicy.archiveHash(file))
    }
    fun delete(id: String) {
        require(UUID.fromString(id).toString() == id)
        val file = File(directory, "$id.zip")
        check(!file.exists() || file.delete()) { "Mod arşivi silinemedi" }
        cache.remove(id)
    }
    fun load(): List<LibraryMod> = directory.listFiles().orEmpty()
        .filter { it.extension == "zip" }.sortedByDescending { it.lastModified() }.map { file ->
            val id = file.nameWithoutExtension
            val cached = cache[id]
            if (cached != null && cached.length == file.length() && cached.modified == file.lastModified()) cached.mod
            else {
                val mod = try { read(file) } catch (_: Exception) { unavailable(id, file) }
                cache[id] = Cached(file.length(), file.lastModified(), mod)
                mod
            }
        }

    fun invalidate(id: String) { cache.remove(id) }
    fun unavailable(id: String, archive: File = archiveLocation(id)) = LibraryMod(id, archive,
        ModMetadata("ZIP · ${id.take(8)}", "", "", "", emptyList(), null), null, damagedArchive = true)

    // URI'nin geçici erişimi yerine uygulamanın özel alanında kalıcı ZIP saklanır.
    suspend fun import(uri: Uri, progress: (Long) -> Unit): LibraryMod = commitImport(stageImport(uri, progress))

    /** Pending imports are never visible to load()/synchronization before user approval. */
    suspend fun stageImport(uri: Uri, progress: (Long) -> Unit): LibraryMod =
        context.contentResolver.openInputStream(uri)?.use { stageImport(it, progress) } ?: error("ZIP açılamadı")

    suspend fun stageImport(input: InputStream, progress: (Long) -> Unit): LibraryMod {
        val id = UUID.randomUUID().toString()
        val temp = File(staging, "$id.part")
        try {
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                var lastProgress = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    done += n
                    if (done > Limits.ZIP_BYTES) throw EngineFailure(9, "ZIP_SIZE")
                    if (staging.usableSpace < n + Limits.RESERVE_BYTES) throw EngineFailure(2, "NO_SPACE")
                    output.write(buffer, 0, n)
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastProgress >= 100) { progress(done); lastProgress = now }
                }
                output.fd.sync()
                progress(done)
            }
            val parsed = read(temp)
            val stage = File(staging, "$id.check").apply { check(mkdir()) }
            val operationContext = currentCoroutineContext()
            try { ZipModParser().extract(temp, GameTarget.PACKAGE_NAME, stage) { _, _ -> operationContext.ensureActive() } }
            finally { stage.deleteRecursively() }
            val archive = File(staging, "$id.zip")
            check(temp.renameTo(archive)) { "Mod kaydedilemedi" }
            return parsed.copy(id = id, archive = archive)
        } finally { temp.delete() }
    }
    fun discardImport(mod: LibraryMod) {
        require(mod.archive == File(staging, "${mod.id}.zip"))
        check(!mod.archive.exists() || mod.archive.delete())
    }
    fun commitImport(mod: LibraryMod, targetId: String = mod.id, expectedArchiveHash: String? = null): LibraryMod {
        require(mod.archive == File(staging, "${mod.id}.zip"))
        val destination = archiveLocation(targetId)
        if (expectedArchiveHash == null) check(!destination.exists())
        else if (!destination.isFile || ModUpdatePolicy.archiveHash(destination) != expectedArchiveHash)
            throw EngineFailure(16, "OVERWRITE_PREVIEW_STALE")
        check(ModUpdatePolicy.archiveHash(mod.archive) == mod.archiveSha256) { "Pending archive changed" }
        android.system.Os.rename(mod.archive.path, destination.path)
        invalidate(targetId)
        return mod.copy(id = targetId, archive = destination)
    }
}
