package es.edgarms.weblauncher.update

/** A release version, X.Y.Z. Tags carry a leading "v"; the app's own versionName does not. */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("""v?(\d{1,4})\.(\d{1,4})\.(\d{1,4})""")

        /** Null for anything that is not exactly X.Y.Z, with or without the "v". */
        fun parse(text: String): Version? {
            val (major, minor, patch) = pattern.matchEntire(text.trim())?.destructured ?: return null
            return Version(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
