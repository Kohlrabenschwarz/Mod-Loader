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
import java.util.UUID

data class LibraryMod(val id: String, val archive: File, val metadata: ModMetadata, val icon: Bitmap?,
    val active: Boolean = false, val archived: Boolean = false, val folder: String = "", val issue: String? = null,
    val shaMismatch: Boolean = false, val warningHidden: Boolean = false)

class ModLibrary(private val context: Context) {
    private val directory = File(context.filesDir, "mods").apply { check(mkdirs() || isDirectory) }
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
        return LibraryMod(file.nameWithoutExtension, file, metadata.copy(icon = null), icon)
    }
    fun delete(id: String) {
        require(UUID.fromString(id).toString() == id)
        val file = File(directory, "$id.zip")
        check(!file.exists() || file.delete()) { "Mod arşivi silinemedi" }
    }
    fun load(): List<LibraryMod> = directory.listFiles().orEmpty()
        .filter { it.extension == "zip" }.sortedByDescending { it.lastModified() }.map(::read)

    // URI'nin geçici erişimi yerine uygulamanın özel alanında kalıcı ZIP saklanır.
    suspend fun import(uri: Uri, progress: (Long) -> Unit): LibraryMod {
        check(directory.listFiles().orEmpty().count { it.extension == "zip" } < 20) { "Kütüphane 20 paket sınırına ulaştı" }
        val id = UUID.randomUUID().toString()
        val temp = File(directory, "$id.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastProgress = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer); if (n < 0) break
                        done += n
                        if (done > Limits.ZIP_BYTES) throw EngineFailure(9, "ZIP_SIZE")
                        if (directory.usableSpace < n + Limits.RESERVE_BYTES) throw EngineFailure(2, "NO_SPACE")
                        output.write(buffer, 0, n)
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastProgress >= 100) { progress(done); lastProgress = now }
                    }
                    output.fd.sync()
                    progress(done)
                }
            } ?: error("ZIP açılamadı")
            val parsed = read(temp)
            val stage = File(directory, "$id.check").apply { check(mkdir()) }
            val operationContext = currentCoroutineContext()
            try { ZipModParser().extract(temp, GameTarget.PACKAGE_NAME, stage) { _, _ -> operationContext.ensureActive() } }
            finally { stage.deleteRecursively() }
            val archive = File(directory, "$id.zip")
            check(temp.renameTo(archive)) { "Mod kaydedilemedi" }
            return parsed.copy(id = id, archive = archive)
        } finally { temp.delete() }
    }
}
