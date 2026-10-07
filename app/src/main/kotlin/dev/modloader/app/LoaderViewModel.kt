package dev.modloader.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.modloader.bridge.*
import dev.modloader.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray

enum class Notice { READY, SYNCING, IMPORTED, QUEUED, UPDATED, DELETED, LAUNCHING, ERROR }
data class ImportCollisionUi(val incoming: LibraryMod, val existing: List<LibraryMod>)
data class LoaderUi(val mods: List<LibraryMod> = emptyList(), val busy: Boolean = false,
    val progress: Progress? = null, val notice: Notice = Notice.READY, val errorCode: Int = 4,
    val updateStatus: UpdateStatus = UpdateStatus.IDLE, val updateVersion: String? = null,
    val appName: String = "Mod Loader", val appVersion: String = "", val appSha: String? = null, val shaFailed: Boolean = false,
    val language: String = AppLanguage.DEFAULT_CODE, val dark: Boolean = false, val accent: Accent = Accent.PURPLE,
    val agreementAccepted: Boolean = false, val errorConflicts: List<ModConflict> = emptyList(),
    val modUpdates: Map<String, ModUpdateUi> = emptyMap(), val developerMode: Boolean = false,
    val publicationBaseUrl: String = "", val importCollision: ImportCollisionUi? = null, val linkImportError: String? = null)

class LoaderViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val shizukuDelegate = lazy(LazyThreadSafetyMode.NONE) { ShizukuManager(app) }
    val shizuku: ShizukuManager by shizukuDelegate
    private val repository: ModRepository by lazy(LazyThreadSafetyMode.NONE) { ShizukuModRepository(shizuku) }
    private val library = ModLibrary(app)
    private val prefs = app.getSharedPreferences("recovery", 0)
    private val mutableUi = MutableStateFlow(LoaderUi(language = AppLanguage.normalize(prefs.getString("language", null)),
        dark = prefs.getBoolean("dark", false), accent = Accent.entries.firstOrNull { it.name == prefs.getString("accent", "PURPLE") } ?: Accent.PURPLE,
        developerMode = prefs.getBoolean("developerMode", false), publicationBaseUrl = prefs.getString("publicationBaseUrl", "").orEmpty(),
        agreementAccepted = UserAgreementPolicy.isAccepted(prefs.getInt("agreementVersion", 0))))
    val ui = mutableUi.asStateFlow()
    private var syncPending = false
    private var pendingImport: LibraryMod? = null

    private var availableUpdate: AppUpdate? = null
    private var lastUpdateAttempt = -60_000L
    private val sessionIgnored = mutableMapOf<String, String>()
    private var integrityPending = false
    private var lastIntegrityAt = 0L
    private var started = false
    init {
        if (mutableUi.value.agreementAccepted) startAfterAgreement()
    }
    fun acceptAgreement() {
        check(prefs.edit().putInt("agreementVersion", UserAgreementPolicy.VERSION)
            .putLong("agreementAcceptedAt", System.currentTimeMillis()).commit())
        mutableUi.update { it.copy(agreementAccepted = true) }
        startAfterAgreement()
    }
    private fun startAfterAgreement() {
        if (started) return
        started = true
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                val label = app.applicationInfo.loadLabel(app.packageManager).toString()
                val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
                val hash = try {
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    java.io.File(app.applicationInfo.sourceDir).inputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                } catch (_: java.io.IOException) { null }
                Triple(label, version, hash)
            }
            mutableUi.update { it.copy(appName = info.first, appVersion = info.second, appSha = info.third, shaFailed = info.third == null) }
            checkUpdates()
        }
        runOperation {
            val mods = withContext(Dispatchers.IO) { library.load().map(::cachedState) }
            mutableUi.update { it.copy(mods = mods) }
        }
        viewModelScope.launch {
            shizuku.status.collect { status ->
                if (status == ShizukuStatus.READY) {
                    if (ui.value.busy) syncPending = true else sync()
                }
            }
        }
    }
    private fun cachedState(mod: LibraryMod) = mod.copy(
        active = prefs.getBoolean("active:${mod.id}", false),
        warningHidden = false,
        shaMismatch = prefs.getBoolean("shaMismatch:${mod.id}", false),
        damagedArchive = mod.damagedArchive || prefs.getString("archiveHash:${mod.id}", null).let { expected ->
            expected != null && expected != mod.archiveSha256
        },
        archived = mod.id in prefs.getStringSet("knownRemote", emptySet()).orEmpty(),
        folder = prefs.getString("folder:${mod.id}", "") ?: ""
    )
    fun checkUpdates() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (ui.value.updateStatus == UpdateStatus.CHECKING || now - lastUpdateAttempt < 60_000) return
        lastUpdateAttempt = now
        mutableUi.update { it.copy(updateStatus = UpdateStatus.CHECKING) }
        viewModelScope.launch {
            try {
                val latest = GitHubUpdates.latest()
                val current = ReleaseVersion.parse(ui.value.appVersion) ?: error("Invalid installed version")
                availableUpdate = latest?.takeIf { it.version > current }
                mutableUi.update { it.copy(updateStatus = when {
                    latest == null -> UpdateStatus.NO_RELEASE
                    availableUpdate != null -> UpdateStatus.AVAILABLE
                    else -> UpdateStatus.CURRENT
                }, updateVersion = availableUpdate?.version?.toString()) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableUi.update { it.copy(updateStatus = UpdateStatus.ERROR) } }
        }
    }
    fun openUpdate(open: (String) -> Unit) {
        val update = availableUpdate ?: return
        try { open(update.downloadUrl) }
        catch (_: android.content.ActivityNotFoundException) { mutableUi.update { it.copy(updateStatus = UpdateStatus.ERROR) } }
    }
    fun language(value: String) {
        require(AppLanguage.supported(value))
        prefs.edit().putString("language", value).apply()
        mutableUi.update { it.copy(language = value) }
    }
    fun dark(value: Boolean) {
        prefs.edit().putBoolean("dark", value).apply()
        mutableUi.update { it.copy(dark = value) }
    }
    fun accent(value: Accent) {
        prefs.edit().putString("accent", value.name).apply()
        mutableUi.update { it.copy(accent = value) }
    }
    fun developerMode(value: Boolean) = runOperation {
        check(prefs.edit().putBoolean("developerMode", value).putBoolean("developerRefreshPending", value).commit())
        mutableUi.update { it.copy(developerMode = value) }
        if (value && shizuku.status.value == ShizukuStatus.READY) {
            synchronize()
        }
    }
    fun publicationBaseUrl(value: String) = runOperation {
        val base = DeveloperPublicationPolicy.baseUrl(value)
        check(prefs.edit().putString("publicationBaseUrl", base)
            .putBoolean("developerRefreshPending", ui.value.developerMode).commit())
        mutableUi.update { it.copy(publicationBaseUrl = base) }
        if (ui.value.developerMode && shizuku.status.value == ShizukuStatus.READY) {
            synchronize()
        }
    }
    fun play(launch: () -> Unit) = runOperation {
        mutableUi.update { it.copy(notice = Notice.LAUNCHING) }
        if (shizuku.status.value == ShizukuStatus.READY) {
            repository.stopGame()
            val states = managedMods()
            publish(states)
            if (states.any { it.issue != null }) throw EngineFailure(6, "RECOVERY_REQUIRED")
        }
        launch() // Foreground Activity üzerinden normal launcher intent'i.
        mutableUi.update { it.copy(notice = Notice.READY) }
    }
    private fun runOperation(modId: String? = null, block: suspend () -> Unit) {
        if (ui.value.busy) return
        mutableUi.update { it.copy(busy = true, errorCode = 4, progress = null, errorConflicts = emptyList(), linkImportError = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (pendingImport != null && ui.value.importCollision == null) {
                    val pending = pendingImport!!
                    withContext(Dispatchers.IO) { runCatching { library.discardImport(pending) } }
                    pendingImport = null
                }
                // Yanıt kaybolsa bile active flag'i tahmin edilmez; erişilebiliyorsa diskten yenilenir.
                if (shizuku.status.value == ShizukuStatus.READY) {
                    try { publish(managedMods()) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { }
                }
                val code = when (e) {
                    is EngineFailure -> e.code
                    is android.os.RemoteException -> 1
                    is SecurityException -> 1
                    is java.util.zip.ZipException, is IllegalArgumentException -> 3
                    is org.json.JSONException -> 3
                    is java.io.IOException -> 5
                    else -> 4
                }
                if (code == 7 && modId != null) {
                    prefs.edit().putBoolean("shaMismatch:$modId", true).apply()
                    mutableUi.update { it.copy(notice = Notice.READY, progress = null,
                        mods = it.mods.map { mod -> if (mod.id == modId) mod.copy(shaMismatch = true) else mod }) }
                } else mutableUi.update { it.copy(notice = Notice.ERROR, errorCode = code, progress = null,
                    errorConflicts = (e as? EngineFailure)?.conflicts.orEmpty()) }
            } finally {
                mutableUi.update { it.copy(busy = false, progress = null) }
                if (syncPending) { syncPending = false; sync() }
                else if (integrityPending) { integrityPending = false; refreshIntegrity() }
            }
        }
    }
    private suspend fun collect(events: Flow<EngineEvent>) {
        events.collect { event -> if (event is EngineEvent.Update) mutableUi.update { it.copy(progress = event.progress) } }
    }
    private suspend fun publish(states: List<ManagedMod>) {
        lastIntegrityAt = android.os.SystemClock.elapsedRealtime()
        withContext(Dispatchers.IO) {
            val edit = prefs.edit().putStringSet("knownRemote", states.map { it.id }.toSet())
            states.forEach { edit.putBoolean("active:${it.id}", it.active).putString("folder:${it.id}", it.folder)
                .putBoolean("shaMismatch:${it.id}", it.shaMismatch)
                .putString("archiveHash:${it.id}", it.archiveSha256) }
            check(edit.commit())
        }
        val byId = states.associateBy { it.id }
        mutableUi.update { current -> current.copy(mods = current.mods.map { mod ->
            byId[mod.id]?.let { mod.copy(active = it.active, archived = true, folder = it.folder, issue = it.issue, shaMismatch = it.shaMismatch,
                damagedArchive = mod.damagedArchive || (it.archiveSha256 != null && mod.archiveSha256 != it.archiveSha256),
                changes = it.changes, backupAt = it.backupAt, requiredBytes = it.requiredBytes, availableBytes = it.availableBytes,
                warningHidden = it.changes.isNotEmpty() && IntegrityWarningPolicy.fingerprint(it.changes).let { fingerprint ->
                    prefs.getString("hiddenFingerprint:${mod.id}", null) == fingerprint || sessionIgnored[mod.id] == fingerprint
                }) }
                ?: mod.copy(active = false, archived = false, folder = "", issue = null, shaMismatch = false)
        } + states.filter { state -> current.mods.none { it.id == state.id } }.map { state ->
            library.unavailable(state.id).copy(archived = true, active = state.active, folder = state.folder,
                issue = state.issue, shaMismatch = state.shaMismatch, changes = state.changes,
                backupAt = state.backupAt, requiredBytes = state.requiredBytes, availableBytes = state.availableBytes)
        }) }
    }
    private fun legacy(id: String): List<String> = JSONArray(prefs.getString("mod:$id", "[]")).let { a ->
        (0 until a.length()).map(a::getString)
    }
    private suspend fun managedMods(): List<ManagedMod> = repository.managedMods().map { state ->
        if (state.issue != "INVALID_RECORD") state else {
            // A broken JSON record may lose its id. Match the last verified folder without removing its cached ZIP.
            val knownId = prefs.getStringSet("knownRemote", emptySet()).orEmpty().firstOrNull {
                prefs.getString("folder:$it", null) == state.folder
            }
            if (knownId != null) state.copy(id = knownId) else state
        }
    }
    private suspend fun pendingDelete(id: String, add: Boolean) = withContext(Dispatchers.IO) {
        val ids = prefs.getStringSet("pendingDeletes", emptySet()).orEmpty().toMutableSet()
        if (add) ids.add(id) else ids.remove(id)
        check(prefs.edit().putStringSet("pendingDeletes", ids).commit())
    }
    private suspend fun synchronize() {
        mutableUi.update { it.copy(notice = Notice.SYNCING) }
        // Delete cevabı kaybolursa sonraki bağlantıda tekrar dene; cache'i yeniden yükleyip modu diriltme.
        prefs.getStringSet("pendingDeletes", emptySet()).orEmpty().toSet().forEach { id ->
            try { repository.deleteStoredMod(id) }
            catch (e: EngineFailure) {
                if (e.code != 7) throw e
                pendingDelete(id, false)
                return@forEach // Oyun güncellemesi: yalnızca kart uyarısı, otomatik silme yok.
            }
            withContext(Dispatchers.IO) { library.delete(id) }
            pendingDelete(id, false)
        }
        prefs.getStringSet("pendingDiscards", emptySet()).orEmpty().toSet().forEach { id ->
            collect(repository.warningAction(id, false))
            withContext(Dispatchers.IO) { library.delete(id) }
            pendingDiscard(id, false)
        }
        var states = managedMods() // İlk bağlantıda Bundles/mods oluşturulur.
        val remoteIds = states.map { it.id }.toSet()
        val known = prefs.getStringSet("knownRemote", emptySet()).orEmpty().toSet()
        val local = withContext(Dispatchers.IO) { library.load() }
        val localIds = local.map { it.id }.toMutableSet()
        var stored = false
        local.forEach { mod ->
            if (mod.id !in remoteIds) {
                if (mod.id in known && states.none { it.issue == "INVALID_RECORD" }) {
                    withContext(Dispatchers.IO) { library.delete(mod.id) }
                    localIds.remove(mod.id)
                } else if (mod.id !in known && !mod.damagedArchive) {
                    collect(repository.storeMod(mod.archive, mod.id, legacy(mod.id)))
                    if (ui.value.developerMode) repository.writeDeveloperFiles(mod.id, ui.value.publicationBaseUrl)
                    stored = true
                }
            }
        }
        // Rehash remote files only when the archive set actually changed.
        if (stored) states = managedMods()
        states.filter { state -> state.issue != "INVALID_RECORD" &&
            (state.id !in localIds || local.any { it.id == state.id && (it.damagedArchive ||
                (state.archiveSha256 != null && it.archiveSha256 != state.archiveSha256)) }) }.forEach { state ->
            try {
                repository.downloadMod(state.id, library.archiveLocation(state.id))
                withContext(Dispatchers.IO) { library.invalidate(state.id) }
                if (ui.value.developerMode) repository.writeDeveloperFiles(state.id, ui.value.publicationBaseUrl)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Keep the damaged card; other mods remain available. */ }
        }
        val reloaded = withContext(Dispatchers.IO) { library.load() }
        mutableUi.update { it.copy(mods = reloaded) }
        publish(states)
        if (ui.value.developerMode && prefs.getBoolean("developerRefreshPending", false)) {
            ui.value.mods.filter { it.archived && !it.damagedArchive && it.issue == null }.forEach {
                repository.writeDeveloperFiles(it.id, ui.value.publicationBaseUrl)
            }
            check(prefs.edit().putBoolean("developerRefreshPending", false).commit())
        }
        mutableUi.update { it.copy(notice = Notice.READY) }
    }
    fun sync() {
        if (shizuku.status.value == ShizukuStatus.READY) runOperation { synchronize() }
    }
    fun ignoreWarning(id: String, showAgain: Boolean) {
        val mod = ui.value.mods.firstOrNull { it.id == id } ?: return
        if (mod.changes.isEmpty()) return
        val fingerprint = IntegrityWarningPolicy.fingerprint(mod.changes)
        prefs.edit().remove("hideWarning:$id").apply {
            if (showAgain) remove("hiddenFingerprint:$id") else putString("hiddenFingerprint:$id", fingerprint)
        }.apply()
        sessionIgnored[id] = fingerprint
        mutableUi.update { it.copy(mods = it.mods.map { mod -> if (mod.id == id) mod.copy(warningHidden = true) else mod }) }
    }
    private suspend fun pendingDiscard(id: String, add: Boolean) = withContext(Dispatchers.IO) {
        val ids = prefs.getStringSet("pendingDiscards", emptySet()).orEmpty().toMutableSet()
        if (add) ids.add(id) else ids.remove(id)
        check(prefs.edit().putStringSet("pendingDiscards", ids).commit())
    }
    fun warningAction(id: String, recover: Boolean) = runOperation(id) {
        if (!recover) pendingDiscard(id, true)
        collect(repository.warningAction(id, recover))
        if (!recover) {
            withContext(Dispatchers.IO) { library.delete(id) }
            pendingDiscard(id, false)
            mutableUi.update { it.copy(mods = it.mods.filterNot { mod -> mod.id == id }) }
        }
        publish(managedMods())
        mutableUi.update { it.copy(notice = if (recover) Notice.UPDATED else Notice.DELETED) }
    }
    fun refreshIntegrity() {
        sessionIgnored.clear()
        shizuku.refreshConnection()
        mutableUi.update { it.copy(mods = it.mods.map { mod -> mod.copy(warningHidden = mod.changes.isNotEmpty() &&
            prefs.getString("hiddenFingerprint:${mod.id}", null) == IntegrityWarningPolicy.fingerprint(mod.changes)) }) }
        if (shizuku.status.value != ShizukuStatus.READY) return
        if (ui.value.busy) { integrityPending = true; return }
        // A just-completed operation already verified the disk. Mutations always hash again in the engine.
        if (android.os.SystemClock.elapsedRealtime() - lastIntegrityAt < 2_000) return
        runOperation { publish(managedMods()) }
    }
    fun repairArchive(id: String) = runOperation(id) {
        withContext(Dispatchers.IO) {
            repository.downloadMod(id, library.archiveLocation(id))
            library.invalidate(id)
            val mods = library.load()
            mutableUi.update { it.copy(mods = mods) }
        }
        publish(managedMods())
        mutableUi.update { it.copy(notice = Notice.UPDATED) }
    }
    fun checkModUpdate(id: String) {
        if (ui.value.busy) return
        val mod = ui.value.mods.firstOrNull { it.id == id } ?: return
        if (mod.metadata.update == null || ui.value.modUpdates[id]?.status == ModUpdateStatus.CHECKING) return
        mutableUi.update { it.copy(modUpdates = it.modUpdates + (id to ModUpdateUi(ModUpdateStatus.CHECKING))) }
        viewModelScope.launch {
            val result = try {
                val latest = ModUpdates.latest(mod.metadata)
                if (latest.versionCode > requireNotNull(mod.metadata.versionCode))
                    ModUpdateUi(ModUpdateStatus.AVAILABLE, latest)
                else ModUpdateUi(ModUpdateStatus.CURRENT)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { ModUpdateUi(ModUpdateStatus.ERROR, errorCode = (e as? EngineFailure)?.code ?: 15,
                errorDetail = ModUpdates.errorDetail(e)) }
            mutableUi.update { current ->
                if (current.mods.any { it.id == id && it.archiveSha256 == mod.archiveSha256 })
                    current.copy(modUpdates = current.modUpdates + (id to result)) else current
            }
        }
    }
    fun installModUpdate(id: String) = runOperation(id) {
        val mod = ui.value.mods.firstOrNull { it.id == id } ?: return@runOperation
        val release = ui.value.modUpdates[id]?.release ?: return@runOperation
        if (mod.issue != null || mod.shaMismatch || mod.damagedArchive) throw EngineFailure(6, "RECOVERY_REQUIRED")
        val temp = java.io.File(app.cacheDir, "mod-update-$id.part")
        try {
            var lastProgress = 0L
            ModUpdates.download(release, temp) { done, total ->
                val now = android.os.SystemClock.elapsedRealtime()
                if (done == total || now - lastProgress >= 100) {
                    mutableUi.update { it.copy(progress = Progress("update-download", done, total)) }
                    lastProgress = now
                }
            }
            collect(repository.updateMod(temp, id, requireNotNull(mod.archiveSha256), dev.modloader.engine.ModUpdateJson.encode(release)))
            if (ui.value.developerMode) repository.writeDeveloperFiles(id, ui.value.publicationBaseUrl)
            // The privileged archive is authoritative. Hash-aware sync repairs a lost reply or interrupted cache copy too.
            synchronize()
            mutableUi.update { it.copy(notice = Notice.UPDATED, modUpdates = it.modUpdates - id) }
        } finally { withContext(Dispatchers.IO) { temp.delete() } }
    }
    fun importZip(uri: Uri) = runOperation {
        if (pendingImport != null) return@runOperation
        if (shizuku.status.value == ShizukuStatus.READY) synchronize()
        val mod = withContext(Dispatchers.IO) {
            library.stageImport(uri) { bytes -> mutableUi.update { it.copy(progress = Progress("import", bytes, 0)) } }
        }
        acceptImport(mod)
    }
    fun importLink(value: String) = runOperation {
        if (pendingImport != null) return@runOperation
        if (shizuku.status.value == ShizukuStatus.READY) synchronize()
        val mod = try {
            ModLinkImports.read(value.trim()) { stream, length ->
                library.stageImport(stream) { bytes ->
                    mutableUi.update { it.copy(progress = Progress("link-import", bytes, length)) }
                }
            }
        } catch (e: LinkImportFailure) {
            mutableUi.update { it.copy(linkImportError = e.detail) }
            throw EngineFailure(15, "LINK_IMPORT_FAILED")
        }
        acceptImport(mod)
    }
    private suspend fun acceptImport(mod: LibraryMod) {
        pendingImport = mod
        val collisions = ui.value.mods.filter { existing -> !existing.damagedArchive &&
            ModImportPolicy.collides(mod.metadata.name, mod.metadata.modId, existing.metadata.name, existing.metadata.modId) }
        if (collisions.isNotEmpty()) {
            mutableUi.update { it.copy(importCollision = ImportCollisionUi(mod, collisions), notice = Notice.READY) }
            return
        }
        val committed = withContext(Dispatchers.IO) { library.commitImport(mod) }
        pendingImport = null
        mutableUi.update { it.copy(mods = listOf(committed) + it.mods) }
        if (shizuku.status.value == ShizukuStatus.READY) {
            synchronize(); mutableUi.update { it.copy(notice = Notice.IMPORTED) }
        } else mutableUi.update { it.copy(notice = Notice.QUEUED) }
    }
    fun cancelImport() = runOperation {
        pendingImport?.let { withContext(Dispatchers.IO) { library.discardImport(it) } }
        pendingImport = null
        mutableUi.update { it.copy(importCollision = null, notice = Notice.READY) }
    }
    fun overwriteImport(targetId: String) = runOperation(targetId) {
        val collision = ui.value.importCollision ?: return@runOperation
        val incoming = pendingImport ?: return@runOperation
        val original = collision.existing.firstOrNull { it.id == targetId } ?: return@runOperation
        if (shizuku.status.value == ShizukuStatus.READY) synchronize()
        val target = ui.value.mods.firstOrNull { it.id == targetId } ?: throw EngineFailure(16, "OVERWRITE_TARGET_MISSING")
        val oldHash = requireNotNull(original.archiveSha256)
        if (target.archiveSha256 != oldHash || target.damagedArchive || target.issue != null)
            throw EngineFailure(16, "OVERWRITE_PREVIEW_STALE")
        if (target.archived) {
            if (shizuku.status.value != ShizukuStatus.READY) throw EngineFailure(1, "SHIZUKU_REQUIRED")
            collect(repository.overwriteMod(incoming.archive, targetId, oldHash, requireNotNull(incoming.archiveSha256),
                ui.value.developerMode, ui.value.publicationBaseUrl))
            synchronize()
            withContext(Dispatchers.IO) { library.discardImport(incoming) }
        } else {
            val committed = withContext(Dispatchers.IO) { library.commitImport(incoming, targetId, oldHash) }
            mutableUi.update { it.copy(mods = it.mods.map { mod -> if (mod.id == targetId) committed else mod }) }
            if (shizuku.status.value == ShizukuStatus.READY) synchronize()
        }
        pendingImport = null
        mutableUi.update { it.copy(importCollision = null, modUpdates = it.modUpdates - targetId, notice = Notice.UPDATED) }
    }
    fun setActive(id: String, active: Boolean) = runOperation(id) {
        collect(repository.setActive(id, active))
        publish(managedMods())
        mutableUi.update { it.copy(notice = Notice.UPDATED) }
    }
    fun deleteMod(id: String) = runOperation(id) {
        val mod = ui.value.mods.firstOrNull { it.id == id } ?: return@runOperation
        if (mod.archived) {
            pendingDelete(id, true)
            try { repository.deleteStoredMod(id) }
            catch (e: EngineFailure) {
                // SHA çakışmasında otomatik silme kuyruğu oluşturma.
                if (e.code == 7) pendingDelete(id, false)
                throw e
            }
        }
        withContext(Dispatchers.IO) { library.delete(id) }
        pendingDelete(id, false)
        mutableUi.update { it.copy(mods = it.mods.filterNot { mod -> mod.id == id }, notice = Notice.DELETED) }
        if (shizuku.status.value == ShizukuStatus.READY) publish(managedMods())
    }
    override fun onCleared() {
        pendingImport?.let { runCatching { library.discardImport(it) } }
        if (shizukuDelegate.isInitialized()) shizuku.close()
    }
}
