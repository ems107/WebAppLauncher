package es.edgarms.weblauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LastWinnerStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file get() = File(folder.root, "winners.json")

    @Test
    fun `winners survive a new instance`() {
        LastWinnerStore(file).apply {
            put("a", "http://10.0.0.2:8731")
            put("b", "http://10.0.0.3:9000")
        }

        val reopened = LastWinnerStore(file)
        assertEquals("http://10.0.0.2:8731", reopened.get("a"))
        assertEquals("http://10.0.0.3:9000", reopened.get("b"))
        assertNull(reopened.get("c"))
    }

    @Test
    fun `a new winner replaces the old one and removal forgets it`() {
        val store = LastWinnerStore(file)
        store.put("a", "http://one.test")
        store.put("a", "http://two.test")
        assertEquals("http://two.test", LastWinnerStore(file).get("a"))

        store.remove("a")
        assertNull(LastWinnerStore(file).get("a"))
    }

    @Test
    fun `a corrupt file is just an empty cache`() {
        file.writeText("nonsense")

        val store = LastWinnerStore(file)
        assertNull(store.get("a"))
        store.put("a", "http://one.test")
        assertEquals("http://one.test", LastWinnerStore(file).get("a"))
    }
}
