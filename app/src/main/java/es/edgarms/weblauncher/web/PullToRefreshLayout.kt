package es.edgarms.weblauncher.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Pull-to-refresh around a page, decided the way Chrome decides it: the page
 * gets the drag first, and only a drag whose very first move nothing on the
 * page used is a pull.
 *
 * Nothing outside the page can tell what would use a drag -- an element
 * scrolled down, a `touch-action` that keeps it for a script, a
 * `preventDefault`, an iframe -- so nothing here tries to. Chromium says it by
 * overscrolling: it reports the part of a scroll nothing on the page took.
 * That report only decides; the spinner then follows the finger as this
 * layout's own gesture, because the reports themselves arrive late and in
 * chunks and a spinner driven by them jumps.
 */
@SuppressLint("ViewConstructor")
class PullToRefreshLayout(context: Context, private val webView: OverscrollWebView) : SwipeRefreshLayout(context) {
    private enum class Gesture {
        /** The finger just landed: the layout must see it, or it can never take the gesture later. */
        LANDING,

        /** The page has the drag, and has not yet said whether it used it. */
        UNDECIDED,

        /** Nothing on the page used the drag's first move: the rest of it is a pull. */
        PULL,

        /** The page used the drag, or it went upwards first. */
        PAGE,
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var gesture = Gesture.PAGE
    private var downY = 0f

    /** When the finger first moved down far enough to scroll, or 0 while it has not. */
    private var scrollBeganAt = 0L

    init {
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnChildScrollUpCallback { _, _ -> gesture != Gesture.LANDING && gesture != Gesture.PULL }
        webView.onOverscrollTop = ::onPageOverscrolledTop
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.y
                scrollBeganAt = 0L
                gesture = Gesture.LANDING
                val handled = super.dispatchTouchEvent(event)
                if (gesture == Gesture.LANDING) gesture = Gesture.UNDECIDED
                return handled
            }
            MotionEvent.ACTION_MOVE -> if (gesture == Gesture.UNDECIDED && scrollBeganAt == 0L) {
                when {
                    event.y - downY > touchSlop -> scrollBeganAt = event.eventTime
                    downY - event.y > touchSlop -> gesture = Gesture.PAGE
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = super.dispatchTouchEvent(event)
                gesture = Gesture.PAGE
                return handled
            }
        }
        return super.dispatchTouchEvent(event)
    }

    /**
     * The page reported a scroll past its top that nothing on it took. Right as
     * the drag began, that means nothing would; later, the drag spent a while
     * scrolling something first and only ran out of it now.
     */
    private fun onPageOverscrolledTop() {
        if (gesture != Gesture.UNDECIDED || scrollBeganAt == 0L) return
        val prompt = SystemClock.uptimeMillis() - scrollBeganAt < FIRST_SCROLL_MILLIS
        gesture = if (prompt) Gesture.PULL else Gesture.PAGE
    }

    private companion object {
        /** How long the page may take to report the first move of a drag as overscroll. */
        const val FIRST_SCROLL_MILLIS = 150L
    }
}

/** A WebView that says when the page overscrolls past its top, which is how Chromium reports a drag nothing used. */
class OverscrollWebView(context: Context) : WebView(context) {
    var onOverscrollTop: (() -> Unit)? = null

    override fun overScrollBy(
        deltaX: Int,
        deltaY: Int,
        scrollX: Int,
        scrollY: Int,
        scrollRangeX: Int,
        scrollRangeY: Int,
        maxOverScrollX: Int,
        maxOverScrollY: Int,
        isTouchEvent: Boolean,
    ): Boolean {
        if (deltaY < 0) onOverscrollTop?.invoke()
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent)
    }
}
