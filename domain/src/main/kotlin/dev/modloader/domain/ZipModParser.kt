package dev.modloader.domain

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipFile

/** ZIP tamamen RAM'e alınmaz. Central directory, boyut, CRC ve gerçek açılan baytlar doğrulanır. */
class ZipModParser {
    fun extract(zip: File, pkg: String, stage: File, progress: (Long, Long) -> Unit): List<ModFile> {
        require(zip.length() in 1..Limits.ZIP_BYTES) { "ZIP boyutu sınır dışında" }
        require(stage.isDirectory && stage.list()?.isEmpty() == true) { "Boş staging dizini gerekli" }
        ZipPreflight.check(zip)
        ZipFile(zip).use { archive ->
            val entries = archive.entries().asSequence().take(Limits.ENTRIES + 1).toList()
            require(entries.size <= Limits.ENTRIES) { "Çok fazla ZIP girdisi" }
            val names = hashSetOf<String>()
            val fileNames = hashSetOf<String>()
            val wrapperDirs = setOf("Android/", "Android/data/", "Android/data/$pkg/", "$pkg/", "files/", "files/gamedata/", "payload/", "Resources/",
                "Bundles/", "files/gamedata/Resources/", "Android/data/$pkg/files/gamedata/Resources/", "$pkg/files/gamedata/Resources/",
                "Android/data/$pkg/files/", "Android/data/$pkg/files/gamedata/", "$pkg/files/", "$pkg/files/gamedata/")
            val mapped = entries.filterNot { entry ->
                // Sunum dosyaları oyuna kopyalanmaz; MetadataReader ayrıca doğrular.
                if (!entry.isDirectory && entry.name in ModPackageFiles.metadata) return@filterNot true
                (entry.isDirectory && entry.name in wrapperDirs).also { wrapper ->
                    if (wrapper) require(entry.size == 0L) { "Veri içeren dizin girdisi" }
                }
            }.map { entry ->
                val path = PathPolicy.relative(entry.name, pkg)
                require(names.add(path.lowercase(Locale.ROOT))) { "Yinelenen veya büyük/küçük harf çakışan yol" }
                require(entry.method == 0 || entry.method == 8) { "Desteklenmeyen sıkıştırma" }
                if (entry.isDirectory) {
                    require(entry.size == 0L) { "Veri içeren dizin girdisi" }
                } else {
                    // Payload adı ve uzantısı yorumlanmaz; uzantısız rastgele adlar aynen korunur.
                    require(path != GameTarget.RELATIVE_RESOURCES && entry.size in 0..Limits.FILE_BYTES) { "Dosya boyutu/yolu geçersiz" }
                    require(entry.crc >= 0 && entry.compressedSize >= 0) { "Eksik ZIP meta verisi" }
                    require(entry.size <= maxOf(1L, entry.compressedSize) * 200L) { "Şüpheli sıkıştırma oranı" }
                    fileNames.add(path.lowercase(Locale.ROOT))
                }
                entry to path
            }
            // files/a dosyası ile files/a/b birlikte olamaz.
            mapped.forEach { (_, path) ->
                var parent = path.substringBeforeLast('/', "")
                while (parent.isNotEmpty()) {
                    require(parent.lowercase(Locale.ROOT) !in fileNames) { "Dosya/dizin çakışması" }
                    parent = parent.substringBeforeLast('/', "")
                }
            }
            val total = mapped.filterNot { it.first.isDirectory }.sumOf { it.first.size }
            require(total <= Limits.TOTAL_BYTES) { "Açılan toplam boyut sınırı aşıldı" }
            if (stage.usableSpace < total + Limits.RESERVE_BYTES) throw EngineFailure(2, "NO_STAGING_SPACE")
            var done = 0L
            val files = mapped.filterNot { it.first.isDirectory }.map { (entry, path) ->
                val out = File(stage, path)
                check(out.parentFile!!.mkdirs() || out.parentFile!!.isDirectory)
                val digest = MessageDigest.getInstance("SHA-256")
                val crc = CRC32()
                val header = ByteArray(UnityBundlePolicy.HEADER_SIZE)
                var headerBytes = 0
                var bytes = 0L
                archive.getInputStream(entry).use { input ->
                    FileOutputStream(out).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            bytes += n; done += n
                            require(bytes <= entry.size && done <= Limits.TOTAL_BYTES) { "ZIP açılım sınırı aşıldı" }
                            if (headerBytes < header.size) {
                                val copied = minOf(n, header.size - headerBytes)
                                buffer.copyInto(header, headerBytes, 0, copied)
                                headerBytes += copied
                            }
                            output.write(buffer, 0, n); digest.update(buffer, 0, n); crc.update(buffer, 0, n)
                            progress(done, total)
                        }
                        output.fd.sync()
                    }
                }
                require(bytes == entry.size && crc.value == entry.crc) { "Bozuk ZIP: boyut/CRC uyuşmuyor" }
                UnityBundlePolicy.requireHeader(header.copyOf(headerBytes))
                ModFile(path, bytes, digest.digest().joinToString("") { "%02x".format(it) })
            }
            require(files.isNotEmpty()) { "ZIP içinde uygulanabilir dosya yok" }
            return files
        }
    }
}
