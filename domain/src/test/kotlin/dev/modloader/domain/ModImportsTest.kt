package dev.modloader.domain

import org.junit.Assert.*
import org.junit.Test

class ModImportsTest {
    @Test fun collisionMeansSameStableIdOrSameName() {
        assertTrue(ModImportPolicy.collides("New name", "com.example.mod", "Old name", "com.example.mod"))
        assertTrue(ModImportPolicy.collides(" Texture Pack ", "com.example.one", "texture pack", "com.example.two"))
        assertTrue(ModImportPolicy.collides("Legacy", null, "legacy", null))
        assertTrue(ModImportPolicy.collides("İlk mod", null, "ilk mod", null))
        assertFalse(ModImportPolicy.collides("One", null, "Two", null))
        assertFalse(ModImportPolicy.collides("One", "com.example.one", "Two", "com.example.two"))
    }
    @Test fun publicationDirectoryNormalizesAndEncodesArchiveNames() {
        assertEquals("https://example.com/mods/", DeveloperPublicationPolicy.baseUrl(" https://example.com/mods "))
        assertEquals("https://example.com/mods/T%C3%BCrk%C3%A7e.zip",
            DeveloperPublicationPolicy.archiveUrl("https://example.com/mods", "Türkçe.zip"))
        assertEquals("", DeveloperPublicationPolicy.archiveUrl("", "mod.zip"))
    }
    @Test fun rejectsNonPublicUrlSyntaxAndSignedDirectoryAddresses() {
        listOf("http://example.com/x", "https://user:pass@example.com/x", "https://example.com/x?token=secret",
            "https://example.com/x#fragment", "https://localhost/x", "https://example.com:8443/x").forEach {
            try { DeveloperPublicationPolicy.baseUrl(it); fail("Invalid address accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
