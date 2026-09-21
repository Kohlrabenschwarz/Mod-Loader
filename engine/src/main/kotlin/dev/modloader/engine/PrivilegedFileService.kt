package dev.modloader.engine

import android.content.Context
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import android.system.ErrnoException
import android.system.OsConstants
import androidx.annotation.Keep
import java.io.File
import java.util.zip.ZipException
import org.json.JSONObject
import kotlin.system.exitProcess

/** Android Service değildir; Shizuku bu Binder sınıfını ayrı app_process içinde oluşturur. */
@Keep
class PrivilegedFileService @Keep constructor(context: Context) : IFileEngine.Stub() {
    // createPackageContextAsUser ile gelen loader paketinin UID'si, shell UID'si değil.
    private val ownerUid = context.applicationInfo.uid
    private val engine = TransactionEngine(File("/storage/emulated/0"), beforeMutation = GameProcess::stop)
    private val managed = ManagedModEngine(File("/storage/emulated/0"), beforeMutation = GameProcess::stop)

    private fun authorize() {
        check(Binder.getCallingUid() == ownerUid) { "Yetkisiz Binder çağrısı" }
        check(ownerUid / 100000 == 0) { "İş profili/çok kullanıcı bu sürümde desteklenmiyor" }
        check(Process.myUid() == 2000) { "Bu sürüm yalnızca ADB shell Shizuku backend destekler" }
    }

    private fun reporter(callback: IProgress?): (String, Long, Long) -> Unit {
        var last = 0L
        return { phase, done, total ->
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - last >= 100 || done == total) {
                last = now
                try { callback?.update(phase, done, total) } catch (_: RemoteException) {
                    // UI kapanması disk transaction'ını kesmez; non-daemon servis öldürülürse journal kurtarılır.
                }
            }
        }
    }

    private inline fun response(block: () -> String): String {
        authorize()
        try { return JSONObject().put("ok", true).put("value", block()).toString() }
        catch (e: Exception) {
            val code = when (e) {
                is dev.modloader.domain.EngineFailure -> e.code
                is ZipException -> 3
                is ErrnoException -> when (e.errno) {
                    OsConstants.EACCES, OsConstants.EPERM -> 1
                    OsConstants.ENOSPC -> 2
                    else -> 5
                }
                is IllegalArgumentException -> 3
                else -> if (e.message?.startsWith("RECOVERY_REQUIRED") == true) 6 else 4
            }
            // ServiceSpecificException public Android SDK parçası değildir; taşınabilir hata zarfı.
            return JSONObject().put("ok", false).put("code", code)
                .put("message", (e.message ?: e.javaClass.simpleName).take(600)).toString()
        }
        // OutOfMemoryError/VM ölümü yakalanıp sahte başarı üretilmez. Journal sonraki bağlantıda kullanılır.
    }

    @Synchronized override fun prepare(zip: ParcelFileDescriptor, packageName: String, progress: IProgress?): String =
        zip.use { response { engine.prepare(it, packageName, reporter(progress)) } }

    @Synchronized override fun apply(packageName: String, transactionId: String, overwriteApproved: Boolean, progress: IProgress?): String =
        response { engine.apply(packageName, transactionId, overwriteApproved, reporter(progress)) }

    @Synchronized override fun recover(packageName: String, progress: IProgress?): String =
        response { engine.recover(packageName, reporter(progress)) }

    @Synchronized override fun restore(packageName: String, transactionId: String, progress: IProgress?): String =
        response { engine.restore(packageName, transactionId, reporter(progress)) }

    @Synchronized override fun managedMods(): String = response { managed.list() }
    @Synchronized override fun stopGame(): String = response { GameProcess.stop(); "Stopped" }
    @Synchronized override fun storeMod(zip: ParcelFileDescriptor, id: String, legacyTransactions: String, progress: IProgress?): String =
        zip.use { response { managed.store(it, id, legacyTransactions, reporter(progress)) } }
    @Synchronized override fun setModActive(id: String, active: Boolean, progress: IProgress?): String =
        response { managed.setActive(id, active, reporter(progress)) }
    @Synchronized override fun deleteStoredMod(id: String, progress: IProgress?): String =
        response { managed.delete(id, reporter(progress)) }
    @Synchronized override fun warningAction(id: String, recover: Boolean, progress: IProgress?): String =
        response { managed.warningAction(id, recover, reporter(progress)) }
    @Synchronized override fun openStoredMod(id: String): ParcelFileDescriptor {
        authorize(); return managed.openArchive(id)
    }

    override fun destroy() {
        // Shizuku reserved lifecycle çağrısı shell'den gelebilir.
        val uid = Binder.getCallingUid()
        if (uid == ownerUid || uid == 2000 || uid == 0) exitProcess(0)
        throw SecurityException("Yetkisiz destroy")
    }
}
