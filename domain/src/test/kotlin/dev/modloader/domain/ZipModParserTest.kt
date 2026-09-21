package dev.modloader.domain

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class ZipModParserTest {
    private val pkg = "com.nekki.shadowfightarena"
    private fun bundle(body: ByteArray = byteArrayOf(1)): ByteArray = UnityBundlePolicy.HEADER.toByteArray() + body

    @Test fun payloadCannotOverwriteManagedArchivesOrBackups() {
        listOf("payload/mods/test/state.json", "payload/MODS/test/backup/a", "Resources/Bundles/mods/test/test.zip").forEach {
            assertFailsWith<IllegalArgumentException> { PathPolicy.relative(it, pkg) }
        }
        assertEquals("${GameTarget.RELATIVE_RESOURCES}/modsTexture", PathPolicy.relative("payload/modsTexture", pkg))
    }

    private fun archive(entries: List<Pair<String, ByteArray>>, wrapPayloads: Boolean = true, block: (File, File) -> Unit) {
        val dir = Files.createTempDirectory("mod-test").toFile()
        try {
            val zip = File(dir, "mod.zip")
            ZipOutputStream(zip.outputStream()).use { out ->
                entries.forEach { (name, bytes) ->
                    val presentation = name in setOf("info.json", "icon.png", "icon.jpg", "icon.webp") || name.endsWith('/')
                    val magic = UnityBundlePolicy.HEADER.toByteArray()
                    val hasHeader = bytes.size >= magic.size && bytes.copyOfRange(0, magic.size).contentEquals(magic)
                    val contents = if (wrapPayloads && !presentation && !hasHeader) bundle(bytes) else bytes
                    out.putNextEntry(ZipEntry(name)); out.write(contents); out.closeEntry()
                }
            }
            val stage = File(dir, "stage").apply { mkdir() }
            block(zip, stage)
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectsTraversalAndWrongPackage() {
        listOf("../files/gamedata/Resources/Bundles/a", "/files/gamedata/Resources/Bundles/a", "files/gamedata/Resources/Bundles/../x", "files\\a", "files/gamedata/Resources/Bundles//a", "files/gamedata/Resources/Bundles/a:b",
            "Android/data/com.other.game/files/gamedata/Resources/Bundles/a", "files/gamedata/Resources/Bundles/./a", "files/gamedata/Resources/Bundles/\u0000a").forEach { path ->
            assertFailsWith<IllegalArgumentException>(path) { PathPolicy.relative(path, pkg) }
        }
    }

    @Test fun rejectsHiddenUnicodeAndSpoofedPaths() {
        listOf(
            "payload/.hidden",
            "payload/folder/.state",
            "payload/name\u202Egnp",
            "payload/e\u0301",
            "payload/Ａ",
            "payload/a\u2215b",
            "payload/a\u007fb"
        ).forEach { path ->
            assertFailsWith<IllegalArgumentException>(path) { PathPolicy.relative(path, pkg) }
        }
        assertEquals("${GameTarget.RELATIVE_RESOURCES}/é", PathPolicy.relative("payload/é", pkg))
    }

    @Test fun rejectsUnsafeDisplayMetadata() {
        assertFailsWith<IllegalArgumentException> { UntrustedTextPolicy.display("Trusted\u202Etxt", 100) }
        assertFailsWith<IllegalArgumentException> { UntrustedTextPolicy.display("two\nlines", 100) }
        assertEquals("two\nlines", UntrustedTextPolicy.display("two\nlines", 100, multiline = true))
    }

    @Test fun supportedMappings() {
        listOf("files/gamedata/Resources/Bundles/assets/a", "$pkg/files/gamedata/Resources/Bundles/assets/a", "Android/data/$pkg/files/gamedata/Resources/Bundles/assets/a").forEach {
            assertEquals("files/gamedata/Resources/Bundles/assets/a", PathPolicy.relative(it, pkg))
        }
    }

    @Test fun extractsFullWrapperAndChecksDigest() {
        archive(listOf("Android/" to byteArrayOf(), "Android/data/" to byteArrayOf(),
            "Android/data/$pkg/" to byteArrayOf(), "Android/data/$pkg/files/gamedata/Resources/Bundles/a" to "hello".toByteArray())) { zip, stage ->
            val result = ZipModParser().extract(zip, pkg, stage) { _, _ -> }
            assertEquals("UnityFShello", File(stage, "files/gamedata/Resources/Bundles/a").readText())
            assertEquals(java.security.MessageDigest.getInstance("SHA-256").digest(bundle("hello".toByteArray())).joinToString("") { "%02x".format(it) }, result.single().sha256)
        }
    }

    @Test fun rejectsNormalizedDuplicates() {
        archive(listOf("files/gamedata/Resources/Bundles/a" to byteArrayOf(1), "$pkg/files/gamedata/Resources/Bundles/a" to byteArrayOf(2))) { zip, stage ->
            assertFailsWith<IllegalArgumentException> { ZipModParser().extract(zip, pkg, stage) { _, _ -> } }
        }
    }

    @Test fun rejectsCaseAndDirectoryConflicts() {
        listOf(listOf("files/gamedata/Resources/Bundles/A", "files/gamedata/Resources/Bundles/a"), listOf("files/gamedata/Resources/Bundles/a", "files/gamedata/Resources/Bundles/a/b")).forEach { names ->
            archive(names.map { it to byteArrayOf(1) }) { zip, stage ->
                assertFailsWith<IllegalArgumentException> { ZipModParser().extract(zip, pkg, stage) { _, _ -> } }
            }
        }
    }

    @Test fun rejectsZipBombRatio() {
        archive(listOf("files/gamedata/Resources/Bundles/bomb" to ByteArray(1024 * 1024))) { zip, stage ->
            assertFailsWith<IllegalArgumentException> { ZipModParser().extract(zip, pkg, stage) { _, _ -> } }
        }
    }

    @Test fun rejectsTooManyEntries() {
        archive((0..Limits.ENTRIES).map { "files/gamedata/Resources/Bundles/$it" to byteArrayOf(1) }) { zip, stage ->
            assertFailsWith<IllegalArgumentException> { ZipModParser().extract(zip, pkg, stage) { _, _ -> } }
        }
    }

    @Test fun rejectsTruncatedCentralDirectory() {
        archive(listOf("files/gamedata/Resources/Bundles/a" to byteArrayOf(1))) { zip, stage ->
            java.io.RandomAccessFile(zip, "rw").use { it.setLength(12) }
            assertFailsWith<IllegalArgumentException> { ZipModParser().extract(zip, pkg, stage) { _, _ -> } }
        }
    }

    private fun patchCentral(zip: File, offset: Int, bytes: ByteArray) {
        val data = zip.readBytes()
        val at = (0..data.size - 4).first { i ->
            data[i] == 0x50.toByte() && data[i + 1] == 0x4b.toByte() && data[i + 2] == 1.toByte() && data[i + 3] == 2.toByte()
        }
        java.io.RandomAccessFile(zip, "rw").use { file -> file.seek((at + offset).toLong()); file.write(bytes) }
    }

    @Test fun rejectsCorruptCrc() {
        archive(listOf("files/gamedata/Resources/Bundles/a" to "payload".toByteArray())) { zip, stage ->
            patchCentral(zip, 16, byteArrayOf(0, 0, 0, 0))
            assertFailsWith<IllegalArgumentException> { ZipModParser().extract(zip, pkg, stage) { _, _ -> } }
        }
    }

    @Test fun rejectsEncryptedArchive() {
        archive(listOf("files/gamedata/Resources/Bundles/a" to byteArrayOf(1))) { zip, stage ->
            patchCentral(zip, 8, byteArrayOf(1, 0))
            assertFailsWith<IllegalArgumentException> { ZipModParser().extract(zip, pkg, stage) { _, _ -> } }
        }
    }

    @Test fun rejectsSymlinkArchive() {
        archive(listOf("files/gamedata/Resources/Bundles/a" to "../../outside".toByteArray())) { zip, stage ->
            patchCentral(zip, 38, byteArrayOf(0, 0, 0xff.toByte(), 0xa1.toByte()))
            assertFailsWith<IllegalArgumentException> { ZipModParser().extract(zip, pkg, stage) { _, _ -> } }
        }
    }

    @Test fun rejectsZip64EntryBeforeZipFileAllocation() {
        archive(listOf("files/gamedata/Resources/Bundles/a" to byteArrayOf(1))) { zip, stage ->
            patchCentral(zip, 24, ByteArray(4) { 0xff.toByte() })
            assertFailsWith<IllegalArgumentException> { ZipModParser().extract(zip, pkg, stage) { _, _ -> } }
        }
    }

    @Test fun fixedPackageAndResourceBoundary() {
        assertFailsWith<IllegalArgumentException> { PathPolicy.packageName("com.other.game") }
        listOf("files/saves/a", "files/gamedata/Resources/BundlesOther/a", "payload/../../saves/a").forEach {
            assertFailsWith<IllegalArgumentException> { PathPolicy.relative(it, pkg) }
        }
        assertEquals("files/gamedata/Resources/Bundles/textures/a", PathPolicy.relative("payload/textures/a", pkg))
        assertEquals("files/gamedata/Resources/Bundles/textures/a", PathPolicy.relative("Resources/Bundles/textures/a", pkg))
        assertFailsWith<IllegalArgumentException> { PathPolicy.relative("files/gamedata/Resources/outside", pkg) }
    }

    @Test fun metadataAndIconNeverBecomeGameFiles() {
        archive(listOf("info.json" to "{}".toByteArray(), "icon.png" to byteArrayOf(1),
            "payload/textures/a" to "data".toByteArray())) { zip, stage ->
            val files = ZipModParser().extract(zip, pkg, stage) { _, _ -> }
            assertEquals(listOf("files/gamedata/Resources/Bundles/textures/a"), files.map { it.path })
            assertFalse(File(stage, "info.json").exists())
            assertFalse(File(stage, "icon.png").exists())
        }
    }

    @Test fun extensionlessRandomNamesKeepExactNameAndBytes() {
        val name = "5a9e13d8bc7844df970f612ea7d306c2"
        val bytes = bundle(byteArrayOf(0, 1, 127, -128, -1))
        archive(listOf("payload/$name" to bytes)) { zip, stage ->
            val result = ZipModParser().extract(zip, pkg, stage) { _, _ -> }.single()
            assertEquals("${GameTarget.RELATIVE_RESOURCES}/$name", result.path)
            assertContentEquals(bytes, File(stage, result.path).readBytes())
            assertFalse(File(stage, result.path + ".bundle").exists())
        }
    }

    @Test fun rejectsEveryPayloadWithoutUnityFsHeader() {
        archive(listOf("payload/random-extensionless-name" to "not-a-bundle".toByteArray()), wrapPayloads = false) { zip, stage ->
            val failure = assertFailsWith<EngineFailure> { ZipModParser().extract(zip, pkg, stage) { _, _ -> } }
            assertEquals(13, failure.code)
        }
    }

    @Test fun bundledTemplateExtractsWithTheProductionParser() {
        val zip = File("../examples/generic-mod-template.zip")
        val stage = Files.createTempDirectory("template-check").toFile()
        try {
            val entries = ZipModParser().extract(zip, pkg, stage) { _, _ -> }
            assertEquals(2, entries.size)
            entries.forEach {
                assertTrue(it.path.startsWith("${GameTarget.RELATIVE_RESOURCES}/"))
                assertTrue(it.path.substringAfterLast('/').matches(Regex("[a-f0-9]{32}")))
            }
        } finally { stage.deleteRecursively() }
    }
}
