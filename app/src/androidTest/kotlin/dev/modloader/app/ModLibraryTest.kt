package dev.modloader.app

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ModLibraryTest {
    @Test fun incompleteNetworkStreamNeverPublishesAnArchiveOrLeavesStagingFiles() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = ModLibrary(context)
        val file = File(context.cacheDir, "partial-${UUID.randomUUID()}.zip")
        try {
            fixture(file, "download")
            val before = library.load().map { it.id }.toSet()
            val stage = File(context.cacheDir, "mod-imports")
            val stagedBefore = stage.list().orEmpty().toSet()
            file.inputStream().use { input ->
                try {
                    library.stageImport(ModLinkImports.ArchiveInput(input, file.length() + 1)) { }
                    fail("Truncated download accepted")
                } catch (e: LinkImportFailure) { assertEquals("INCOMPLETE_DOWNLOAD", e.detail) }
            }
            assertEquals(before, library.load().map { it.id }.toSet())
            assertEquals(stagedBefore, stage.list().orEmpty().toSet())
        } finally { file.delete() }
    }
    private fun fixture(file: File, payload: String) = ZipOutputStream(file.outputStream()).use { out ->
        listOf("info.json" to """{"name":"Pending fixture","creator":"Tests","description":"Import approval","affectedFiles":["texture"]}""",
            "payload/texture" to "UnityFS$payload").forEach { (path, data) ->
            out.putNextEntry(ZipEntry(path)); out.write(data.toByteArray()); out.closeEntry()
        }
    }
    @Test fun canceledStagedImportNeverAppearsInLibraryOrChangesExistingArchive() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = ModLibrary(context)
        val file = File(context.cacheDir, "approval-${UUID.randomUUID()}.zip")
        var original: LibraryMod? = null
        var pending: LibraryMod? = null
        try {
            fixture(file, "old"); original = library.import(android.net.Uri.fromFile(file)) { }
            fixture(file, "new"); pending = library.stageImport(android.net.Uri.fromFile(file)) { }
            assertFalse(library.load().any { it.id == pending.id })
            assertEquals(original.archiveSha256, dev.modloader.domain.ModUpdatePolicy.archiveHash(original.archive))
            library.discardImport(pending)
            assertFalse(pending.archive.exists())
            assertEquals(original.archiveSha256, library.load().single { it.id == original.id }.archiveSha256)
        } finally {
            pending?.let { if (it.archive.exists()) library.discardImport(it) }
            original?.let { library.delete(it.id) }; file.delete()
        }
    }
    @Test fun approvedLocalOverwriteKeepsIdentityAndRejectsChangedPreview() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = ModLibrary(context)
        val file = File(context.cacheDir, "overwrite-${UUID.randomUUID()}.zip")
        var original: LibraryMod? = null
        var pending: LibraryMod? = null
        try {
            fixture(file, "old"); original = library.import(android.net.Uri.fromFile(file)) { }
            fixture(file, "new"); pending = library.stageImport(android.net.Uri.fromFile(file)) { }
            try { library.commitImport(pending, original.id, "0".repeat(64)); fail("Stale preview accepted") }
            catch (e: dev.modloader.domain.EngineFailure) { assertEquals(16, e.code) }
            assertTrue(pending.archive.exists())
            assertEquals(original.archiveSha256, dev.modloader.domain.ModUpdatePolicy.archiveHash(original.archive))
            val replaced = library.commitImport(pending, original.id, original.archiveSha256)
            assertEquals(original.id, replaced.id); assertEquals(pending.archiveSha256, replaced.archiveSha256)
            assertFalse(pending.archive.exists())
            assertEquals(replaced.archiveSha256, library.load().single { it.id == original.id }.archiveSha256)
        } finally {
            pending?.let { if (it.archive.exists()) library.discardImport(it) }
            original?.let { library.delete(it.id) }; file.delete()
        }
    }
    @Test fun importsMoreThan20Archives() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = ModLibrary(context)
        val fixture = File(context.cacheDir, "import-${UUID.randomUUID()}.zip")
        val imported = mutableListOf<String>()
        try {
            ZipOutputStream(fixture.outputStream()).use { out ->
                listOf("info.json" to """{"name":"Imported texture","creator":"Tests","description":"Count regression","affectedFiles":["texture"]}""",
                    "payload/texture" to "UnityFSdata").forEach { (path, data) ->
                    out.putNextEntry(ZipEntry(path)); out.write(data.toByteArray()); out.closeEntry()
                }
            }
            repeat(21) { imported.add(library.import(android.net.Uri.fromFile(fixture)) { }.id) }
            assertEquals(21, library.load().count { it.id in imported })
        } finally { imported.forEach(library::delete); fixture.delete() }
    }
    @Test fun damagedZipIsIsolatedAndMetadataCacheCanBeRepaired() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val library = ModLibrary(context)
        val goodId = UUID.randomUUID().toString()
        val badId = UUID.randomUUID().toString()
        val good = library.archiveLocation(goodId)
        val bad = library.archiveLocation(badId)
        fun writeArchive(file: File) = ZipOutputStream(file.outputStream()).use { out ->
            listOf("info.json" to """{"name":"Fixture","creator":"Tests","description":"Library fixture","affectedFiles":["texture"]}""",
                "payload/texture" to "UnityFSdata").forEach { (path, data) ->
                out.putNextEntry(ZipEntry(path)); out.write(data.toByteArray()); out.closeEntry()
            }
        }
        try {
            writeArchive(good); bad.writeText("broken zip")
            val first = library.load()
            val healthy = first.single { it.id == goodId }
            assertFalse(healthy.damagedArchive)
            assertTrue(first.single { it.id == badId }.damagedArchive)
            assertSame(healthy, library.load().single { it.id == goodId })
            writeArchive(bad); library.invalidate(badId)
            assertFalse(library.load().single { it.id == badId }.damagedArchive)
        } finally { library.delete(goodId); library.delete(badId) }
    }
}
