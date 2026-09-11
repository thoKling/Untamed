package dev.untamed.launcher.infrastructure.launch

import dev.untamed.launcher.application.launch.GameLaunchPlanner
import dev.untamed.launcher.application.launch.GameLauncher
import dev.untamed.launcher.application.launch.LaunchOutcome
import dev.untamed.launcher.application.launch.LaunchPreparation
import dev.untamed.launcher.branding.Branding
import dev.untamed.launcher.domain.account.PlayerSession
import dev.untamed.launcher.domain.game.GameConfiguration
import dev.untamed.launcher.domain.java.JavaRuntime
import dev.untamed.launcher.domain.launch.LaunchLayout
import dev.untamed.launcher.domain.minecraft.HostPlatform
import dev.untamed.launcher.domain.minecraft.VersionProfile
import dev.untamed.launcher.domain.settings.LauncherSettings
import dev.untamed.launcher.infrastructure.filesystem.InstallationPaths
import dev.untamed.launcher.infrastructure.logging.LauncherLog
import dev.untamed.launcher.infrastructure.minecraft.VersionDocuments
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Starts the installed game as a child process and waits for it to end.
 *
 * Everything decided here is decided by [GameLaunchPlanner], which is pure. This
 * class does only what a pure function cannot: read the version documents back
 * off disk, unpack the natives, start the process, and keep its output.
 *
 * The version documents are read from the installation rather than remembered
 * from the install run. They are the files that were verified against Mojang's
 * digests, they are where a Fabric installation says which main class to use,
 * and reading them back means a launch reflects what is actually installed.
 */
class ProcessGameLauncher(
    private val paths: InstallationPaths,
    private val settings: LauncherSettings,
    private val platform: HostPlatform,
    private val outputFile: Path,
    private val log: LauncherLog,
) : GameLauncher {

    override fun run(
        game: GameConfiguration,
        session: PlayerSession,
        java: JavaRuntime,
        onStarted: (String) -> Unit,
    ): LaunchOutcome {
        val versionId = game.fabricProfileId
        val profile = resolved(versionId)
            ?: return LaunchOutcome.Refused(
                "The installed version files could not be read.",
                "no usable version document for $versionId under ${paths.versionsDirectory}",
            )

        val natives = paths.versionNatives(versionId)
        val preparation = GameLaunchPlanner.plan(
            profile = profile,
            versionId = versionId,
            session = session,
            java = java,
            settings = settings,
            layout = layoutFor(game, profile, natives),
            platform = platform,
            launcherName = Branding.LAUNCHER_BRAND,
            launcherVersion = Branding.LAUNCHER_VERSION,
        )

        val ready = when (preparation) {
            is LaunchPreparation.Refused -> return LaunchOutcome.Refused(
                "The installation is missing something the game needs to start.",
                preparation.problems.joinToString("; "),
            )

            is LaunchPreparation.Ready -> preparation
        }
        ready.warnings.forEach { log.warn(CATEGORY, it) }

        val nativeProblems = NativeLibraries.extract(ready.plan.nativeArchives, natives, log)
        if (nativeProblems.isNotEmpty()) {
            return LaunchOutcome.Refused(
                "The game's native libraries could not be unpacked.",
                nativeProblems.joinToString("; "),
            )
        }

        // The redacted form is the only one that is ever written down. The list
        // being started still has the access token in it, and that list goes
        // nowhere but the process.
        log.info(CATEGORY, "Starting ${game.minecraftVersion} with Java ${java.majorVersion}")
        log.info(CATEGORY, ready.plan.describe())

        return start(
            command = ready.plan.command,
            workingDirectory = ready.plan.workingDirectory,
            redactedCommand = ready.plan.describe(),
            onStarted = onStarted,
        )
    }

    private fun start(
        command: List<String>,
        workingDirectory: String,
        redactedCommand: String,
        onStarted: (String) -> Unit,
    ): LaunchOutcome {
        val process = try {
            ProcessBuilder(command)
                .directory(File(workingDirectory))
                // One stream, so the crash report reads in the order things
                // happened instead of two halves interleaved by chance.
                .redirectErrorStream(true)
                .start()
        } catch (error: Exception) {
            log.error(CATEGORY, "The game process could not be started", error)
            return LaunchOutcome.Refused(
                "The game could not be started. Check that the Java runtime is still there.",
                error.message,
            )
        }

        onStarted(redactedCommand)
        // The game reads nothing from its input, and leaving it open holds a
        // pipe the launcher would then have to remember to close.
        runCatching { process.outputStream.close() }

        val tail = collectOutput(process)
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            log.info(CATEGORY, "The game closed normally")
            return LaunchOutcome.Finished
        }
        log.warn(CATEGORY, "The game exited with code $exitCode")
        return LaunchOutcome.Crashed(exitCode, tail.joinToString(System.lineSeparator()))
    }

    /**
     * Reads the game's output until it ends, writing all of it to a file and
     * keeping the last lines in memory.
     *
     * Reading on this thread is what makes the wait a wait: a process whose
     * output nobody reads fills its pipe buffer and stops, which looks exactly
     * like a game that has frozen.
     */
    private fun collectOutput(process: Process): List<String> {
        val tail = ArrayDeque<String>()
        val writer = runCatching {
            Files.createDirectories(outputFile.parent)
            Files.newBufferedWriter(
                outputFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
        }.getOrNull()

        process.inputStream.bufferedReader().use { reader ->
            reader.forEachLine { line ->
                tail.addLast(line)
                while (tail.size > RETAINED_LINES) tail.removeFirst()
                runCatching { writer?.appendLine(line) }
            }
        }
        runCatching { writer?.close() }
        return tail.toList()
    }

    /**
     * The version document for [versionId] with whatever it inherits from
     * already folded in, or null if any of it is unreadable.
     *
     * The chain is followed rather than assumed to be one deep, with a limit,
     * because a version file is a document on disk and a cycle in it must not
     * become a launcher that hangs before it shows anything.
     */
    private fun resolved(versionId: String): VersionProfile? {
        var profile = read(versionId) ?: return null
        var remaining = MAXIMUM_INHERITANCE
        val seen = mutableSetOf(versionId)
        while (true) {
            val parentId = profile.inheritsFrom ?: return profile
            if (!seen.add(parentId) || remaining-- <= 0) {
                log.error(CATEGORY, "The version files inherit from each other in a loop")
                return null
            }
            val parent = read(parentId) ?: return null
            profile = profile.inheriting(parent)
        }
    }

    private fun read(versionId: String): VersionProfile? {
        val file = paths.versionManifest(versionId)
        return runCatching { VersionDocuments.parseProfile(Files.readString(file)) }
            .onFailure { log.error(CATEGORY, "Could not read the version file $file", it) }
            .getOrNull()
    }

    private fun layoutFor(
        game: GameConfiguration,
        profile: VersionProfile,
        natives: Path,
    ): LaunchLayout = LaunchLayout(
        gameDirectory = absolute(paths.root),
        assetsDirectory = absolute(paths.assetsDirectory),
        librariesDirectory = absolute(paths.librariesDirectory),
        nativesDirectory = absolute(natives),
        // Minecraft's own jar, not the Fabric profile's. Fabric publishes no
        // client download, and the file beside its profile does not exist.
        clientJar = absolute(paths.versionJar(game.minecraftVersion)),
        pathSeparator = File.pathSeparator,
        loggingConfiguration = loggingConfiguration(profile),
    )

    /**
     * The log4j configuration the installer wrote, or null when it is not there.
     * Missing it costs the game its log formatting, which is not worth refusing
     * to start over.
     */
    private fun loggingConfiguration(profile: VersionProfile): String? {
        val fileId = profile.logging?.fileId ?: return null
        val file = paths.assetsDirectory.resolve(LOG_CONFIGS).resolve(fileId)
        if (!Files.isRegularFile(file)) {
            log.warn(CATEGORY, "The logging configuration $file is missing")
            return null
        }
        return absolute(file)
    }

    private fun absolute(path: Path): String = path.toAbsolutePath().normalize().toString()

    private companion object {
        const val CATEGORY = "launch"
        const val LOG_CONFIGS = "log_configs"
        const val RETAINED_LINES = 60
        const val MAXIMUM_INHERITANCE = 8
    }
}
