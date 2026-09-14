package es.edgarms.weblauncher.web

import java.net.URI
import java.net.URISyntaxException

object Origins {
    /** Whether two URLs share scheme, host and port: what the web calls the same origin. */
    fun same(a: String, b: String): Boolean {
        val origin = origin(a) ?: return false
        return origin == origin(b)
    }

    private fun origin(url: String): Triple<String, String, Int>? {
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val port = when {
            uri.port != -1 -> uri.port
            scheme == "http" -> 80
            scheme == "https" -> 443
            else -> -1
        }
        return Triple(scheme, host, port)
    }
}
