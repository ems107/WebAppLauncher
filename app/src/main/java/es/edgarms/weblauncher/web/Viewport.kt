package es.edgarms.weblauncher.web

import kotlin.math.roundToInt

/**
 * How wide the page is laid out -- which is the only question the header's zoom
 * asks, and the reason it is not a pinch.
 *
 * **The header re-lays the page out; the pinch magnifies it. They are separate
 * and neither does the other's job.** Page zoom changes how many CSS pixels the
 * window is worth, so the page reflows at the new size and still fits across; a
 * pinch leaves the layout alone and makes a region bigger. Both are wanted, and
 * conflating them is how zooming in ends up meaning "now scroll sideways".
 *
 * So the width is always divided by the zoom and the scale is always whatever
 * makes that width fit the view exactly. There is no second rule for desktop
 * mode: the switch only decides the width the page would be laid out at with the
 * zoom at 100.
 *
 * - **off** -- the view's own width, so 100 % is precisely the
 *   `width=device-width, initial-scale=1` a page asks for.
 * - **on** -- 1280 px, so 100 % is the desktop layout scaled to fit.
 *
 * Zooming in inside desktop mode narrows the layout, so past some zoom a page's
 * own breakpoints hand it back to its phone layout at a larger size. That is
 * what reflowing means, and a desktop browser does the same to a narrow window.
 */
object Viewport {
    /** The width "desktop" lays the page out at, whatever the phone's own is. */
    const val DESKTOP_WIDTH = 1280

    /** Layout zoom, in per cent of the width the mode above would use on its own. */
    const val ZOOM_MIN = 30
    const val ZOOM_MAX = 300
    const val ZOOM_STEP = 10
    const val ZOOM_DEFAULT = 100

    /** How far a pinch may go, either way: how close somebody looks is not the app's business. */
    const val PINCH_MIN = 0.05f
    const val PINCH_MAX = 10f

    data class Layout(val width: Int, val scale: Float)

    /** @param base the width the view has, in CSS pixels. */
    fun layoutFor(base: Int, desktop: Boolean, zoom: Int): Layout {
        val natural = if (desktop) DESKTOP_WIDTH else base
        val width = (natural / (zoom / 100f)).roundToInt().coerceAtLeast(1)
        return Layout(width, base.toFloat() / width)
    }

    /**
     * Replaces the page's own viewport tag rather than fighting it, because
     * `useWideViewPort` is only consulted when a page declares none.
     *
     * With [pin], the scale is pinned first and freed a beat later. Chromium
     * honours `initial-scale` when the viewport changes, but it will not pull a
     * page back from a scale the reader chose by pinching; pinning both ends
     * forces the new layout zoom, and relaxing them hands the pinch back at the
     * scale just asserted. The pending relax is cancelled first, and that is not
     * tidiness: each injection closes over ITS width and scale, so a timer from
     * the previous one firing after this one writes the old viewport back.
     *
     * **A page that has just loaded must NOT be pinned.** Pinned at any scale
     * but its own while a load settles, Chromium keeps the page's scale limits
     * at that single value for good: freeing them afterwards, or any later
     * change to the tag, never gives the pinch back until the page is closed.
     * Seen on the DT50 (Chrome 138) after a reload or a navigation at 110 %.
     * A fresh load has no pinch to pull back from anyway.
     */
    fun script(base: Int, desktop: Boolean, zoom: Int, pin: Boolean): String {
        val (width, scale) = layoutFor(base, desktop, zoom)
        val free = "width=$width, initial-scale=$scale, minimum-scale=$PINCH_MIN, maximum-scale=$PINCH_MAX"
        val apply = if (pin) {
            """
              if (window.__wlFree) clearTimeout(window.__wlFree);
              m.setAttribute('content', 'width=$width, initial-scale=$scale, minimum-scale=$scale, maximum-scale=$scale');
              window.__wlFree = setTimeout(function () { m.setAttribute('content', '$free'); }, 50);
            """
        } else {
            """
              if (window.__wlFree) clearTimeout(window.__wlFree);
              m.setAttribute('content', '$free');
            """
        }
        return """
            (function () {
              var m = document.querySelector('meta[name="viewport"]');
              if (!m) { m = document.createElement('meta'); m.setAttribute('name', 'viewport'); (document.head || document.documentElement).appendChild(m); }
              $apply
            })();
        """.trimIndent()
    }
}
