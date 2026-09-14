package es.edgarms.weblauncher.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TileTest {
    @Test
    fun `the initial is the first letter or digit, upper-cased`() {
        assertEquals("J", Tile.initial("jackery"))
        assertEquals("E", Tile.initial("  (Explorer) 240"))
        assertEquals("2", Tile.initial("240"))
        assertEquals("?", Tile.initial("   "))
    }

    @Test
    fun `the color depends only on the name`() {
        assertEquals(Tile.color("Jackery"), Tile.color(" jackery "))
        assertEquals(0xFF, Tile.color("anything") ushr 24)
    }
}
