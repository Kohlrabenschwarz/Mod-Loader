package dev.modloader.domain

/** Only stable numeric tags are accepted; comparison is numeric, never lexical. */
data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    override fun toString() = "$major.$minor.$patch"
    companion object {
        fun parse(value: String): ReleaseVersion? {
            if (!value.matches(Regex("v?(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})"))) return null
            val parts = value.removePrefix("v").split('.').map(String::toInt)
            return ReleaseVersion(parts[0], parts[1], parts[2])
        }
    }
}

object ReleaseSource {
    const val REPOSITORY = "Kohlrabenschwarz/Mod-Loader"
    const val API = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    const val PAGE = "https://github.com/$REPOSITORY/releases/latest"
    fun assetName(version: ReleaseVersion) = "Mod-Loader-v$version-release.apk"
    fun download(version: ReleaseVersion) = "https://github.com/$REPOSITORY/releases/download/v$version/${assetName(version)}"
}
