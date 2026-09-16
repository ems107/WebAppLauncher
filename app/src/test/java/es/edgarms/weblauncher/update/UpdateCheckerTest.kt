package es.edgarms.weblauncher.update

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateCheckerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private var clock = 1_800_000_000_000L

    @After
    fun tearDown() = server.shutdown()

    private val storeFile get() = File(folder.root, "update.json")

    private fun checker(running: String = "0.0.1") = UpdateChecker(
        UpdateStore(storeFile),
        GitHubReleaseSource("ems107/WebAppLauncher", baseUrl = server.url("").toString().trimEnd('/')),
        Version.parse(running)!!,
        now = { clock },
    )

    private fun releases(vararg tags: String) = tags.joinToString(",", "[", "]") { tag ->
        """{"tag_name": "$tag", "body": "Notes", "assets": [{"name": "app.apk", "size": 10, "browser_download_url": "https://example.test/$tag.apk"}]}"""
    }

    private fun fresh(body: String, etag: String = "\"one\"") = MockResponse().setBody(body).setHeader("ETag", etag)

    @Test
    fun `asks for the repository's releases, as GitHub requires`() = runBlocking {
        server.enqueue(fresh(releases()))

        checker().check(force = true)

        val request = server.takeRequest()
        assertEquals("/repos/ems107/WebAppLauncher/releases?per_page=30", request.path)
        assertEquals("WebLauncher", request.getHeader("User-Agent"))
    }

    @Test
    fun `a newer release is found, and remembered for the next start`() = runBlocking {
        server.enqueue(fresh(releases("v0.0.2")))

        assertEquals("0.0.2", (checker().check(force = true) as CheckOutcome.Found).update.version)
        assertEquals("0.0.2", checker().available()?.version)
    }

    @Test
    fun `nothing newer is up to date`() = runBlocking {
        server.enqueue(fresh(releases("v0.0.1")))

        assertEquals(CheckOutcome.UpToDate, checker().check(force = true))
    }

    @Test
    fun `a recent answer is reused unless the check is forced`() = runBlocking {
        server.enqueue(fresh(releases("v0.0.2")))
        server.enqueue(fresh(releases("v0.0.3")))
        val checker = checker()
        checker.check(force = true)

        clock += UpdateChecker.RECENT_MILLIS - 1
        assertEquals("0.0.2", (checker.check(force = false) as CheckOutcome.Found).update.version)
        assertEquals(1, server.requestCount)

        assertEquals("0.0.3", (checker.check(force = true) as CheckOutcome.Found).update.version)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `an old answer is asked again, with its ETag, and a 304 keeps what was found`() = runBlocking {
        server.enqueue(fresh(releases("v0.0.2"), etag = "\"abc\""))
        server.enqueue(MockResponse().setResponseCode(304))
        val checker = checker()
        checker.check(force = true)
        server.takeRequest()

        clock += UpdateChecker.RECENT_MILLIS
        assertEquals("0.0.2", (checker.check(force = false) as CheckOutcome.Found).update.version)
        assertEquals("\"abc\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `what a previous version learnt is forgotten after updating, ETag included`() = runBlocking {
        server.enqueue(fresh(releases("v0.0.2"), etag = "\"abc\""))
        checker(running = "0.0.1").check(force = true)
        server.takeRequest()

        val updated = checker(running = "0.0.2")
        assertNull(updated.available())

        server.enqueue(fresh(releases("v0.0.2")))
        assertEquals(CheckOutcome.UpToDate, updated.check(force = false))
        assertNull(server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `failures are told apart and do not forget a known update`() = runBlocking {
        server.enqueue(fresh(releases("v0.0.2")))
        server.enqueue(MockResponse().setResponseCode(403).setHeader("X-RateLimit-Remaining", "0"))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(fresh("""{"message": "Not Found"}"""))
        val checker = checker()
        checker.check(force = true)

        assertEquals(CheckOutcome.Failed(FeedFailure.RATE_LIMITED), checker.check(force = true))
        assertEquals(CheckOutcome.Failed(FeedFailure.SERVER), checker.check(force = true))
        assertEquals(CheckOutcome.Failed(FeedFailure.SERVER), checker.check(force = true))
        assertEquals("0.0.2", checker.available()?.version)
    }

    @Test
    fun `no server at all is offline`() = runBlocking {
        val checker = checker()
        server.shutdown()

        assertEquals(CheckOutcome.Failed(FeedFailure.OFFLINE), checker.check(force = true))
    }
}
