package dev.modloader.app

import dev.modloader.domain.*
import dev.modloader.engine.ModMetadata
import dev.modloader.engine.ModUpdateJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.net.ssl.SSLException
import org.json.JSONException

enum class ModUpdateStatus { IDLE, CHECKING, AVAILABLE, CURRENT, ERROR }
data class ModUpdateUi(val status: ModUpdateStatus = ModUpdateStatus.IDLE,
    val release: ModUpdateRelease? = null, val errorCode: Int = 15, val errorDetail: String? = null)

private class UpdateRequestFailure(val detail: String) : IOException(detail)

internal object ModUpdates {
    /** Stable diagnostic codes; never expose response bodies, credentials or arbitrary exception messages. */
    fun errorDetail(error: Exception): String = when (error) {
        is UpdateRequestFailure -> error.detail
        is UnknownHostException -> "DNS"
        is SSLException -> "TLS"
        is SocketTimeoutException -> "TIMEOUT"
        is ConnectException -> "CONNECTION"
        is JSONException -> "MANIFEST_JSON"
        is java.nio.charset.CharacterCodingException -> "MANIFEST_UTF8"
        is EngineFailure -> "E${error.code}"
        is IllegalArgumentException -> "METADATA_OR_ADDRESS"
        is IOException -> "NETWORK"
        else -> "UNEXPECTED"
    }
    fun diagnosticSource(value: String): String {
        val uri = ModUpdatePolicy.httpsUrl(value)
        return "https://${uri.rawAuthority}${uri.rawPath.orEmpty()}".take(512)
    }
    internal fun connect(value: String,
        resolve: (String) -> Array<InetAddress> = InetAddress::getAllByName,
        openConnection: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
        checkCancelled: () -> Unit): HttpURLConnection {
        var next = value
        repeat(6) { attempt ->
            checkCancelled()
            val uri = ModUpdatePolicy.httpsUrl(next)
            val addresses = resolve(uri.host.removeSurrounding("[", "]"))
            require(addresses.isNotEmpty() && addresses.all(ModUpdatePolicy::publicAddress)) { "Private endpoint rejected" }
            val connection = openConnection(next)
            try {
                connection.connectTimeout = 10_000; connection.readTimeout = 20_000
                connection.instanceFollowRedirects = false; connection.useCaches = false
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("User-Agent", "Mod-Loader-Android")
                val status = connection.responseCode
                if (status in setOf(301, 302, 303, 307, 308)) {
                    check(attempt < 5) { "Too many redirects" }
                    next = uri.resolve(requireNotNull(connection.getHeaderField("Location"))).toString()
                    connection.disconnect()
                } else {
                    if (status != 200) throw UpdateRequestFailure("HTTP $status")
                    if (!connection.contentEncoding.isNullOrEmpty() && connection.contentEncoding != "identity")
                        throw UpdateRequestFailure("CONTENT_ENCODING")
                    return connection
                }
            } catch (e: Exception) { connection.disconnect(); throw e }
        }
        error("Too many redirects")
    }
    suspend fun latest(metadata: ModMetadata): ModUpdateRelease = withContext(Dispatchers.IO) {
        val source = requireNotNull(metadata.update)
        val context = coroutineContext
        val connection = connect(source.manifestUrl) { context.ensureActive() }
        try {
            require(connection.contentLengthLong <= ModUpdatePolicy.METADATA_BYTES)
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    context.ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    require(output.size() + n <= ModUpdatePolicy.METADATA_BYTES)
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            validateRelease(ModUpdateJson.release(text), metadata)
        } finally { connection.disconnect() }
    }
    internal fun validateRelease(release: ModUpdateRelease, metadata: ModMetadata): ModUpdateRelease {
        if (release.modId != metadata.modId || release.modId != metadata.update?.modId ||
            (release.versionCode == metadata.versionCode && release.version != metadata.version))
            throw EngineFailure(16, "UPDATE_IDENTITY_OR_VERSION")
        return release
    }
    suspend fun download(release: ModUpdateRelease, destination: File, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val context = coroutineContext
        try {
            val connection = connect(release.zipUrl) { context.ensureActive() }
            try {
                if (connection.contentLengthLong >= 0 && connection.contentLengthLong != release.zipSize)
                    throw EngineFailure(14, "UPDATE_SIZE_MISMATCH")
                connection.inputStream.use { input ->
                    ModUpdatePolicy.copyVerified(input, destination, release) { done, total ->
                        context.ensureActive(); progress(done, total)
                    }
                }
            } finally { connection.disconnect() }
        } catch (e: CancellationException) { throw e }
        catch (e: EngineFailure) { throw e }
        catch (_: Exception) { throw EngineFailure(15, "UPDATE_DOWNLOAD_FAILED") }
    }
}
