package es.edgarms.weblauncher.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginsTest {
    @Test
    fun `same scheme, host and port is the same origin, whatever the path`() {
        assertTrue(Origins.same("http://172.30.172.16:8731/history?x=1", "http://172.30.172.16:8731"))
        assertTrue(Origins.same("HTTP://PC.lan/a", "http://pc.lan:80/b"))
        assertTrue(Origins.same("https://pc.lan/", "https://pc.lan:443"))
    }

    @Test
    fun `a different port, host or scheme is another origin`() {
        assertFalse(Origins.same("http://pc.lan:8731", "http://pc.lan:9000"))
        assertFalse(Origins.same("http://pc.lan:8731", "http://other.lan:8731"))
        assertFalse(Origins.same("https://pc.lan", "http://pc.lan"))
    }

    @Test
    fun `links that are not web addresses never match`() {
        assertFalse(Origins.same("mailto:someone@example.test", "http://pc.lan"))
        assertFalse(Origins.same("not a url", "http://pc.lan"))
    }
}
