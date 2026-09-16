package es.edgarms.weblauncher.update

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ApkDownloaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private val content = ByteArray(200_000) { (it % 251).toByte() }

    @After
    fun tearDown() = server.shutdown()

    private val dir get() = File(folder.root, "updates")
    private val downloader get() = ApkDownloader(dir, retryDelayMillis = 0)
    private fun update(size: Long = content.size.toLong()) =
        AvailableUpdate("0.0.2", "", server.url("/app.apk").toString(), size)

    private fun body(bytes: ByteArray) = Buffer().write(bytes)

    @Test
    fun `downloads the whole file and reports progress up to the end`() = runBlocking {
        server.enqueue(MockResponse().setBody(body(content)))
        var last = 0f

        val file = downloader.download(update()) { last = it }

        assertArrayEquals(content, file.readBytes())
        assertEquals("weblauncher-0.0.2.apk", file.name)
        assertEquals(1f, last)
    }

    @Test
    fun `a dropped connection resumes from the bytes already on disk`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(body(content)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )
        server.enqueue(
            MockResponse().setResponseCode(206).setBody(body(content.copyOfRange(content.size / 2, content.size))),
        )

        // MockWebServer cuts the body at half, so the second request must ask for the rest.
        val file = downloader.download(update()) {}

        server.takeRequest()
        assertEquals("bytes=${content.size / 2}-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(content, file.readBytes())
    }

    @Test
    fun `a server that ignores the range starts the file over`() = runBlocking {
        dir.mkdirs()
        File(dir, "weblauncher-0.0.2.apk.part").writeBytes(ByteArray(1000) { 7 })
        server.enqueue(MockResponse().setBody(body(content)))

        val file = downloader.download(update()) {}

        assertEquals("bytes=1000-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(content, file.readBytes())
    }

    @Test
    fun `a finished download is not fetched again`() = runBlocking {
        dir.mkdirs()
        File(dir, "weblauncher-0.0.2.apk").writeBytes(content)

        downloader.download(update()) {}

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the wrong size is a failure, after every attempt`() {
        repeat(5) { server.enqueue(MockResponse().setBody(body(content))) }

        val failure = runCatching { runBlocking { downloader.download(update(size = 5)) {} } }.exceptionOrNull()

        assertTrue("got $failure", failure is IOException)
        assertEquals(5, server.requestCount)
        assertFalse(File(dir, "weblauncher-0.0.2.apk").exists())
    }

    @Test
    fun `pruning keeps only the version still on offer`() {
        dir.mkdirs()
        listOf("weblauncher-0.0.2.apk", "weblauncher-0.0.3.apk", "weblauncher-0.0.3.apk.part").forEach {
            File(dir, it).writeText("x")
        }

        downloader.prune(keepVersion = "0.0.3")
        assertEquals(setOf("weblauncher-0.0.3.apk", "weblauncher-0.0.3.apk.part"), dir.list()!!.toSet())

        downloader.prune(keepVersion = null)
        assertNull(dir.list()?.takeIf { it.isNotEmpty() })
    }
}
