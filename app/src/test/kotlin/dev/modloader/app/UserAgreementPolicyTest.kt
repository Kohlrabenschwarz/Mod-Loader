package dev.modloader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgreementPolicyTest {
    @Test fun onlyCurrentAgreementVersionIsAccepted() {
        assertFalse(UserAgreementPolicy.isAccepted(0))
        assertTrue(UserAgreementPolicy.isAccepted(UserAgreementPolicy.VERSION))
        assertFalse(UserAgreementPolicy.isAccepted(UserAgreementPolicy.VERSION + 1))
    }

    @Test fun languageDefaultsToEnglishAndKeepsSupportedPreferences() {
        assertTrue(AppLanguage.DEFAULT_CODE == "en")
        assertTrue(AppLanguage.normalize(null) == "en")
        assertTrue(AppLanguage.normalize("invalid") == "en")
        assertTrue(AppLanguage.normalize("tr") == "tr")
    }

    @Test fun agreementMarkdownPreservesHeadingsParagraphsBulletsAndLinks() {
        val blocks = parseAgreementMarkdown("""
            # Title

            Intro **important** text.

            ## 1. Section

            - Read the [rules](https://example.com/rules).
        """.trimIndent())

        assertEquals(4, blocks.size)
        assertEquals(AgreementBlock.Heading(1, "Title"), blocks[0])
        assertEquals(AgreementBlock.Paragraph("Intro important text."), blocks[1])
        assertEquals(AgreementBlock.Heading(2, "1. Section"), blocks[2])
        assertEquals(
            AgreementBlock.Bullet("Read the rules (https://example.com/rules)."),
            blocks[3]
        )
    }

    @Test fun everySupportedLanguageMapsToABundledAgreementName() {
        assertEquals("legal/TERMS.md", agreementAssetPath("en"))
        AppLanguage.entries.filterNot { it.code == "en" }.forEach { language ->
            assertEquals("legal/terms/TERMS_${language.code}.md", agreementAssetPath(language.code))
        }
    }
}
