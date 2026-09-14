package es.edgarms.weblauncher.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageUrlsTest {
    @Test
    fun `http and https URLs with a host are valid`() {
        assertTrue(PageUrls.isValid("http://172.30.172.16:8731"))
        assertTrue(PageUrls.isValid("https://pc.lan/app/"))
        assertTrue(PageUrls.isValid("  HTTP://pc.lan  "))
    }

    @Test
    fun `anything else is not`() {
        assertFalse(PageUrls.isValid(""))
        assertFalse(PageUrls.isValid("172.30.172.16:8731"))
        assertFalse(PageUrls.isValid("ftp://pc.lan"))
        assertFalse(PageUrls.isValid("http://"))
        assertFalse(PageUrls.isValid("http://bad host"))
    }
}
