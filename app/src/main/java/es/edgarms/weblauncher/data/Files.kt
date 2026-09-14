package es.edgarms.weblauncher.data

import java.io.File
import java.nio.file.StandardCopyOption

internal object Files {
    fun writeAtomically(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        replace(tmp, file)
    }

    fun replace(source: File, target: File) {
        java.nio.file.Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }
}
