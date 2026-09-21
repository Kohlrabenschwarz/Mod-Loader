package dev.modloader.engine

import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.UUID

class SafeFsTest {
    private fun fixture(block: (File) -> Unit) {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, UUID.randomUUID().toString())
            .apply { check(mkdir()) }
        try { block(root) } finally { root.deleteRecursively() }
    }

    @Test fun noFollowReadsRejectSymbolicLinks() = fixture { root ->
        val real = File(root, "real").apply { writeText("trusted") }
        val link = File(root, "link")
        Os.symlink(real.path, link.path)
        try { SafeFs.hash(link); fail("Symbolic-link read accepted") } catch (_: Exception) { }
        assertEquals("trusted", real.readText())
    }

    @Test fun noFollowWritesRejectSymbolicLinks() = fixture { root ->
        val real = File(root, "real").apply { writeText("trusted") }
        val link = File(root, "link")
        Os.symlink(real.path, link.path)
        try {
            "replacement".byteInputStream().use { SafeFs.copy(it, link, 32) }
            fail("Symbolic-link write accepted")
        } catch (_: Exception) { }
        assertEquals("trusted", real.readText())
    }
}
