package es.edgarms.weblauncher.web

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import es.edgarms.weblauncher.BuildConfig
import es.edgarms.weblauncher.WebLauncherApp
import es.edgarms.weblauncher.icons.PageIcons
import es.edgarms.weblauncher.model.Page
import es.edgarms.weblauncher.ui.theme.WebLauncherTheme
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * One page, full screen, with no address bar. Each page runs as its own task,
 * with its name in recents, so it behaves like an app of its own.
 */
class WebActivity : ComponentActivity() {
    private val viewModel: PageViewModel by viewModels()
    private lateinit var webView: WebView
    private val chrome = PageChrome()

    private var loadedId = 0
    private var loadedUrl: String? = null
    private var clearHistoryWhenLoaded = false
    private var describedPage: Page? = null

    /** Back walks the page's history first, and only then leaves. */
    private val historyBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = webView.goBack()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        // Without cookies the session is gone on every exit; for the Jackery that is its PIN.
        CookieManager.getInstance().setAcceptCookie(true)
        webView = createWebView()
        onBackPressedDispatcher.addCallback(this, historyBack)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    state.page?.let(::describeTask)
                    if (state is PageState.Ready && state.loadId != loadedId) {
                        // A second load means another address won: the old history leads nowhere.
                        clearHistoryWhenLoaded = loadedId != 0
                        loadedId = state.loadId
                        loadedUrl = state.url
                        webView.loadUrl(state.url)
                    }
                }
            }
        }
        // The header's zoom and desktop switch re-lay the page out at once, without
        // reloading it. Not the starting value: that is onPageFinished's to apply.
        lifecycleScope.launch {
            snapshotFlow { chrome.desktop to chrome.zoom }.drop(1).collect { applyViewport(pin = true) }
        }

        setContent {
            WebLauncherTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val config by (application as WebLauncherApp).pages.config.collectAsStateWithLifecycle()
                val page = state.page?.let { config?.page(it.id) ?: it }
                PageScreen(
                    state,
                    page,
                    webView,
                    chrome,
                    onReload = webView::reload,
                    onRetry = viewModel::retry,
                    onClose = ::finish,
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    /** The page's name and icon in recents, instead of the launcher's. */
    private fun describeTask(page: Page) {
        if (page == describedPage) return
        describedPage = page
        @Suppress("DEPRECATION") // The replacement needs API 33; this works everywhere.
        setTaskDescription(ActivityManager.TaskDescription(page.name, PageIcons.bitmap(this, page)))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(this).apply {
        // MATCH_PARENT, not the WRAP_CONTENT a bare view gets: a viewport with no
        // defined height resolves every percentage height inside it to zero.
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled = true
        // Off by default: the page loads, looks fine, and silently loses all its state.
        settings.domStorageEnabled = true
        // Only consulted when a page declares no viewport; the header's zoom writes one anyway.
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        // The pinch, the other half of the header's zoom: without the controls on,
        // the scale range the viewport offers is never offered to anybody.
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // The layout width comes from the view, so rotating needs the viewport again.
        // The first layout is not a rotation: it happens while the page is loading.
        addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            val old = oldRight - oldLeft
            if (old > 0 && right - left != old) applyViewport(pin = true)
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                chrome.progress = newProgress
            }
        }

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val base = loadedUrl ?: return false
                if (!request.isForMainFrame || Origins.same(request.url.toString(), base)) return false
                openElsewhere(request.url)
                return true
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                historyBack.isEnabled = view.canGoBack()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                // Never pinned here: see Viewport.script.
                applyViewport(pin = false)
                if (clearHistoryWhenLoaded) {
                    clearHistoryWhenLoaded = false
                    view.clearHistory()
                    historyBack.isEnabled = false
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                viewModel.onMainFrameError(error.description.toString())
            }
        }
    }

    /**
     * Lays the page out at the header's width and zoom. The width is measured off
     * the view rather than asked of `window.screen`, which answers for the whole
     * display; before the first layout there is nothing to measure, so the screen
     * stands in.
     */
    private fun applyViewport(pin: Boolean) {
        val metrics = resources.displayMetrics
        val base = (webView.width / metrics.density).roundToInt()
            .takeIf { it > 0 }
            ?: (metrics.widthPixels / metrics.density).roundToInt()
        webView.evaluateJavascript(Viewport.script(base, chrome.desktop, chrome.zoom, pin), null)
    }

    /** Links to anything but this page's own server go to whatever the system opens them with. */
    private fun openElsewhere(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            // Nothing on the phone handles it; staying on the page is the least surprising answer.
        }
    }

    companion object {
        const val EXTRA_PAGE_ID = "pageId"

        /**
         * The data URI is what makes each page a separate document task: with
         * `documentLaunchMode="intoExisting"`, intents that differ only in extras
         * would all land in the same task.
         */
        fun intent(context: Context, pageId: String): Intent =
            Intent(context, WebActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.Builder().scheme("weblauncher").authority("page").appendPath(pageId).build())
                .putExtra(EXTRA_PAGE_ID, pageId)
    }
}

private val PageState.page: Page?
    get() = when (this) {
        is PageState.Probing -> page
        is PageState.Ready -> page
        is PageState.Unreachable -> page
        PageState.Loading, PageState.Missing -> null
    }
