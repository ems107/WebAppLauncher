package es.edgarms.weblauncher.data

import es.edgarms.weblauncher.model.Config
import es.edgarms.weblauncher.model.ConfigJson
import java.io.File

/**
 * The configuration as one JSON file. Writes go to a temporary file first and
 * replace the real one in a single move, so a crash never leaves half a file.
 */
class ConfigStore(private val file: File) {

    /**
     * Returns the stored configuration, or an empty one if there is none.
     * A file that cannot be parsed is set aside as `<name>.bad` rather than
     * overwritten, so whatever was in it can still be recovered by hand.
     */
    @Synchronized
    fun load(): Config {
        if (!file.exists()) return Config()
        return try {
            ConfigJson.decode(file.readText())
        } catch (_: IllegalArgumentException) {
            Files.replace(file, File(file.parentFile, file.name + ".bad"))
            Config()
        }
    }

    @Synchronized
    fun save(config: Config) {
        Files.writeAtomically(file, ConfigJson.encode(config))
    }
}
