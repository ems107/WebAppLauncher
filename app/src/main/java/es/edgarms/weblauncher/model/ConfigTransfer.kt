package es.edgarms.weblauncher.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

sealed interface ImportResult {
    data class Valid(val config: Config) : ImportResult

    /** @property detail the name of the offending page, for [Reason.BAD_PAGE]. */
    data class Invalid(val reason: Reason, val detail: String? = null) : ImportResult

    enum class Reason { UNREADABLE, NOT_A_CONFIG, NEWER_VERSION, BAD_PAGE }
}

/** Exporting and importing the configuration: the file is the same JSON the app keeps. */
object ConfigTransfer {

    /**
     * The configuration as a file to keep. Icon paths stay behind: they name
     * files inside this install, and on another phone or after a reinstall each
     * page simply fetches its icon again.
     */
    fun export(config: Config): String =
        ConfigJson.encode(config.copy(pages = config.pages.map { it.copy(iconPath = null) }))

    /**
     * Checks a file before it is allowed to replace anything. Stricter than
     * loading the app's own file: `{}` would load as an empty configuration,
     * and importing it would silently delete every page.
     */
    fun read(text: String): ImportResult {
        val root = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (_: IllegalArgumentException) {
            null
        }
        if (root?.get("pages") !is JsonArray) return ImportResult.Invalid(ImportResult.Reason.NOT_A_CONFIG)

        val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: Config.CURRENT_VERSION
        if (version > Config.CURRENT_VERSION) return ImportResult.Invalid(ImportResult.Reason.NEWER_VERSION)

        val config = try {
            ConfigJson.decode(text)
        } catch (_: IllegalArgumentException) {
            return ImportResult.Invalid(ImportResult.Reason.NOT_A_CONFIG)
        }

        val ids = HashSet<String>()
        val bad = config.pages.firstOrNull { page ->
            page.id.isBlank() || !ids.add(page.id) || page.name.isBlank() ||
                page.urls.isEmpty() || !page.urls.all(PageUrls::isValid)
        }
        if (bad != null) return ImportResult.Invalid(ImportResult.Reason.BAD_PAGE, bad.name.ifBlank { bad.id })

        return ImportResult.Valid(
            Config(pages = config.pages.map { it.copy(iconPath = null) }),
        )
    }

    /** The imported pages replace the current ones; a page already here (same id) keeps its icon. */
    fun merge(current: Config, imported: Config): Config {
        val icons = current.pages.associate { it.id to it.iconPath }
        return imported.copy(pages = imported.pages.map { it.copy(iconPath = icons[it.id]) })
    }
}
