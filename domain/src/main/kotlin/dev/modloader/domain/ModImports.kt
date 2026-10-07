package dev.modloader.domain

import java.net.URI

object ModImportPolicy {
    fun collides(name: String, modId: String?, otherName: String, otherModId: String?): Boolean =
        (modId != null && modId == otherModId) ||
            name.trim().equals(otherName.trim(), ignoreCase = true)
}

object DeveloperPublicationPolicy {
    /** Empty means a draft. A configured address always names a directory, never a signed download URL. */
    fun baseUrl(value: String): String {
        if (value.isBlank()) return ""
        val uri = ModUpdatePolicy.httpsUrl(value.trim())
        require(uri.rawQuery == null) { "Publication directory cannot contain a query" }
        return uri.toASCIIString().trimEnd('/') + "/"
    }
    fun archiveUrl(base: String, archiveName: String): String {
        val directory = baseUrl(base)
        if (directory.isEmpty()) return ""
        require('/' !in archiveName && '\\' !in archiveName)
        return URI(directory).resolve(URI(null, null, archiveName, null)).toASCIIString()
            .also(ModUpdatePolicy::httpsUrl)
    }
}
