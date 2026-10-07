package dev.modloader.engine

import android.os.ParcelFileDescriptor
import android.system.Os
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

class ModDeveloperEngineTest {
    private val base = "https://example.com/publish/"
    private val noop: (String, Long, Long) -> Unit = { _, _, _ -> }
    private class Death : Error()
    private fun archive(file: File, code: Int? = 1, name: String = "Dev Test", modId: String = "com.example.dev",
        paths: List<String> = listOf("existing", "added")) {
        ZipOutputStream(file.outputStream()).use { zip ->
            val info = JSONObject().apply {
                put("name", name); put("creator", "Tests"); put("description", "Developer fixture")
                put("version", "1.0.0"); put("affectedFiles", JSONArray(paths))
                if (code != null) { put("modId", modId); put("versionCode", code) }
            }
            val entries = mutableListOf("info.json" to info.toString())
            if (code != null) entries.add("update.json" to JSONObject().apply {
                put("schemaVersion", 1); put("modId", modId); put("manifestUrl", "$base$modId/latest.json")
            }.toString())
            entries.addAll(paths.map { "payload/$it" to "UnityFS${code ?: 0}-$it-$modId" })
            entries.forEach { (name, data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data.toByteArray()); zip.closeEntry() }
        }
    }
    private fun fixture(block: (File, File, File, String, ManagedModEngine) -> Unit) {
        val storage = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, UUID.randomUUID().toString()).apply { mkdir() }
        try {
            val bundles = File(storage, "Android/data/${GameTarget.PACKAGE_NAME}/${GameTarget.RELATIVE_RESOURCES}").apply { mkdirs() }
            File(bundles, "existing").writeText("original")
            val zip = File(storage, "v1.zip").also { archive(it) }
            val id = UUID.randomUUID().toString()
            val engine = ManagedModEngine(storage)
            ParcelFileDescriptor.open(zip, ParcelFileDescriptor.MODE_READ_ONLY).use { engine.store(it, id, "[]", noop) }
            block(storage, bundles, zip, id, engine)
        } finally { storage.deleteRecursively() }
    }
    private fun overwrite(engine: ManagedModEngine, zip: File, id: String, oldHash: String, newHash: String = ModUpdatePolicy.archiveHash(zip)) =
        ParcelFileDescriptor.open(zip, ParcelFileDescriptor.MODE_READ_ONLY).use {
            engine.overwrite(it, id, oldHash, newHash, true, base, noop)
        }
    private fun current(bundles: File) = File(bundles, "mods/Dev_Test")
    private fun state(engine: ManagedModEngine) = JSONArray(engine.list()).getJSONObject(0)

    @Test fun developerFilesDescribeExactArchiveAndPayloadWithoutChangingActiveState() = fixture { _, bundles, zip, id, engine ->
        val hash = ModUpdatePolicy.archiveHash(zip)
        engine.setActive(id, true, noop)
        engine.writeDeveloperFiles(id, base)
        val dev = File(current(bundles), ".dev")
        val release = ModUpdateJson.release(File(dev, "latest.json").readText())
        assertEquals(hash, release.zipSha256); assertEquals(zip.length(), release.zipSize)
        assertEquals(base + "com.example.dev/Dev_Test.zip", release.zipUrl)
        assertEquals(hash, File(dev, "zip-sha256.txt").readText().trim())
        assertEquals(zip.length().toString(), File(dev, "zip-size.txt").readText().trim())
        val checks = JSONObject(File(dev, "checksums.json").readText())
        assertTrue(checks.getBoolean("readyToPublish")); assertEquals(2, checks.getJSONArray("files").length())
        val first = checks.getJSONArray("files").getJSONObject(0)
        assertEquals(ModUpdatePolicy.archiveHash(File(bundles, "existing")), first.getString("sha256"))
        assertTrue(state(engine).getBoolean("active")); assertEquals(hash, ModUpdatePolicy.archiveHash(File(current(bundles), "Dev_Test.zip")))
        engine.writeDeveloperFiles(id, base)
        assertEquals(release, ModUpdateJson.release(File(dev, "latest.json").readText()))
    }
    @Test fun approvedSameVersionNameCollisionRestoresActiveModAndRetainsPreviousArchive() = fixture { storage, bundles, zip, id, engine ->
        engine.writeDeveloperFiles(id, base); engine.setActive(id, true, noop)
        val newer = File(storage, "other.zip").also { archive(it, 1, modId = "com.example.other", paths = listOf("existing", "different")) }
        val oldHash = ModUpdatePolicy.archiveHash(zip)
        overwrite(engine, newer, id, oldHash)
        val result = state(engine)
        assertEquals(id, result.getString("id")); assertFalse(result.getBoolean("active")); assertTrue(result.isNull("issue"))
        assertEquals("original", File(bundles, "existing").readText()); assertFalse(File(bundles, "added").exists())
        assertFalse(File(bundles, "different").exists())
        assertEquals(oldHash, ModUpdatePolicy.archiveHash(File(current(bundles), "previous/Dev_Test.zip")))
        val release = ModUpdateJson.release(File(current(bundles), ".dev/latest.json").readText())
        assertEquals("com.example.other", release.modId); assertEquals(1L, release.versionCode)
        assertEquals(ModUpdatePolicy.archiveHash(newer), release.zipSha256)
        engine.setActive(id, true, noop)
        overwrite(engine, newer, id, oldHash) // Lost reply retry must preserve reactivation.
        assertTrue(state(engine).getBoolean("active")); assertEquals(1, JSONArray(engine.list()).length())
    }
    @Test fun staleHashChangedPendingZipAndUnrelatedIdentityNeverDisableExistingMod() = fixture { storage, bundles, zip, id, engine ->
        val newer = File(storage, "v2.zip").also { archive(it, 2) }
        val unrelated = File(storage, "unrelated.zip").also { archive(it, 2, "Another name", "com.example.other") }
        val hash = ModUpdatePolicy.archiveHash(zip)
        engine.setActive(id, true, noop)
        listOf(Triple(newer, "0".repeat(64), ModUpdatePolicy.archiveHash(newer)),
            Triple(newer, hash, "0".repeat(64)), Triple(unrelated, hash, ModUpdatePolicy.archiveHash(unrelated))).forEach { (input, old, next) ->
            try { overwrite(engine, input, id, old, next); fail("Unapproved content accepted") }
            catch (_: EngineFailure) { }
            assertTrue(state(engine).getBoolean("active")); assertEquals(hash, state(engine).getString("archiveSha256"))
            assertEquals("UnityFS1-existing-com.example.dev", File(bundles, "existing").readText())
        }
    }
    @Test fun generatedLegacyTemplatesBecomePublishableOnlyAfterReimport() = fixture { storage, bundles, original, id, engine ->
        val legacy = File(storage, "legacy.zip").also { archive(it, null) }
        overwrite(engine, legacy, id, ModUpdatePolicy.archiveHash(original))
        val dev = File(current(bundles), ".dev")
        val draft = JSONObject(File(dev, "checksums.json").readText())
        assertFalse(draft.getBoolean("readyToPublish"))
        val generatedId = JSONObject(File(dev, "info-update.json").readText()).getString("modId")
        assertEquals("dev.${id.replace("-", "")}", generatedId)
        val ready = File(storage, "ready.zip").also { archive(it, 1, modId = generatedId) }
        overwrite(engine, ready, id, ModUpdatePolicy.archiveHash(legacy))
        assertTrue(JSONObject(File(dev, "checksums.json").readText()).getBoolean("readyToPublish"))
        assertEquals(ModUpdatePolicy.archiveHash(ready), ModUpdateJson.release(File(dev, "latest.json").readText()).zipSha256)
    }
    @Test fun overwriteAndDeveloperFilesRecoverAtEveryExchangeBoundary() {
        listOf("UPDATE_READY", "UPDATE_OLD_MOVED", "UPDATE_PUBLISHED", "UPDATE_PREVIOUS_SAVED").forEach { boundary ->
            fixture { storage, bundles, original, id, engine ->
                val newer = File(storage, "v2.zip").also { archive(it, 2, "Renamed", paths = listOf("existing", "different")) }
                engine.setActive(id, true, noop)
                val dying = ManagedModEngine(storage, checkpoint = { if (it == boundary) throw Death() })
                try { overwrite(dying, newer, id, ModUpdatePolicy.archiveHash(original)); fail("Crash expected") } catch (_: Death) { }
                val recovered = ManagedModEngine(storage)
                val result = state(recovered)
                assertEquals(id, result.getString("id")); assertFalse(result.getBoolean("active")); assertTrue(result.isNull("issue"))
                assertEquals("original", File(bundles, "existing").readText())
                assertEquals(ModUpdatePolicy.archiveHash(newer), ModUpdateJson.release(File(current(bundles), ".dev/latest.json").readText()).zipSha256)
                assertEquals(ModUpdatePolicy.archiveHash(original), ModUpdatePolicy.archiveHash(File(current(bundles), "previous/Dev_Test.zip")))
                assertFalse(File(bundles, "mods/.update-$id").exists())
            }
        }
    }
    @Test fun devDirectorySymlinkIsRejectedWithoutTouchingExternalFiles() = fixture { storage, bundles, zip, id, engine ->
        val outside = File(storage, "outside").apply { mkdir() }
        File(outside, "latest.json").writeText("unchanged")
        Os.symlink(outside.path, File(current(bundles), ".dev").path)
        try { engine.writeDeveloperFiles(id, base); fail("Symlink followed") } catch (_: IllegalStateException) { }
        assertEquals("unchanged", File(outside, "latest.json").readText())
        assertEquals(ModUpdatePolicy.archiveHash(zip), ModUpdatePolicy.archiveHash(File(current(bundles), "Dev_Test.zip")))
    }
}
