package dev.modloader.domain

import kotlinx.coroutines.flow.Flow
import java.text.Normalizer

object GameTarget {
    const val PACKAGE_NAME = "com.nekki.shadowfightarena"
    const val RELATIVE_RESOURCES = "files/gamedata/Resources/Bundles"
    const val RESOURCES_PATH = "/storage/emulated/0/Android/data/$PACKAGE_NAME/$RELATIVE_RESOURCES/"
}

data class ModFile(val path: String, val size: Long, val sha256: String)
data class IntegrityChange(val path: String, val expected: String?, val actual: String?)
data class ManagedMod(val id: String, val folder: String, val active: Boolean, val issue: String? = null,
    val shaMismatch: Boolean = false, val changes: List<IntegrityChange> = emptyList(),
    val backupAt: Long = 0, val requiredBytes: Long = 0, val availableBytes: Long = 0,
    val archiveSha256: String? = null)
data class ModConflict(val name: String, val files: List<String>)
class EngineFailure(val code: Int, message: String, val conflicts: List<ModConflict> = emptyList()) : Exception(message)
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
    fun updateMod(localZip: java.io.File, id: String, expectedArchiveHash: String, manifest: String): Flow<EngineEvent>
    fun overwriteMod(localZip: java.io.File, id: String, expectedArchiveHash: String, incomingHash: String,
        developerMode: Boolean, publicationBaseUrl: String): Flow<EngineEvent>
    suspend fun writeDeveloperFiles(id: String, publicationBaseUrl: String)
    fun setActive(id: String, active: Boolean): Flow<EngineEvent>
    fun warningAction(id: String, recover: Boolean): Flow<EngineEvent>
    suspend fun deleteStoredMod(id: String)
    suspend fun downloadMod(id: String, destination: java.io.File)
}

object Limits {
    const val ZIP_BYTES = 1024L * 1024 * 1024
    const val FILE_BYTES = 512L * 1024 * 1024
    const val ICON_BYTES = 4 * 1024 * 1024
    const val TOTAL_BYTES = 2048L * 1024 * 1024
    const val ENTRIES = 200 // Önizleme Binder cevabı da sınırlıdır.
    const val RESERVE_BYTES = 32L * 1024 * 1024
}

/** Hide only the observed mismatch, never a later change to the same mod. */
object IntegrityWarningPolicy {
    fun fingerprint(changes: List<IntegrityChange>): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(changes.sortedBy { it.path }.joinToString("\n") {
            "${it.path}\u0000${it.expected ?: "missing"}\u0000${it.actual ?: "missing"}"
        }.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** Extensionless game files are accepted only when they identify as a UnityFS AssetBundle. */
object UnityBundlePolicy {
    const val HEADER = "UnityFS"
    const val HEADER_SIZE = 7

    fun requireHeader(prefix: ByteArray) {
        if (prefix.size != HEADER_SIZE || !prefix.contentEquals(HEADER.toByteArray(Charsets.US_ASCII))) {
            throw EngineFailure(13, "INVALID_UNITYFS_HEADER")
        }
    }
}

object PathPolicy {
    fun packageName(value: String): String {
        require(value == GameTarget.PACKAGE_NAME) { "Yalnızca Shadow Fight Arena desteklenir" }
        require(value.length <= 180 && value.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+"))) { "Geçersiz paket adı" }
        return value
    }

    fun relative(raw: String, pkg: String): String {
        packageName(pkg)
        require(raw.length in 1..240 && raw == Normalizer.normalize(raw, Normalizer.Form.NFKC)) { "Geçersiz ZIP yolu" }
        require(raw.none { it.code < 32 || it.code == 127 || it == '\\' || it == ':' || Character.getType(it) == Character.FORMAT.toInt() }) {
            "Geçersiz ZIP yolu"
        }
        require(raw.none { it in setOf('\u2044', '\u2215', '\u29F5', '\uFF0F', '\uFF3C') }) { "Belirsiz yol ayırıcı reddedildi" }
        require(!raw.startsWith('/')) { "Mutlak yol reddedildi" }
        val parts = raw.removeSuffix("/").split('/')
        require(parts.none { it.isEmpty() || it == "." || it == ".." }) { "Yol geçişi reddedildi" }
        require(parts.all { it.toByteArray(Charsets.UTF_8).size <= 255 }) { "ZIP yol bileşeni çok uzun" }
        val normalized = when {
            raw.startsWith("Android/data/$pkg/") -> raw.removePrefix("Android/data/$pkg/")
            raw.startsWith("$pkg/") -> raw.removePrefix("$pkg/")
            raw.startsWith("payload/") -> GameTarget.RELATIVE_RESOURCES + "/" + raw.removePrefix("payload/")
            raw.startsWith("Resources/Bundles/") -> GameTarget.RELATIVE_RESOURCES + "/" + raw.removePrefix("Resources/Bundles/")
            raw.startsWith("Bundles/") -> GameTarget.RELATIVE_RESOURCES + "/" + raw.removePrefix("Bundles/")
            else -> raw
        }.removeSuffix("/")
        require(normalized == GameTarget.RELATIVE_RESOURCES || normalized.startsWith(GameTarget.RELATIVE_RESOURCES + "/")) { "Yalnızca gamedata/Resources/Bundles/ ağacı desteklenir" }
        val targetParts = normalized.removePrefix(GameTarget.RELATIVE_RESOURCES).trimStart('/').split('/').filter(String::isNotEmpty)
        require(targetParts.none { it.startsWith('.') }) { "Gizli veya ayrılmış hedef adı reddedildi" }
        require(targetParts.firstOrNull()?.lowercase(java.util.Locale.ROOT) != "mods") {
            "mods/ uygulamanın arşiv ve yedek alanıdır; mod payload hedefi olamaz"
        }
        return normalized
    }
}

/** ZIP üreticisinin kontrol ettiği metinlerin arayüz yönünü veya satır düzenini taklit etmesini engeller. */
object UntrustedTextPolicy {
    fun display(raw: String, max: Int, multiline: Boolean = false): String {
        require(raw == Normalizer.normalize(raw, Normalizer.Form.NFKC)) { "Metin Unicode NFKC biçiminde olmalı" }
        val value = raw.trim()
        require(value.isNotBlank() && value.length <= max) { "Geçersiz metin uzunluğu" }
        require(value.none { character ->
            Character.getType(character) == Character.FORMAT.toInt() ||
                (Character.isISOControl(character) && !(multiline && character == '\n'))
        }) { "Kontrol veya yönlendirme karakteri reddedildi" }
        if (!multiline) require('\n' !in value && '\r' !in value) { "Tek satırlı metin gerekli" }
        if (multiline) require(value.count { it == '\n' } <= 20) { "Çok fazla metin satırı" }
        return value
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
