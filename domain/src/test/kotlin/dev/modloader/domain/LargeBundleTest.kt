package dev.modloader.domain

import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import org.junit.Assume.assumeTrue

class LargeBundleTest {
    @Test fun importsAnExact512MiBStoredBundleWithBoundedBuffers() {
        val root = Files.createTempDirectory("large-bundle").toFile()
        try {
            val bytes = 512L * 1024 * 1024
            assumeTrue("The boundary fixture needs room for ZIP and extracted payload", root.usableSpace > bytes * 2 + Limits.RESERVE_BYTES)
            val prefix = "UnityFS".toByteArray()
            val chunk = ByteArray(1024 * 1024)
            fun stream(consume: (ByteArray, Int) -> Unit) {
                consume(prefix, prefix.size)
                var remaining = bytes - prefix.size
                while (remaining > 0) {
                    val count = minOf(remaining, chunk.size.toLong()).toInt()
                    consume(chunk, count); remaining -= count
                }
            }
            val crc = CRC32()
            stream { buffer, count -> crc.update(buffer, 0, count) }
            val zip = File(root, "texture.zip")
            ZipOutputStream(zip.outputStream().buffered()).use { out ->
                out.putNextEntry(ZipEntry("payload/rgba32").apply {
                    method = ZipEntry.STORED; size = bytes; compressedSize = bytes; this.crc = crc.value
                })
                stream { buffer, count -> out.write(buffer, 0, count) }
                out.closeEntry()
            }
            assertTrue(zip.length() > bytes) // The archive limit must allow ZIP overhead.
            val stage = File(root, "stage").apply { mkdir() }
            var completed = 0L
            val files = ZipModParser().extract(zip, GameTarget.PACKAGE_NAME, stage) { done, total ->
                assertEquals(bytes, total); completed = done
            }
            assertEquals(bytes, completed)
            assertEquals(bytes, files.single().size)
            assertEquals(bytes, File(stage, "${GameTarget.RELATIVE_RESOURCES}/rgba32").length())
        } finally { root.deleteRecursively() }
    }

    @Test fun rejects512MiBPlusOneBeforeWritingAnyPayload() {
        val root = Files.createTempDirectory("oversized-bundle").toFile()
        try {
            val zip = File(root, "oversized.zip")
            ZipOutputStream(zip.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("payload/large")); out.write("UnityFSfixture".toByteArray()); out.closeEntry()
            }
            val data = zip.readBytes()
            val central = (0..data.size - 4).first { i ->
                data[i] == 0x50.toByte() && data[i+1] == 0x4b.toByte() && data[i+2] == 1.toByte() && data[i+3] == 2.toByte()
            }
            val claimed = 512L * 1024 * 1024 + 1
            java.io.RandomAccessFile(zip, "rw").use { out ->
                out.seek((central + 24).toLong())
                repeat(4) { shift -> out.write(((claimed shr (shift * 8)) and 255).toInt()) }
            }
            val stage = File(root, "stage").apply { mkdir() }
            assertFailsWith<IllegalArgumentException> { ZipModParser().extract(zip, GameTarget.PACKAGE_NAME, stage) { _, _ -> } }
            assertTrue(stage.listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }
}
