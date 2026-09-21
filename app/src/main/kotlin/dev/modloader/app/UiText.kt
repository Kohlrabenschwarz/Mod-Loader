package dev.modloader.app

import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

enum class AppLanguage(val code: String, val nativeName: String) {
    TURKISH("tr", "Türkçe"), ENGLISH("en", "English"), HINDI("hi", "हिन्दी"),
    CHINESE("zh", "简体中文"), RUSSIAN("ru", "Русский"), GERMAN("de", "Deutsch");

    companion object {
        const val DEFAULT_CODE = "en"
        fun supported(code: String) = entries.any { it.code == code }
        fun normalize(code: String?): String = code?.takeIf(::supported) ?: DEFAULT_CODE
    }
}

class UiText(private val resources: Resources) {
    fun get(@StringRes id: Int, vararg args: Any): String = resources.getString(id, *args)
}

/** Uygulama tercihini kullanır; cihazın genel dilini veya Locale globalini değiştirmez. */
@Composable
fun uiText(language: String): UiText {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration, language) {
        val config = Configuration(configuration).apply {
            setLocale(Locale.forLanguageTag(AppLanguage.normalize(language)))
        }
        UiText(context.createConfigurationContext(config).resources)
    }
}
