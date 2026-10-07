package dev.modloader.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.modloader.domain.ModSearch
import dev.modloader.domain.GameTarget
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.modloader.bridge.ShizukuStatus

@Composable
fun LoaderScreen(ui: LoaderUi, status: ShizukuStatus, attempt: Int,
    onCheckUpdates: () -> Unit = {}, onUpdate: () -> Unit = {},
    onImport: () -> Unit = {}, onImportLink: (String) -> Unit = {}, onPlay: () -> Unit = {}, onLanguage: (String) -> Unit = {},
    onDark: (Boolean) -> Unit = {}, onAccent: (Accent) -> Unit = {},
    onToggle: (String, Boolean) -> Unit = { _, _ -> }, onDelete: (String) -> Unit = {},
    onRetryConnection: () -> Unit = {}, onRepair: (String) -> Unit = {},
    onCheckModUpdate: (String) -> Unit = {}, onInstallModUpdate: (String) -> Unit = {},
    onDeveloperMode: (Boolean) -> Unit = {}, onPublicationBaseUrl: (String) -> Unit = {},
    onCancelImport: () -> Unit = {}, onOverwriteImport: (String) -> Unit = {},
    onWarningAction: (String, Boolean) -> Unit = { _, _ -> }, onIgnore: (String, Boolean) -> Unit = { _, _ -> }) {
    val text = uiText(ui.language)
    var settings by remember { mutableStateOf(false) }
    var agreementOpen by remember { mutableStateOf(false) }
    var importMenuOpen by remember { mutableStateOf(false) }
    var linkDialogOpen by rememberSaveable { mutableStateOf(false) }
    var importUrl by rememberSaveable { mutableStateOf("") }
    val importEnabled = !ui.busy && ui.importCollision == null
    LaunchedEffect(importEnabled) { if (!importEnabled) importMenuOpen = false }
    val menuOffset = with(LocalDensity.current) { IntOffset(0, -76.dp.roundToPx()) }
    var query by rememberSaveable { mutableStateOf("") }
    val filteredMods = remember(ui.mods, query) { ui.mods.filter { mod ->
        ModSearch.matches(query, mod.metadata.name, mod.metadata.creator, mod.metadata.description,
            mod.metadata.version, mod.folder, mod.metadata.affectedFiles.joinToString("\n"))
    } }
    val connected = status == ShizukuStatus.READY
    val ready = connected && !ui.busy
    val libraryHealthy = ui.mods.none { it.issue == "INVALID_RECORD" }
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
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
            if (!importMenuOpen) {
            FloatingActionButton(onClick = {
                if (!ui.busy) onPlay()
            }, shape = CircleShape, modifier = Modifier.padding(end = 4.dp).size(56.dp),
                containerColor = if (ui.busy) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer) {
                Icon(painterResource(R.drawable.ic_play), contentDescription =
                    if (connected) text.get(R.string.restart_game) else text.get(R.string.open_game))
            }
            }
            Box {
                FloatingActionButton(onClick = {
                    if (importEnabled) importMenuOpen = !importMenuOpen
                }, shape = CircleShape, modifier = Modifier.size(64.dp).semantics {
                    contentDescription = text.get(if (importMenuOpen) R.string.close_import_menu else R.string.import_zip)
                }, containerColor = if (importEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Text(if (importMenuOpen) "×" else "+", style = MaterialTheme.typography.headlineLarge)
                }
                if (importMenuOpen) Popup(alignment = Alignment.BottomEnd, offset = menuOffset,
                    onDismissRequest = { importMenuOpen = false }, properties = PopupProperties(focusable = true)) {
                    val appearing = remember { MutableTransitionState(false).apply { targetState = true } }
                    androidx.compose.animation.AnimatedVisibility(visibleState = appearing, enter = fadeIn() + slideInVertically { it / 2 }) {
                        Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.End) {
                            ImportMenuButton(text.get(R.string.import_from_link), R.drawable.ic_import_link) {
                                importMenuOpen = false; linkDialogOpen = true
                            }
                            ImportMenuButton(text.get(R.string.import_from_file), R.drawable.ic_import_file) {
                                importMenuOpen = false; onImport()
                            }
                        }
                    }
                }
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
                    if (status == ShizukuStatus.DENIED) Text(text.get(R.string.permission_hint), color = Color(0xFF991B1B))
                    if (status == ShizukuStatus.UNSUPPORTED) Text(text.get(R.string.backend_hint), color = Color(0xFF991B1B))
                    if (status in setOf(ShizukuStatus.OFFLINE, ShizukuStatus.ERROR, ShizukuStatus.DENIED))
                        TextButton(onClick = onRetryConnection, enabled = !ui.busy) { Text(text.get(R.string.retry_connection)) }
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
                    Notice.ERROR -> ui.linkImportError?.let { text.get(R.string.import_link_failed, it) }
                        ?: text.get(errorText(ui.errorCode))
                }
                Text(notice, color = if (ui.notice == Notice.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                ui.errorConflicts.forEach { conflict ->
                    Text(conflict.name, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    conflict.files.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (!connected && ui.mods.isNotEmpty()) Text(text.get(R.string.cached), style = MaterialTheme.typography.bodySmall)
                ui.progress?.let { progress ->
                    Spacer(Modifier.height(8.dp))
                    Text(text.get(progressText(progress.phase)))
                    val fraction = progress.fraction
                    if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else { LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth()); Text("${(fraction * 100).toInt()}%") }
                    if (progress.phase in setOf("import", "link-import", "update-download", "ZIP aktarımı", "ZIP doğrulama", "Mod arşivleniyor", "Mod doğrulanıyor", "Yedekleme"))
                        Text(if (progress.total > 0) "${sizeText(progress.done)} / ${sizeText(progress.total)}" else sizeText(progress.done),
                            style = MaterialTheme.typography.bodySmall)
                }
            }
            if (ui.updateVersion != null) item {
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(text.get(R.string.update_available, ui.updateVersion))
                    Text(text.get(R.string.update_install_hint), style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !ui.busy, onClick = onUpdate) { Text(text.get(R.string.download_update)) }
                } }
            }
            item {
                Text(text.get(R.string.mods_count, ui.mods.size), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text(text.get(R.string.search_mods)) },
                    placeholder = { Text(text.get(R.string.search_hint)) },
                    trailingIcon = if (query.isNotEmpty()) { {
                        TextButton(onClick = { query = "" }) { Text(text.get(R.string.clear_search)) }
                    } } else null)
                if (query.isNotBlank()) Text(text.get(R.string.search_results, filteredMods.size, ui.mods.size),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (ui.mods.isEmpty()) item { OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp)) {
                Text(text.get(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
                Text(text.get(R.string.empty_hint))
            } } }
            if (ui.mods.isNotEmpty() && filteredMods.isEmpty()) item {
                Text(text.get(R.string.no_search_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(filteredMods, key = { it.id }) { mod ->
                ModRow(mod, ui.language, ready && libraryHealthy && mod.archived, !ui.busy,
                    update = ui.modUpdates[mod.id] ?: ModUpdateUi(), developerMode = ui.developerMode,
                    onCheckUpdate = { onCheckModUpdate(mod.id) }, onInstallUpdate = { onInstallModUpdate(mod.id) },
                    canDelete = !ui.busy && mod.issue != "INVALID_RECORD" && (!mod.archived || (ready && libraryHealthy)) && !(mod.active && mod.shaMismatch),
                    conflicts = ui.mods.filter { other -> other.id != mod.id && other.active }.mapNotNull { other ->
                        val paths = other.metadata.affectedFiles.map { it.lowercase(java.util.Locale.ROOT) }.toSet()
                        mod.metadata.affectedFiles.filter { it.lowercase(java.util.Locale.ROOT) in paths }
                            .takeIf { it.isNotEmpty() }?.let { other.metadata.name to it }
                    }, onReimport = onImport, onRepair = { onRepair(mod.id) },
                    onToggle = { onToggle(mod.id, it) }, onDelete = { onDelete(mod.id) },
                    onWarningAction = { onWarningAction(mod.id, it) }, onIgnore = { onIgnore(mod.id, it) })
            }
        }
    }
    if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text(text.get(R.string.settings)) },
        text = { SettingsPanel(ui, onLanguage, onDark, onAccent, onCheckUpdates, onUpdate,
            onAgreement = { settings = false; agreementOpen = true },
            onDeveloperMode = onDeveloperMode, onPublicationBaseUrl = onPublicationBaseUrl) },
        confirmButton = { TextButton(onClick = { settings = false }) { Text(text.get(R.string.done)) } })
    if (agreementOpen) UserAgreementDialog(ui.language, required = false, onLanguage = onLanguage,
        onDismiss = { agreementOpen = false })
    if (linkDialogOpen) {
        val validUrl = importUrl.isNotBlank() && runCatching {
            dev.modloader.domain.ModUpdatePolicy.httpsUrl(importUrl.trim())
        }.isSuccess
        AlertDialog(onDismissRequest = { linkDialogOpen = false },
            title = { Text(text.get(R.string.import_from_link)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text.get(R.string.import_link_hint), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = importUrl, onValueChange = { importUrl = it },
                    label = { Text(text.get(R.string.import_link_url)) }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, isError = importUrl.isNotEmpty() && !validUrl,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (validUrl && importEnabled) { linkDialogOpen = false; onImportLink(importUrl.trim()) }
                    }))
            } },
            confirmButton = { TextButton(enabled = validUrl && importEnabled, onClick = {
                linkDialogOpen = false; onImportLink(importUrl.trim())
            }) { Text(text.get(R.string.import_link_confirm)) } },
            dismissButton = { TextButton(onClick = { linkDialogOpen = false }) { Text(text.get(R.string.cancel_action)) } })
    }
    ui.importCollision?.let { collision ->
        var selectedId by remember(collision.incoming.id) { mutableStateOf(collision.existing.first().id) }
        val selected = ui.mods.firstOrNull { it.id == selectedId }
        val selectedPreview = collision.existing.firstOrNull { it.id == selectedId }
        val previewMatches = selected != null && selected.archiveSha256 == selectedPreview?.archiveSha256
        AlertDialog(onDismissRequest = { if (!ui.busy) onCancelImport() },
            title = { Text(text.get(R.string.import_collision_title)) },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(text.get(R.string.import_collision_body, collision.incoming.metadata.name, collision.incoming.metadata.version))
                collision.existing.forEach { existing ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !ui.busy) { selectedId = existing.id },
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedId == existing.id, onClick = { selectedId = existing.id }, enabled = !ui.busy)
                        Column(Modifier.weight(1f)) {
                            Text("${existing.metadata.name} · v${existing.metadata.version}")
                            Text(existing.folder.ifEmpty { existing.id.take(8) }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Text(text.get(R.string.import_overwrite_hint), style = MaterialTheme.typography.bodySmall)
                if (selected?.archived == true && status != ShizukuStatus.READY)
                    Text(text.get(R.string.import_overwrite_connection), color = MaterialTheme.colorScheme.error)
                if (!previewMatches) Text(text.get(R.string.import_overwrite_stale), color = MaterialTheme.colorScheme.error)
                if (selected?.shaMismatch == true) Text(text.get(errorText(7)), color = MaterialTheme.colorScheme.error)
                if (ui.notice == Notice.ERROR) Text(text.get(errorText(ui.errorCode)), color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { TextButton(enabled = !ui.busy && previewMatches && selected.issue == null &&
                !selected.shaMismatch && !selected.damagedArchive &&
                (!selected.archived || status == ShizukuStatus.READY), onClick = { onOverwriteImport(selectedId) }) {
                Text(text.get(R.string.import_overwrite))
            } },
            dismissButton = { TextButton(enabled = !ui.busy, onClick = onCancelImport) { Text(text.get(R.string.cancel_action)) } })
    }
}

@Composable
private fun ImportMenuButton(label: String, icon: Int, onClick: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 2.dp, modifier = Modifier.clickable(onClick = onClick)) {
            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
        SmallFloatingActionButton(onClick = onClick, shape = CircleShape, modifier = Modifier.size(48.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(painterResource(icon), contentDescription = label)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModRow(mod: LibraryMod, language: String, enabled: Boolean, menuEnabled: Boolean, canDelete: Boolean,
    update: ModUpdateUi, developerMode: Boolean, onCheckUpdate: () -> Unit, onInstallUpdate: () -> Unit,
    conflicts: List<Pair<String, List<String>>>, onReimport: () -> Unit, onRepair: () -> Unit,
    onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onWarningAction: (Boolean) -> Unit, onIgnore: (Boolean) -> Unit) {
    val text = uiText(language)
    var expanded by remember(mod.id) { mutableStateOf(false) }
    var menuOpen by remember(mod.id) { mutableStateOf(false) }
    var warningOpen by remember(mod.id) { mutableStateOf(false) }
    var warningChoice by remember(mod.id) { mutableStateOf<String?>(null) }
    var activationPending by remember(mod.id) { mutableStateOf(false) }
    var updatePending by remember(mod.id) { mutableStateOf(false) }
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
                    Text(if (mod.damagedArchive && mod.folder.isNotBlank()) mod.folder else mod.metadata.name,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!mod.damagedArchive) Text("${mod.metadata.creator} · v${mod.metadata.version}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
                }, enabled = enabled && mod.issue == null && !mod.shaMismatch && !mod.damagedArchive,
                    modifier = Modifier.semantics { contentDescription = text.get(R.string.activate_mod, mod.metadata.name) })
            }
            if (!mod.archived) Text(text.get(R.string.waiting_archive), style = MaterialTheme.typography.bodySmall)
            if (mod.issue != null) Text(text.get(R.string.state_issue), color = MaterialTheme.colorScheme.error)
            if (mod.issue == "INVALID_RECORD") Text(text.get(R.string.invalid_record), color = MaterialTheme.colorScheme.error)
            if (mod.damagedArchive) {
                Text(text.get(R.string.damaged_archive), color = MaterialTheme.colorScheme.error)
                if (mod.archived && mod.issue == null) TextButton(enabled = enabled, onClick = onRepair) { Text(text.get(R.string.repair_archive)) }
            }
            if (mod.damagedArchive || mod.issue == "INVALID_RECORD")
                TextButton(enabled = menuEnabled, onClick = onReimport) { Text(text.get(R.string.reimport_archive)) }
            if (expanded) {
                HorizontalDivider(); Spacer(Modifier.height(8.dp))
                if (developerMode && mod.archived && mod.folder.isNotEmpty()) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(text.get(R.string.dev_files_location, "${GameTarget.RESOURCES_PATH}mods/${mod.folder}/.dev"),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(text.get(R.string.affected_files), style = MaterialTheme.typography.labelLarge)
                mod.metadata.affectedFiles.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                if (mod.requiredBytes > 0 && !mod.active) {
                    Text(text.get(R.string.space_estimate, sizeText(mod.requiredBytes), sizeText(mod.availableBytes)),
                        style = MaterialTheme.typography.bodySmall)
                }
                if (mod.metadata.update != null && !mod.damagedArchive) {
                    TextButton(enabled = menuEnabled && update.status != ModUpdateStatus.CHECKING, onClick = onCheckUpdate) {
                        Text(text.get(if (update.status == ModUpdateStatus.CHECKING) R.string.update_checking else R.string.mod_check_update))
                    }
                    when (update.status) {
                        ModUpdateStatus.CURRENT -> Text(text.get(R.string.mod_update_current), style = MaterialTheme.typography.bodySmall)
                        ModUpdateStatus.ERROR -> {
                            Text(text.get(errorText(update.errorCode)), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                            update.errorDetail?.let { detail ->
                                Text(text.get(R.string.mod_update_diagnostic, detail), style = MaterialTheme.typography.bodySmall)
                            }
                            Text(text.get(R.string.mod_update_source,
                                ModUpdates.diagnosticSource(mod.metadata.update!!.manifestUrl)),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        ModUpdateStatus.AVAILABLE -> {
                            Text(text.get(R.string.update_available, update.release!!.version), style = MaterialTheme.typography.bodySmall)
                            TextButton(enabled = enabled && mod.issue == null && !mod.shaMismatch, onClick = { updatePending = true }) {
                                Text(text.get(R.string.mod_download_update))
                            }
                        }
                        else -> Unit
                    }
                }
            }
            if (mod.shaMismatch && !mod.warningHidden) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { warningOpen = true }) {
                    Icon(painterResource(R.drawable.ic_warning), contentDescription = text.get(R.string.sha_title),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (updatePending && update.release != null) AlertDialog(onDismissRequest = { updatePending = false },
        title = { Text(text.get(R.string.update_available, update.release.version)) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text.get(R.string.mod_update_confirm))
            Text(text.get(R.string.mod_update_source, dev.modloader.domain.ModUpdatePolicy.httpsUrl(mod.metadata.update!!.manifestUrl).host))
            Text(sizeText(update.release.zipSize))
            if (update.release.changelog.isNotEmpty()) Text(update.release.changelog)
            Text(text.get(R.string.mod_update_hash_hint), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(enabled = enabled && mod.issue == null && !mod.shaMismatch,
            onClick = { updatePending = false; onInstallUpdate() }) { Text(text.get(R.string.mod_download_update)) } },
        dismissButton = { TextButton(onClick = { updatePending = false }) { Text(text.get(R.string.cancel_action)) } })
    if (activationPending) AlertDialog(
        onDismissRequest = { activationPending = false },
        title = { Text(text.get(R.string.activate_confirm_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text.get(R.string.activate_confirm_body))
                Text(text.get(R.string.unverified_metadata), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error)
                if (mod.requiredBytes > 0) Text(text.get(R.string.space_estimate,
                    sizeText(mod.requiredBytes), sizeText(mod.availableBytes)))
                if (mod.requiredBytes > mod.availableBytes) Text(text.get(R.string.error_space), color = MaterialTheme.colorScheme.error)
                if (conflicts.isNotEmpty()) {
                    Text(text.get(R.string.conflict_details), color = MaterialTheme.colorScheme.error)
                    conflicts.forEach { (name, paths) ->
                        Text(name, fontWeight = FontWeight.Bold)
                        paths.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                Text(text.get(R.string.affected_files), style = MaterialTheme.typography.labelLarge)
                mod.metadata.affectedFiles.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(enabled = enabled && conflicts.isEmpty() && mod.requiredBytes <= mod.availableBytes,
            onClick = { activationPending = false; onToggle(true) }) {
            Text(text.get(R.string.activate_anyway))
        } },
        dismissButton = { TextButton(onClick = { activationPending = false }) { Text(text.get(R.string.cancel_action)) } }
    )
    if (warningOpen) AlertDialog(onDismissRequest = { warningOpen = false },
        title = { Text(text.get(R.string.sha_title)) }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text.get(R.string.sha_body))
                Text(text.get(R.string.backup_date, if (mod.backupAt > 0)
                    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
                        java.util.Locale.forLanguageTag(language)).format(java.util.Date(mod.backupAt))
                    else text.get(R.string.unknown_date)))
                Text(text.get(R.string.warning_actions_help))
                mod.changes.forEach { change ->
                    HorizontalDivider()
                    Text(change.path, fontWeight = FontWeight.Bold)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Column {
                            Text(text.get(R.string.expected_hash, change.expected ?: text.get(R.string.missing_file)), style = MaterialTheme.typography.bodySmall)
                            Text(text.get(R.string.current_hash, if (change.actual == "unreadable") text.get(R.string.unreadable_file)
                                else change.actual ?: text.get(R.string.missing_file)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
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

private fun sizeText(bytes: Long): String = String.format(java.util.Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0))
private fun progressText(phase: String): Int = when (phase) {
    "link-import" -> R.string.import_link_downloading
    "update-download" -> R.string.mod_update_downloading
    "import", "ZIP aktarımı", "Mod arşivleniyor" -> R.string.phase_import
    "ZIP doğrulama", "Mod doğrulanıyor" -> R.string.phase_validate
    "Hedef dosyalar inceleniyor", "Uygulama öncesi doğrulama" -> R.string.phase_inspect
    "Yedekleme" -> R.string.phase_backup
    "Dosyalar uygulanıyor" -> R.string.phase_apply
    "restore", "Geri alma" -> R.string.phase_restore
    else -> R.string.processing
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
    14 -> R.string.mod_update_hash_error
    15 -> R.string.mod_update_network_error
    16 -> R.string.mod_update_metadata_error
    else -> R.string.operation_error
}

/** Shizuku veya ViewModel oluşturmaz; Preview ve gerçek diyalog aynı UI'ı kullanır. */
@Composable
fun SettingsPanel(ui: LoaderUi, onLanguage: (String) -> Unit = {}, onDark: (Boolean) -> Unit = {},
    onAccent: (Accent) -> Unit = {}, onCheckUpdates: () -> Unit = {}, onUpdate: () -> Unit = {},
    onAgreement: () -> Unit = {}, onDeveloperMode: (Boolean) -> Unit = {}, onPublicationBaseUrl: (String) -> Unit = {}) {
    val text = uiText(ui.language)
    var publicationUrl by remember(ui.publicationBaseUrl) { mutableStateOf(ui.publicationBaseUrl) }
    val validPublication = remember(publicationUrl) {
        runCatching { dev.modloader.domain.DeveloperPublicationPolicy.baseUrl(publicationUrl) }.isSuccess
    }
    val focusManager = LocalFocusManager.current
    val savePublication = {
        if (!ui.busy && validPublication && publicationUrl != ui.publicationBaseUrl) onPublicationBaseUrl(publicationUrl)
        focusManager.clearFocus()
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text.get(R.string.dev_mode), modifier = Modifier.weight(1f))
                Switch(checked = ui.developerMode, onCheckedChange = onDeveloperMode, enabled = !ui.busy,
                    modifier = Modifier.semantics { contentDescription = text.get(R.string.dev_mode) })
            }
            if (ui.developerMode) {
                Text(text.get(R.string.dev_mode_help), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = publicationUrl, onValueChange = { publicationUrl = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !ui.busy,
                    label = { Text(text.get(R.string.dev_publication_url)) }, isError = !validPublication,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { savePublication() }))
                if (!validPublication) Text(text.get(R.string.dev_publication_invalid), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                TextButton(enabled = !ui.busy && validPublication && publicationUrl != ui.publicationBaseUrl,
                    onClick = savePublication) { Text(text.get(R.string.dev_publication_save)) }
                if (ui.publicationBaseUrl.isEmpty()) Text(text.get(R.string.dev_draft_hint), style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
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
