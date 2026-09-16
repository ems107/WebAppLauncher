package es.edgarms.weblauncher.web

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Pull-to-refresh around a page, pulling only when the page is at the top.
 *
 * The WebView cannot say whether it is: many pages scroll an element of their
 * own and leave the document still, so the view's `scrollY` stays 0 however far
 * down they are. Only the page knows, so a script in it answers on every
 * `touchstart` whether anything under the finger is scrolled down. The gesture
 * itself stays this layout's own, which is what keeps the spinner following the
 * finger and lets it be pushed back to cancel.
 *
 * Like Chrome, only a drag that starts with the page at the top pulls.
 */
@SuppressLint("ViewConstructor")
class PullToRefreshLayout(context: Context, private val webView: WebView) : SwipeRefreshLayout(context) {
    private enum class Gesture {
        /** The finger just landed and the layout is deciding whether to follow it: it must. */
        LANDING,

        /** The page has not answered yet: nothing is pulled until it does. */
        ASKING,
        AT_TOP,
        SCROLLED,
    }

    @Volatile
    private var gesture = Gesture.ASKING
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f

    init {
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnChildScrollUpCallback { _, _ -> gesture != Gesture.LANDING && gesture != Gesture.AT_TOP }
        webView.addJavascriptInterface(Bridge(), BRIDGE)
    }

    /** Puts the script into the page on screen; loading it twice into one document is harmless. */
    fun watchPage() {
        webView.evaluateJavascript(SCRIPT, null)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                // The layout only follows a gesture whose landing it was allowed to see.
                gesture = Gesture.LANDING
                val intercepted = super.onInterceptTouchEvent(event)
                if (gesture == Gesture.LANDING) gesture = Gesture.ASKING
                return intercepted
            }
            // Scrolling the page down first makes the rest of the drag a scroll, not a pull.
            MotionEvent.ACTION_MOVE -> if (downY - event.y > touchSlop) gesture = Gesture.SCROLLED
        }
        return super.onInterceptTouchEvent(event)
    }

    private inner class Bridge {
        /** Called on the page's touchstart, from the WebView's own thread. */
        @JavascriptInterface
        fun touchStarted(atTop: Boolean) {
            if (gesture == Gesture.LANDING || gesture == Gesture.ASKING) {
                gesture = if (atTop) Gesture.AT_TOP else Gesture.SCROLLED
            }
        }
    }

    private companion object {
        const val BRIDGE = "WebLauncherPull"

        /** Anything scrolled down under the finger, or the document itself, means a pull would scroll instead. */
        val SCRIPT = """
            (() => {
              if (window.__webLauncherPull) return;
              window.__webLauncherPull = true;
              addEventListener('touchstart', event => {
                if (event.touches.length !== 1) return;
                const scrolled = window.scrollY > 0 ||
                  event.composedPath().some(node => node instanceof Element && node.scrollTop > 0);
                $BRIDGE.touchStarted(!scrolled);
              }, { capture: true, passive: true });
            })();
        """.trimIndent()
    }
}
