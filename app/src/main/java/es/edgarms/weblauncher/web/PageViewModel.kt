package es.edgarms.weblauncher.web

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import es.edgarms.weblauncher.WebLauncherApp
import es.edgarms.weblauncher.model.Page
import es.edgarms.weblauncher.net.Attempt
import es.edgarms.weblauncher.net.FailureKind
import es.edgarms.weblauncher.net.ProbeResult
import es.edgarms.weblauncher.net.RaceResult
import es.edgarms.weblauncher.net.UrlRace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PageState {
    data object Loading : PageState

    /** The page was deleted, or a shortcut points at one that never existed. */
    data object Missing : PageState

    data class Probing(val page: Page) : PageState

    /** @property loadId changes every time the WebView must load, even the same URL again. */
    data class Ready(val page: Page, val url: String, val loadId: Int) : PageState

    data class Unreachable(val page: Page, val attempts: List<Attempt>) : PageState
}

class PageViewModel(application: Application, savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as WebLauncherApp
    private val pageId: String? = savedState[WebActivity.EXTRA_PAGE_ID]
    private val race = UrlRace(app.prober)

    private val _state = MutableStateFlow<PageState>(PageState.Loading)
    val state: StateFlow<PageState> = _state.asStateFlow()

    private var job: Job? = null
    private var loads = 0
    private var wonAt = 0L

    init {
        open()
    }

    fun retry() = open()

    /** The WebView could not load the page it was pointed at. */
    fun onMainFrameError(description: String) {
        val ready = _state.value as? PageState.Ready ?: return
        if (SystemClock.elapsedRealtime() - wonAt < QUICK_FAILURE_MILLIS) {
            // The server answered the probe a moment ago and the page still fails:
            // racing again would only loop. Say what the WebView said.
            val failure = ProbeResult.Failed(FailureKind.OTHER, description)
            _state.value = PageState.Unreachable(ready.page, listOf(Attempt(ready.url, failure)))
        } else {
            launchRace(ready.page)
        }
    }

    private fun open() {
        job?.cancel()
        job = viewModelScope.launch {
            // Read again every time: the page may have been edited since this screen opened.
            val page = withContext(Dispatchers.IO) { pageId?.let { app.configStore.load().page(it) } }
            if (page == null) _state.value = PageState.Missing else raceFor(page)
        }
    }

    private fun launchRace(page: Page) {
        job?.cancel()
        job = viewModelScope.launch { raceFor(page) }
    }

    private suspend fun raceFor(page: Page) {
        if (page.urls.isEmpty()) {
            _state.value = PageState.Unreachable(page, emptyList())
            return
        }
        _state.value = PageState.Probing(page)
        val remembered = withContext(Dispatchers.IO) { app.winners.get(page.id) }
        _state.value = when (val result = race.run(page.urls, remembered)) {
            is RaceResult.Won -> {
                withContext(Dispatchers.IO) { app.winners.put(page.id, result.url) }
                wonAt = SystemClock.elapsedRealtime()
                PageState.Ready(page, result.url, ++loads)
            }
            is RaceResult.Lost -> PageState.Unreachable(page, result.attempts)
        }
    }

    private companion object {
        const val QUICK_FAILURE_MILLIS = 5_000L
    }
}
