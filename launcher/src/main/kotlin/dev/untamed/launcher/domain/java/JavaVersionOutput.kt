package dev.untamed.launcher.domain.java

/**
 * Reads what `java -version` says about itself.
 *
 * The output is a human-facing banner rather than a contract, and it differs
 * between vendors and between eras: `1.8.0_402` for Java 8, `25.0.1` since 9.
 * Both are understood, and anything else produces null rather than a guess,
 * because a runtime the launcher cannot identify is one it must not launch the
 * game with.
 */
object JavaVersionOutput {

    data class Details(val majorVersion: Int, val description: String)

    fun parse(output: String): Details? {
        val version = QUOTED_VERSION.find(output)?.groupValues?.get(1) ?: return null
        val major = majorOf(version) ?: return null
        // The second line names the vendor and build, which is what makes two
        // runtimes of the same major version distinguishable in the UI.
        val banner = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
        val description = banner.elementAtOrNull(1) ?: banner.firstOrNull() ?: version
        return Details(major, description)
    }

    private fun majorOf(version: String): Int? {
        val parts = version.split('.', '_', '-')
        val first = parts.firstOrNull()?.toIntOrNull() ?: return null
        // Java 8 and earlier report 1.8.0; the major version is the second part.
        if (first == 1) return parts.getOrNull(1)?.toIntOrNull()
        return first.takeIf { it > 1 }
    }

    private val QUOTED_VERSION = Regex("version \"([^\"]+)\"")
}
