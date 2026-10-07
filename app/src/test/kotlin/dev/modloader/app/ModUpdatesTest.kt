package dev.modloader.app

import dev.modloader.domain.*
import dev.modloader.engine.ModMetadata
import dev.modloader.engine.ModUpdateJson
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

class ModUpdatesTest {
    private val source = ModUpdateSource("com.example.mod", "https://example.com/latest.json")
    private val metadata = ModMetadata("Name", "Author", "Description", "1.0.0", listOf("a"), null,
        source.modId, 1, source)
    private val release = ModUpdateRelease(source.modId, "1.1.0", 2, "https://example.com/mod.zip", "a".repeat(64), 20, "Changes")
    @Test fun manifestRoundTripsAndAcceptsCompatibleIdentity() {
        assertEquals(release, ModUpdateJson.release(ModUpdateJson.encode(release)))
        assertEquals(release, ModUpdates.validateRelease(release, metadata))
    }
    @Test fun rejectsCoercedVersionCodeSizeHashAndSchema() {
        val json = ModUpdateJson.encode(release)
        listOf(json.replace("\"versionCode\":2", "\"versionCode\":\"2\""),
            json.replace("\"versionCode\":2", "\"versionCode\":2.5"),
            json.replace("\"zipSize\":20", "\"zipSize\":0"),
            json.replace("\"zipSize\":20", "\"zipSize\":1073741825"),
            json.replace("a".repeat(64), "invalid"),
            json.replace("\"schemaVersion\":1", "\"schemaVersion\":2")).forEach { bad ->
                try { ModUpdateJson.release(bad); fail("Invalid manifest accepted: $bad") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun rejectsWrongIdentityAndSameCodeWithDifferentVersion() {
        listOf(release.copy(modId = "com.example.other"), release.copy(versionCode = 1, version = "9.0.0")).forEach {
            try { ModUpdates.validateRelease(it, metadata); fail("Invalid identity accepted") }
            catch (e: EngineFailure) { assertEquals(16, e.code) }
        }
    }
    @Test fun sourceMustDeclareExactSupportedSchemaAndHttpsUrl() {
        assertEquals(source, ModUpdateJson.source("""{"schemaVersion":1,"modId":"com.example.mod","manifestUrl":"https://example.com/latest.json"}"""))
        listOf("http://example.com/x", "https://user:pass@example.com/x", "https://localhost/x").forEach { url ->
            try { ModUpdateJson.source("""{"schemaVersion":1,"modId":"com.example.mod","manifestUrl":"$url"}"""); fail("Bad source accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
    private class Connection(url: String, private val status: Int, private val redirect: String? = null) : HttpURLConnection(URL(url)) {
        var closed = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String): String? = if (name == "Location") redirect else null
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun connect() = Unit
    }
    @Test fun followsBoundedPublicHttpsRedirectsAndClosesIntermediateConnections() {
        val first = Connection("https://example.com/x", 302, "https://cdn.example.com/mod.zip")
        val final = Connection("https://cdn.example.com/mod.zip", 200)
        val opened = mutableListOf<String>()
        val result = ModUpdates.connect("https://example.com/x", resolve = { arrayOf(InetAddress.getByName("8.8.8.8")) },
            openConnection = { url -> opened.add(url); if (url == first.url.toString()) first else final }, checkCancelled = {})
        assertSame(final, result); assertTrue(first.closed)
        assertEquals(listOf(first.url.toString(), final.url.toString()), opened)
        assertFalse(final.instanceFollowRedirects); assertEquals("identity", final.getRequestProperty("Accept-Encoding"))
    }
    @Test fun httpFailuresRetainStatusAndCloseConnections() {
        listOf(401, 403, 404, 500).forEach { status ->
            val connection = Connection(source.manifestUrl, status)
            try {
                ModUpdates.connect(source.manifestUrl, resolve = { arrayOf(InetAddress.getByName("8.8.8.8")) },
                    openConnection = { connection }, checkCancelled = {})
                fail("HTTP error accepted")
            } catch (e: java.io.IOException) {
                assertEquals("HTTP $status", ModUpdates.errorDetail(e))
            }
            assertTrue(connection.closed)
        }
    }
    @Test fun diagnosticsDistinguishNetworkFailuresWithoutExposingSecrets() {
        assertEquals("DNS", ModUpdates.errorDetail(java.net.UnknownHostException("secret")))
        assertEquals("TLS", ModUpdates.errorDetail(javax.net.ssl.SSLHandshakeException("secret")))
        assertEquals("TIMEOUT", ModUpdates.errorDetail(java.net.SocketTimeoutException("secret")))
        assertEquals("CONNECTION", ModUpdates.errorDetail(java.net.ConnectException("secret")))
        assertEquals("MANIFEST_JSON", ModUpdates.errorDetail(org.json.JSONException("secret")))
        assertEquals("UNEXPECTED", ModUpdates.errorDetail(IllegalStateException("secret")))
        assertEquals("https://example.com/latest.json",
            ModUpdates.diagnosticSource("https://example.com/latest.json?token=secret"))
    }
    @Test fun rejectsPrivateRedirectsDowngradesCredentialsAndRedirectLoops() {
        listOf("http://example.com/x", "https://user:pass@example.com/x", "https://private.example.com/x").forEach { target ->
            val first = Connection("https://example.com/x", 302, target)
            var opens = 0
            try {
                ModUpdates.connect("https://example.com/x", resolve = { host ->
                    arrayOf(InetAddress.getByName(if (host == "private.example.com") "127.0.0.1" else "8.8.8.8"))
                }, openConnection = { opens++; first }, checkCancelled = {})
                fail("Unsafe redirect accepted")
            } catch (_: IllegalArgumentException) { }
            assertTrue(first.closed); assertEquals(1, opens)
        }
        val connections = mutableListOf<Connection>()
        try {
            ModUpdates.connect("https://example.com/x", resolve = { arrayOf(InetAddress.getByName("8.8.8.8")) },
                openConnection = { Connection(it, 302, "/x").also(connections::add) }, checkCancelled = {})
            fail("Redirect loop accepted")
        } catch (_: IllegalStateException) { }
        assertEquals(6, connections.size); assertTrue(connections.all { it.closed })
    }
}
