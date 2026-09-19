package dev.modloader.domain

import kotlinx.coroutines.flow.Flow

object GameTarget {
    const val PACKAGE_NAME = "com.nekki.shadowfightarena"
    const val RELATIVE_RESOURCES = "files/gamedata/Resources/Bundles"
    const val RESOURCES_PATH = "/storage/emulated/0/Android/data/$PACKAGE_NAME/$RELATIVE_RESOURCES/"
}

data class ModFile(val path: String, val size: Long, val sha256: String)
data class ManagedMod(val id: String, val folder: String, val active: Boolean, val issue: String? = null,
    val shaMismatch: Boolean = false)
class EngineFailure(val code: Int, message: String) : Exception(message)
data class PreviewEntry(val file: ModFile, val originalHash: String?)
data class Plan(val id: String, val packageName: String, val entries: List<PreviewEntry>)
data class Progress(val phase: String, val done: Long, val total: Long) {
    val fraction: Float? get() = if (total > 0) (done.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else null
}
sealed interface EngineEvent {
    data class Update(val progress: Progress) : EngineEvent
    data class Preview(val plan: Plan) : EngineEvent
    data class Finished(val message: String) : EngineEvent
}

// Android URI/FD veya Binder tipi domain katmanına sızmaz.
interface ModRepository {
    suspend fun stopGame()
    fun prepare(localZip: java.io.File, packageName: String): Flow<EngineEvent>
    fun apply(plan: Plan, overwriteApproved: Boolean): Flow<EngineEvent>
    fun recover(packageName: String): Flow<EngineEvent>
    fun restore(packageName: String, transactionId: String): Flow<EngineEvent>
    suspend fun managedMods(): List<ManagedMod>
    fun storeMod(localZip: java.io.File, id: String, legacyTransactions: List<String>): Flow<EngineEvent>
    fun setActive(id: String, active: Boolean): Flow<EngineEvent>
    fun warningAction(id: String, recover: Boolean): Flow<EngineEvent>
    suspend fun deleteStoredMod(id: String)
    suspend fun downloadMod(id: String, destination: java.io.File)
}

object Limits {
    const val ZIP_BYTES = 256L * 1024 * 1024
    const val FILE_BYTES = 256L * 1024 * 1024
    const val ICON_BYTES = 4 * 1024 * 1024
    const val TOTAL_BYTES = 512L * 1024 * 1024
    const val ENTRIES = 200 // Önizleme Binder cevabı da sınırlıdır.
    const val RESERVE_BYTES = 32L * 1024 * 1024
}

object PathPolicy {
    fun packageName(value: String): String {
        require(value == GameTarget.PACKAGE_NAME) { "Yalnızca Shadow Fight Arena desteklenir" }
        require(value.length <= 180 && value.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+"))) { "Geçersiz paket adı" }
        return value
    }

    fun relative(raw: String, pkg: String): String {
        packageName(pkg)
        require(raw.length in 1..240 && raw.none { it.code < 32 || it == '\\' || it == ':' }) { "Geçersiz ZIP yolu" }
        require(!raw.startsWith('/')) { "Mutlak yol reddedildi" }
        val parts = raw.removeSuffix("/").split('/')
        require(parts.none { it.isEmpty() || it == "." || it == ".." }) { "Yol geçişi reddedildi" }
        val normalized = when {
            raw.startsWith("Android/data/$pkg/") -> raw.removePrefix("Android/data/$pkg/")
            raw.startsWith("$pkg/") -> raw.removePrefix("$pkg/")
            raw.startsWith("payload/") -> GameTarget.RELATIVE_RESOURCES + "/" + raw.removePrefix("payload/")
            raw.startsWith("Resources/Bundles/") -> GameTarget.RELATIVE_RESOURCES + "/" + raw.removePrefix("Resources/Bundles/")
            raw.startsWith("Bundles/") -> GameTarget.RELATIVE_RESOURCES + "/" + raw.removePrefix("Bundles/")
            else -> raw
        }.removeSuffix("/")
        require(normalized == GameTarget.RELATIVE_RESOURCES || normalized.startsWith(GameTarget.RELATIVE_RESOURCES + "/")) { "Yalnızca gamedata/Resources/Bundles/ ağacı desteklenir" }
        require(normalized.removePrefix(GameTarget.RELATIVE_RESOURCES + "/").substringBefore('/').lowercase(java.util.Locale.ROOT) != "mods") {
            "mods/ uygulamanın arşiv ve yedek alanıdır; mod payload hedefi olamaz"
        }
        return normalized
    }
}

object ModFolderPolicy {
    fun slug(name: String): String = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC)
        .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
        .joinToString("").trim('_').take(40).ifEmpty { "mod" }
    fun validate(folder: String): String {
        require(folder.length in 1..64 && folder.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "Geçersiz mod klasörü" }
        return folder
    }
}
