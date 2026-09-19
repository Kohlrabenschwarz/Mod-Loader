package dev.modloader.engine

import dev.modloader.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class Journal(
    val plan: Plan,
    var state: String = "PREPARED",
    var intentCount: Int = 0
) {
    fun json(): String = JSONObject().apply {
        put("schema", 1); put("id", plan.id); put("package", plan.packageName)
        put("state", state); put("intentCount", intentCount)
        put("entries", JSONArray().apply {
            plan.entries.forEach { entry -> put(JSONObject().apply {
                put("path", entry.file.path); put("size", entry.file.size); put("sha256", entry.file.sha256)
                put("old", entry.originalHash ?: JSONObject.NULL)
            }) }
        })
    }.toString()

    companion object {
        fun read(file: File, pkg: String, id: String): Journal {
            require(file.isFile && file.length() in 1..256L * 1024) { "Geçersiz işlem günlüğü" }
            val obj = JSONObject(file.readText())
            require(obj.getInt("schema") == 1 && obj.getString("id") == id && obj.getString("package") == pkg)
            val array = obj.getJSONArray("entries")
            require(array.length() in 1..Limits.ENTRIES)
            val entries = (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                val path = item.getString("path")
                require(PathPolicy.relative(path, pkg) == path && path != GameTarget.RELATIVE_RESOURCES)
                val sha = item.getString("sha256")
                val old = if (item.isNull("old")) null else item.getString("old")
                require(sha.matches(Regex("[0-9a-f]{64}")) && (old == null || old.matches(Regex("[0-9a-f]{64}"))))
                val size = item.getLong("size"); require(size in 0..Limits.FILE_BYTES)
                PreviewEntry(ModFile(path, size, sha), old)
            }
            require(entries.map { it.file.path.lowercase(java.util.Locale.ROOT) }.distinct().size == entries.size)
            require(entries.sumOf { it.file.size } <= Limits.TOTAL_BYTES)
            val state = obj.getString("state")
            require(state in setOf("PREPARED", "BACKING_UP", "APPLYING", "ROLLING_BACK", "COMMITTED", "ROLLED_BACK", "ABORTED", "FORCE_RESTORING"))
            val intent = obj.getInt("intentCount"); require(intent in 0..entries.size)
            return Journal(Plan(id, pkg, entries), state, intent)
        }
    }
}
