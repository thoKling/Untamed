package dev.survivaloverhaul.launcher.domain.settings

/** What the launcher does with its own window once the game is running. */
enum class LaunchBehaviour {
    KEEP_OPEN,
    HIDE,
    CLOSE,
}

/**
 * Everything the user can change. Paths are held as text rather than as
 * `Path`, so this layer stays free of the filesystem and stays trivially
 * serialisable; the infrastructure layer resolves them.
 *
 * Values are never trusted as read. [sanitised] is applied on load and on save,
 * so a hand-edited settings file cannot put the launcher into a state its own
 * UI could not produce.
 */
data class LauncherSettings(
    val installationDirectory: String,
    val javaExecutable: String? = null,
    val memoryMegabytes: Int = DEFAULT_MEMORY_MB,
    val windowWidth: Int = DEFAULT_WIDTH,
    val windowHeight: Int = DEFAULT_HEIGHT,
    val fullscreen: Boolean = false,
    val extraJvmArguments: String = "",
    val automaticUpdates: Boolean = true,
    val launchBehaviour: LaunchBehaviour = LaunchBehaviour.HIDE,
    /**
     * Where to fetch the release manifest from, or null for the address the
     * launcher shipped with. Overriding it is how a build can be tested against
     * a staging manifest without a new launcher.
     */
    val manifestUrl: String? = null,
) {
    fun sanitised(fallbackDirectory: String): LauncherSettings = copy(
        installationDirectory = installationDirectory.trim().ifBlank { fallbackDirectory },
        javaExecutable = javaExecutable?.trim()?.ifBlank { null },
        memoryMegabytes = memoryMegabytes.coerceIn(MINIMUM_MEMORY_MB, MAXIMUM_MEMORY_MB),
        windowWidth = windowWidth.coerceIn(MINIMUM_WIDTH, MAXIMUM_DIMENSION),
        windowHeight = windowHeight.coerceIn(MINIMUM_HEIGHT, MAXIMUM_DIMENSION),
        extraJvmArguments = extraJvmArguments.trim(),
        // Deliberately not checked for https here. Silently dropping a bad
        // address would leave the user with a launcher quietly using a
        // different manifest than the one on screen; the transport refuses it
        // and the fallback says why.
        manifestUrl = manifestUrl?.trim()?.ifBlank { null },
    )

    /**
     * The extra JVM arguments as a list. Split on whitespace only: the launcher
     * passes arguments to the JVM as a list rather than through a shell, so
     * there is nothing here for quoting to protect against.
     */
    fun extraJvmArgumentList(): List<String> =
        extraJvmArguments.split(' ', '\t', '\n').filter { it.isNotBlank() }

    companion object {
        const val MINIMUM_MEMORY_MB = 1024
        const val MAXIMUM_MEMORY_MB = 32768
        const val DEFAULT_MEMORY_MB = 4096
        const val MINIMUM_WIDTH = 640
        const val MINIMUM_HEIGHT = 480
        const val MAXIMUM_DIMENSION = 7680
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720

        fun defaults(installationDirectory: String): LauncherSettings =
            LauncherSettings(installationDirectory = installationDirectory)
    }
}
