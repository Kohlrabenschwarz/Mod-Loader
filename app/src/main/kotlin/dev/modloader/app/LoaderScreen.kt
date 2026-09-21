package dev.modloader.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.modloader.bridge.ShizukuStatus

@Composable
fun LoaderScreen(ui: LoaderUi, status: ShizukuStatus, attempt: Int,
    onCheckUpdates: () -> Unit = {}, onUpdate: () -> Unit = {},
    onImport: () -> Unit = {}, onPlay: () -> Unit = {}, onLanguage: (String) -> Unit = {},
    onDark: (Boolean) -> Unit = {}, onAccent: (Accent) -> Unit = {},
    onToggle: (String, Boolean) -> Unit = { _, _ -> }, onDelete: (String) -> Unit = {},
    onWarningAction: (String, Boolean) -> Unit = { _, _ -> }, onIgnore: (String, Boolean) -> Unit = { _, _ -> }) {
    val text = uiText(ui.language)
    var settings by remember { mutableStateOf(false) }
    var agreementOpen by remember { mutableStateOf(false) }
    val connected = status == ShizukuStatus.READY
    val ready = connected && !ui.busy
    val statusLabel = text.get(when (status) {
        ShizukuStatus.READY -> R.string.connected
        ShizukuStatus.CONNECTING -> R.string.connecting
        ShizukuStatus.PERMISSION_REQUIRED -> R.string.permission
        ShizukuStatus.DENIED -> R.string.denied
        ShizukuStatus.UNSUPPORTED -> R.string.unsupported
        ShizukuStatus.OFFLINE -> R.string.offline
        ShizukuStatus.ERROR -> R.string.connection_failed
    })
    Scaffold(floatingActionButton = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FloatingActionButton(onClick = {
                if (!ui.busy) onPlay()
            }, shape = CircleShape, modifier = Modifier.size(56.dp),
                containerColor = if (ui.busy) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer) {
                Icon(painterResource(R.drawable.ic_play), contentDescription =
                    if (connected) text.get(R.string.restart_game) else text.get(R.string.open_game))
            }
        FloatingActionButton(onClick = {
            if (!ui.busy) onImport()
        }, shape = CircleShape, modifier = Modifier.size(64.dp).semantics {
            contentDescription = text.get(R.string.import_zip)
        }, containerColor = if (ui.busy) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer) {
            Text("+", style = MaterialTheme.typography.headlineLarge)
        }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 170.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SHADOW FIGHT ARENA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(text.get(R.string.library), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { settings = true }) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = text.get(R.string.settings))
                    }
                }
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text("${ui.appName} - ${ui.appVersion} - ${ui.appSha ?: text.get(if (ui.shaFailed) R.string.sha_unavailable else R.string.sha_loading)}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            item { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = if (connected) Color(0xFFDDF4E5) else Color(0xFFFDE3E3))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("● Shizuku — $statusLabel", style = MaterialTheme.typography.titleMedium,
                        color = if (connected) Color(0xFF166534) else Color(0xFF991B1B))
                    if (status == ShizukuStatus.CONNECTING || status == ShizukuStatus.PERMISSION_REQUIRED)
                        Text(text.get(R.string.attempt, attempt), color = Color(0xFF991B1B))
                }
            } }
            item {
                val notice = when (ui.notice) {
                    Notice.READY -> text.get(R.string.ready)
                    Notice.SYNCING -> text.get(R.string.syncing)
                    Notice.IMPORTED -> text.get(R.string.imported)
                    Notice.QUEUED -> text.get(R.string.queued)
                    Notice.UPDATED -> text.get(R.string.updated)
                    Notice.DELETED -> text.get(R.string.deleted)
                    Notice.LAUNCHING -> text.get(R.string.launching)
                    Notice.ERROR -> text.get(errorText(ui.errorCode))
                }
                Text(notice, color = if (ui.notice == Notice.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (!connected && ui.mods.isNotEmpty()) Text(text.get(R.string.cached), style = MaterialTheme.typography.bodySmall)
                ui.progress?.let { progress ->
                    Spacer(Modifier.height(8.dp))
                    Text(text.get(R.string.processing))
                    val fraction = progress.fraction
                    if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else { LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth()); Text("${(fraction * 100).toInt()}%") }
                }
            }
            if (ui.updateVersion != null) item {
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(text.get(R.string.update_available, ui.updateVersion))
                    Text(text.get(R.string.update_install_hint), style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !ui.busy, onClick = onUpdate) { Text(text.get(R.string.download_update)) }
                } }
            }
            item { Text(text.get(R.string.mods_count, ui.mods.size), style = MaterialTheme.typography.titleLarge) }
            if (ui.mods.isEmpty()) item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp)) {
                Text(text.get(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
                Text(text.get(R.string.empty_hint))
            } } }
            items(ui.mods, key = { it.id }) { mod ->
                ModRow(mod, ui.language, ready && mod.archived, !ui.busy,
                    canDelete = !ui.busy && (!mod.archived || ready) && !(mod.active && mod.shaMismatch),
                    onToggle = { onToggle(mod.id, it) }, onDelete = { onDelete(mod.id) },
                    onWarningAction = { onWarningAction(mod.id, it) }, onIgnore = { onIgnore(mod.id, it) })
            }
        }
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text(text.get(R.string.settings)) },
        text = { SettingsPanel(ui, onLanguage, onDark, onAccent, onCheckUpdates, onUpdate,
            onAgreement = { settings = false; agreementOpen = true }) },
        confirmButton = { TextButton(onClick = { settings = false }) { Text(text.get(R.string.done)) } })
    if (agreementOpen) UserAgreementDialog(ui.language, required = false, onLanguage = onLanguage,
        onDismiss = { agreementOpen = false })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModRow(mod: LibraryMod, language: String, enabled: Boolean, menuEnabled: Boolean, canDelete: Boolean,
    onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onWarningAction: (Boolean) -> Unit, onIgnore: (Boolean) -> Unit) {
    val text = uiText(language)
    var expanded by remember(mod.id) { mutableStateOf(false) }
    var menuOpen by remember(mod.id) { mutableStateOf(false) }
    var warningOpen by remember(mod.id) { mutableStateOf(false) }
    var warningChoice by remember(mod.id) { mutableStateOf<String?>(null) }
    var activationPending by remember(mod.id) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().combinedClickable(enabled = menuEnabled,
        onClick = { expanded = !expanded }, onLongClick = { menuOpen = true }, onLongClickLabel = text.get(R.string.mod_options))) {
        Column(Modifier.padding(14.dp)) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (mod.shaMismatch) DropdownMenuItem(text = { Text(text.get(R.string.sha_title)) },
                    onClick = { menuOpen = false; warningOpen = true })
                DropdownMenuItem(text = { Text(text.get(R.string.recover)) }, enabled = enabled && !mod.shaMismatch && (mod.active || mod.issue != null),
                    onClick = { menuOpen = false; onToggle(false) })
                DropdownMenuItem(text = { Text(text.get(R.string.delete)) }, enabled = canDelete,
                    onClick = { menuOpen = false; onDelete() })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                val shape = RoundedCornerShape(16.dp)
                if (mod.icon != null) Image(mod.icon.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(shape), contentScale = ContentScale.Crop)
                else Surface(Modifier.size(64.dp), shape = shape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Text(mod.metadata.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(mod.metadata.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${mod.metadata.creator} · v${mod.metadata.version}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(mod.metadata.description, style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (mod.active) text.get(R.string.active) else text.get(R.string.inactive),
                        color = if (mod.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text.get(R.string.files_details, mod.metadata.affectedFiles.size), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = mod.active, onCheckedChange = { active ->
                    if (active) activationPending = true else onToggle(false)
                }, enabled = enabled && mod.issue == null && !mod.shaMismatch,
                    modifier = Modifier.semantics { contentDescription = text.get(R.string.activate_mod, mod.metadata.name) })
            }
            if (!mod.archived) Text(text.get(R.string.waiting_archive), style = MaterialTheme.typography.bodySmall)
            if (mod.issue != null) Text(text.get(R.string.state_issue), color = MaterialTheme.colorScheme.error)
            if (expanded) {
                HorizontalDivider(); Spacer(Modifier.height(8.dp))
                Text(text.get(R.string.affected_files), style = MaterialTheme.typography.labelLarge)
                mod.metadata.affectedFiles.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
            if (mod.shaMismatch && !mod.warningHidden) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { warningOpen = true }) {
                    Icon(painterResource(R.drawable.ic_warning), contentDescription = text.get(R.string.sha_title),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (activationPending) AlertDialog(
        onDismissRequest = { activationPending = false },
        title = { Text(text.get(R.string.activate_confirm_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text.get(R.string.activate_confirm_body))
                Text(text.get(R.string.unverified_metadata), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error)
                Text(text.get(R.string.affected_files), style = MaterialTheme.typography.labelLarge)
                mod.metadata.affectedFiles.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(enabled = enabled, onClick = { activationPending = false; onToggle(true) }) {
            Text(text.get(R.string.activate_anyway))
        } },
        dismissButton = { TextButton(onClick = { activationPending = false }) { Text(text.get(R.string.cancel_action)) } }
    )
    if (warningOpen) AlertDialog(onDismissRequest = { warningOpen = false },
        title = { Text(text.get(R.string.sha_title)) }, text = { Text(text.get(R.string.sha_body)) },
        confirmButton = { Column(horizontalAlignment = Alignment.End) {
            TextButton(enabled = enabled, onClick = { warningOpen = false; warningChoice = "delete" }) { Text(text.get(R.string.delete)) }
            TextButton(enabled = enabled, onClick = { warningOpen = false; warningChoice = "recover" }) { Text(text.get(R.string.recover_base)) }
            TextButton(onClick = { warningOpen = false; warningChoice = "ignore" }) { Text(text.get(R.string.ignore_warning)) }
        } }, dismissButton = { TextButton(onClick = { warningOpen = false }) { Text(text.get(R.string.done)) } })
    warningChoice?.let { choice ->
        val ignore = choice == "ignore"
        AlertDialog(onDismissRequest = { warningChoice = null },
            title = { Text(text.get(if (ignore) R.string.ignore_warning else if (choice == "recover") R.string.recover_base else R.string.delete)) },
            text = { Text(text.get(if (ignore) R.string.ignore_prompt else if (choice == "recover") R.string.recover_prompt else R.string.discard_prompt)) },
            confirmButton = { Column(horizontalAlignment = Alignment.End) {
                TextButton(enabled = ignore || enabled, onClick = {
                    warningChoice = null
                    if (ignore) onIgnore(true) else onWarningAction(choice == "recover")
                }) { Text(text.get(if (ignore) R.string.show_again else R.string.confirm_action)) }
                if (ignore) TextButton(onClick = { warningChoice = null; onIgnore(false) }) { Text(text.get(R.string.never_again)) }
            } }, dismissButton = { TextButton(onClick = { warningChoice = null }) { Text(text.get(R.string.cancel_action)) } })
    }
}

// Hatalar Binder mesajının diliyle değil, sabit kod üzerinden seçili dilde gösterilir.
private fun errorText(code: Int): Int = when (code) {
    1 -> R.string.error_access
    2 -> R.string.error_space
    3 -> R.string.error_archive
    5 -> R.string.error_io
    6 -> R.string.error_recovery
    7 -> R.string.sha_body
    8 -> R.string.error_conflict
    9 -> R.string.error_limit
    10 -> R.string.error_game
    11 -> R.string.no_base_backup
    12 -> R.string.game_data_missing
    13 -> R.string.error_bundle_header
    else -> R.string.operation_error
}

/** Shizuku veya ViewModel oluşturmaz; Preview ve gerçek diyalog aynı UI'ı kullanır. */
@Composable
fun SettingsPanel(ui: LoaderUi, onLanguage: (String) -> Unit = {}, onDark: (Boolean) -> Unit = {},
    onAccent: (Accent) -> Unit = {}, onCheckUpdates: () -> Unit = {}, onUpdate: () -> Unit = {},
    onAgreement: () -> Unit = {}) {
    val text = uiText(ui.language)
    Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(text.get(R.string.app_updates), style = MaterialTheme.typography.titleMedium)
            val updateLabel = when (ui.updateStatus) {
                UpdateStatus.CHECKING -> R.string.update_checking
                UpdateStatus.CURRENT -> R.string.update_current
                UpdateStatus.ERROR -> R.string.update_failed
                UpdateStatus.NO_RELEASE -> R.string.update_no_release
                else -> R.string.update_source
            }
            Text(text.get(updateLabel), style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = ui.updateStatus != UpdateStatus.CHECKING, onClick = onCheckUpdates) { Text(text.get(R.string.check_updates)) }
            if (ui.updateVersion != null) TextButton(enabled = !ui.busy, onClick = onUpdate) { Text(text.get(R.string.download_update)) }
            TextButton(onClick = onAgreement) { Text(text.get(R.string.agreement_review)) }
            HorizontalDivider()

            Text(text.get(R.string.app_language), style = MaterialTheme.typography.titleMedium)
            AppLanguage.entries.chunked(2).forEach { languages ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    languages.forEach { language ->
                        FilterChip(selected = ui.language == language.code, onClick = { onLanguage(language.code) },
                            modifier = Modifier.weight(1f), label = { Text(language.nativeName) })
                    }
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text.get(R.string.dark_mode), modifier = Modifier.weight(1f))
                Switch(checked = ui.dark, onCheckedChange = onDark,
                    modifier = Modifier.semantics { contentDescription = text.get(R.string.dark_mode) })
            }
            Text(text.get(R.string.accent_color), style = MaterialTheme.typography.titleMedium)
            Accent.entries.chunked(2).forEach { colors ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { accent ->
                        FilterChip(selected = ui.accent == accent, onClick = { onAccent(accent) },
                            modifier = Modifier.weight(1f), label = { Text(text.get(accent.label)) },
                            leadingIcon = {
                                Surface(Modifier.size(16.dp), shape = CircleShape, color = if (ui.dark) accent.dark else accent.light) { }
                            })
                    }
                }
            }
    }
}
