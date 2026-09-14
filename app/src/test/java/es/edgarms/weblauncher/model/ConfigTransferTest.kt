package es.edgarms.weblauncher.model

import es.edgarms.weblauncher.model.ImportResult.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConfigTransferTest {
    private val jackery = Page(
        id = "jackery",
        name = "Jackery",
        urls = listOf("http://172.30.172.16:8731", "http://172.30.172.17:8731"),
        iconPath = "icons/jackery-1.png",
    )
    private val other = Page(id = "other", name = "Other", urls = listOf("http://pc.lan:9000"))

    @Test
    fun `an export reads back as the same pages, without their icon files`() {
        val text = ConfigTransfer.export(Config(pages = listOf(jackery, other)))

        assertFalse("icons/jackery-1.png" in text)
        assertEquals(
            ImportResult.Valid(Config(pages = listOf(jackery.copy(iconPath = null), other))),
            ConfigTransfer.read(text),
        )
    }

    @Test
    fun `an exported empty configuration is a valid import`() {
        assertEquals(ImportResult.Valid(Config()), ConfigTransfer.read(ConfigTransfer.export(Config())))
    }

    @Test
    fun `anything without a pages list is not a configuration, so it cannot wipe the pages`() {
        listOf("{}", """{"foo": 1}""", "[]", "not json at all", """{"pages": "none"}""").forEach { text ->
            assertEquals(text, ImportResult.Invalid(Reason.NOT_A_CONFIG), ConfigTransfer.read(text))
        }
    }

    @Test
    fun `a file from a newer version is refused as such`() {
        assertEquals(
            ImportResult.Invalid(Reason.NEWER_VERSION),
            ConfigTransfer.read("""{"version": 2, "pages": []}"""),
        )
    }

    @Test
    fun `a page the editor could not have saved is named in the refusal`() {
        fun page(name: String, urls: String) = """{"id": "$name", "name": "$name", "urls": $urls}"""

        assertEquals(
            ImportResult.Invalid(Reason.BAD_PAGE, "NoUrls"),
            ConfigTransfer.read("""{"pages": [${page("Fine", """["http://a.test"]""")}, ${page("NoUrls", "[]")}]}"""),
        )
        assertEquals(
            ImportResult.Invalid(Reason.BAD_PAGE, "BadUrl"),
            ConfigTransfer.read("""{"pages": [${page("BadUrl", """["172.30.172.16:8731"]""")}]}"""),
        )
        assertEquals(
            ImportResult.Invalid(Reason.BAD_PAGE, "x"),
            ConfigTransfer.read("""{"pages": [{"id": "x", "name": " ", "urls": ["http://a.test"]}]}"""),
        )
    }

    @Test
    fun `two pages with the same id are refused`() {
        val text = """{"pages": [
            {"id": "same", "name": "A", "urls": ["http://a.test"]},
            {"id": "same", "name": "B", "urls": ["http://b.test"]}
        ]}"""

        assertEquals(ImportResult.Invalid(Reason.BAD_PAGE, "B"), ConfigTransfer.read(text))
    }

    @Test
    fun `a merge takes the imported pages and keeps the icons of those already here`() {
        val current = Config(pages = listOf(jackery, other))
        val imported = Config(
            pages = listOf(
                jackery.copy(name = "Explorer 240", iconPath = null),
                Page(id = "new", name = "New", urls = listOf("http://new.test")),
            ),
        )

        assertEquals(
            Config(
                pages = listOf(
                    jackery.copy(name = "Explorer 240"),
                    Page(id = "new", name = "New", urls = listOf("http://new.test")),
                ),
            ),
            ConfigTransfer.merge(current, imported),
        )
    }
}
