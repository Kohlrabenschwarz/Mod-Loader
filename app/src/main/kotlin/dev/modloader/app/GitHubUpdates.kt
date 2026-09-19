package dev.modloader.app

import dev.modloader.domain.ReleaseSource
import dev.modloader.domain.ReleaseVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal data class AppUpdate(val version: ReleaseVersion, val downloadUrl: String)
enum class UpdateStatus { IDLE, CHECKING, AVAILABLE, CURRENT, NO_RELEASE, ERROR }

internal object GitHubUpdates {
    /** No credentials, device identifiers or mod data are sent to GitHub. */
    suspend fun latest(): AppUpdate? = withContext(Dispatchers.IO) {
        val connection = URL(ReleaseSource.API).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Mod-Loader-Android")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            val status = connection.responseCode
            if (status == 404) return@withContext null
            check(status == 200) { "Release check failed" }
            val payload = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    check(output.size() + n <= 256 * 1024) { "Release response too large" }
                    output.write(buffer, 0, n)
                }
                output.toString("UTF-8")
            }
            parse(payload)
        } finally { connection.disconnect() }
    }

    internal fun parse(payload: String): AppUpdate? {
        val release = JSONObject(payload)
        if (release.getBoolean("draft") || release.getBoolean("prerelease")) return null
        val version = ReleaseVersion.parse(release.getString("tag_name")) ?: return null
        // Require canonical vX.Y.Z tags, and never open a URL supplied by arbitrary release text.
        if (release.getString("tag_name") != "v$version") return null
        val expected = ReleaseSource.download(version)
        val assets = release.getJSONArray("assets")
        check(assets.length() <= 100)
        val found = (0 until assets.length()).any { i ->
            val asset = assets.getJSONObject(i)
            asset.optString("name") == ReleaseSource.assetName(version) &&
                asset.optString("state") == "uploaded" &&
                asset.optLong("size") in 1..256L * 1024 * 1024 &&
                asset.optString("browser_download_url") == expected
        }
        return if (found) AppUpdate(version, expected) else null
    }
}
