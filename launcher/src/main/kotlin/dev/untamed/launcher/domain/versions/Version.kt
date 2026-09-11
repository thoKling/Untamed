package dev.untamed.launcher.domain.versions

/**
 * An ordered version number, tolerant enough for every version string this
 * launcher meets: Minecraft's `26.2`, Fabric Loader's `0.19.5`, Fabric API's
 * `0.160.0+26.2` and the mod's own `0.1.0`.
 *
 * Parsing never fails. A version that cannot be understood still compares
 * consistently and still remembers the text it came from, because a manifest
 * written by someone else must not be able to crash the launcher.
 */
data class Version(
    val numbers: List<Int>,
    val preRelease: String?,
    val build: String?,
    val raw: String,
) : Comparable<Version> {

    /** True when the text was fully understood as a dotted number sequence. */
    val isWellFormed: Boolean get() = numbers.isNotEmpty()

    override fun compareTo(other: Version): Int {
        val length = maxOf(numbers.size, other.numbers.size)
        for (index in 0 until length) {
            val mine = numbers.getOrElse(index) { 0 }
            val theirs = other.numbers.getOrElse(index) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }
        // Build metadata is deliberately ignored, as in semantic versioning:
        // `0.160.0+26.2` and `0.160.0+26.3` are the same release of the library.
        return comparePreRelease(preRelease, other.preRelease)
    }

    override fun toString(): String = raw

    companion object {
        fun parse(raw: String): Version {
            val text = raw.trim()
            val withoutBuild = text.substringBefore('+')
            val build = if ('+' in text) text.substringAfter('+').ifBlank { null } else null
            val core = withoutBuild.substringBefore('-')
            val preRelease =
                if ('-' in withoutBuild) withoutBuild.substringAfter('-').ifBlank { null } else null

            val numbers = mutableListOf<Int>()
            for (segment in core.split('.')) {
                val number = segment.trim().toIntOrNull() ?: break
                numbers += number
            }
            return Version(numbers, preRelease, build, text)
        }

        /**
         * Whether [candidate] satisfies a requirement of at least [minimum].
         * Used for the launcher's own minimum version in a remote manifest.
         */
        fun atLeast(candidate: String, minimum: String): Boolean =
            parse(candidate) >= parse(minimum)

        private fun comparePreRelease(mine: String?, theirs: String?): Int = when {
            mine == null && theirs == null -> 0
            // A release outranks any pre-release of the same numbers.
            mine == null -> 1
            theirs == null -> -1
            else -> mine.compareTo(theirs)
        }
    }
}
