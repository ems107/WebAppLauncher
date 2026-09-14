package es.edgarms.weblauncher.net

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class OkHttpUrlProberTest {
    private val server = MockWebServer()

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `a 401 counts as alive, asked with HEAD`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(ProbeResult.Alive(401), OkHttpUrlProber().probe(server.url("/").toString()))
        assertEquals("HEAD", server.takeRequest().method)
    }

    @Test
    fun `redirects are an answer, not something to follow`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://elsewhere.test/"))

        assertEquals(ProbeResult.Alive(302), OkHttpUrlProber().probe(server.url("/").toString()))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a closed port fails, and quickly`() = runBlocking {
        val port = ServerSocket(0).use { it.localPort }

        val started = System.nanoTime()
        val result = OkHttpUrlProber().probe("http://127.0.0.1:$port/")
        val elapsedMillis = (System.nanoTime() - started) / 1_000_000

        assertTrue("got $result", result is ProbeResult.Failed)
        assertTrue("took $elapsedMillis ms", elapsedMillis < 5_000)
    }

    @Test
    fun `an invalid URL fails without throwing`() = runBlocking {
        assertEquals(FailureKind.OTHER, (OkHttpUrlProber().probe("not a url") as ProbeResult.Failed).kind)
    }

    @Test
    fun `the reason is read from the cause OkHttp wraps it in`() {
        fun wrapped(cause: Throwable) = ConnectException("Failed to connect to /10.0.0.2:8731").apply { initCause(cause) }

        assertEquals(
            ProbeResult.Failed(FailureKind.TIMEOUT, "connect timed out"),
            OkHttpUrlProber.classify(wrapped(SocketTimeoutException("connect timed out"))),
        )
        assertEquals(
            FailureKind.UNREACHABLE,
            OkHttpUrlProber.classify(wrapped(Exception("isConnected failed: EHOSTUNREACH (No route to host)"))).kind,
        )
        assertEquals(
            FailureKind.REFUSED,
            OkHttpUrlProber.classify(wrapped(Exception("isConnected failed: ECONNREFUSED (Connection refused)"))).kind,
        )
        assertEquals(
            FailureKind.UNREACHABLE,
            OkHttpUrlProber.classify(wrapped(Exception("connect failed: ENETUNREACH (Network is unreachable)"))).kind,
        )
    }

    @Test
    fun `a bare connect failure with nothing recognisable is OTHER, with its message`() {
        assertEquals(
            ProbeResult.Failed(FailureKind.OTHER, "Failed to connect to /10.0.0.2:8731"),
            OkHttpUrlProber.classify(ConnectException("Failed to connect to /10.0.0.2:8731")),
        )
        assertEquals(FailureKind.UNKNOWN_HOST, OkHttpUrlProber.classify(UnknownHostException("pc.lan")).kind)
    }
}
