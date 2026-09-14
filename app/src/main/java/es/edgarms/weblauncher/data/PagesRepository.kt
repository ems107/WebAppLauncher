package es.edgarms.weblauncher.data

import es.edgarms.weblauncher.WebLauncherApp
import es.edgarms.weblauncher.icons.IconEdit
import es.edgarms.weblauncher.icons.PageIcons
import es.edgarms.weblauncher.model.Config
import es.edgarms.weblauncher.model.Page
import es.edgarms.weblauncher.shortcuts.Shortcuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The one copy of the pages while the app runs. The list, the editor and every
 * open page share it, so an icon an open page fetched is not overwritten by an
 * edit the list saves a moment later from an older copy.
 */
class PagesRepository(private val app: WebLauncherApp) {
    /** One writer at a time, so changes reach the disk in the order they were made. */
    private val disk = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + disk)

    private val _config = MutableStateFlow<Config?>(null)

    /** Null until the file has been read. */
    val config: StateFlow<Config?> = _config.asStateFlow()

    init {
        scope.launch {
            val loaded = app.configStore.load()
            _config.value = loaded
            // Brings the launcher's shortcuts in line with the file, whatever changed it.
            Shortcuts.sync(app, loaded)
        }
    }

    suspend fun loaded(): Config = config.filterNotNull().first()

    /** Adds [page], or replaces the one with its id. Its icon only changes as [icon] says. */
    fun save(page: Page, icon: IconEdit = IconEdit.Keep) {
        update { config ->
            val saved = page.copy(iconPath = config.page(page.id)?.iconPath)
            val index = config.pages.indexOfFirst { it.id == page.id }
            val pages = if (index < 0) config.pages + saved else config.pages.toMutableList().also { it[index] = saved }
            config.copy(pages = pages)
        }
        when (icon) {
            IconEdit.Keep -> Unit
            IconEdit.Reset -> {
                app.iconAttempts.remove(page.id)
                setIcon(page.id, null)
            }
            is IconEdit.Picked -> scope.launch {
                val bitmap = PageIcons.decodePicked(app, icon.uri) ?: return@launch
                setIcon(page.id, PageIcons.save(app, page.id, bitmap))
            }
        }
    }

    fun delete(pageId: String) {
        update { config -> config.copy(pages = config.pages.filterNot { it.id == pageId }) }
        scope.launch {
            app.winners.remove(pageId)
            PageIcons.prune(app, pageId)
            Shortcuts.disable(app, pageId)
        }
    }

    /** Points the page at another icon file, or at none, and deletes the files it no longer uses. */
    fun setIcon(pageId: String, iconPath: String?) {
        update { config ->
            config.copy(pages = config.pages.map { if (it.id == pageId) it.copy(iconPath = iconPath) else it })
        }
        scope.launch { PageIcons.prune(app, pageId, keep = iconPath) }
    }

    private fun update(change: (Config) -> Config) {
        _config.update { it?.let(change) }
        scope.launch {
            _config.value?.let {
                app.configStore.save(it)
                Shortcuts.sync(app, it)
            }
        }
    }
}
