package es.edgarms.weblauncher.net

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class Attempt(val url: String, val result: ProbeResult)

sealed interface RaceResult {
    data class Won(val url: String) : RaceResult

    /** @property attempts what each URL said, in the page's order. */
    data class Lost(val attempts: List<Attempt>) : RaceResult
}

/**
 * Finds which of a page's URLs answers. The one that answered last time is
 * tried alone first; only if it fails do the others race, all at once, and the
 * first to answer wins. This is what makes a PC moving between cable and
 * Wi-Fi invisible.
 */
class UrlRace(private val prober: UrlProber) {

    suspend fun run(urls: List<String>, remembered: String? = null): RaceResult {
        require(urls.isNotEmpty()) { "A page has at least one URL" }
        val failures = mutableListOf<Attempt>()

        val favourite = remembered?.takeIf { it in urls }
        if (favourite != null) {
            val result = prober.probe(favourite)
            if (result is ProbeResult.Alive) return RaceResult.Won(favourite)
            failures += Attempt(favourite, result)
        }

        val winner = race(urls.filter { it != favourite }.distinct(), failures)
        return if (winner != null) {
            RaceResult.Won(winner)
        } else {
            RaceResult.Lost(failures.sortedBy { urls.indexOf(it.url) })
        }
    }

    /** Probes [urls] at once and returns the first that answers, cancelling the rest. */
    private suspend fun race(urls: List<String>, failures: MutableList<Attempt>): String? {
        if (urls.isEmpty()) return null
        return coroutineScope {
            val finished = Channel<Attempt>(Channel.UNLIMITED)
            val probes = urls.map { url -> launch { finished.send(Attempt(url, prober.probe(url))) } }
            repeat(urls.size) {
                val attempt = finished.receive()
                if (attempt.result is ProbeResult.Alive) {
                    probes.forEach { it.cancel() }
                    return@coroutineScope attempt.url
                }
                failures += attempt
            }
            null
        }
    }
}
