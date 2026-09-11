package dev.survivaloverhaul.launcher.domain.launch

/**
 * Where one launch reads its files from.
 *
 * Paths are text rather than `Path` for the same reason the settings are: this
 * layer stays off the filesystem, and the infrastructure layer that has an
 * installation directory is what fills these in.
 *
 * [clientJar] is Minecraft's own jar, not the Fabric profile's. A Fabric
 * profile has no client download of its own; it runs the vanilla jar under a
 * different main class, and a launcher that looks for a jar beside the Fabric
 * profile will not find one.
 */
data class LaunchLayout(
    val gameDirectory: String,
    val assetsDirectory: String,
    val librariesDirectory: String,
    val nativesDirectory: String,
    val clientJar: String,
    val pathSeparator: String,
    val loggingConfiguration: String? = null,
)

/**
 * A command line that will start the game, and the two things about it that are
 * not arguments: where the natives have to be unpacked, and which of its words
 * must never be shown to anyone.
 *
 * Built whole by a pure planner and handed to the process launcher as-is. The
 * launcher adds nothing to it, which is what makes "what would this launch do"
 * a question that can be answered without launching anything.
 */
data class LaunchPlan(
    val executable: String,
    val arguments: List<String>,
    val workingDirectory: String,
    val nativesDirectory: String,
    val nativeArchives: List<String> = emptyList(),
    val secrets: Set<String> = emptySet(),
) {
    val command: List<String> get() = listOf(executable) + arguments

    /**
     * The command as one line with every secret removed, which is the only form
     * of it that may be logged, copied or pasted into a support thread.
     *
     * Replacement is by value rather than by which argument it landed in: an
     * access token that a template put inside a longer argument is still an
     * access token.
     */
    fun describe(): String = command.joinToString(" ") { part ->
        secrets.filter { it.isNotBlank() }
            .fold(part) { text, secret -> text.replace(secret, HIDDEN) }
    }

    companion object {
        const val HIDDEN = "<hidden>"
    }
}
