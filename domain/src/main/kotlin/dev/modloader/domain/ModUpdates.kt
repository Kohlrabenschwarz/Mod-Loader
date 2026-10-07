package dev.modloader.domain

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.URI
import java.security.MessageDigest

object ModPackageFiles {
    val icons = setOf("icon.png", "icon.jpg", "icon.webp")
    val metadata = icons + setOf("info.json", "update.json")
}

data class ModUpdateSource(val modId: String, val manifestUrl: String)
data class ModUpdateRelease(val modId: String, val version: String, val versionCode: Long,
    val zipUrl: String, val zipSha256: String, val zipSize: Long, val changelog: String)

object ModUpdatePolicy {
    const val METADATA_BYTES = 32 * 1024
    fun modId(value: String): String {
        require(value.length in 3..120 && value.matches(Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*"))) { "Invalid modId" }
        return value
    }
    fun versionCode(value: Long): Long {
        require(value in 1..Int.MAX_VALUE.toLong()) { "Invalid versionCode" }; return value
    }
    fun sha256(value: String): String {
        require(value.matches(Regex("[0-9a-f]{64}"))) { "Invalid SHA-256" }; return value
    }
    fun httpsUrl(value: String): URI {
        require(value.length in 1..4096 && value.none { it.isWhitespace() || Character.isISOControl(it) })
        val uri = URI(value)
        require(uri.scheme == "https" && uri.rawUserInfo == null && uri.rawFragment == null &&
            uri.host != null && uri.port in setOf(-1, 443)) { "Public HTTPS URL required" }
        val host = uri.host.lowercase().removeSurrounding("[", "]")
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local") &&
            !host.endsWith('.') && (host.contains('.') || host.contains(':'))) { "Local host rejected" }
        return uri
    }
    /** Applied to every DNS result and redirect. IPv4-mapped IPv6 is normalized by InetAddress. */
    fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 255 }
        return if (bytes.size == 4) {
            bytes[0] !in setOf(0, 10, 127) && bytes[0] < 224 &&
                !(bytes[0] == 100 && bytes[1] in 64..127) &&
                !(bytes[0] == 169 && bytes[1] == 254) &&
                !(bytes[0] == 172 && bytes[1] in 16..31) &&
                !(bytes[0] == 192 && bytes[1] == 168)
        } else bytes.size == 16 && bytes[0] in 0x20..0x3f
    }
    fun archiveHash(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024); var count = 0L
        while (true) {
            val n = input.read(buffer); if (n < 0) break
            count += n; require(count <= Limits.ZIP_BYTES)
            digest.update(buffer, 0, n)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    /** Downloads stay unpublished until exact length AND SHA-256 match. */
    fun copyVerified(input: InputStream, destination: File, release: ModUpdateRelease,
        progress: (Long, Long) -> Unit = { _, _ -> }) {
        sha256(release.zipSha256)
        require(release.zipSize in 1..Limits.ZIP_BYTES)
        if (destination.parentFile!!.usableSpace < release.zipSize + Limits.RESERVE_BYTES)
            throw EngineFailure(2, "NO_DOWNLOAD_SPACE")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(64 * 1024); var count = 0L
                progress(0, release.zipSize)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    count += n
                    if (count > release.zipSize) throw EngineFailure(14, "UPDATE_SIZE_MISMATCH")
                    if (destination.parentFile!!.usableSpace < n + Limits.RESERVE_BYTES)
                        throw EngineFailure(2, "NO_DOWNLOAD_SPACE")
                    output.write(buffer, 0, n); digest.update(buffer, 0, n)
                    progress(count, release.zipSize)
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (count != release.zipSize || actual != release.zipSha256)
                    throw EngineFailure(14, "UPDATE_HASH_MISMATCH")
                output.fd.sync()
            }
        } catch (e: Throwable) {
            destination.delete(); throw e
        }
    }
}
