package dev.modloader.app

import org.junit.Assert.*
import org.junit.Test

class GitHubUpdatesTest {
    private fun payload(url: String = "https://github.com/Kohlrabenschwarz/Mod-Loader/releases/download/v0.10.0/Mod-Loader-v0.10.0-release.apk", draft: Boolean = false) =
        """{"tag_name":"v0.10.0","draft":$draft,"prerelease":false,"assets":[{"name":"Mod-Loader-v0.10.0-release.apk","state":"uploaded","size":1234,"browser_download_url":"$url"}]}"""
    @Test fun acceptsPublishedApkFromFixedRepository() { assertEquals("0.10.0", GitHubUpdates.parse(payload())?.version.toString()) }
    @Test fun ignoresDrafts() { assertNull(GitHubUpdates.parse(payload(draft = true))) }
    @Test fun rejectsExternalAndLookalikeDownloadHosts() {
        listOf("https://evil.example/a.apk", "https://github.com.evil.example/a.apk", "http://github.com/Kohlrabenschwarz/Mod-Loader/a.apk").forEach {
            assertNull(GitHubUpdates.parse(payload(it)))
        }
    }
    @Test fun rejectsMissingApkAndPrereleases() {
        assertNull(GitHubUpdates.parse(payload().replace("release.apk", "debug.apk")))
        assertNull(GitHubUpdates.parse(payload().replace("\"prerelease\":false", "\"prerelease\":true")))
    }
}
