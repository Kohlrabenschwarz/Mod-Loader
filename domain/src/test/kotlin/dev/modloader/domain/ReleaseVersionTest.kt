package dev.modloader.domain

import kotlin.test.*

class ReleaseVersionTest {
    @Test fun comparesNumerically() {
        assertTrue(ReleaseVersion.parse("v0.10.0")!! > ReleaseVersion.parse("0.9.9")!!)
        assertTrue(ReleaseVersion.parse("v1.0.0")!! > ReleaseVersion.parse("v0.99.99")!!)
        assertEquals(ReleaseVersion.parse("v0.9.0"), ReleaseVersion.parse("0.9.0"))
    }
    @Test fun rejectsUnstableMalformedAndOverflowingVersions() {
        listOf("v1.0.0-beta", "01.0.0", "1.0", "1.0.0/../../x", "1.0.0?x", "999999999999.0.0", " 1.0.0", "1.0.0\n").forEach {
            assertNull(ReleaseVersion.parse(it), it)
        }
    }
    @Test fun constructsOnlyTheFixedRepositoryAsset() {
        assertEquals("https://github.com/Kohlrabenschwarz/Mod-Loader/releases/download/v0.9.0/Mod-Loader-v0.9.0-release.apk",
            ReleaseSource.download(ReleaseVersion(0, 9, 0)))
    }
}
