package dev.modloader.bridge

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import dev.modloader.engine.IFileEngine
import dev.modloader.engine.PrivilegedFileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

enum class ShizukuStatus(val label: String) {
    OFFLINE("Bağlı değil"), PERMISSION_REQUIRED("İzin bekleniyor"), DENIED("İzin reddedildi"),
    CONNECTING("Bağlanıyor"), READY("Bağlı"), UNSUPPORTED("Desteklenmiyor"), ERROR("Bağlanılamadı")
}

class ShizukuManager(context: Context) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val mutableStatus = MutableStateFlow(ShizukuStatus.OFFLINE)
    val status = mutableStatus.asStateFlow()
    private val mutableAttempt = MutableStateFlow(0)
    val attempt = mutableAttempt.asStateFlow()
    @Volatile private var engine: IFileEngine? = null
    private var closed = false
    private var active = false
    private var binding = false
    private var permissionRequested = false
    private var remoteBinder: IBinder? = null
    private val args = Shizuku.UserServiceArgs(ComponentName(context, PrivilegedFileService::class.java))
        .tag("modloader-v1-u${android.os.Process.myUid() / 100000}")
        .version(14).processNameSuffix("modengine").daemon(false).debuggable(false)
    private val timeout = Runnable { failAttempt() }
    private val retry = Runnable { beginAttempt() }
    private val death = IBinder.DeathRecipient { main.post { disconnected() } }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed || !active || !binding) return
            try {
                binder.linkToDeath(death, 0)
                remoteBinder = binder; engine = IFileEngine.Stub.asInterface(binder)
                main.removeCallbacks(timeout); main.removeCallbacks(retry)
                active = false; mutableStatus.value = ShizukuStatus.READY
            } catch (_: Exception) { failAttempt() }
        }
        override fun onServiceDisconnected(name: ComponentName) { disconnected() }
    }
    private val received = Shizuku.OnBinderReceivedListener { if (active) connectAttempt() }
    private val dead = Shizuku.OnBinderDeadListener { disconnected() }
    private val permission = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST_CODE && active) {
            if (result == PackageManager.PERMISSION_GRANTED) connectAttempt() else failAttempt()
        }
    }
    init {
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        Shizuku.addBinderReceivedListenerSticky(received)
        beginAttempt()
    }
    private fun beginAttempt() {
        if (closed || active || mutableStatus.value == ShizukuStatus.READY || mutableAttempt.value >= 3) return
        mutableAttempt.value += 1; active = true
        mutableStatus.value = ShizukuStatus.CONNECTING
        // Binder henüz gelmediyse de bu denemenin süresini bekle.
        main.postDelayed(timeout, 15_000)
        connectAttempt()
    }
    private fun connectAttempt() {
        if (closed || !active || binding) return
        try {
            if (!Shizuku.pingBinder()) return
            if (Shizuku.getVersion() < 13 || Shizuku.getUid() != 2000 || android.os.Process.myUid() / 100000 != 0) {
                failAttempt(); return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                mutableStatus.value = ShizukuStatus.PERMISSION_REQUIRED
                if (!permissionRequested && !Shizuku.shouldShowRequestPermissionRationale()) {
                    permissionRequested = true
                    main.removeCallbacks(timeout); main.postDelayed(timeout, 30_000)
                    Shizuku.requestPermission(REQUEST_CODE)
                }
                return
            }
            mutableStatus.value = ShizukuStatus.CONNECTING
            binding = true
            Shizuku.bindUserService(args, connection)
        } catch (_: Exception) { failAttempt() }
    }
    private fun failAttempt() {
        if (closed || !active) return
        active = false; detach()
        if (mutableAttempt.value >= 3) mutableStatus.value = ShizukuStatus.ERROR
        else { mutableStatus.value = ShizukuStatus.CONNECTING; main.postDelayed(retry, 1_000) }
    }
    private fun disconnected() {
        if (closed) return
        if (mutableStatus.value == ShizukuStatus.READY) {
            mutableStatus.value = ShizukuStatus.OFFLINE; detach()
            mutableAttempt.value = 0; beginAttempt() // Yeni bağlantı kaybı için yeni, sınırlı tur.
        } else if (active) failAttempt()
    }
    fun requireEngine(): IFileEngine {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
            throw dev.modloader.domain.EngineFailure(1, "SHIZUKU_UNAVAILABLE")
        return engine?.takeIf { it.asBinder().isBinderAlive }
            ?: throw dev.modloader.domain.EngineFailure(1, "SERVICE_UNAVAILABLE")
    }
    private fun detach() {
        main.removeCallbacks(timeout)
        remoteBinder?.let { runCatching { it.unlinkToDeath(death, 0) } }
        remoteBinder = null; engine = null
        val wasBinding = binding; binding = false
        if (wasBinding) runCatching { Shizuku.unbindUserService(args, connection, false) }
    }
    override fun close() {
        closed = true; active = false; main.removeCallbacks(retry)
        Shizuku.removeBinderReceivedListener(received)
        Shizuku.removeBinderDeadListener(dead)
        Shizuku.removeRequestPermissionResultListener(permission)
        detach()
    }
    companion object { private const val REQUEST_CODE = 1701 }
}
