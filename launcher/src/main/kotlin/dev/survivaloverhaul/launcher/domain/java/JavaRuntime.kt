package dev.survivaloverhaul.launcher.domain.java

/** Where a Java runtime was found, which decides how much it is trusted. */
enum class JavaSource {
    /** The path in settings. The user said so, so it is considered first. */
    CONFIGURED,

    /** `JAVA_HOME`. */
    JAVA_HOME,

    /** The JVM the launcher itself is running on. */
    LAUNCHER_RUNTIME,

    /** Found by looking where this platform installs Java. */
    INSTALLED,
}

/**
 * A Java runtime the launcher has actually confirmed, never one it assumes.
 *
 * [majorVersion] comes from running the executable and reading what it says
 * about itself, so a `java` on the path that is a wrapper, a stub or the wrong
 * version cannot be mistaken for a working runtime.
 */
data class JavaRuntime(
    val executable: String,
    val majorVersion: Int,
    val description: String,
    val source: JavaSource,
) {
    fun satisfies(requiredMajor: Int): Boolean = majorVersion >= requiredMajor
}

/**
 * The outcome of looking for a Java runtime for one version of the game.
 *
 * Three outcomes rather than a nullable runtime, because the launcher has three
 * different things to say: this is the one it will use, the one you chose will
 * not run this version, and there is no Java on this machine that will.
 */
sealed interface JavaSelection {

    data class Selected(val runtime: JavaRuntime) : JavaSelection

    /** The configured executable exists but is too old for the game. */
    data class Unsuitable(
        val runtime: JavaRuntime,
        val requiredMajor: Int,
        val alternative: JavaRuntime?,
    ) : JavaSelection

    data class None(val requiredMajor: Int, val searched: Int) : JavaSelection

    /**
     * One line saying which runtime would be used and why, or what is wrong.
     *
     * It lives here rather than in a screen so that the settings panel and a
     * pasted diagnostics report cannot drift into describing the same selection
     * two different ways.
     */
    fun describe(): String = when (this) {
        is Selected -> "Java ${runtime.majorVersion} at ${runtime.executable}"

        is Unsuitable -> "The chosen Java is ${runtime.majorVersion}, but " +
            "Java $requiredMajor is required" +
            (alternative?.let { ". Java ${it.majorVersion} at ${it.executable} would work" } ?: "")

        is None -> if (searched == 0) {
            "No Java runtime found. Java $requiredMajor is required."
        } else {
            "None of the $searched Java runtimes found is Java $requiredMajor or newer"
        }
    }
}

/**
 * Picks the runtime to launch with. Pure, so the preference order is a thing
 * that can be read and tested rather than behaviour spread across a search.
 *
 * The order: an explicitly configured runtime that will work always wins, since
 * the user chose it deliberately. Otherwise the exact major version the version
 * document asks for is preferred over a newer one, because that is the runtime
 * Mojang tested this release against. Among equals, the earlier source wins.
 */
object JavaRuntimes {

    fun select(candidates: List<JavaRuntime>, requiredMajor: Int): JavaSelection {
        val configured = candidates.firstOrNull { it.source == JavaSource.CONFIGURED }
        if (configured != null && configured.satisfies(requiredMajor)) {
            return JavaSelection.Selected(configured)
        }

        val usable = candidates.filter { it.satisfies(requiredMajor) }
        val best = usable.minWithOrNull(
            compareBy({ it.majorVersion != requiredMajor }, { it.majorVersion }, { it.source }),
        )

        return when {
            configured != null -> JavaSelection.Unsuitable(configured, requiredMajor, best)
            best != null -> JavaSelection.Selected(best)
            else -> JavaSelection.None(requiredMajor, candidates.size)
        }
    }
}
