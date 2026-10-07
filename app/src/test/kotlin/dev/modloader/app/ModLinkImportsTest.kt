package dev.modloader.app

import dev.modloader.domain.EngineFailure
import dev.modloader.domain.Limits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class ModLinkImportsTest {
    private class Connection(private val input: InputStream, private val length: Long) : HttpURLConnection(URL("https://example.com/mod.zip")) {
        var closed = false
        override fun getContentLengthLong() = length
        override fun getInputStream() = input
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun connect() = Unit
    }
    @Test fun acceptsExactAndUnknownLengthStreamsAndClosesConnections() = runBlocking {
        listOf(3L, -1L).forEach { length ->
            val connection = Connection(ByteArrayInputStream(byteArrayOf(1, 2, 3)), length)
            val data = ModLinkImports.read("https://example.com/mod.zip", connect = { _, _ -> connection }) { input, total ->
                assertEquals(length.coerceAtLeast(0), total); input.readBytes()
            }
            assertArrayEquals(byteArrayOf(1, 2, 3), data); assertTrue(connection.closed)
        }
    }
    @Test fun oversizedHeaderIsRejectedBeforeConsumerRuns() = runBlocking {
        val connection = Connection(ByteArrayInputStream(byteArrayOf(1)), Limits.ZIP_BYTES + 1)
        var consumed = false
        try {
            ModLinkImports.read("https://example.com/mod.zip", connect = { _, _ -> connection }) { _, _ -> consumed = true }
            fail("Oversized response accepted")
        } catch (e: EngineFailure) { assertEquals(9, e.code) }
        assertFalse(consumed); assertTrue(connection.closed)
    }
    @Test fun incompleteAndOverlongResponsesAreRejected() = runBlocking {
        listOf(byteArrayOf(1, 2), byteArrayOf(1, 2, 3, 4)).forEach { bytes ->
            val connection = Connection(ByteArrayInputStream(bytes), 3)
            try {
                ModLinkImports.read("https://example.com/mod.zip", connect = { _, _ -> connection }) { input, _ -> input.readBytes() }
                fail("Invalid response length accepted")
            } catch (e: LinkImportFailure) { assertEquals("INCOMPLETE_DOWNLOAD", e.detail) }
            assertTrue(connection.closed)
        }
    }
    @Test fun unknownLengthStillHasAByteLimit() {
        val stream = ModLinkImports.ArchiveInput(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), -1, 3)
        try { stream.readBytes(); fail("Unbounded response accepted") }
        catch (e: EngineFailure) { assertEquals(9, e.code) }
    }
    @Test fun timeoutsRetainSafeDiagnosticsAndCancellationIsNotRewritten() = runBlocking {
        val connection = Connection(object : InputStream() {
            override fun read(): Int = throw SocketTimeoutException("Sensitive URL or token")
        }, -1)
        try {
            ModLinkImports.read("https://example.com/mod.zip", connect = { _, _ -> connection }) { input, _ -> input.read() }
            fail("Timeout ignored")
        } catch (e: LinkImportFailure) { assertEquals("TIMEOUT", e.detail) }
        assertTrue(connection.closed)
        val canceled = Connection(ByteArrayInputStream(byteArrayOf(1)), 1)
        try {
            ModLinkImports.read("https://example.com/mod.zip", connect = { _, _ -> canceled }) { _, _ -> throw CancellationException("cancel") }
            fail("Cancellation ignored")
        } catch (_: CancellationException) { }
        assertTrue(canceled.closed)
    }
}
