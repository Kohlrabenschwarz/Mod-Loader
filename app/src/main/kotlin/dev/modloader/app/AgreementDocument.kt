package dev.modloader.app

internal sealed interface AgreementBlock {
    data class Heading(val level: Int, val text: String) : AgreementBlock
    data class Paragraph(val text: String) : AgreementBlock
    data class Bullet(val text: String) : AgreementBlock
}

/** Parses only the small Markdown subset used by the bundled agreement. */
internal fun parseAgreementMarkdown(markdown: String): List<AgreementBlock> {
    val blocks = mutableListOf<AgreementBlock>()
    val paragraph = mutableListOf<String>()
    fun clean(value: String): String = value
        .replace(Regex("\\[([^]]+)]\\((https?://[^)]+)\\)"), "$1 ($2)")
        .replace("**", "")
        .replace("`", "")
        .trim()
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += AgreementBlock.Paragraph(clean(paragraph.joinToString(" ")))
            paragraph.clear()
        }
    }
    markdown.replace("\r\n", "\n").lineSequence().forEach { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> flushParagraph()
            line.startsWith("## ") -> {
                flushParagraph()
                blocks += AgreementBlock.Heading(2, clean(line.removePrefix("## ")))
            }
            line.startsWith("# ") -> {
                flushParagraph()
                blocks += AgreementBlock.Heading(1, clean(line.removePrefix("# ")))
            }
            line.startsWith("- ") -> {
                flushParagraph()
                blocks += AgreementBlock.Bullet(clean(line.removePrefix("- ")))
            }
            else -> paragraph += line
        }
    }
    flushParagraph()
    return blocks
}

internal fun agreementAssetPath(language: String): String = when (AppLanguage.normalize(language)) {
    "en" -> "legal/TERMS.md"
    else -> "legal/terms/TERMS_${AppLanguage.normalize(language)}.md"
}
