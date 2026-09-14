package es.edgarms.weblauncher.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import es.edgarms.weblauncher.data.Files
import es.edgarms.weblauncher.model.Page
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** Page icons as files in the app's storage, always stored as squares ready for an adaptive icon. */
object PageIcons {
    /** What Android keeps of a shortcut icon anyway ("Max icon dim" in `dumpsys shortcut`). */
    const val SIZE_PX = 192
    private const val DIR = "icons"

    /** The page's own icon, or its generated tile when it has none or the file is gone. */
    fun bitmap(context: Context, page: Page): Bitmap =
        load(context, page.iconPath) ?: TileBitmap.render(page.name, SIZE_PX)

    fun load(context: Context, iconPath: String?): Bitmap? =
        iconPath?.let { BitmapFactory.decodeFile(File(context.filesDir, it).path) }

    /**
     * Stores [bitmap] as the page's icon and returns its path. Every icon gets a
     * new file name, so nothing that loaded the old one by path keeps showing it.
     */
    fun save(context: Context, pageId: String, bitmap: Bitmap): String {
        val path = "$DIR/$pageId-${System.currentTimeMillis()}.png"
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        Files.writeAtomically(File(context.filesDir, path), bytes)
        return path
    }

    /** Deletes the page's icon files, all but [keep]. */
    fun prune(context: Context, pageId: String, keep: String? = null) {
        File(context.filesDir, DIR)
            .listFiles { file -> file.name.startsWith("$pageId-") }
            ?.filter { "$DIR/${it.name}" != keep }
            ?.forEach { it.delete() }
    }

    /** An image the user picked, cropped to a square. Null if it cannot be read. */
    fun decodePicked(context: Context, uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = IconBitmaps.sampleSize(bounds.outWidth, bounds.outHeight, SIZE_PX)
        }
        context.contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?.let { IconBitmaps.fullBleed(it, SIZE_PX) }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}
