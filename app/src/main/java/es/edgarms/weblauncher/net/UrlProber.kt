package es.edgarms.weblauncher.net

sealed interface ProbeResult {
    /** The server answered. Any status counts: a 401 proves it is there as well as a 200. */
    data class Alive(val code: Int) : ProbeResult

    data class Failed(val kind: FailureKind, val detail: String? = null) : ProbeResult
}

enum class FailureKind { TIMEOUT, REFUSED, UNREACHABLE, UNKNOWN_HOST, OTHER }

/** Asks whether a server answers at a URL. Never throws, except to propagate cancellation. */
fun interface UrlProber {
    suspend fun probe(url: String): ProbeResult
}
