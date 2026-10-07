package dev.modloader.engine

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import dev.modloader.domain.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ModUpdateEngineTest {
    private class Death : Error()
    private val noop: (String, Long, Long) -> Unit = { _, _, _ -> }
    private fun archive(file: File, code: Int, paths: List<String> = listOf("existing", "added"),
        modId: String = "com.example.mod", manifestUrl: String = "https://example.com/latest.json") {
        ZipOutputStream(file.outputStream()).use { zip ->
            val entries = listOf("info.json" to JSONObject().apply {
                put("schemaVersion", 1); put("name", "test"); put("creator", "Tests"); put("description", "Fixture")
                put("modId", modId); put("versionCode", code); put("version", "1.$code.0"); put("affectedFiles", JSONArray(paths))
            }.toString(), "update.json" to JSONObject().apply {
                put("schemaVersion", 1); put("modId", modId); put("manifestUrl", manifestUrl)
            }.toString()) + paths.map { "payload/$it" to "UnityFSversion-$code-$it" }
            entries.forEach { (path, text) -> zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry() }
        }
    }
    private fun fixture(block: (File, File, File, File, String, ModUpdateRelease) -> Unit) {
        val storage = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, UUID.randomUUID().toString()).apply { mkdir() }
        try {
            val bundles = File(storage, "Android/data/${GameTarget.PACKAGE_NAME}/${GameTarget.RELATIVE_RESOURCES}").apply { mkdirs() }
            File(bundles, "existing").writeText("original")
            val v1 = File(storage, "v1.zip").also { archive(it, 1) }
            val v2 = File(storage, "v2.zip").also { archive(it, 2, listOf("existing", "different")) }
            val id = UUID.randomUUID().toString()
            ParcelFileDescriptor.open(v1, ParcelFileDescriptor.MODE_READ_ONLY).use { ManagedModEngine(storage).store(it, id, "[]", noop) }
            val release = ModUpdateRelease("com.example.mod", "1.2.0", 2, "https://example.com/v2.zip",
                requireNotNull(SafeFs.hash(v2)), v2.length(), "Changes")
            block(storage, bundles, v1, v2, id, release)
        } finally { storage.deleteRecursively() }
    }
    private fun update(engine: ManagedModEngine, zip: File, id: String, old: File, release: ModUpdateRelease) =
        ParcelFileDescriptor.open(zip, ParcelFileDescriptor.MODE_READ_ONLY).use {
            engine.update(it, id, requireNotNull(SafeFs.hash(old)), ModUpdateJson.encode(release), noop)
        }
    @Test fun activeUpdateRestoresRemovedTargetsRetainsBackupsAndKeepsIdentity() = fixture { storage, bundles, v1, v2, id, release ->
        val engine = ManagedModEngine(storage)
        engine.setActive(id, true, noop)
        update(engine, v2, id, v1, release)
        val summary = JSONArray(engine.list()).getJSONObject(0)
        assertEquals(id, summary.getString("id")); assertFalse(summary.getBoolean("active")); assertTrue(summary.isNull("issue"))
        assertEquals(release.zipSha256, summary.getString("archiveSha256"))
        assertEquals("original", File(bundles, "existing").readText())
        assertFalse(File(bundles, "added").exists()); assertFalse(File(bundles, "different").exists())
        assertEquals(SafeFs.hash(v1), SafeFs.hash(File(bundles, "mods/test/previous/test.zip")))
        assertTrue(File(bundles, "mods/test/previous/backup").walkTopDown().any { it.isFile && it.name == "existing" && it.readText() == "original" })
        engine.setActive(id, true, noop)
        assertEquals("UnityFSversion-2-existing", File(bundles, "existing").readText())
        assertTrue(File(bundles, "different").exists())
        engine.setActive(id, false, noop)
        assertEquals("original", File(bundles, "existing").readText()); assertFalse(File(bundles, "different").exists())
    }
    @Test fun interruptedDirectoryExchangeRecoversAtEveryDurableBoundary() {
        listOf("UPDATE_READY", "UPDATE_OLD_MOVED", "UPDATE_PUBLISHED", "UPDATE_PREVIOUS_SAVED").forEach { boundary ->
            fixture { storage, bundles, v1, v2, id, release ->
                ManagedModEngine(storage).setActive(id, true, noop)
                val dying = ManagedModEngine(storage, checkpoint = { if (it == boundary) throw Death() })
                try { update(dying, v2, id, v1, release); fail("Death expected") } catch (_: Death) { }
                val state = JSONArray(ManagedModEngine(storage).list()).getJSONObject(0)
                assertEquals(id, state.getString("id")); assertFalse(state.getBoolean("active")); assertTrue(state.isNull("issue"))
                assertEquals(release.zipSha256, state.getString("archiveSha256"))
                assertEquals("original", File(bundles, "existing").readText()); assertFalse(File(bundles, "added").exists())
                assertTrue(File(bundles, "mods/test/previous/backup").isDirectory)
                assertFalse(File(bundles, "mods/.update-$id").exists())
                // Lost response is idempotent, even after the user reactivates the new version.
                val engine = ManagedModEngine(storage)
                engine.setActive(id, true, noop)
                update(engine, v2, id, v1, release)
                assertTrue(JSONArray(engine.list()).getJSONObject(0).getBoolean("active"))
            }
        }
    }
    @Test fun invalidHashIdentitySourceAndVersionNeverDisableActiveMod() = fixture { storage, bundles, v1, v2, id, release ->
        val engine = ManagedModEngine(storage); engine.setActive(id, true, noop)
        listOf(release.copy(zipSha256 = "0".repeat(64)), release.copy(modId = "com.example.other"),
            release.copy(versionCode = 1), release.copy(version = "9.0.0")).forEach { bad ->
            try { update(engine, v2, id, v1, bad); fail("Invalid update accepted") } catch (_: EngineFailure) { }
            assertTrue(JSONArray(engine.list()).getJSONObject(0).getBoolean("active"))
            assertEquals("UnityFSversion-1-existing", File(bundles, "existing").readText())
        }
        archive(v2, 2, manifestUrl = "https://example.com/other.json")
        val badSource = release.copy(zipSha256 = requireNotNull(SafeFs.hash(v2)), zipSize = v2.length())
        try { update(engine, v2, id, v1, badSource); fail("Source change accepted") } catch (e: EngineFailure) { assertEquals(16, e.code) }
        assertTrue(JSONArray(engine.list()).getJSONObject(0).getBoolean("active"))
    }
    @Test fun shaMismatchAndStaleArchiveBlockUpdateAndPreserveRecovery() = fixture { storage, bundles, v1, v2, id, release ->
        val engine = ManagedModEngine(storage); engine.setActive(id, true, noop)
        File(bundles, "existing").writeText("game-update")
        try { update(engine, v2, id, v1, release); fail("Mismatch expected") } catch (e: EngineFailure) { assertEquals(7, e.code) }
        assertEquals("game-update", File(bundles, "existing").readText())
        assertEquals(SafeFs.hash(v1), SafeFs.hash(File(bundles, "mods/test/test.zip")))
        File(bundles, "existing").writeText("UnityFSversion-1-existing")
        ParcelFileDescriptor.open(v2, ParcelFileDescriptor.MODE_READ_ONLY).use {
            try { engine.update(it, id, "0".repeat(64), ModUpdateJson.encode(release), noop); fail("Stale preview accepted") }
            catch (e: EngineFailure) { assertEquals(16, e.code) }
        }
    }
    @Test fun repeatedUpdatesRetainExactlyOnePreviousVersion() = fixture { storage, bundles, v1, v2, id, release ->
        val engine = ManagedModEngine(storage); update(engine, v2, id, v1, release)
        val v3 = File(storage, "v3.zip").also { archive(it, 3) }
        update(engine, v3, id, v2, release.copy(version = "1.3.0", versionCode = 3,
            zipSha256 = requireNotNull(SafeFs.hash(v3)), zipSize = v3.length()))
        assertEquals(SafeFs.hash(v2), SafeFs.hash(File(bundles, "mods/test/previous/test.zip")))
        assertFalse(File(bundles, "mods/test/previous/previous").exists())
    }
    @Test fun crashBeforeExchangeKeepsOldArchiveAndRecoveryFinishesRestoration() {
        listOf("UPDATE_VERIFIED", "MOD_RESTORED").forEach { boundary ->
            fixture { storage, bundles, v1, v2, id, release ->
                val engine = ManagedModEngine(storage); engine.setActive(id, true, noop)
                val dying = ManagedModEngine(storage, checkpoint = { if (it == boundary) throw Death() })
                try { update(dying, v2, id, v1, release); fail("Death expected") } catch (_: Death) { }
                val state = JSONArray(engine.list()).getJSONObject(0)
                assertEquals(SafeFs.hash(v1), state.getString("archiveSha256"))
                assertEquals(boundary == "UPDATE_VERIFIED", state.getBoolean("active"))
                assertEquals(if (boundary == "UPDATE_VERIFIED") "UnityFSversion-1-existing" else "original",
                    File(bundles, "existing").readText())
                assertTrue(File(bundles, "mods/test/backup").isDirectory)
            }
        }
    }
    @Test fun conflictingNewTargetsAreRejectedBeforeOldModIsDisabled() = fixture { storage, bundles, v1, v2, id, release ->
        val engine = ManagedModEngine(storage); engine.setActive(id, true, noop)
        val otherZip = File(storage, "other.zip").also { archive(it, 1, listOf("different"), "com.example.other") }
        val otherId = UUID.randomUUID().toString()
        ParcelFileDescriptor.open(otherZip, ParcelFileDescriptor.MODE_READ_ONLY).use { engine.store(it, otherId, "[]", noop) }
        engine.setActive(otherId, true, noop)
        try { update(engine, v2, id, v1, release); fail("Conflict accepted") }
        catch (e: EngineFailure) { assertEquals(8, e.code); assertEquals(listOf("different"), e.conflicts.single().files) }
        assertEquals("UnityFSversion-1-existing", File(bundles, "existing").readText())
        assertEquals(SafeFs.hash(v1), SafeFs.hash(File(bundles, "mods/test/test.zip")))
    }
    @Test fun damagedStagedArchivePreservesOldVersionAndBlocksPublication() = fixture { storage, bundles, v1, v2, id, release ->
        val dying = ManagedModEngine(storage, checkpoint = { if (it == "UPDATE_READY") throw Death() })
        try { update(dying, v2, id, v1, release); fail("Death expected") } catch (_: Death) { }
        File(bundles, "mods/.update-$id/new/test.zip").writeText("damaged")
        try { ManagedModEngine(storage).list(); fail("Damaged update accepted") }
        catch (e: EngineFailure) { assertEquals(6, e.code) }
        assertEquals(SafeFs.hash(v1), SafeFs.hash(File(bundles, "mods/test/test.zip")))
        assertTrue(File(bundles, "mods/.update-$id/journal.json").exists())
        assertEquals("original", File(bundles, "existing").readText())
    }
}
