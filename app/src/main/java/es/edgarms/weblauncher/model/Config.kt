package es.edgarms.weblauncher.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything the user configures. This is also the export format. */
@Serializable
data class Config(
    val version: Int = CURRENT_VERSION,
    val pages: List<Page> = emptyList(),
) {
    fun page(id: String): Page? = pages.firstOrNull { it.id == id }

    companion object {
        const val CURRENT_VERSION = 1
    }
}

object ConfigJson {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(config: Config): String = json.encodeToString(Config.serializer(), config)

    /** @throws IllegalArgumentException if the text is not a valid configuration. */
    fun decode(text: String): Config = json.decodeFromString(Config.serializer(), text)
}
