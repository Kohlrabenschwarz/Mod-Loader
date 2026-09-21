package dev.modloader.engine

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TransactionEngineTest {
    private class SimulatedDeath : Error("Simulated process death")
    private val pkg = "com.nekki.shadowfightarena"
    private val noop: (String, Long, Long) -> Unit = { _, _, _ -> }

    private fun fixture(block: (File, File, File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = File(context.cacheDir, UUID.randomUUID().toString()).apply { mkdir() }
        try {
            val root = File(storage, "Android/data/$pkg").apply { mkdirs() }
            File(root, "files/gamedata/Resources/Bundles").mkdirs()
            File(root, "files/gamedata/Resources/Bundles/existing").writeText("original")
            val zip = File(storage, "mod.zip")
            ZipOutputStream(zip.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("info.json"))
                out.write("""{"name":"Fixture","creator":"Tests","description":"Test","affectedFiles":["existing","new"]}""".toByteArray())
                out.closeEntry()
                listOf("files/gamedata/Resources/Bundles/existing" to "UnityFSreplacement", "files/gamedata/Resources/Bundles/new" to "UnityFSadded").forEach { (path, data) ->
                    out.putNextEntry(ZipEntry(path)); out.write(data.toByteArray()); out.closeEntry()
                }
            }
            block(storage, root, zip)
        } finally { storage.deleteRecursively() }
    }

    private fun prepare(engine: TransactionEngine, zip: File): String =
        ParcelFileDescriptor.open(zip, ParcelFileDescriptor.MODE_READ_ONLY).use {
            JSONObject(engine.prepare(it, pkg, noop)).getString("id")
        }

    @Test fun explicitRestoreIsIdempotent() = fixture { storage, root, zip ->
        val engine = TransactionEngine(storage)
        val id = prepare(engine, zip)
        engine.apply(pkg, id, true, noop)
        engine.restore(pkg, id, noop)
        engine.restore(pkg, id, noop)
        assertEquals("original", File(root, "files/gamedata/Resources/Bundles/existing").readText())
        assertFalse(File(root, "files/gamedata/Resources/Bundles/new").exists())
    }

    @Test fun explicitRestoreChecksAllFilesBeforeChangingAny() = fixture { storage, root, zip ->
        val engine = TransactionEngine(storage)
        val id = prepare(engine, zip)
        engine.apply(pkg, id, true, noop)
        File(root, "files/gamedata/Resources/Bundles/new").writeText("external change")
        try { engine.restore(pkg, id, noop); fail("Conflict expected") }
        catch (e: dev.modloader.domain.EngineFailure) { assertEquals(7, e.code) }
        assertEquals("UnityFSreplacement", File(root, "files/gamedata/Resources/Bundles/existing").readText())
        assertEquals("external change", File(root, "files/gamedata/Resources/Bundles/new").readText())
    }

    @Test fun committedFilesAndOriginalBackup() = fixture { storage, root, zip ->
        val engine = TransactionEngine(storage)
        val id = prepare(engine, zip)
        engine.apply(pkg, id, true, noop)
        assertEquals("UnityFSreplacement", File(root, "files/gamedata/Resources/Bundles/existing").readText())
        assertEquals("UnityFSadded", File(root, "files/gamedata/Resources/Bundles/new").readText())
        assertEquals("original", File(root, ".backup/modloader-v1/$id/old/files/gamedata/Resources/Bundles/existing").readText())
        engine.recover(pkg, noop)
        assertEquals("UnityFSreplacement", File(root, "files/gamedata/Resources/Bundles/existing").readText())
    }

    @Test fun crashAtEveryMutationBoundaryIsRecoverable() {
        listOf("APPLYING", "INTENT:0", "RENAMED:0", "INTENT:1", "RENAMED:1").forEach { point ->
            fixture { storage, root, zip ->
                val engine = TransactionEngine(storage) { if (it == point) throw SimulatedDeath() }
                val id = prepare(engine, zip)
                try { engine.apply(pkg, id, true, noop); fail("Crash expected") } catch (_: SimulatedDeath) { }
                TransactionEngine(storage).recover(pkg, noop)
                TransactionEngine(storage).recover(pkg, noop) // Tekrarlanan recovery aynı sonucu verir.
                assertEquals(point, "original", File(root, "files/gamedata/Resources/Bundles/existing").readText())
                assertFalse(File(root, "files/gamedata/Resources/Bundles/new").exists())
            }
        }
    }

    @Test fun crashDuringRollbackCanResume() = fixture { storage, root, zip ->
        val engine = TransactionEngine(storage) { if (it == "RENAMED:1") throw SimulatedDeath() }
        val id = prepare(engine, zip)
        try { engine.apply(pkg, id, true, noop) } catch (_: SimulatedDeath) { }
        try {
            TransactionEngine(storage) { if (it == "RESTORED:0") throw SimulatedDeath() }.recover(pkg, noop)
            fail("Crash expected")
        } catch (_: SimulatedDeath) { }
        TransactionEngine(storage).recover(pkg, noop)
        assertEquals("original", File(root, "files/gamedata/Resources/Bundles/existing").readText())
        assertFalse(File(root, "files/gamedata/Resources/Bundles/new").exists())
    }

    @Test fun changedPreviewAndMissingApprovalCannotOverwrite() = fixture { storage, root, zip ->
        val engine = TransactionEngine(storage)
        val id = prepare(engine, zip)
        try { engine.apply(pkg, id, false, noop); fail("Approval required") } catch (_: IllegalArgumentException) { }
        File(root, "files/gamedata/Resources/Bundles/existing").writeText("external change")
        try { engine.apply(pkg, id, true, noop); fail("Stale preview") } catch (_: IllegalStateException) { }
        assertEquals("external change", File(root, "files/gamedata/Resources/Bundles/existing").readText())
        assertFalse(File(root, "files/gamedata/Resources/Bundles/new").exists())
    }

    @Test fun recoveryPreservesUnexpectedExternalChanges() = fixture { storage, root, zip ->
        val engine = TransactionEngine(storage) { if (it == "RENAMED:0") throw SimulatedDeath() }
        val id = prepare(engine, zip)
        try { engine.apply(pkg, id, true, noop) } catch (_: SimulatedDeath) { }
        File(root, "files/gamedata/Resources/Bundles/existing").writeText("new user save")
        try { TransactionEngine(storage).recover(pkg, noop); fail("Conflict expected") } catch (_: IllegalStateException) { }
        assertEquals("new user save", File(root, "files/gamedata/Resources/Bundles/existing").readText())
        assertEquals("original", File(root, ".backup/modloader-v1/$id/old/files/gamedata/Resources/Bundles/existing").readText())
    }

    @Test fun ordinaryIoFailureRollsBackImmediately() = fixture { storage, root, zip ->
        val engine = TransactionEngine(storage) { if (it == "RENAMED:1") throw java.io.IOException("Injected I/O failure") }
        val id = prepare(engine, zip)
        try { engine.apply(pkg, id, true, noop); fail("I/O failure expected") } catch (_: java.io.IOException) { }
        assertEquals("original", File(root, "files/gamedata/Resources/Bundles/existing").readText())
        assertFalse(File(root, "files/gamedata/Resources/Bundles/new").exists())
        val state = JSONObject(File(root, ".backup/modloader-v1/$id/journal.json").readText()).getString("state")
        assertEquals("ROLLED_BACK", state)
    }

    @Test fun lostSuccessReplyDoesNotUndoCommittedTransaction() = fixture { storage, root, zip ->
        val engine = TransactionEngine(storage) { if (it == "COMMITTED") throw SimulatedDeath() }
        val id = prepare(engine, zip)
        try { engine.apply(pkg, id, true, noop); fail("Lost reply expected") } catch (_: SimulatedDeath) { }
        TransactionEngine(storage).recover(pkg, noop)
        assertEquals("UnityFSreplacement", File(root, "files/gamedata/Resources/Bundles/existing").readText())
        assertEquals("UnityFSadded", File(root, "files/gamedata/Resources/Bundles/new").readText())
    }
}
