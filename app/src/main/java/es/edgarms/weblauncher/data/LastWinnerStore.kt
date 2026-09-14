package es.edgarms.weblauncher.data

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The URL that last answered for each page, tried first next time. Kept apart
 * from the configuration: it is a cache of this phone's network, not something
 * to export.
 */
class LastWinnerStore(private val file: File) {
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private var winners: Map<String, String>? = null

    @Synchronized
    fun get(pageId: String): String? = all()[pageId]

    @Synchronized
    fun put(pageId: String, url: String) {
        if (all()[pageId] == url) return
        write(all() + (pageId to url))
    }

    @Synchronized
    fun remove(pageId: String) {
        if (pageId !in all()) return
        write(all() - pageId)
    }

    private fun all(): Map<String, String> = winners ?: read().also { winners = it }

    private fun read(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return try {
            Json.decodeFromString(serializer, file.readText())
        } catch (_: IllegalArgumentException) {
            emptyMap() // Only a cache: losing it costs one race.
        }
    }

    private fun write(map: Map<String, String>) {
        winners = map
        Files.writeAtomically(file, Json.encodeToString(serializer, map))
    }
}
