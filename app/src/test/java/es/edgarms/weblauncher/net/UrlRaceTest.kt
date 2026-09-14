package es.edgarms.weblauncher.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UrlRaceTest {

    /** Answers each URL with a fixed result after a fixed (virtual) delay. */
    private class FakeProber(private val answers: Map<String, Pair<Long, ProbeResult>>) : UrlProber {
        val probed = mutableListOf<String>()
        val cancelled = mutableListOf<String>()

        override suspend fun probe(url: String): ProbeResult {
            probed += url
            val (millis, result) = answers.getValue(url)
            try {
                delay(millis)
            } catch (e: CancellationException) {
                cancelled += url
                throw e
            }
            return result
        }
    }

    private val ok = ProbeResult.Alive(200)
    private val refused = ProbeResult.Failed(FailureKind.REFUSED)
    private val timeout = ProbeResult.Failed(FailureKind.TIMEOUT)

    @Test
    fun `the first to answer wins, whatever its position, and the rest are cancelled`() = runTest {
        val prober = FakeProber(mapOf("a" to (1500L to timeout), "b" to (200L to ok), "c" to (800L to ok)))

        assertEquals(RaceResult.Won("b"), UrlRace(prober).run(listOf("a", "b", "c")))
        assertEquals(listOf("a", "b", "c"), prober.probed)
        assertEquals(setOf("a", "c"), prober.cancelled.toSet())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `the race lasts as long as the winner, not the slowest`() = runTest {
        val prober = FakeProber(mapOf("a" to (1500L to timeout), "b" to (200L to ok)))

        UrlRace(prober).run(listOf("a", "b"))
        assertEquals(200L, testScheduler.currentTime)
    }

    @Test
    fun `any HTTP status proves the server is there`() = runTest {
        val prober = FakeProber(mapOf("a" to (300L to ProbeResult.Alive(401)), "b" to (500L to ok)))

        assertEquals(RaceResult.Won("a"), UrlRace(prober).run(listOf("a", "b")))
    }

    @Test
    fun `a fast failure does not win`() = runTest {
        val prober = FakeProber(mapOf("a" to (10L to refused), "b" to (900L to ok)))

        assertEquals(RaceResult.Won("b"), UrlRace(prober).run(listOf("a", "b")))
    }

    @Test
    fun `when nobody answers, every URL is reported in the page's order`() = runTest {
        val prober = FakeProber(mapOf("a" to (1500L to timeout), "b" to (20L to refused)))

        assertEquals(
            RaceResult.Lost(listOf(Attempt("a", timeout), Attempt("b", refused))),
            UrlRace(prober).run(listOf("a", "b")),
        )
    }

    @Test
    fun `the remembered URL is tried alone first`() = runTest {
        val prober = FakeProber(mapOf("a" to (10L to ok), "b" to (50L to ok)))

        assertEquals(RaceResult.Won("b"), UrlRace(prober).run(listOf("a", "b"), remembered = "b"))
        assertEquals(listOf("b"), prober.probed)
    }

    @Test
    fun `if the remembered URL fails, the others race without it`() = runTest {
        val prober = FakeProber(mapOf("a" to (100L to ok), "b" to (1500L to timeout), "c" to (50L to refused)))

        assertEquals(RaceResult.Won("a"), UrlRace(prober).run(listOf("a", "b", "c"), remembered = "b"))
        assertEquals(listOf("b", "a", "c"), prober.probed)
    }

    @Test
    fun `a remembered failure is still reported when everything fails`() = runTest {
        val prober = FakeProber(mapOf("a" to (10L to refused), "b" to (1500L to timeout)))

        assertEquals(
            RaceResult.Lost(listOf(Attempt("a", refused), Attempt("b", timeout))),
            UrlRace(prober).run(listOf("a", "b"), remembered = "b"),
        )
        assertEquals(listOf("b", "a"), prober.probed)
    }

    @Test
    fun `a remembered URL the page no longer has is ignored`() = runTest {
        val prober = FakeProber(mapOf("a" to (10L to ok)))

        assertEquals(RaceResult.Won("a"), UrlRace(prober).run(listOf("a"), remembered = "gone"))
        assertFalse("gone" in prober.probed)
    }
}
