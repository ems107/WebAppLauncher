package es.edgarms.weblauncher.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A published version newer than the one running, and what it takes to install it. */
@Serializable
data class AvailableUpdate(
    val version: String,
    /** The notes of every release between the running version and this one, newest first. */
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

/** Reads GitHub's list of releases and decides whether any of them is an update. */
object ReleaseFeed {
    @Serializable
    internal data class Release(
        @SerialName("tag_name") val tag: String,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val body: String? = null,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    internal data class Asset(
        val name: String,
        val size: Long,
        @SerialName("browser_download_url") val url: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The newest installable release above [current], or null if there is none.
     * Drafts, prereleases, tags that are not a version and releases without an
     * APK are not updates.
     *
     * @throws IllegalArgumentException if [body] is not a list of releases.
     */
    fun newest(body: String, current: Version): AvailableUpdate? {
        val newer = json.decodeFromString(ListSerializer(Release.serializer()), body)
            .asSequence()
            .filter { !it.draft && !it.prerelease }
            .mapNotNull { release -> Version.parse(release.tag)?.let { it to release } }
            .filter { (version, _) -> version > current }
            .sortedByDescending { (version, _) -> version }
            .toList()
        val (version, release) = newer.firstOrNull { (_, release) -> release.apk() != null } ?: return null
        val apk = release.apk()!!
        val covered = newer.filter { (other, _) -> other <= version }
        val notes = covered.joinToString("\n\n") { (other, release) ->
            val text = release.body.orEmpty().trim()
            if (covered.size == 1) text else "v$other\n$text".trim()
        }
        return AvailableUpdate(version.toString(), notes, apk.url, apk.size)
    }

    private fun Release.apk(): Asset? = assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}
