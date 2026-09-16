package es.edgarms.weblauncher.update

import es.edgarms.weblauncher.data.Files
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** What the last check learnt. Only meaningful for the version that made it: see [checkedFrom]. */
@Serializable
data class UpdateRecord(
    val checkedAt: Long = 0,
    /** The running version when the check was made; once the app is updated the rest no longer applies. */
    val checkedFrom: String? = null,
    val etag: String? = null,
    val available: AvailableUpdate? = null,
)

/** Kept apart from the configuration, like the last winners: it is not something to export. */
class UpdateStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private var record: UpdateRecord? = null

    @Synchronized
    fun load(): UpdateRecord = record ?: read().also { record = it }

    @Synchronized
    fun save(updated: UpdateRecord) {
        record = updated
        Files.writeAtomically(file, json.encodeToString(UpdateRecord.serializer(), updated))
    }

    private fun read(): UpdateRecord {
        if (!file.exists()) return UpdateRecord()
        return try {
            json.decodeFromString(UpdateRecord.serializer(), file.readText())
        } catch (_: IllegalArgumentException) {
            UpdateRecord() // Only a cache: losing it costs one request.
        }
    }
}
