package es.edgarms.weblauncher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import es.edgarms.weblauncher.WebLauncherApp
import es.edgarms.weblauncher.model.Config
import es.edgarms.weblauncher.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PagesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WebLauncherApp

    /** One writer at a time, so saves reach the disk in the order they were made. */
    private val disk = Dispatchers.IO.limitedParallelism(1)

    private val _config = MutableStateFlow<Config?>(null)

    /** Null until the file has been read. */
    val config: StateFlow<Config?> = _config.asStateFlow()

    init {
        viewModelScope.launch {
            _config.value = withContext(disk) { app.configStore.load() }
        }
    }

    /** Adds the page, or replaces the one with the same id. */
    fun save(page: Page) = update { config ->
        val index = config.pages.indexOfFirst { it.id == page.id }
        val pages = if (index < 0) config.pages + page else config.pages.toMutableList().also { it[index] = page }
        config.copy(pages = pages)
    }

    fun delete(pageId: String) {
        update { config -> config.copy(pages = config.pages.filterNot { it.id == pageId }) }
        viewModelScope.launch(disk) { app.winners.remove(pageId) }
    }

    private fun update(change: (Config) -> Config) {
        val current = _config.value ?: return
        _config.value = change(current)
        viewModelScope.launch(disk) {
            _config.value?.let { app.configStore.save(it) }
        }
    }
}
