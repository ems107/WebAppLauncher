package es.edgarms.weblauncher.icons

import android.net.Uri

/** What the editor did to a page's icon. */
sealed interface IconEdit {
    data object Keep : IconEdit

    /** Back to automatic: the site is asked for its icon again the next time the page opens. */
    data object Reset : IconEdit

    data class Picked(val uri: Uri) : IconEdit
}
