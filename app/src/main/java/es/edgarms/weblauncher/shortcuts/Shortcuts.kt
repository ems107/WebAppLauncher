package es.edgarms.weblauncher.shortcuts

import android.content.Context
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import es.edgarms.weblauncher.R
import es.edgarms.weblauncher.icons.PageIcons
import es.edgarms.weblauncher.model.Config
import es.edgarms.weblauncher.model.Page
import es.edgarms.weblauncher.web.WebActivity

/** Shortcuts use the page id as theirs, so every call below can find a page's shortcut again. */
object Shortcuts {
    private const val TAG = "Shortcuts"

    /**
     * Asks the launcher to put [page] on the home screen; the launcher shows its
     * own confirmation. Returns false when this launcher cannot pin at all.
     */
    fun requestPin(context: Context, page: Page): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut(context, page, rank = 0), null)
    }

    /**
     * Publishes the pages as the shortcuts shown on a long press of the app
     * icon, and refreshes pinned ones, so a renamed page or a new icon reaches
     * the home screen too. Never fails a save: a shortcut problem is only logged.
     */
    fun sync(context: Context, config: Config) {
        try {
            val shortcuts = config.pages.mapIndexed { index, page -> shortcut(context, page, index) }
            val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts.take(max))
            ShortcutManagerCompat.updateShortcuts(context, shortcuts)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not publish shortcuts", e)
        }
    }

    /** A deleted page's pinned shortcut stays on the home screen, greyed out, saying why. */
    fun disable(context: Context, pageId: String) {
        try {
            ShortcutManagerCompat.disableShortcuts(
                context,
                listOf(pageId),
                context.getString(R.string.shortcut_page_deleted),
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not disable the shortcut of $pageId", e)
        }
    }

    private fun shortcut(context: Context, page: Page, rank: Int): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, page.id)
            .setShortLabel(page.name.ifBlank { "?" })
            .setLongLabel(page.name.ifBlank { "?" })
            .setIcon(IconCompat.createWithAdaptiveBitmap(PageIcons.bitmap(context, page)))
            .setIntent(WebActivity.intent(context, page.id))
            .setRank(rank)
            .build()
}
