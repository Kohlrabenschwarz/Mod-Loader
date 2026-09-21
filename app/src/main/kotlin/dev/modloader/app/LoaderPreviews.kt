package dev.modloader.app

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.modloader.bridge.ShizukuStatus
import dev.modloader.engine.ModMetadata
import java.io.File

// Dosya okumaz, ViewModel/Shizuku oluşturmaz. Interactive Mode değişimleri yalnızca örnek duruma yazar.
private fun sampleMods() = listOf(
    LibraryMod("preview-1", File("preview-1.zip"),
        ModMetadata("Arena Night", "Demo Creator", "Sample mod description — preview data only.",
            "1.0", listOf("5a9e13d8bc7844df970f612ea7d306c2"), null), null, active = true, archived = true),
    LibraryMod("preview-2", File("preview-2.zip"),
        ModMetadata("Classic Style", "Demo Creator", "A second card for checking spacing and text wrapping.",
            "2.0", listOf("b074ce9261ad4e8ab2597380cfed615a"), null), null, archived = true, shaMismatch = true)
)

@Preview(name = "Türkçe · Light", locale = "tr", widthDp = 393, heightDp = 851, showBackground = true)
@Preview(name = "English · Dark", locale = "en", widthDp = 393, heightDp = 851, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Deutsch · Large text", locale = "de", widthDp = 360, heightDp = 800, fontScale = 1.3f)
@Composable
private fun LibraryPreview() {
    val language = LocalConfiguration.current.locales[0].language
    val dark = isSystemInDarkTheme()
    var ui by remember { mutableStateOf(LoaderUi(mods = sampleMods(), language = language, dark = dark)) }
    LoaderTheme(ui.dark, ui.accent) {
        LoaderScreen(ui, ShizukuStatus.READY, 1,
            onLanguage = { ui = ui.copy(language = it) }, onDark = { ui = ui.copy(dark = it) },
            onAccent = { ui = ui.copy(accent = it) },
            onToggle = { id, active -> ui = ui.copy(mods = ui.mods.map { if (it.id == id) it.copy(active = active) else it }) },
            onDelete = { id -> ui = ui.copy(mods = ui.mods.filterNot { it.id == id }) })
    }
}

@Preview(name = "Türkçe settings", locale = "tr", widthDp = 320, heightDp = 560, showBackground = true)
@Preview(name = "English settings", locale = "en", widthDp = 320, heightDp = 560, showBackground = true)
@Preview(name = "हिन्दी settings", locale = "hi", widthDp = 320, heightDp = 560, showBackground = true)
@Preview(name = "简体中文 settings", locale = "zh", widthDp = 320, heightDp = 560, showBackground = true)
@Preview(name = "Русский settings", locale = "ru", widthDp = 320, heightDp = 560, showBackground = true)
@Preview(name = "Deutsch settings", locale = "de", widthDp = 320, heightDp = 560, showBackground = true)
@Composable
private fun SettingsPreview() {
    val language = LocalConfiguration.current.locales[0].language
    var ui by remember { mutableStateOf(LoaderUi(language = language)) }
    LoaderTheme(ui.dark, ui.accent) {
        Surface(Modifier.padding(16.dp)) {
            SettingsPanel(ui, { ui = ui.copy(language = it) }, { ui = ui.copy(dark = it) }, { ui = ui.copy(accent = it) })
        }
    }
}
