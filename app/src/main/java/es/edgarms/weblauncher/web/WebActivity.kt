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
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import es.edgarms.weblauncher.BuildConfig
import es.edgarms.weblauncher.icons.PageIcons
import es.edgarms.weblauncher.model.Page
import es.edgarms.weblauncher.ui.theme.WebLauncherTheme
import kotlinx.coroutines.launch

/**
 * One page, full screen, with no address bar. Each page runs as its own task,
 * with its name in recents, so it behaves like an app of its own.
 */
class WebActivity : ComponentActivity() {
    private val viewModel: PageViewModel by viewModels()
    private lateinit var webView: WebView
    private lateinit var refresher: SwipeRefreshLayout

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
        refresher = SwipeRefreshLayout(this).apply {
            addView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            setOnRefreshListener { webView.reload() }
            // Pulling down refreshes only from the top of the page; anywhere else it scrolls.
            setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
        }
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

        setContent {
            WebLauncherTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                PageScreen(state, refresher, onRetry = viewModel::retry, onClose = ::finish)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        (refresher.parent as? ViewGroup)?.removeView(refresher)
        refresher.removeView(webView)
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
        settings.javaScriptEnabled = true
        // Off by default: the page loads, looks fine, and silently loses all its state.
        settings.domStorageEnabled = true

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
                refresher.isRefreshing = false
                if (clearHistoryWhenLoaded) {
                    clearHistoryWhenLoaded = false
                    view.clearHistory()
                    historyBack.isEnabled = false
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                refresher.isRefreshing = false
                viewModel.onMainFrameError(error.description.toString())
            }
        }
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
