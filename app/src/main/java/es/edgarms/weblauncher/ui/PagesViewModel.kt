package es.edgarms.weblauncher.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import es.edgarms.weblauncher.WebLauncherApp
import es.edgarms.weblauncher.icons.IconEdit
import es.edgarms.weblauncher.model.Config
import es.edgarms.weblauncher.model.ConfigTransfer
import es.edgarms.weblauncher.model.ImportResult
import es.edgarms.weblauncher.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException

class PagesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WebLauncherApp
    private val pages = app.pages

    /** Null until the file has been read. */
    val config: StateFlow<Config?> = pages.config

    fun save(page: Page, icon: IconEdit) = pages.save(page, icon)

    fun delete(pageId: String) = pages.delete(pageId)

    /** Writes the configuration to a file the user chose. False if it could not be written. */
    suspend fun exportTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val bytes = ConfigTransfer.export(pages.loaded()).toByteArray()
        try {
            // "wt" truncates: overwriting a longer old export must not leave its tail behind.
            app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    /** Reads and checks a file the user chose; nothing changes until [applyImport]. */
    suspend fun readImport(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = try {
            app.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
        if (text == null) ImportResult.Invalid(ImportResult.Reason.UNREADABLE) else ConfigTransfer.read(text)
    }

    fun applyImport(config: Config) = pages.replaceAll(config)
}
