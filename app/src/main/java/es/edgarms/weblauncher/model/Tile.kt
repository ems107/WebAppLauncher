package es.edgarms.weblauncher.model

/** The generated look of a page that has no icon: its initial on a color derived from its name. */
object Tile {
    private val palette = longArrayOf(
        0xFF1E5AA8, 0xFF2E7D32, 0xFFC62828, 0xFF6A1B9A,
        0xFFEF6C00, 0xFF00838F, 0xFF5D4037, 0xFFAD1457,
    )

    fun initial(name: String): String =
        name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

    /** An ARGB color, the same for the same name on every run. */
    fun color(name: String): Int =
        palette[Math.floorMod(name.trim().lowercase().hashCode(), palette.size)].toInt()
}
