package es.edgarms.weblauncher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import es.edgarms.weblauncher.WebLauncherApp
import es.edgarms.weblauncher.icons.IconEdit
import es.edgarms.weblauncher.model.Config
import es.edgarms.weblauncher.model.Page
import kotlinx.coroutines.flow.StateFlow

class PagesViewModel(application: Application) : AndroidViewModel(application) {
    private val pages = (application as WebLauncherApp).pages

    /** Null until the file has been read. */
    val config: StateFlow<Config?> = pages.config

    fun save(page: Page, icon: IconEdit) = pages.save(page, icon)

    fun delete(pageId: String) = pages.delete(pageId)
}
