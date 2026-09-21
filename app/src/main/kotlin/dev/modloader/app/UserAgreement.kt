package dev.modloader.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Increment only when a material agreement change requires renewed consent. */
internal object UserAgreementPolicy {
    const val VERSION = 2
    fun isAccepted(storedVersion: Int): Boolean = storedVersion == VERSION
}

@Composable
internal fun UserAgreementDialog(
    language: String,
    required: Boolean,
    onLanguage: (String) -> Unit = {},
    onAccept: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val text = uiText(language)
    val context = androidx.compose.ui.platform.LocalContext.current
    val normalizedLanguage = AppLanguage.normalize(language)
    val blocks = remember(context, normalizedLanguage) {
        val path = agreementAssetPath(normalizedLanguage)
        val markdown = runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }
            .getOrElse { context.assets.open("legal/TERMS.md").bufferedReader().use { it.readText() } }
        parseAgreementMarkdown(markdown).dropWhile { it is AgreementBlock.Heading && it.level == 1 }
    }
    var acknowledged by remember(required) { mutableStateOf(false) }
    var languageMenuOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!required) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !required,
            dismissOnClickOutside = !required,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.96f).padding(horizontal = 12.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text.get(R.string.agreement_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        TextButton(
                            onClick = { languageMenuOpen = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("🌐 ${AppLanguage.entries.first { it.code == normalizedLanguage }.nativeName}")
                        }
                        DropdownMenu(expanded = languageMenuOpen, onDismissRequest = { languageMenuOpen = false }) {
                            AppLanguage.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.nativeName) },
                                    onClick = {
                                        languageMenuOpen = false
                                        onLanguage(option.code)
                                    }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp, bottom = 12.dp))

                SelectionContainer(Modifier.weight(1f)) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        blocks.forEach { block -> AgreementBlockView(block) }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                HorizontalDivider(Modifier.padding(top = 10.dp))
                if (required) {
                    Row(
                        Modifier.fillMaxWidth().clickable { acknowledged = !acknowledged }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text.get(R.string.agreement_accept_checkbox),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (required) TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(text.get(R.string.agreement_decline))
                    }
                    TextButton(
                        enabled = !required || acknowledged,
                        onClick = { if (required) onAccept() else onDismiss() },
                        modifier = if (required) Modifier.weight(1f) else Modifier.fillMaxWidth()
                    ) { Text(text.get(if (required) R.string.agreement_accept else R.string.done)) }
                }
            }
        }
    }
}

@Composable
private fun AgreementBlockView(block: AgreementBlock) {
    when (block) {
        is AgreementBlock.Heading -> Text(
            block.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp)
        )
        is AgreementBlock.Paragraph -> Text(
            block.text,
            style = MaterialTheme.typography.bodyMedium
        )
        is AgreementBlock.Bullet -> Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text("•", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(block.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}
