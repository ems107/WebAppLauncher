package es.edgarms.weblauncher.web

import android.annotation.SuppressLint
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
import es.edgarms.weblauncher.BuildConfig
import es.edgarms.weblauncher.ui.theme.WebLauncherTheme
import kotlinx.coroutines.launch

/** One page, full screen, with no address bar. */
class WebActivity : ComponentActivity() {
    private val viewModel: PageViewModel by viewModels()
    private lateinit var webView: WebView

    private var loadedId = 0
    private var loadedUrl: String? = null
    private var clearHistoryWhenLoaded = false

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
                PageScreen(state, webView, onRetry = viewModel::retry, onClose = ::finish)
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
                if (clearHistoryWhenLoaded) {
                    clearHistoryWhenLoaded = false
                    view.clearHistory()
                    historyBack.isEnabled = false
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) viewModel.onMainFrameError(error.description.toString())
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

        fun intent(context: Context, pageId: String): Intent =
            Intent(context, WebActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_PAGE_ID, pageId)
    }
}
