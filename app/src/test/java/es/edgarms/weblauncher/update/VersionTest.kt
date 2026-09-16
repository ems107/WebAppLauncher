package es.edgarms.weblauncher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun `tags and version names both parse`() {
        assertEquals(Version(0, 0, 2), Version.parse("v0.0.2"))
        assertEquals(Version(1, 12, 3), Version.parse("1.12.3"))
    }

    @Test
    fun `anything else is not a version`() {
        listOf("", "v1.2", "1.2.3-beta", "latest", "1.2.3.4").forEach { assertNull(it, Version.parse(it)) }
    }

    @Test
    fun `parts compare as numbers, not text`() {
        assertTrue(Version.parse("0.0.10")!! > Version.parse("0.0.9")!!)
        assertTrue(Version.parse("1.0.0")!! > Version.parse("0.99.99")!!)
    }
}
