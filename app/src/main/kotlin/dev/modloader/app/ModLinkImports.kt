package dev.modloader.app

import dev.modloader.domain.EngineFailure
import dev.modloader.domain.Limits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.IOException
import java.net.HttpURLConnection

internal class LinkImportFailure(val detail: String) : IOException(detail)

/** Direct ZIP imports share the update transport's HTTPS, public DNS and redirect policy. */
internal object ModLinkImports {
    internal fun <T> network(block: () -> T): T = try { block() }
    catch (e: CancellationException) { throw e }
    catch (e: LinkImportFailure) { throw e }
    catch (e: Exception) { throw LinkImportFailure(ModUpdates.errorDetail(e)) }

    suspend fun <T> read(value: String,
        connect: (String, () -> Unit) -> HttpURLConnection = { url, cancel -> ModUpdates.connect(url, checkCancelled = cancel) },
        consume: suspend (InputStream, Long) -> T): T = withContext(Dispatchers.IO) {
        val context = coroutineContext
        val connection = network { connect(value) { context.ensureActive() } }
        try {
            val length = connection.contentLengthLong
            if (length > Limits.ZIP_BYTES) throw EngineFailure(9, "ZIP_SIZE")
            network { connection.inputStream }.use { input ->
                consume(ArchiveInput(input, length), length.coerceAtLeast(0))
            }
        } finally { connection.disconnect() }
    }

    internal class ArchiveInput(private val source: InputStream, private val expectedSize: Long,
        private val limit: Long = Limits.ZIP_BYTES) : InputStream() {
        private var count = 0L
        private fun checked(n: Int): Int {
            if (n < 0) {
                if (expectedSize >= 0 && count != expectedSize) throw LinkImportFailure("INCOMPLETE_DOWNLOAD")
            } else {
                count += n
                if (count > limit) throw EngineFailure(9, "ZIP_SIZE")
                if (expectedSize >= 0 && count > expectedSize) throw LinkImportFailure("INCOMPLETE_DOWNLOAD")
            }
            return n
        }
        override fun read(): Int {
            val value = network { source.read() }
            checked(if (value < 0) -1 else 1)
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            checked(network { source.read(buffer, offset, length) })
        override fun close() = source.close()
    }
}
