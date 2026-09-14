package es.edgarms.weblauncher.icons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import java.net.URI
import java.net.URISyntaxException

/** A place a page's icon might be downloaded from. */
data class IconRef(val url: String, val maskable: Boolean = false)

/**
 * Where to look for a site's icon, best first: the web app manifest, then
 * `apple-touch-icon`, then `<link rel="icon">`, then `/favicon.ico`. SVG is left
 * out everywhere, since Android cannot draw it without a library.
 *
 * Only decides where to look; downloading is [IconFetcher]'s job, so all of
 * this runs on the JVM with no network.
 */
object IconCandidates {
    /** A maskable icon smaller than this does not jump ahead of a big ordinary one. */
    private const val MIN_MASKABLE_SIDE = 96

    fun chain(pageUrl: String, html: String?, manifestIcons: List<IconRef>): List<IconRef> =
        (manifestIcons + html?.let { fromHtml(pageUrl, it) }.orEmpty() + listOfNotNull(favicon(pageUrl)))
            .distinctBy { it.url }

    fun manifestUrl(pageUrl: String, html: String): String? =
        Jsoup.parse(html, pageUrl).select("link[href]")
            .firstOrNull { "manifest" in rels(it.attr("rel")) }
            ?.absUrl("href")
            ?.ifEmpty { null }

    /** The manifest's icons, maskable ones first, then the biggest; relative to the manifest's own URL. */
    fun fromManifest(manifestUrl: String, json: String): List<IconRef> {
        val root = try {
            Json.parseToJsonElement(json) as? JsonObject
        } catch (_: IllegalArgumentException) {
            null
        }
        val icons = root?.get("icons") as? JsonArray ?: return emptyList()
        return icons
            .mapNotNull { element ->
                val icon = element as? JsonObject ?: return@mapNotNull null
                val src = icon.string("src") ?: return@mapNotNull null
                if (isSvg(src, icon.string("type"))) return@mapNotNull null
                val url = resolve(manifestUrl, src) ?: return@mapNotNull null
                val maskable = "maskable" in icon.string("purpose").orEmpty().lowercase().split(' ')
                Candidate(IconRef(url, maskable), largestSide(icon.string("sizes")))
            }
            .sortedWith(
                compareByDescending<Candidate> { it.ref.maskable && it.side >= MIN_MASKABLE_SIDE }
                    .thenByDescending { it.side },
            )
            .map { it.ref }
    }

    /** Icons the HTML declares: `apple-touch-icon` before `rel="icon"`, the biggest first within each. */
    fun fromHtml(pageUrl: String, html: String): List<IconRef> {
        val links = Jsoup.parse(html, pageUrl).select("link[href]")
        fun declared(vararg wanted: String) = links
            .filter { link -> rels(link.attr("rel")).any { it in wanted } }
            .filterNot { isSvg(it.attr("href"), it.attr("type")) }
            .sortedByDescending { largestSide(it.attr("sizes")) }
            .mapNotNull { link -> link.absUrl("href").ifEmpty { null }?.let { IconRef(it) } }
        return declared("apple-touch-icon", "apple-touch-icon-precomposed") + declared("icon")
    }

    fun favicon(pageUrl: String): IconRef? = resolve(pageUrl, "/favicon.ico")?.let { IconRef(it) }

    /** The biggest side in a `sizes` attribute such as "32x32 64x64"; 0 when there is none. */
    internal fun largestSide(sizes: String?): Int =
        sizes.orEmpty().lowercase().split(Regex("\\s+"))
            .mapNotNull { it.substringBefore('x').toIntOrNull() }
            .maxOrNull() ?: 0

    private fun rels(rel: String): Set<String> = rel.lowercase().split(Regex("\\s+")).toSet()

    private fun isSvg(src: String, type: String?): Boolean =
        type.orEmpty().contains("svg", ignoreCase = true) ||
            src.substringBefore('?').substringBefore('#').endsWith(".svg", ignoreCase = true)

    private fun resolve(base: String, src: String): String? = try {
        URI(base).resolve(src.trim()).toString()
    } catch (_: URISyntaxException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private data class Candidate(val ref: IconRef, val side: Int)

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
}
