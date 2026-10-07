package dev.modloader.domain

import java.util.Locale
import kotlin.test.*

class ModSearchTest {
    @Test fun searchesAcrossFieldsAndIgnoresCaseAccentsAndTurkishI() {
        assertTrue(ModSearch.matches("ITU creator", "İtû texture", "Creator", "RGBA32"))
        assertTrue(ModSearch.matches("isik", "IŞIK"))
        assertTrue(ModSearch.matches("éclair", "Eclair"))
        assertTrue(ModSearch.matches("texture 4k", "Itu texture", "4K RGBA32"))
        assertFalse(ModSearch.matches("itu fire", "Itu", "Ice texture"))
    }
    @Test fun blankQueryMatchesEverythingAndNonLatinSearchIsPreserved() {
        assertTrue(ModSearch.matches("  \n ", "Any mod"))
        assertTrue(ModSearch.matches("纹理", "高清纹理"))
        assertTrue(ModSearch.matches("ТЕКСТУРА", "Новая текстура"))
        assertTrue(ModSearch.matches("मॉड", "नया मॉड"))
    }
    @Test fun matchingDoesNotDependOnDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertTrue(ModSearch.matches("itu", "ITU"))
        } finally { Locale.setDefault(original) }
    }
}
