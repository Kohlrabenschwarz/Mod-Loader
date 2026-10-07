package dev.modloader.engine

import dev.modloader.domain.*
import org.json.JSONObject

/** Shared by app and service; JSON strings and floating point values are never coerced into integers. */
object ModUpdateJson {
    fun text(json: JSONObject, key: String): String = (json.get(key) as? String)
        ?: throw IllegalArgumentException("Invalid $key")
    fun integer(json: JSONObject, key: String): Long {
        val value = json.get(key)
        require(value is Int || value is Long) { "Invalid $key" }
        return (value as Number).toLong()
    }
    private fun document(text: String): JSONObject {
        require(text.toByteArray(Charsets.UTF_8).size in 1..ModUpdatePolicy.METADATA_BYTES)
        return JSONObject(text).also { require(integer(it, "schemaVersion") == 1L) }
    }
    fun source(text: String): ModUpdateSource = document(text).let {
        ModUpdateSource(ModUpdatePolicy.modId(text(it, "modId")),
            text(it, "manifestUrl").also(ModUpdatePolicy::httpsUrl))
    }
    fun release(text: String): ModUpdateRelease = document(text).let {
        val size = integer(it, "zipSize"); require(size in 1..Limits.ZIP_BYTES)
        ModUpdateRelease(ModUpdatePolicy.modId(text(it, "modId")),
            UntrustedTextPolicy.display(text(it, "version"), 40),
            ModUpdatePolicy.versionCode(integer(it, "versionCode")),
            text(it, "zipUrl").also(ModUpdatePolicy::httpsUrl),
            ModUpdatePolicy.sha256(text(it, "zipSha256")), size,
            if (it.has("changelog")) text(it, "changelog").let { notes ->
                if (notes.isBlank()) "" else UntrustedTextPolicy.display(notes, 2000, multiline = true)
            } else "")
    }
    fun encode(release: ModUpdateRelease): String = JSONObject().apply {
        put("schemaVersion", 1); put("modId", release.modId); put("version", release.version)
        put("versionCode", release.versionCode); put("zipUrl", release.zipUrl)
        put("zipSha256", release.zipSha256); put("zipSize", release.zipSize); put("changelog", release.changelog)
    }.toString()
}
