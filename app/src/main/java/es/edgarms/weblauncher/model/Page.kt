package es.edgarms.weblauncher.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** One web app the launcher can open. */
@Serializable
data class Page(
    /** Stable for the life of the page: shortcuts and the remembered winner refer to it. */
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** In order of preference; all of them are raced when the remembered one fails. */
    val urls: List<String>,
    /** A file inside the app's storage, or null for a generated tile. */
    val iconPath: String? = null,
)
