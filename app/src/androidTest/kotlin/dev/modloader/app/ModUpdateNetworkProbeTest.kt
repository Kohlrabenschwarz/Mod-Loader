package dev.modloader.app

import androidx.test.platform.app.InstrumentationRegistry
import dev.modloader.domain.ModUpdateSource
import dev.modloader.engine.ModMetadata
import dev.modloader.engine.ModMetadataReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in probe against a supplied public host using Android's actual DNS and HTTPS stack. */
class ModUpdateNetworkProbeTest {
    @Test fun publicZipLinkCanBeStagedWithoutPublishingIt() = runBlocking {
        val url = InstrumentationRegistry.getArguments().getString("linkImportProbeUrl").orEmpty()
        assumeTrue("No live ZIP import source supplied", url.isNotBlank())
        val library = ModLibrary(InstrumentationRegistry.getInstrumentation().targetContext)
        val before = library.load().map { it.id }.toSet()
        val pending = ModLinkImports.read(url) { stream, _ -> library.stageImport(stream) { } }
        try {
            assertEquals(before, library.load().map { it.id }.toSet())
            assertEquals(pending.archiveSha256, dev.modloader.domain.ModUpdatePolicy.archiveHash(pending.archive))
            assertEquals("com.example.update-test", pending.metadata.modId)
        } finally { library.discardImport(pending) }
    }
    @Test fun publicManifestAndVerifiedArchiveCanBeReadOnAndroid() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val manifestUrl = args.getString("updateProbeManifest").orEmpty()
        val modId = args.getString("updateProbeModId").orEmpty()
        assumeTrue("No live update source supplied", manifestUrl.isNotBlank() && modId.isNotBlank())
        val source = ModUpdateSource(modId, manifestUrl)
        val metadata = ModMetadata("Probe", "Tests", "Network probe", "1.0.0", listOf("a"), null,
            modId, 1, source)
        val archive = File.createTempFile("update-probe-", ".zip", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        try {
            val release = ModUpdates.latest(metadata)
            ModUpdates.download(release, archive) { _, _ -> }
            val incoming = ModMetadataReader.read(archive)
            assertEquals(modId, incoming.modId)
            assertEquals(release.version, incoming.version)
            assertEquals(release.versionCode, incoming.versionCode)
            assertEquals(source, incoming.update)
        } finally { archive.delete() }
    }
}
