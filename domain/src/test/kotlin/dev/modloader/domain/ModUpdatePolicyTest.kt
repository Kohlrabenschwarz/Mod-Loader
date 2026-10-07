package dev.modloader.domain

import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import kotlin.test.*

class ModUpdatePolicyTest {
    @Test fun rejectsNonHttpsCredentialsFragmentsAndLocalHostnames() {
        listOf("http://example.com/latest.json", "https://user:pass@example.com/mod.zip",
            "https://example.com/mod.zip#part", "https://localhost/latest.json", "https://a.local/x",
            "https://example.com:8443/x", "https://example.com./x", "https://example.com/\nx").forEach {
            assertFails { ModUpdatePolicy.httpsUrl(it) }
        }
        assertEquals("example.com", ModUpdatePolicy.httpsUrl("https://example.com/mod.zip?token=123").host)
    }
    @Test fun rejectsPrivateDnsResultsIncludingMappedIpv6AndCarrierNat() {
        listOf("127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1", "169.254.1.1", "100.64.0.1",
            "0.0.0.0", "224.0.0.1", "::1", "fe80::1", "fd00::1", "::ffff:127.0.0.1").forEach {
            assertFalse(ModUpdatePolicy.publicAddress(InetAddress.getByName(it)), it)
        }
        assertTrue(ModUpdatePolicy.publicAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(ModUpdatePolicy.publicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
    @Test fun verifiesDownloadAndRemovesPartialFilesOnMismatchOrCancellation() {
        val directory = Files.createTempDirectory("update-test").toFile()
        try {
            val payload = "example ZIP bytes".toByteArray()
            val original = File(directory, "original").apply { writeBytes(payload) }
            val release = ModUpdateRelease("com.example.mod", "1.2", 2, "https://example.com/mod.zip",
                ModUpdatePolicy.archiveHash(original), payload.size.toLong(), "")
            val target = File(directory, "download.part")
            ModUpdatePolicy.copyVerified(payload.inputStream(), target, release)
            assertContentEquals(payload, target.readBytes())
            listOf(payload.dropLast(1).toByteArray(), payload + byteArrayOf(1), ByteArray(payload.size)).forEach { bad ->
                assertEquals(14, assertFailsWith<EngineFailure> {
                    ModUpdatePolicy.copyVerified(bad.inputStream(), target, release)
                }.code)
                assertFalse(target.exists())
            }
            assertFailsWith<InterruptedException> {
                ModUpdatePolicy.copyVerified(payload.inputStream(), target, release) { done, _ ->
                    if (done > 0) throw InterruptedException("cancel")
                }
            }
            assertFalse(target.exists())
            assertContentEquals(payload, original.readBytes())
        } finally { directory.deleteRecursively() }
    }
    @Test fun updateMetadataNeverGetsExtractedToGamePayload() {
        val directory = Files.createTempDirectory("update-zip").toFile()
        try {
            val archive = File(directory, "mod.zip")
            java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
                listOf("info.json" to "{}", "update.json" to "{}", "payload/a" to "UnityFStest").forEach { (path, value) ->
                    zip.putNextEntry(java.util.zip.ZipEntry(path)); zip.write(value.toByteArray()); zip.closeEntry()
                }
            }
            val stage = File(directory, "stage").apply { mkdir() }
            val files = ZipModParser().extract(archive, GameTarget.PACKAGE_NAME, stage) { _, _ -> }
            assertEquals(listOf("${GameTarget.RELATIVE_RESOURCES}/a"), files.map { it.path })
            assertEquals(1, stage.walkTopDown().count { it.isFile })
        } finally { directory.deleteRecursively() }
    }
}
