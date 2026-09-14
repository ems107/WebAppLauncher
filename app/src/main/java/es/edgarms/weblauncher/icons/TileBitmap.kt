package es.edgarms.weblauncher.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import es.edgarms.weblauncher.model.Tile

object TileBitmap {
    /**
     * A full-bleed square for an adaptive icon. The launcher crops the edges to
     * its own shape, so the initial is kept small enough to sit in the middle.
     */
    fun render(name: String, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Tile.color(name))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(Tile.initial(name), size / 2f, baseline, paint)
        return bitmap
    }
}
