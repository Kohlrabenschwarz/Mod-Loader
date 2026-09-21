package dev.modloader.engine

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModMetadataReaderTest {
    private fun bundle(body: ByteArray = byteArrayOf(1)) = "UnityFS".toByteArray() + body
    @Test fun acceptsIconLargerThanOldOneMiBLimit() {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "${UUID.randomUUID()}.zip")
        try {
            ZipOutputStream(file.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("info.json"))
                out.write("""{"name":"Large icon","creator":"Tests","description":"Fixture","icon":"icon.png","affectedFiles":["a"]}""".toByteArray())
                out.closeEntry()
                out.putNextEntry(ZipEntry("payload/a")); out.write(bundle()); out.closeEntry()
                out.putNextEntry(ZipEntry("icon.png")); out.write(ByteArray(2 * 1024 * 1024)); out.closeEntry()
            }
            // Metadata katmanının byte limiti test edilir; bitmap çözümleme ayrı UI katmanındadır.
            assertEquals(2 * 1024 * 1024, ModMetadataReader.read(file).icon!!.size)
        } finally { file.delete() }
    }
    private fun archive(info: String?, payload: ByteArray = bundle(), body: (File) -> Unit) {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "${UUID.randomUUID()}.zip")
        try {
            ZipOutputStream(file.outputStream()).use { out ->
                if (info != null) { out.putNextEntry(ZipEntry("info.json")); out.write(info.toByteArray()); out.closeEntry() }
                out.putNextEntry(ZipEntry("payload/textures/a")); out.write(payload); out.closeEntry()
            }
            body(file)
        } finally { file.delete() }
    }
    @Test fun readsMetadataAndMatchesActualFiles() = archive("""{"name":"Mod","creator":"Author","description":"Description","affectedFiles":["textures/a"]}""") {
        val metadata = ModMetadataReader.read(it)
        assertEquals("Mod", metadata.name); assertEquals("Author", metadata.creator)
        assertEquals(listOf("textures/a"), metadata.affectedFiles); assertNull(metadata.icon)
    }
    @Test fun rejectsMissingInfo() = archive(null) {
        try { ModMetadataReader.read(it); fail("Missing info accepted") } catch (_: IllegalStateException) { }
    }
    @Test fun rejectsMisleadingAffectedFiles() = archive("""{"name":"Mod","creator":"Author","description":"Description","affectedFiles":["other"]}""") {
        try { ModMetadataReader.read(it); fail("Mismatch accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun rejectsEscapingIcon() = archive("""{"name":"Mod","creator":"Author","description":"Description","icon":"../icon.png","affectedFiles":["textures/a"]}""") {
        try { ModMetadataReader.read(it); fail("Escaping icon accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun rejectsBidiMetadataSpoofing() = archive("""{"name":"Safe\u202Etxt","creator":"Author","description":"Description","affectedFiles":["textures/a"]}""") {
        try { ModMetadataReader.read(it); fail("Bidi metadata accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun acceptsMultilineDescription() = archive("""{"name":"Mod","creator":"Author","description":"First\nSecond","affectedFiles":["textures/a"]}""") {
        assertEquals("First\nSecond", ModMetadataReader.read(it).description)
    }
    @Test fun rejectsPayloadWithoutUnityFsHeader() = archive(
        """{"name":"Mod","creator":"Author","description":"Description","affectedFiles":["textures/a"]}""",
        "not-a-bundle".toByteArray()
    ) {
        try { ModMetadataReader.read(it); fail("Non-UnityFS payload accepted") }
        catch (e: dev.modloader.domain.EngineFailure) { assertEquals(13, e.code) }
    }
}
