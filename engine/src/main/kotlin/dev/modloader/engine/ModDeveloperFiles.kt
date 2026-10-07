package dev.modloader.engine

import dev.modloader.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Sidecars stay outside the ZIP: adding a manifest must never change the hash it describes. */
internal object ModDeveloperFiles {
    fun write(directory: File, id: String, archive: File, metadata: ModMetadata, files: List<ModFile>,
        hash: String, publicationBaseUrl: String) {
        val base = DeveloperPublicationPolicy.baseUrl(publicationBaseUrl)
        val modId = metadata.modId ?: "dev.${id.replace("-", "")}"
        val publicationDirectory = if (base.isEmpty()) "" else "$base$modId/"
        val manifestUrl = if (base.isEmpty()) metadata.update?.manifestUrl.orEmpty() else publicationDirectory + "latest.json"
        val code = metadata.versionCode ?: 1L
        val release = ModUpdateRelease(modId, metadata.version, code,
            DeveloperPublicationPolicy.archiveUrl(publicationDirectory, archive.name), hash, archive.length(), "")
        val missing = mutableListOf<String>()
        if (metadata.modId == null) missing.add("info.json: modId")
        if (metadata.versionCode == null) missing.add("info.json: versionCode")
        if (base.isEmpty()) missing.add("publicationBaseUrl")
        if (metadata.update?.manifestUrl != manifestUrl || metadata.update.modId != modId || manifestUrl.isEmpty())
            missing.add("update.json")
        val dev = SafeFs.checked(directory, ".dev").also(SafeFs::mkdir)
        SafeFs.writeAtomic(SafeFs.checked(dev, "latest.json"), ModUpdateJson.encode(release))
        SafeFs.writeAtomic(SafeFs.checked(dev, "zip-size.txt"), "${archive.length()}\n")
        SafeFs.writeAtomic(SafeFs.checked(dev, "zip-sha256.txt"), "$hash\n")
        SafeFs.writeAtomic(SafeFs.checked(dev, "checksums.json"), JSONObject().apply {
            put("schemaVersion", 1); put("algorithm", "SHA-256"); put("archiveName", archive.name)
            put("zipSize", archive.length()); put("zipSha256", hash)
            put("payloadSize", files.sumOf { it.size }); put("readyToPublish", missing.isEmpty())
            put("missingFields", JSONArray(missing)); put("manifestUrl", manifestUrl)
            put("publicationDirectory", publicationDirectory)
            put("files", JSONArray().apply { files.forEach { file -> put(JSONObject().apply {
                put("path", file.path); put("size", file.size); put("sha256", file.sha256)
            }) } })
        }.toString(2))
        SafeFs.writeAtomic(SafeFs.checked(dev, "info-update.json"), JSONObject().apply {
            put("schemaVersion", 1); put("modId", modId); put("versionCode", code); put("version", metadata.version)
        }.toString(2))
        SafeFs.writeAtomic(SafeFs.checked(dev, "update.json"), JSONObject().apply {
            put("schemaVersion", 1); put("modId", modId); put("manifestUrl", manifestUrl)
        }.toString(2))
        SafeFs.writeAtomic(SafeFs.checked(dev, "README.txt"), """
            Mod Loader Dev Mode
            Archive: ${archive.name}
            ZIP download: ${release.zipUrl}
            Manifest: $manifestUrl
            Ready to publish: ${missing.isEmpty()}
            Missing or mismatched fields: ${missing.joinToString(", ").ifEmpty { "none" }}

            Publication directory: $publicationDirectory
            Upload the archive next to this .dev directory, using its exact filename, then upload .dev/latest.json to this publication directory.
            Each modId has its own publication subdirectory so different mods cannot replace each other's latest.json.
            Do not upload info-update.json as the full info.json. Merge its fields into the existing ZIP info.json and put update.json at the ZIP root if needed.
            After changing any ZIP contents, import it again and approve overwrite. Use the newly generated archive hash and latest.json together.
            Increase info.json versionCode for a new release. All releases must keep the same modId and manifestUrl.
            These sidecars are not part of the ZIP and are never applied to the game. Turning Dev Mode off preserves existing sidecars.
        """.trimIndent() + "\n")
    }
}
