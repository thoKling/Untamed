package dev.survivaloverhaul.launcher.infrastructure.filesystem

import java.nio.file.Path
import kotlin.io.path.createDirectories

/**
 * Where the launcher keeps its own files.
 *
 * Deliberately not user-configurable: settings and logs have to be findable by
 * someone helping with a support request, and a configurable settings location
 * is a setting that has nowhere to live.
 */
object LauncherDirectories {

    private const val WINDOWS_FOLDER = "SurvivalOverhaul"
    private const val UNIX_FOLDER = "survival-overhaul"

    val home: Path by lazy {
        when (HostSystem.operatingSystem) {
            OperatingSystem.WINDOWS ->
                Path.of(System.getenv("APPDATA") ?: userHome().toString(), WINDOWS_FOLDER)

            OperatingSystem.MAC_OS ->
                userHome().resolve("Library/Application Support/$WINDOWS_FOLDER")

            else -> {
                val dataHome = System.getenv("XDG_DATA_HOME")
                if (dataHome.isNullOrBlank()) {
                    userHome().resolve(".local/share/$UNIX_FOLDER")
                } else {
                    Path.of(dataHome, UNIX_FOLDER)
                }
            }
        }
    }

    val settingsFile: Path get() = home.resolve("settings.json")

    val logDirectory: Path get() = home.resolve("logs")

    /** Where the game is installed unless the user chooses somewhere else. */
    val defaultInstallationDirectory: Path get() = home.resolve("game")

    fun ensureHome(): Path = home.createDirectories()

    private fun userHome(): Path = Path.of(System.getProperty("user.home"))
}
