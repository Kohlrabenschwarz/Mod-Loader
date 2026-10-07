package dev.modloader.engine

import dev.modloader.domain.EngineFailure
import dev.modloader.domain.ModFolderPolicy
import dev.modloader.domain.ModUpdatePolicy
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Directory exchange journal. Game files have already been restored before READY is written. */
internal object ModArchiveUpdate {
    fun recover(home: File, validate: (File, String, String, String) -> Unit,
        checkpoint: (String) -> Unit = {}) {
        home.list().orEmpty().filter { it.startsWith(".update-") }.forEach { name ->
            val id = name.removePrefix(".update-")
            require(UUID.fromString(id).toString() == id)
            val operation = SafeFs.checked(home, name)
            val journal = SafeFs.checked(operation, "journal.json")
            if (!journal.exists()) {
                // No directory exchange was authorized. An interrupted restore is recovered by its own journal.
                if (SafeFs.checked(operation, "old").exists()) throw EngineFailure(6, "UPDATE_JOURNAL_MISSING")
                SafeFs.removeTree(home, name)
                return@forEach
            }
            try {
                val json = JSONObject(SafeFs.readText(journal, 4096))
                require(ModUpdateJson.integer(json, "schema") == 1L && json.getString("id") == id)
                val folder = ModFolderPolicy.validate(json.getString("folder"))
                val oldHash = ModUpdatePolicy.sha256(json.getString("oldHash"))
                val newHash = ModUpdatePolicy.sha256(json.getString("newHash"))
                require(oldHash != newHash)
                val current = SafeFs.checked(home, folder)
                val old = SafeFs.checked(operation, "old")
                val incoming = SafeFs.checked(operation, "new")
                if (!old.exists() && incoming.exists()) {
                    validate(current, folder, id, oldHash)
                    validate(incoming, folder, id, newHash)
                    SafeFs.rename(current, old)
                    checkpoint("UPDATE_OLD_MOVED")
                }
                if (!current.exists()) {
                    validate(old, folder, id, oldHash)
                    validate(incoming, folder, id, newHash)
                    SafeFs.rename(incoming, current)
                    checkpoint("UPDATE_PUBLISHED")
                }
                validate(current, folder, id, newHash)
                check(!incoming.exists()) { "Ambiguous update state" }
                val previous = SafeFs.checked(current, "previous")
                if (old.exists()) {
                    validate(old, folder, id, oldHash)
                    check(!previous.exists()) { "Ambiguous previous version" }
                    // Retain exactly one prior archive and its restored backups, rather than a growing chain.
                    SafeFs.removeTree(old, "previous")
                    SafeFs.rename(old, previous)
                    checkpoint("UPDATE_PREVIOUS_SAVED")
                } else validate(previous, folder, id, oldHash)
                SafeFs.removeTree(home, name)
            } catch (e: Exception) {
                throw EngineFailure(6, "RECOVERY_REQUIRED: mod update; ${e.message}")
            }
        }
    }
}
