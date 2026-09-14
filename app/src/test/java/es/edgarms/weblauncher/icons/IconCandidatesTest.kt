package es.edgarms.weblauncher.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconCandidatesTest {

    /** What the Jackery Explorer 240 interface actually serves. */
    private val jackeryHtml = """
        <!doctype html>
        <html><head>
        <link rel="manifest" href="/manifest.webmanifest">
        <link rel="icon" href="/static/icon.svg" type="image/svg+xml">
        <link rel="apple-touch-icon" href="/static/icon.svg">
        </head><body></body></html>
    """.trimIndent()

    private val jackeryManifest = """
        {
          "name": "Jackery Explorer 240",
          "icons": [
            { "src": "/static/icon-192.png", "sizes": "192x192", "type": "image/png", "purpose": "any" },
            { "src": "/static/icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any" },
            { "src": "/static/icon-maskable-512.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable" },
            { "src": "/static/icon.svg", "sizes": "any", "type": "image/svg+xml", "purpose": "any" }
          ]
        }
    """.trimIndent()

    @Test
    fun `the Jackery page leads to its maskable icon first, and never to its SVG`() {
        val page = "http://172.30.172.16:8731/"
        val manifestUrl = IconCandidates.manifestUrl(page, jackeryHtml)
        assertEquals("http://172.30.172.16:8731/manifest.webmanifest", manifestUrl)

        val chain = IconCandidates.chain(page, jackeryHtml, IconCandidates.fromManifest(manifestUrl!!, jackeryManifest))

        assertEquals(
            listOf(
                IconRef("http://172.30.172.16:8731/static/icon-maskable-512.png", maskable = true),
                IconRef("http://172.30.172.16:8731/static/icon-512.png"),
                IconRef("http://172.30.172.16:8731/static/icon-192.png"),
                IconRef("http://172.30.172.16:8731/favicon.ico"),
            ),
            chain,
        )
    }

    @Test
    fun `manifest icons resolve against the manifest, not the page`() {
        val icons = IconCandidates.fromManifest(
            "http://pc.lan/app/meta/manifest.json",
            """{"icons": [{"src": "../img/icon.png", "sizes": "96x96"}]}""",
        )

        assertEquals(listOf(IconRef("http://pc.lan/app/img/icon.png")), icons)
    }

    @Test
    fun `purpose can hold several values, and a tiny maskable icon does not beat a big one`() {
        val icons = IconCandidates.fromManifest(
            "http://pc.lan/m.json",
            """
            {"icons": [
              {"src": "small-mask.png", "sizes": "48x48", "purpose": "maskable"},
              {"src": "big.png", "sizes": "512x512"},
              {"src": "both.png", "sizes": "192x192", "purpose": "any maskable"}
            ]}
            """.trimIndent(),
        )

        assertEquals(
            listOf("http://pc.lan/both.png", "http://pc.lan/big.png", "http://pc.lan/small-mask.png"),
            icons.map { it.url },
        )
        assertTrue(icons.first().maskable)
    }

    @Test
    fun `without a manifest, apple-touch-icon comes before the biggest rel icon, and the favicon last`() {
        val html = """
            <head>
              <link rel="shortcut icon" href="fav-16.png" sizes="16x16">
              <link rel="icon" href="fav-64.png" sizes="64x64">
              <link rel="apple-touch-icon" href="/touch.png">
            </head>
        """.trimIndent()

        assertEquals(
            listOf(
                "http://pc.lan/touch.png",
                "http://pc.lan/app/fav-64.png",
                "http://pc.lan/app/fav-16.png",
                "http://pc.lan/favicon.ico",
            ),
            IconCandidates.chain("http://pc.lan/app/", html, emptyList()).map { it.url },
        )
    }

    @Test
    fun `a page that declares nothing, or could not be read, still has the favicon to try`() {
        val expected = listOf(IconRef("http://pc.lan:9000/favicon.ico"))

        assertEquals(expected, IconCandidates.chain("http://pc.lan:9000/x", "<html></html>", emptyList()))
        assertEquals(expected, IconCandidates.chain("http://pc.lan:9000/x", null, emptyList()))
    }

    @Test
    fun `a broken manifest gives no icons instead of failing`() {
        assertEquals(emptyList<IconRef>(), IconCandidates.fromManifest("http://pc.lan/m.json", "{ nope"))
        assertEquals(emptyList<IconRef>(), IconCandidates.fromManifest("http://pc.lan/m.json", """{"icons": "x"}"""))
        assertEquals(emptyList<IconRef>(), IconCandidates.fromManifest("http://pc.lan/m.json", "[]"))
    }

    @Test
    fun `the same file is tried once`() {
        val html = """<link rel="apple-touch-icon" href="/icon.png">"""
        val chain = IconCandidates.chain("http://pc.lan/", html, listOf(IconRef("http://pc.lan/icon.png")))

        assertEquals(listOf("http://pc.lan/icon.png", "http://pc.lan/favicon.ico"), chain.map { it.url })
    }

    @Test
    fun `sizes are read by their biggest side`() {
        assertEquals(64, IconCandidates.largestSide("16x16 64x64 32x32"))
        assertEquals(0, IconCandidates.largestSide("any"))
        assertEquals(0, IconCandidates.largestSide(null))
    }

    @Test
    fun `a picked photo is reduced only while both sides stay above the icon size`() {
        assertEquals(8, IconBitmaps.sampleSize(4000, 3000, 192))
        assertEquals(4, IconBitmaps.sampleSize(1000, 1000, 192))
        assertEquals(1, IconBitmaps.sampleSize(100, 100, 192))
    }
}
