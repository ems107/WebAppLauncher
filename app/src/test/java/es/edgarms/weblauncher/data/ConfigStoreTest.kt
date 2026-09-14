package es.edgarms.weblauncher.data

import es.edgarms.weblauncher.model.Config
import es.edgarms.weblauncher.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ConfigStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val file get() = File(folder.root, "config.json")

    @Test
    fun `a missing file is an empty configuration`() {
        assertEquals(Config(), ConfigStore(file).load())
    }

    @Test
    fun `saving and loading loses nothing`() {
        val config = Config(
            pages = listOf(
                Page(name = "Jackery", urls = listOf("http://10.0.0.2:8731", "http://10.0.0.3:8731")),
                Page(name = "Other", urls = listOf("https://example.test/app"), iconPath = "icons/x.png"),
            ),
        )
        ConfigStore(file).save(config)

        assertEquals(config, ConfigStore(file).load())
        assertFalse(File(folder.root, "config.json.tmp").exists())
    }

    @Test
    fun `saving twice replaces the file`() {
        val store = ConfigStore(file)
        store.save(Config(pages = listOf(Page(name = "A", urls = listOf("http://a.test")))))
        val second = Config(pages = listOf(Page(name = "B", urls = listOf("http://b.test"))))
        store.save(second)

        assertEquals(second, store.load())
    }

    @Test
    fun `a corrupt file loads as empty and is kept aside`() {
        file.writeText("{ this is not json")

        assertEquals(Config(), ConfigStore(file).load())
        val bad = File(folder.root, "config.json.bad")
        assertTrue(bad.exists())
        assertEquals("{ this is not json", bad.readText())
        assertFalse(file.exists())
    }

    @Test
    fun `a file with the wrong shape counts as corrupt`() {
        file.writeText("""{"pages": [{"name": "no urls"}]}""")

        assertEquals(Config(), ConfigStore(file).load())
        assertTrue(File(folder.root, "config.json.bad").exists())
    }

    @Test
    fun `unknown keys from a newer version are ignored`() {
        file.writeText(
            """{"version": 1, "future": true, "pages": [{"id": "p1", "name": "A", "urls": ["http://a.test"], "color": "red"}]}""",
        )

        assertEquals(
            Config(pages = listOf(Page(id = "p1", name = "A", urls = listOf("http://a.test")))),
            ConfigStore(file).load(),
        )
    }
}
