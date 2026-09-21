package dev.modloader.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

enum class Accent(@androidx.annotation.StringRes val label: Int, val light: Color, val dark: Color) {
    PURPLE(R.string.purple, Color(0xFF6750A4), Color(0xFFD0BCFF)),
    BLUE(R.string.blue, Color(0xFF245EB5), Color(0xFFA8C7FF)),
    GREEN(R.string.green, Color(0xFF216C46), Color(0xFF8BDBAA)),
    ORANGE(R.string.orange, Color(0xFF914A00), Color(0xFFFFB875)),
    PINK(R.string.pink, Color(0xFF9A365F), Color(0xFFFFB0CC)),
    TEAL(R.string.teal, Color(0xFF006A70), Color(0xFF80D5DC))
}

@Composable
fun LoaderTheme(dark: Boolean, accent: Accent, content: @Composable () -> Unit) {
    val primary = if (dark) accent.dark else accent.light
    val surface = if (dark) Color(0xFF121318) else Color(0xFFFCFAFC)
    val ink = if (dark) Color(0xFFE5E2E8) else Color(0xFF1C1B20)
    val container = lerp(surface, primary, if (dark) 0.22f else 0.13f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = base.copy(
        primary = primary, onPrimary = if (dark) Color(0xFF17181C) else Color.White,
        primaryContainer = container, onPrimaryContainer = ink,
        secondary = primary, onSecondary = if (dark) Color(0xFF17181C) else Color.White,
        secondaryContainer = container, onSecondaryContainer = ink,
        tertiary = primary, onTertiary = if (dark) Color(0xFF17181C) else Color.White,
        tertiaryContainer = container, onTertiaryContainer = ink,
        background = surface, onBackground = ink, surface = surface, onSurface = ink,
        surfaceVariant = if (dark) Color(0xFF34363D) else Color(0xFFE4E3E8),
        onSurfaceVariant = if (dark) Color(0xFFC5C6CE) else Color(0xFF45474F),
        surfaceDim = if (dark) Color(0xFF121318) else Color(0xFFDDDEE3),
        surfaceBright = if (dark) Color(0xFF383940) else surface,
        surfaceContainerLowest = if (dark) Color(0xFF0D0E12) else Color.White,
        surfaceContainerLow = if (dark) Color(0xFF1A1B20) else Color(0xFFF5F4F7),
        surfaceContainer = if (dark) Color(0xFF202127) else Color(0xFFEFEEF2),
        surfaceContainerHigh = if (dark) Color(0xFF292A30) else Color(0xFFE9E8ED),
        surfaceContainerHighest = if (dark) Color(0xFF34353B) else Color(0xFFE3E2E7),
        outline = if (dark) Color(0xFF92949E) else Color(0xFF757780),
        outlineVariant = if (dark) Color(0xFF44464F) else Color(0xFFC5C6CE),
        inverseSurface = if (dark) Color(0xFFE3E2E7) else Color(0xFF303137),
        inverseOnSurface = if (dark) Color(0xFF303137) else Color(0xFFF1F0F5),
        surfaceTint = primary, inversePrimary = if (dark) accent.light else accent.dark
    ), content = content)
}
