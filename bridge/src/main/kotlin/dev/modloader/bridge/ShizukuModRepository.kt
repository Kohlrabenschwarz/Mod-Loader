package dev.modloader.bridge

import android.os.ParcelFileDescriptor
import dev.modloader.domain.*
import dev.modloader.engine.IProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

class ShizukuModRepository(private val manager: ShizukuManager) : ModRepository {
    override suspend fun stopGame() { withContext(Dispatchers.IO) { unwrap(manager.requireEngine().stopGame()) } }
    override suspend fun managedMods(): List<ManagedMod> = withContext(Dispatchers.IO) {
        val array = JSONArray(unwrap(manager.requireEngine().managedMods()))
        (0 until array.length()).map { i -> array.getJSONObject(i).let {
            ManagedMod(it.getString("id"), it.getString("folder"), it.getBoolean("active"),
                if (it.isNull("issue")) null else it.getString("issue"), it.optBoolean("shaMismatch", false))
        } }
    }
    override fun storeMod(localZip: File, id: String, legacyTransactions: List<String>): Flow<EngineEvent> = channelFlow {
        val progress = callback()
        withContext(Dispatchers.IO) {
            ParcelFileDescriptor.open(localZip, ParcelFileDescriptor.MODE_READ_ONLY).use {
                unwrap(manager.requireEngine().storeMod(it, id, JSONArray(legacyTransactions).toString(), progress))
            }
        }
        send(EngineEvent.Finished("stored"))
    }
    override fun setActive(id: String, active: Boolean): Flow<EngineEvent> = channelFlow {
        val progress = callback()
        withContext(Dispatchers.IO) { unwrap(manager.requireEngine().setModActive(id, active, progress)) }
        send(EngineEvent.Finished("updated"))
    }
    override fun warningAction(id: String, recover: Boolean): Flow<EngineEvent> = channelFlow {
        val progress = callback()
        withContext(Dispatchers.IO) { unwrap(manager.requireEngine().warningAction(id, recover, progress)) }
        send(EngineEvent.Finished("updated"))
    }
    override suspend fun deleteStoredMod(id: String) {
        withContext(Dispatchers.IO) { unwrap(manager.requireEngine().deleteStoredMod(id, null)) }
    }
    override suspend fun downloadMod(id: String, destination: File) = withContext(Dispatchers.IO) {
        val temp = File(destination.parentFile, "${destination.name}.download")
        try {
            val fd = requireNotNull(manager.requireEngine().openStoredMod(id))
            ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                java.io.FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024); var total = 0L
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        total += n; require(total <= Limits.ZIP_BYTES)
                        output.write(buffer, 0, n)
                    }
                    output.fd.sync()
                }
            }
            check(temp.renameTo(destination)) { "Mod önbelleği kaydedilemedi" }
            Unit
        } finally { temp.delete() }
    }
    private fun unwrap(response: String): String {
        val envelope = JSONObject(response)
        if (!envelope.getBoolean("ok")) throw EngineFailure(envelope.getInt("code"), envelope.getString("message"))
        return envelope.getString("value")
    }
    private fun ProducerScope<EngineEvent>.callback() = object : IProgress.Stub() {
        override fun update(phase: String, done: Long, total: Long) {
            trySend(EngineEvent.Update(Progress(phase, done, total)))
        }
    }

    override fun prepare(localZip: File, packageName: String): Flow<EngineEvent> = channelFlow {
        val progress = callback()
        val json = withContext(Dispatchers.IO) {
            ParcelFileDescriptor.open(localZip, ParcelFileDescriptor.MODE_READ_ONLY).use {
                unwrap(manager.requireEngine().prepare(it, packageName, progress))
            }
        }
        val obj = JSONObject(json)
        val entries = obj.getJSONArray("entries")
        send(EngineEvent.Preview(Plan(obj.getString("id"), obj.getString("package"),
            (0 until entries.length()).map { i ->
                val e = entries.getJSONObject(i)
                PreviewEntry(ModFile(e.getString("path"), e.getLong("size"), e.getString("sha256")),
                    if (e.isNull("old")) null else e.getString("old"))
            }
        )))
    }

    override fun apply(plan: Plan, overwriteApproved: Boolean): Flow<EngineEvent> = channelFlow {
        val progress = callback()
        val message = withContext(Dispatchers.IO) {
            unwrap(manager.requireEngine().apply(plan.packageName, plan.id, overwriteApproved, progress))
        }
        send(EngineEvent.Finished(message))
    }

    override fun restore(packageName: String, transactionId: String): Flow<EngineEvent> = channelFlow {
        val progress = callback()
        val message = withContext(Dispatchers.IO) { unwrap(manager.requireEngine().restore(packageName, transactionId, progress)) }
        send(EngineEvent.Finished(message))
    }

    override fun recover(packageName: String): Flow<EngineEvent> = channelFlow {
        val progress = callback()
        val message = withContext(Dispatchers.IO) { unwrap(manager.requireEngine().recover(packageName, progress)) }
        send(EngineEvent.Finished(message))
    }
}
