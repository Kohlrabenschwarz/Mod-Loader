package dev.modloader.domain

import java.text.Normalizer
import java.util.Locale

object ModSearch {
    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        .lowercase(Locale.ROOT).replace('ı', 'i')

    fun matches(query: String, vararg fields: String): Boolean {
        val tokens = normalize(query).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return true
        val searchable = normalize(fields.joinToString("\n"))
        return tokens.all { it in searchable }
    }
}
