package dev.modloader.engine

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import dev.modloader.domain.GameTarget
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ManagedModEngineTest {
    private class Death : Error()
    private val noop: (String, Long, Long) -> Unit = { _, _, _ -> }
    private fun fixture(block: (File, File, File) -> Unit) {
        val storage = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, UUID.randomUUID().toString()).apply { mkdir() }
        try {
            val bundles = File(storage, "Android/data/${GameTarget.PACKAGE_NAME}/${GameTarget.RELATIVE_RESOURCES}").apply { mkdirs() }
            File(bundles, "existing").writeText("original")
            val zip = File(storage, "test.zip")
            ZipOutputStream(zip.outputStream()).use { out ->
                listOf("info.json" to """{"name":"test","creator":"Tests","description":"Fixture","affectedFiles":["existing","new"]}""",
                    "payload/existing" to "UnityFSreplacement", "payload/new" to "UnityFSadded").forEach { (path, data) ->
                    out.putNextEntry(ZipEntry(path)); out.write(data.toByteArray()); out.closeEntry()
                }
            }
            block(storage, bundles, zip)
        } finally { storage.deleteRecursively() }
    }
    private fun store(engine: ManagedModEngine, zip: File, id: String = UUID.randomUUID().toString(), legacy: String = "[]"): String {
        ParcelFileDescriptor.open(zip, ParcelFileDescriptor.MODE_READ_ONLY).use { engine.store(it, id, legacy, noop) }
        return id
    }
    private fun active(storage: File): Boolean = JSONArray(ManagedModEngine(storage).list()).getJSONObject(0).getBoolean("active")

    @Test fun storesMoreThan100ModsAndStillActivatesRestoresAndDeletes() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val ids = (1..101).map { store(engine, zip) }
        val listed = JSONArray(engine.list())
        assertEquals(101, listed.length())
        assertEquals(ids.toSet(), (0 until listed.length()).map { listed.getJSONObject(it).getString("id") }.toSet())
        engine.setActive(ids.last(), true, noop)
        assertEquals("UnityFSreplacement", File(bundles, "existing").readText())
        engine.setActive(ids.last(), false, noop)
        assertEquals("original", File(bundles, "existing").readText())
        engine.delete(ids.last(), noop)
        assertEquals(100, JSONArray(ManagedModEngine(storage).list()).length())
    }

    @Test fun corruptedRecordDoesNotHideHealthyModsOrAllowUnsafeWrites() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val id = store(engine, zip)
        val broken = File(bundles, "mods/broken").apply { mkdir() }
        File(broken, "state.json").writeText("not json")
        File(broken, "backup").mkdir()
        File(broken, "backup/keep").writeText("saved")
        val records = JSONArray(engine.list())
        assertEquals(2, records.length())
        assertTrue((0 until records.length()).any { records.getJSONObject(it).getString("id") == id })
        assertTrue((0 until records.length()).any { records.getJSONObject(it).optString("issue") == "INVALID_RECORD" })
        try { engine.setActive(id, true, noop); fail("Unknown conflicts must block writes") } catch (_: Exception) { }
        assertEquals("original", File(bundles, "existing").readText())
        assertEquals("saved", File(broken, "backup/keep").readText())
    }

    @Test fun mismatchReportsExactFilesHashesAndBackupDate() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val id = store(engine, zip)
        val initial = JSONArray(engine.list()).getJSONObject(0)
        assertTrue(initial.getLong("requiredBytes") > zip.length())
        assertTrue(initial.getLong("availableBytes") > 0)
        engine.setActive(id, true, noop)
        val expected = SafeFs.hash(File(bundles, "existing"))
        File(bundles, "existing").writeText("updated")
        val summary = JSONArray(engine.list()).getJSONObject(0)
        assertTrue(summary.getLong("backupAt") > 0)
        val change = summary.getJSONArray("changes").getJSONObject(0)
        assertEquals("existing", change.getString("path"))
        assertEquals(expected, change.getString("expected"))
        assertEquals(SafeFs.hash(File(bundles, "existing")), change.getString("actual"))
        File(bundles, "existing").delete()
        assertTrue(JSONArray(engine.list()).getJSONObject(0).getJSONArray("changes").getJSONObject(0).isNull("actual"))
    }

    @Test fun listingWithUnknownRecordDoesNotResumeInterruptedWrites() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val id = store(engine, zip)
        val interrupted = ManagedModEngine(storage, checkpoint = { if (it == "RENAMED:0") throw Death() })
        try { interrupted.setActive(id, true, noop); fail("Death expected") } catch (_: Death) { }
        val before = File(bundles, "existing").readText()
        File(bundles, "mods/broken").mkdir()
        File(bundles, "mods/broken/state.json").writeText("broken")
        val summaries = JSONArray(engine.list())
        assertEquals(2, summaries.length())
        assertEquals(before, File(bundles, "existing").readText())
        assertTrue(File(bundles, "mods/test/backup").isDirectory)
    }

    @Test fun missingGameDataIsReportedWithoutCreatingGameDirectories() {
        val storage = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, UUID.randomUUID().toString()).apply { mkdir() }
        try {
            try { ManagedModEngine(storage).list(); fail("Missing game data expected") }
            catch (e: dev.modloader.domain.EngineFailure) { assertEquals(12, e.code) }
            assertFalse(File(storage, "Android/data/${GameTarget.PACKAGE_NAME}").exists())
        } finally { storage.deleteRecursively() }
    }
    @Test fun discardDoesNotResumeOrRemoveIncompleteRecovery() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val id = store(engine, zip)
        engine.setActive(id, true, noop)
        File(bundles, "existing").writeText("update")
        val dying = ManagedModEngine(storage, checkpoint = { if (it == "FORCE_INTENT") throw Death() })
        try { dying.warningAction(id, true, noop); fail("Death expected") } catch (_: Death) { }
        try { engine.warningAction(id, false, noop); fail("Pending recovery expected") }
        catch (e: dev.modloader.domain.EngineFailure) { assertEquals(6, e.code) }
        assertEquals("update", File(bundles, "existing").readText())
        assertTrue(File(bundles, "mods/test/backup").isDirectory)
    }
    @Test fun repeatedRecoveryKeepsPreRecoverySnapshot() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val id = store(engine, zip)
        engine.setActive(id, true, noop)
        File(bundles, "existing").writeText("update")
        engine.warningAction(id, true, noop)
        engine.warningAction(id, true, noop)
        assertEquals("original", File(bundles, "existing").readText())
        assertTrue(File(bundles, "mods/test/backup").walkTopDown().any {
            it.isFile && it.path.contains("before-recovery") && it.name == "existing" && it.readText() == "update"
        })
    }
    @Test fun warningDiscardPreservesUpdatedGameFiles() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val id = store(engine, zip)
        engine.setActive(id, true, noop)
        File(bundles, "existing").writeText("game-update")
        engine.warningAction(id, false, noop)
        assertEquals("game-update", File(bundles, "existing").readText())
        assertEquals("UnityFSadded", File(bundles, "new").readText())
        assertFalse(File(bundles, "mods/test").exists())
        engine.warningAction(id, false, noop) // Lost Binder reply can be retried.
    }
    @Test fun approvedBaseRecoveryResumesAfterDeath() {
        listOf("FORCE_INTENT", "FORCE_RESTORED:0", "FORCE_RESTORED:1").forEach { boundary -> fixture { storage, bundles, zip ->
            val engine = ManagedModEngine(storage)
            val id = store(engine, zip)
            engine.setActive(id, true, noop)
            File(bundles, "existing").writeText("game-update")
            File(bundles, "new").delete()
            val dying = ManagedModEngine(storage, checkpoint = { if (it == boundary) throw Death() })
            try { dying.warningAction(id, true, noop); fail("Death expected") } catch (_: Death) { }
            val state = JSONArray(ManagedModEngine(storage).list()).getJSONObject(0)
            assertTrue(state.isNull("issue"))
            assertFalse(state.getBoolean("active"))
            assertFalse(state.getBoolean("shaMismatch"))
            assertEquals("original", File(bundles, "existing").readText())
            assertFalse(File(bundles, "new").exists())
            assertTrue(File(bundles, "mods/test/backup").walkTopDown().any {
                it.isFile && it.path.contains("before-recovery") && it.name == "existing" && it.readText() == "game-update"
            })
        } }
    }
    @Test fun baseRecoveryNeedsOriginalBackup() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val id = store(engine, zip)
        File(bundles, "existing").writeText("game-update")
        try { engine.warningAction(id, true, noop); fail("Missing backup expected") }
        catch (e: dev.modloader.domain.EngineFailure) { assertEquals(11, e.code) }
        assertEquals("game-update", File(bundles, "existing").readText())
    }
    @Test fun baseRecoveryRefusesChangesAfterApprovalSnapshot() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val id = store(engine, zip)
        engine.setActive(id, true, noop)
        File(bundles, "existing").writeText("game-update")
        val racing = ManagedModEngine(storage, checkpoint = { if (it == "FORCE_INTENT") File(bundles, "existing").writeText("later-update") })
        try { racing.warningAction(id, true, noop); fail("Conflict expected") }
        catch (e: dev.modloader.domain.EngineFailure) { assertEquals(6, e.code) }
        assertEquals("later-update", File(bundles, "existing").readText())
    }
    @Test fun archiveActivateRestartDeactivateDelete() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val id = store(engine, zip)
        assertTrue(File(bundles, "mods/test/test.zip").isFile)
        assertEquals("original", File(bundles, "existing").readText())
        assertFalse(active(storage))
        engine.setActive(id, true, noop)
        assertTrue(active(storage))
        assertEquals("UnityFSreplacement", File(bundles, "existing").readText())
        assertTrue(File(bundles, "mods/test/backup").walkTopDown().any { it.isFile && it.name == "existing" && it.readText() == "original" })
        ManagedModEngine(storage).setActive(id, false, noop)
        assertFalse(active(storage))
        assertEquals("original", File(bundles, "existing").readText())
        assertFalse(File(bundles, "new").exists())
        engine.setActive(id, false, noop)
        engine.delete(id, noop)
        engine.delete(id, noop)
        assertFalse(File(bundles, "mods/test").exists())
    }
    @Test fun deletedActiveModRestoresOriginals() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val id = store(engine, zip)
        engine.setActive(id, true, noop)
        engine.delete(id, noop)
        assertFalse(File(bundles, "mods/test").exists())
        assertFalse(File(bundles, "new").exists())
        assertEquals("original", File(bundles, "existing").readText())
    }
    @Test fun abandonedImportIsCleanedWithoutRemovingPublishedMod() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        store(engine, zip)
        val abandoned = File(bundles, "mods/.incoming-${UUID.randomUUID()}").apply { mkdir() }
        File(abandoned, "archive.zip").writeText("partial transfer")
        assertEquals(1, JSONArray(engine.list()).length())
        assertFalse(abandoned.exists())
        assertTrue(File(bundles, "mods/test/test.zip").isFile)
        assertEquals("original", File(bundles, "existing").readText())
    }
    @Test fun failedGameStopPreventsTargetChangesAndPreservesBackup() = fixture { storage, bundles, zip ->
        val normal = ManagedModEngine(storage)
        val id = store(normal, zip)
        val denied = ManagedModEngine(storage, beforeMutation = { error("Stop denied") })
        try { denied.setActive(id, true, noop); fail("Stop failure expected") } catch (_: IllegalStateException) { }
        assertEquals("original", File(bundles, "existing").readText())
        assertFalse(File(bundles, "new").exists())
        assertFalse(active(storage))
        normal.setActive(id, true, noop)
        try { denied.setActive(id, false, noop); fail("Stop failure expected") } catch (_: IllegalStateException) { }
        assertEquals("UnityFSreplacement", File(bundles, "existing").readText())
        assertTrue(active(storage))
        normal.setActive(id, false, noop)
        assertEquals("original", File(bundles, "existing").readText())
    }
    @Test fun journalRepairsFlagAfterProcessDeath() {
        listOf("MOD_COMMITTED", "MOD_RESTORED").forEach { boundary -> fixture { storage, bundles, zip ->
            val id = store(ManagedModEngine(storage), zip)
            if (boundary == "MOD_RESTORED") ManagedModEngine(storage).setActive(id, true, noop)
            try {
                ManagedModEngine(storage) { if (it == boundary) throw Death() }.setActive(id, boundary == "MOD_COMMITTED", noop)
                fail("Expected simulated death")
            } catch (_: Death) { }
            assertEquals(boundary == "MOD_COMMITTED", active(storage))
                assertEquals(if (boundary == "MOD_COMMITTED") "UnityFSreplacement" else "original", File(bundles, "existing").readText())
        } }
    }
    @Test fun duplicateNamesPreserveArchivesAndConflictingModsCannotActivate() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val first = store(engine, zip)
        val second = store(engine, zip)
        assertTrue(File(bundles, "mods/test-${second.take(8)}/test-${second.take(8)}.zip").isFile)
        engine.setActive(first, true, noop)
        try { engine.setActive(second, true, noop); fail("Conflict expected") }
        catch (e: dev.modloader.domain.EngineFailure) { assertTrue(e.code == 7 || e.code == 8) }
        assertEquals("UnityFSreplacement", File(bundles, "existing").readText())
        assertEquals(1, JSONArray(engine.list()).let { a -> (0 until a.length()).count { a.getJSONObject(it).getBoolean("active") } })
    }
    @Test fun legacyBackupMovesWithActiveState() = fixture { storage, bundles, zip ->
        val old = TransactionEngine(storage)
        val tx = ParcelFileDescriptor.open(zip, ParcelFileDescriptor.MODE_READ_ONLY).use {
            JSONObject(old.prepare(it, GameTarget.PACKAGE_NAME, noop)).getString("id")
        }
        old.apply(GameTarget.PACKAGE_NAME, tx, true, noop)
        val engine = ManagedModEngine(storage)
        val id = store(engine, zip, legacy = JSONArray(listOf(tx)).toString())
        assertTrue(active(storage))
        assertTrue(File(bundles, "mods/test/backup/$tx/journal.json").isFile)
        engine.setActive(id, false, noop)
        assertEquals("original", File(bundles, "existing").readText())
    }

    @Test fun gameUpdateShowsMismatchAndKeepsOriginalHashesAndBackups() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val id = store(engine, zip)
        val original = SafeFs.hash(File(bundles, "existing"))
        engine.setActive(id, true, noop)
        val manifest = File(bundles, "mods/test/sha.json")
        val snapshot = manifest.readText()
        val entries = JSONObject(snapshot).getJSONArray("files")
        assertEquals(original, entries.getJSONObject(0).getString("originalSha256"))
        assertEquals(SafeFs.hash(File(bundles, "existing")), entries.getJSONObject(0).getString("modifiedSha256"))
        assertTrue(entries.getJSONObject(1).isNull("originalSha256"))
        File(bundles, "existing").writeText("updated game")
        val status = JSONArray(ManagedModEngine(storage).list()).getJSONObject(0)
        assertTrue(status.getBoolean("shaMismatch"))
        assertTrue(status.isNull("issue"))
        assertEquals(snapshot, manifest.readText())
        try { engine.setActive(id, false, noop); fail("Mismatch expected") }
        catch (e: dev.modloader.domain.EngineFailure) { assertEquals(7, e.code) }
        try { engine.delete(id, noop); fail("Mismatch expected") }
        catch (e: dev.modloader.domain.EngineFailure) { assertEquals(7, e.code) }
        assertEquals("updated game", File(bundles, "existing").readText())
        assertTrue(File(bundles, "mods/test/backup").walkTopDown().any { it.name == "existing" && it.isFile && it.readText() == "original" })
        File(bundles, "existing").writeText("UnityFSreplacement")
        assertFalse(JSONArray(engine.list()).getJSONObject(0).getBoolean("shaMismatch"))
        engine.setActive(id, false, noop)
        assertEquals("original", File(bundles, "existing").readText())
    }

    @Test fun missingTargetWarnsAndMissingManifestRebuildsFromJournal() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val id = store(engine, zip)
        engine.setActive(id, true, noop)
        val original = JSONObject(File(bundles, "mods/test/sha.json").readText()).getJSONArray("files").getJSONObject(0).getString("originalSha256")
        assertTrue(File(bundles, "existing").delete())
        assertTrue(File(bundles, "mods/test/sha.json").delete())
        assertTrue(JSONArray(ManagedModEngine(storage).list()).getJSONObject(0).getBoolean("shaMismatch"))
        val rebuilt = JSONObject(File(bundles, "mods/test/sha.json").readText())
        assertEquals(original, rebuilt.getJSONArray("files").getJSONObject(0).getString("originalSha256"))
    }

    @Test fun inactiveBaselineDetectsUpdatesButArchiveCanBeDeletedSafely() = fixture { storage, bundles, zip ->
        val engine = ManagedModEngine(storage)
        val id = store(engine, zip)
        File(bundles, "existing").writeText("updated game")
        assertTrue(JSONArray(engine.list()).getJSONObject(0).getBoolean("shaMismatch"))
        try { engine.setActive(id, true, noop); fail("Mismatch expected") }
        catch (e: dev.modloader.domain.EngineFailure) { assertEquals(7, e.code) }
        engine.delete(id, noop)
        assertEquals("updated game", File(bundles, "existing").readText())
        assertFalse(File(bundles, "mods/test").exists())
    }
}
