package es.edgarms.weblauncher.web

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import kotlin.math.abs

/**
 * A WebView that hands its pull past the top to the pull-to-refresh around it.
 *
 * Its own scroll position cannot say whether a page is at the top: many pages
 * scroll an element of their own and leave the document still, so the view's
 * `scrollY` stays 0 however far down they are. Only the page knows, and it
 * tells by overscrolling: Chromium reports the part of a drag nothing on the
 * page could scroll. That part, and only while a finger is down, is passed to
 * the parent as a nested scroll -- which is what moves the spinner.
 *
 * Like Chrome, only a drag that starts with the page already at the top pulls:
 * one that scrolls the page up and then runs out of page does not refresh it.
 */
@SuppressLint("ViewConstructor")
class PageWebView(context: Context) : WebView(context) {
    /**
     * True only while an overscroll is being handed to the parent. The parent
     * must think the page can always scroll up, or it steals every downward
     * drag before the page has seen it; this is the one moment it must not.
     */
    var handingOverPull = false
        private set

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var touching = false
    private var downY = 0f
    private var lastY = 0f

    /** When the finger moved far enough to scroll, or 0 while it has not. */
    private var scrollBeganAt = 0L

    /** Whether this gesture may pull the spinner, once the page's first overscroll has decided it. */
    private var mayPull: Boolean? = null

    /** How far the spinner has been pulled in this gesture, in pixels. */
    private var pulled = 0

    init {
        isNestedScrollingEnabled = true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touching = true
                pulled = 0
                downY = event.y
                lastY = event.y
                scrollBeganAt = 0L
                mayPull = null
                startNestedScroll(View.SCROLL_AXIS_VERTICAL)
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = (lastY - event.y).toInt()
                lastY = event.y
                if (scrollBeganAt == 0L && abs(event.y - downY) > touchSlop) scrollBeganAt = event.eventTime
                if (pulled > 0) {
                    // The spinner is out: the finger drives it, not the page, until it is back.
                    if (dy > 0) dispatchNestedPreScroll(0, dy, IntArray(2), null) else handOver(dy)
                    pulled = (pulled - dy).coerceAtLeast(0)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = super.onTouchEvent(event)
                touching = false
                stopNestedScroll()
                return handled
            }
        }
        return super.onTouchEvent(event)
    }

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
        // A fling that reaches the top overscrolls too; only a finger pulls the spinner.
        if (touching && deltaY < 0 && decideMayPull()) {
            handOver(deltaY)
            pulled -= deltaY
        }
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent)
    }

    /**
     * A page at the top overscrolls on the very first move of a drag. If the
     * first overscroll comes later than that, the drag spent a while scrolling
     * the page first, and it is not a pull.
     */
    private fun decideMayPull(): Boolean = mayPull ?: (
        scrollBeganAt == 0L || SystemClock.uptimeMillis() - scrollBeganAt < FIRST_OVERSCROLL_MILLIS
    ).also { mayPull = it }

    private fun handOver(dy: Int) {
        handingOverPull = true
        try {
            dispatchNestedScroll(0, 0, 0, dy, null)
        } finally {
            handingOverPull = false
        }
    }

    private companion object {
        /** How long a page at the top may take to report the first move as overscroll. */
        const val FIRST_OVERSCROLL_MILLIS = 150L
    }
}
