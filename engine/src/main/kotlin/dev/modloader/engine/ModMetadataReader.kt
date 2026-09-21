package dev.modloader.engine

import dev.modloader.domain.*
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipFile

data class ModMetadata(val name: String, val creator: String, val description: String,
    val version: String, val affectedFiles: List<String>, val icon: ByteArray?)

/** Aynı okuyucu normal uygulamada ve privileged serviste çalışır; metadata hedef yolu belirleyemez. */
object ModMetadataReader {
    private val presentation = setOf("info.json", "icon.png", "icon.jpg", "icon.webp")
    fun read(file: File): ModMetadata {
        ZipPreflight.check(file)
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().toList()
            require(entries.map { it.name.lowercase(Locale.ROOT) }.distinct().size == entries.size) { "Yinelenen ZIP girdisi" }
            fun bytes(name: String, limit: Int): ByteArray {
                val e = zip.getEntry(name) ?: error("ZIP içinde $name bulunamadı")
                if (e.isDirectory || e.size !in 1..limit.toLong()) throw EngineFailure(9, "METADATA_SIZE")
                val result = zip.getInputStream(e).use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        require(output.size() + n <= limit) { "$name sınırı aşıldı" }
                        output.write(buffer, 0, n)
                    }
                    output.toByteArray()
                }
                require(result.size.toLong() == e.size && CRC32().apply { update(result) }.value == e.crc) { "$name CRC hatası" }
                return result
            }
            val infoBytes = bytes("info.json", 32 * 1024)
            val infoText = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(infoBytes)).toString()
            val info = JSONObject(infoText)
            require(info.optInt("schemaVersion", 1) == 1) { "Desteklenmeyen info.json sürümü" }
            fun field(key: String, max: Int, multiline: Boolean = false): String {
                val value = info.get(key)
                require(value is String) { "Geçersiz $key" }
                return try { UntrustedTextPolicy.display(value, max, multiline) }
                catch (e: IllegalArgumentException) { throw IllegalArgumentException("Geçersiz $key", e) }
            }
            val declared = info.getJSONArray("affectedFiles")
            require(declared.length() in 1..Limits.ENTRIES) { "affectedFiles boş veya çok uzun" }
            val affected = (0 until declared.length()).map { i ->
                val value = declared.get(i); require(value is String)
                PathPolicy.relative("payload/$value", GameTarget.PACKAGE_NAME)
                    .removePrefix(GameTarget.RELATIVE_RESOURCES + "/")
            }
            require(affected.map { it.lowercase(Locale.ROOT) }.distinct().size == affected.size) { "affectedFiles tekrarlı" }
            val payload = entries.filterNot { it.isDirectory || it.name in presentation }
            payload.forEach { entry ->
                if (entry.size !in 0..Limits.FILE_BYTES) throw EngineFailure(9, "FILE_SIZE")
                val header = ByteArray(UnityBundlePolicy.HEADER_SIZE)
                var read = 0
                zip.getInputStream(entry).use { input ->
                    while (read < header.size) {
                        val count = input.read(header, read, header.size - read)
                        if (count < 0) break
                        read += count
                    }
                }
                UnityBundlePolicy.requireHeader(header.copyOf(read))
            }
            if (payload.sumOf { it.size } > Limits.TOTAL_BYTES) throw EngineFailure(9, "PAYLOAD_SIZE")
            val actual = payload.map { PathPolicy.relative(it.name, GameTarget.PACKAGE_NAME).removePrefix(GameTarget.RELATIVE_RESOURCES + "/") }
            require(actual.map { it.lowercase(Locale.ROOT) }.distinct().size == actual.size) { "Çakışan mod yolları" }
            require(actual.toSet() == affected.toSet()) { "affectedFiles ile ZIP içeriği uyuşmuyor" }
            val iconName = if (info.has("icon")) field("icon", 32) else null
            require(iconName == null || iconName in presentation - "info.json") { "İkon icon.png, icon.jpg veya icon.webp olmalı" }
            return ModMetadata(field("name", 100), field("creator", 100), field("description", 2000, multiline = true),
                if (info.has("version")) field("version", 40) else "1.0", affected,
                iconName?.let { bytes(it, Limits.ICON_BYTES) })
        }
    }
}
