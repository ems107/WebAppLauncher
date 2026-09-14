package es.edgarms.weblauncher.model

import java.net.URI
import java.net.URISyntaxException

object PageUrls {
    /** True for an absolute http(s) URL with a host, which is all a page URL has to be. */
    fun isValid(url: String): Boolean {
        val uri = try {
            URI(url.trim())
        } catch (_: URISyntaxException) {
            return false
        }
        return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrEmpty()
    }
}
