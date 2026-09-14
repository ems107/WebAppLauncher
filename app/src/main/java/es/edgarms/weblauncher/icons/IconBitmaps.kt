package es.edgarms.weblauncher.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/** Turns downloaded or picked images into squares a launcher can use as an adaptive icon. */
object IconBitmaps {
    /**
     * How much of an adaptive icon an ordinary icon may cover. Launchers crop
     * everything outside the middle 66/108 of it; a little less keeps a margin.
     */
    private const val SAFE_FRACTION = 0.58f

    /** Centre-crops [source] to a square that fills the whole icon: for maskable icons and photos. */
    fun fullBleed(source: Bitmap, size: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            source,
            Rect(left, top, left + side, top + side),
            Rect(0, 0, size, size),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return out
    }

    /** Fits an ordinary icon, whole, inside the part no launcher crops, on white. */
    fun padded(source: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val scale = size * SAFE_FRACTION / maxOf(source.width, source.height)
        val width = source.width * scale
        val height = source.height * scale
        val left = (size - width) / 2
        val top = (size - height) / 2
        canvas.drawBitmap(source, null, RectF(left, top, left + width, top + height), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** The biggest power-of-two reduction that still leaves both sides at least [target] pixels. */
    fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
        return sample
    }
}
