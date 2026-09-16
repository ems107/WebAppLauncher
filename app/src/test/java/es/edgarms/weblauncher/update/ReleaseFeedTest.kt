package es.edgarms.weblauncher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseFeedTest {
    private fun release(
        tag: String,
        body: String = "Notes of $tag",
        apk: Boolean = true,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ): String {
        val assets = if (apk) {
            """[{"name": "weblauncher-${tag.removePrefix("v")}.apk", "size": 1234, "browser_download_url": "https://example.test/$tag.apk"}]"""
        } else {
            "[]"
        }
        return """{"tag_name": "$tag", "draft": $draft, "prerelease": $prerelease, "body": "$body", "assets": $assets, "id": 1}"""
    }

    private fun feed(vararg releases: String) = releases.joinToString(",", "[", "]")

    private val running = Version(0, 0, 1)

    @Test
    fun `the newest release above the running one is the update`() {
        val update = ReleaseFeed.newest(feed(release("v0.0.2"), release("v0.0.3"), release("v0.0.1")), running)

        assertEquals("0.0.3", update?.version)
        assertEquals("https://example.test/v0.0.3.apk", update?.apkUrl)
        assertEquals(1234L, update?.apkSize)
    }

    @Test
    fun `notes cover every version being skipped, newest first`() {
        val update = ReleaseFeed.newest(feed(release("v0.0.2"), release("v0.0.3")), running)

        assertEquals("v0.0.3\nNotes of v0.0.3\n\nv0.0.2\nNotes of v0.0.2", update?.notes)
    }

    @Test
    fun `a single new version shows its notes alone`() {
        assertEquals("Notes of v0.0.2", ReleaseFeed.newest(feed(release("v0.0.2")), running)?.notes)
    }

    @Test
    fun `nothing newer means no update`() {
        assertNull(ReleaseFeed.newest(feed(release("v0.0.1"), release("v0.0.0")), running))
        assertNull(ReleaseFeed.newest("[]", running))
    }

    @Test
    fun `drafts, prereleases, odd tags and releases without an APK are skipped`() {
        val update = ReleaseFeed.newest(
            feed(
                release("v0.0.2"),
                release("v0.0.3", apk = false),
                release("v0.0.4", draft = true),
                release("v0.0.5", prerelease = true),
                release("nightly"),
            ),
            running,
        )

        assertEquals("0.0.2", update?.version)
        assertEquals("Notes of v0.0.2", update?.notes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an answer that is not a list of releases is an error`() {
        ReleaseFeed.newest("""{"message": "API rate limit exceeded"}""", running)
    }
}
