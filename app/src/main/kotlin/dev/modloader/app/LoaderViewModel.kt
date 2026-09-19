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
data class LoaderUi(val mods: List<LibraryMod> = emptyList(), val busy: Boolean = false,
    val progress: Progress? = null, val notice: Notice = Notice.READY, val errorCode: Int = 4,
    val updateStatus: UpdateStatus = UpdateStatus.IDLE, val updateVersion: String? = null,
    val appName: String = "Mod Loader", val appVersion: String = "", val appSha: String? = null, val shaFailed: Boolean = false,
    val language: String = "tr", val dark: Boolean = false, val accent: Accent = Accent.PURPLE)

class LoaderViewModel(application: Application) : AndroidViewModel(application) {
    val shizuku = ShizukuManager(application)
    private val repository: ModRepository = ShizukuModRepository(shizuku)
    private val library = ModLibrary(application)
    private val prefs = application.getSharedPreferences("recovery", 0)
    private val mutableUi = MutableStateFlow(LoaderUi(language = prefs.getString("language", "tr")?.takeIf(AppLanguage::supported) ?: "tr",
        dark = prefs.getBoolean("dark", false), accent = Accent.entries.firstOrNull { it.name == prefs.getString("accent", "PURPLE") } ?: Accent.PURPLE))
    val ui = mutableUi.asStateFlow()
    private var syncPending = false

    private var availableUpdate: AppUpdate? = null
    private var lastUpdateAttempt = -60_000L
    private val sessionIgnored = mutableSetOf<String>()
    init {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                val label = application.applicationInfo.loadLabel(application.packageManager).toString()
                val version = application.packageManager.getPackageInfo(application.packageName, 0).versionName.orEmpty()
                val hash = try {
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    java.io.File(application.applicationInfo.sourceDir).inputStream().use { input ->
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
        warningHidden = prefs.getBoolean("hideWarning:${mod.id}", false),
        shaMismatch = prefs.getBoolean("shaMismatch:${mod.id}", false),
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
    fun play(launch: () -> Unit) = runOperation {
        mutableUi.update { it.copy(notice = Notice.LAUNCHING) }
        if (shizuku.status.value == ShizukuStatus.READY) {
            repository.stopGame()
            val states = repository.managedMods()
            publish(states)
            if (states.any { it.issue != null }) throw EngineFailure(6, "RECOVERY_REQUIRED")
        }
        launch() // Foreground Activity üzerinden normal launcher intent'i.
        mutableUi.update { it.copy(notice = Notice.READY) }
    }
    private fun runOperation(modId: String? = null, block: suspend () -> Unit) {
        if (ui.value.busy) return
        mutableUi.update { it.copy(busy = true, errorCode = 4, progress = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // Yanıt kaybolsa bile active flag'i tahmin edilmez; erişilebiliyorsa diskten yenilenir.
                if (shizuku.status.value == ShizukuStatus.READY) {
                    try { publish(repository.managedMods()) }
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
                } else mutableUi.update { it.copy(notice = Notice.ERROR, errorCode = code, progress = null) }
            } finally {
                mutableUi.update { it.copy(busy = false, progress = null) }
                if (syncPending) { syncPending = false; sync() }
            }
        }
    }
    private suspend fun collect(events: Flow<EngineEvent>) {
        events.collect { event -> if (event is EngineEvent.Update) mutableUi.update { it.copy(progress = event.progress) } }
    }
    private suspend fun publish(states: List<ManagedMod>) {
        withContext(Dispatchers.IO) {
            val edit = prefs.edit().putStringSet("knownRemote", states.map { it.id }.toSet())
            states.forEach { edit.putBoolean("active:${it.id}", it.active).putString("folder:${it.id}", it.folder)
                .putBoolean("shaMismatch:${it.id}", it.shaMismatch) }
            check(edit.commit())
        }
        val byId = states.associateBy { it.id }
        mutableUi.update { current -> current.copy(mods = current.mods.map { mod ->
            byId[mod.id]?.let { mod.copy(active = it.active, archived = true, folder = it.folder, issue = it.issue, shaMismatch = it.shaMismatch,
                warningHidden = prefs.getBoolean("hideWarning:${mod.id}", false) || mod.id in sessionIgnored) }
                ?: mod.copy(active = false, archived = false, folder = "", issue = null, shaMismatch = false)
        }) }
    }
    private fun legacy(id: String): List<String> = JSONArray(prefs.getString("mod:$id", "[]")).let { a ->
        (0 until a.length()).map(a::getString)
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
        var states = repository.managedMods() // İlk bağlantıda Bundles/mods oluşturulur.
        val remoteIds = states.map { it.id }.toSet()
        val known = prefs.getStringSet("knownRemote", emptySet()).orEmpty().toSet()
        val local = withContext(Dispatchers.IO) { library.load() }
        val localIds = local.map { it.id }.toMutableSet()
        var stored = false
        local.forEach { mod ->
            if (mod.id !in remoteIds) {
                if (mod.id in known) {
                    withContext(Dispatchers.IO) { library.delete(mod.id) }
                    localIds.remove(mod.id)
                } else {
                    collect(repository.storeMod(mod.archive, mod.id, legacy(mod.id)))
                    stored = true
                }
            }
        }
        // Rehash remote files only when the archive set actually changed.
        if (stored) states = repository.managedMods()
        states.filter { it.id !in localIds }.forEach { repository.downloadMod(it.id, library.archiveLocation(it.id)) }
        val reloaded = withContext(Dispatchers.IO) { library.load() }
        mutableUi.update { it.copy(mods = reloaded) }
        publish(states)
        mutableUi.update { it.copy(notice = Notice.READY) }
    }
    fun sync() {
        if (shizuku.status.value == ShizukuStatus.READY) runOperation { synchronize() }
    }
    fun ignoreWarning(id: String, showAgain: Boolean) {
        prefs.edit().putBoolean("hideWarning:$id", !showAgain).apply()
        sessionIgnored.add(id)
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
        publish(repository.managedMods())
        mutableUi.update { it.copy(notice = if (recover) Notice.UPDATED else Notice.DELETED) }
    }
    fun refreshIntegrity() {
        sessionIgnored.clear()
        mutableUi.update { it.copy(mods = it.mods.map { mod -> mod.copy(warningHidden = prefs.getBoolean("hideWarning:${mod.id}", false)) }) }
        if (shizuku.status.value == ShizukuStatus.READY) runOperation { publish(repository.managedMods()) }
    }
    fun importZip(uri: Uri) = runOperation {
        val mod = withContext(Dispatchers.IO) {
            library.import(uri) { bytes -> mutableUi.update { it.copy(progress = Progress("import", bytes, 0)) } }
        }
        mutableUi.update { it.copy(mods = listOf(mod) + it.mods) }
        if (shizuku.status.value == ShizukuStatus.READY) {
            synchronize(); mutableUi.update { it.copy(notice = Notice.IMPORTED) }
        } else mutableUi.update { it.copy(notice = Notice.QUEUED) }
    }
    fun setActive(id: String, active: Boolean) = runOperation(id) {
        collect(repository.setActive(id, active))
        publish(repository.managedMods())
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
        if (shizuku.status.value == ShizukuStatus.READY) publish(repository.managedMods())
    }
    override fun onCleared() { shizuku.close() }
}
