package dev.modloader.domain

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.junit.Assume.assumeTrue
import kotlin.test.*

/** İsteğe bağlı gerçek arşiv regresyonu. Mod arşivi kaynak projeye dahil edilmez. */
class ExternalArchiveTest {
    @Test fun streamsProvidedLargeArchiveWithoutChangingPayload() {
        val path = System.getenv("MODLOADER_TEST_ZIP")
        assumeTrue("Set MODLOADER_TEST_ZIP to run the real-archive regression", !path.isNullOrBlank())
        val zip = File(requireNotNull(path))
        val stage = Files.createTempDirectory("external-mod-check").toFile()
        try {
            val files = ZipModParser().extract(zip, GameTarget.PACKAGE_NAME, stage) { _, _ -> }
            assertTrue(files.any { it.size > 128L * 1024 * 1024 }, "Fixture must exercise the old size limit")
            ZipFile(zip).use { archive ->
                files.forEach { file ->
                    val entryName = "payload/" + file.path.removePrefix(GameTarget.RELATIVE_RESOURCES + "/")
                    val digest = MessageDigest.getInstance("SHA-256")
                    archive.getInputStream(archive.getEntry(entryName)).use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
                    }
                    assertEquals(digest.digest().joinToString("") { "%02x".format(it) }, file.sha256)
                    assertEquals(file.size, File(stage, file.path).length())
                }
            }
        } finally { stage.deleteRecursively() }
    }
}
