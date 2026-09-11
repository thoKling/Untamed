package dev.survivaloverhaul.launcher

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.survivaloverhaul.launcher.application.account.Accounts
import dev.survivaloverhaul.launcher.application.account.RefreshTokens
import dev.survivaloverhaul.launcher.application.installation.FallbackManifestSource
import dev.survivaloverhaul.launcher.application.installation.Installer
import dev.survivaloverhaul.launcher.application.installation.ManifestSource
import dev.survivaloverhaul.launcher.application.installation.StagedInstaller
import dev.survivaloverhaul.launcher.application.launch.GameLauncher
import dev.survivaloverhaul.launcher.branding.Branding
import dev.survivaloverhaul.launcher.domain.settings.LauncherSettings
import dev.survivaloverhaul.launcher.infrastructure.account.MemoryRefreshTokens
import dev.survivaloverhaul.launcher.infrastructure.account.MicrosoftAccounts
import dev.survivaloverhaul.launcher.infrastructure.filesystem.HostSystem
import dev.survivaloverhaul.launcher.infrastructure.filesystem.InstallationPaths
import dev.survivaloverhaul.launcher.infrastructure.filesystem.LauncherDirectories
import dev.survivaloverhaul.launcher.infrastructure.http.HttpTransport
import dev.survivaloverhaul.launcher.infrastructure.installation.FilesystemInstallationProbe
import dev.survivaloverhaul.launcher.infrastructure.installation.InstallationStateStore
import dev.survivaloverhaul.launcher.infrastructure.installation.ManifestModInstaller
import dev.survivaloverhaul.launcher.infrastructure.installation.MojangGameInstaller
import dev.survivaloverhaul.launcher.infrastructure.java.SystemJavaRuntimeProvider
import dev.survivaloverhaul.launcher.infrastructure.launch.ProcessGameLauncher
import dev.survivaloverhaul.launcher.infrastructure.logging.LauncherLog
import dev.survivaloverhaul.launcher.infrastructure.manifest.BundledManifestSource
import dev.survivaloverhaul.launcher.infrastructure.manifest.Distribution
import dev.survivaloverhaul.launcher.infrastructure.manifest.RemoteManifestSource
import dev.survivaloverhaul.launcher.infrastructure.settings.JsonSettingsStore
import dev.survivaloverhaul.launcher.ui.LauncherApp
import dev.survivaloverhaul.launcher.ui.LauncherStore
import dev.survivaloverhaul.launcher.ui.theme.SurvivalOverhaulTheme
import java.awt.Dimension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The composition root.
 *
 * This is the only place that knows which implementation of a port is in use,
 * which is what keeps the rest of the launcher testable: the manifest arrives
 * from the network with the bundled copy behind it, and an installation is two
 * installers composed into one, and nothing above this file knows either.
 */
fun main() {
    LauncherDirectories.ensureHome()
    val log = LauncherLog(LauncherDirectories.logDirectory)

    // A crash that reaches the top of a thread would otherwise be invisible to
    // the person it happened to, since a packaged desktop app has no console.
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        log.error("launcher", "Uncaught error on thread ${thread.name}", error)
    }

    log.info(
        "launcher",
        "${Branding.PRODUCT_NAME} launcher ${Branding.LAUNCHER_VERSION} starting",
    )
    log.info("launcher", "Host: ${HostSystem.description}")
    log.info(
        "launcher",
        "Runtime: ${System.getProperty("java.vendor")} ${System.getProperty("java.version")}",
    )
    log.info("launcher", "Launcher files: ${LauncherDirectories.home}")

    val settingsStore = JsonSettingsStore(
        file = LauncherDirectories.settingsFile,
        defaultInstallationDirectory = LauncherDirectories.defaultInstallationDirectory.toString(),
        log = log,
    )
    // One transport for the whole launcher, so every download shares the same
    // connection pool, timeouts and retry policy rather than each caller
    // inventing its own.
    val transport = HttpTransport(log)
    // Built here rather than inside the factory below, because the sentence it
    // offers about where the sign-in is kept is shown to the user, and the
    // launcher should say what it actually does rather than a second copy of it.
    val refreshTokens = MemoryRefreshTokens()
    val store = LauncherStore(
        settingsStore = settingsStore,
        manifestSourceFactory = { settings -> manifestSourceFor(settings, transport, log) },
        probeFactory = { settings -> probeFor(settings, log) },
        installerFactory = { settings -> installerFor(settings, transport, log) },
        launcherFactory = { settings -> launcherFor(settings, log) },
        accounts = accountsFor(transport, refreshTokens, log),
        sessionPersistence = refreshTokens.describesPersistence,
        javaRuntimeProvider = SystemJavaRuntimeProvider(log),
        log = log,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    )

    application {
        // The launch behaviour setting can ask the launcher to close itself
        // once the game is running. It is requested as state rather than done
        // where it is decided, because only this scope can end the application.
        LaunchedEffect(store.exitRequested) {
            if (store.exitRequested) {
                log.info("launcher", "Closing while the game runs, as the settings ask")
                log.close()
                exitApplication()
            }
        }
        Window(
            onCloseRequest = {
                log.info("launcher", "Shutting down")
                log.close()
                exitApplication()
            },
            title = "${Branding.PRODUCT_NAME} - ${Branding.UNOFFICIAL_LABEL}",
            state = rememberWindowState(size = DpSize(1060.dp, 680.dp)),
            // Hidden rather than closed while the game plays, so the window that
            // comes back when it exits is the same one, with the same state.
            visible = store.windowVisible,
        ) {
            window.minimumSize = Dimension(940, 620)
            SurvivalOverhaulTheme {
                LauncherApp(store, log)
            }
        }
    }
}

/**
 * The one way to obtain a session.
 *
 * Built once rather than per call, because it holds the session the rest of the
 * launcher reads and the refresh token behind it. There is deliberately no
 * second implementation of [Accounts] anywhere in this file: an offline account
 * would be a bypass of the ownership check, and the place it would have to be
 * added is here, where it is not.
 *
 * The refresh token is kept in memory only. Nothing in the Java standard
 * library reaches the Windows Credential Manager, the macOS Keychain or the
 * Linux Secret Service, and a token written anywhere the launcher could read it
 * back unaided is a token anything else on the machine can read too.
 */
private fun accountsFor(
    transport: HttpTransport,
    refreshTokens: RefreshTokens,
    log: LauncherLog,
): Accounts = MicrosoftAccounts(
    transport = transport,
    refreshTokens = refreshTokens,
    log = log,
)

private fun probeFor(settings: LauncherSettings, log: LauncherLog): FilesystemInstallationProbe {
    val paths = InstallationPaths.of(settings.installationDirectory)
    return FilesystemInstallationProbe(
        paths = paths,
        stateStore = InstallationStateStore(paths.stateFile, log),
        log = log,
    )
}

/**
 * Built per launch for the same reason the installer is: the installation
 * directory, the memory, the resolution and the extra JVM arguments are all
 * settings, and a launcher built once would start the game the way the settings
 * looked when the window opened.
 *
 * The game's own output goes beside the launcher's log rather than into it. It
 * is thousands of lines of someone else's logging, and mixing it in would push
 * the launcher's own account of what it did out of the file.
 */
private fun launcherFor(settings: LauncherSettings, log: LauncherLog): GameLauncher =
    ProcessGameLauncher(
        paths = InstallationPaths.of(settings.installationDirectory),
        settings = settings,
        platform = HostSystem.platform,
        outputFile = LauncherDirectories.logDirectory.resolve("game-output.log"),
        log = log,
    )

/**
 * The published manifest, with the copy inside the jar behind it.
 *
 * Built per read rather than once, because the address is a setting: a source
 * holding the old one would keep asking a host the user has moved away from.
 */
private fun manifestSourceFor(
    settings: LauncherSettings,
    transport: HttpTransport,
    log: LauncherLog,
): ManifestSource = FallbackManifestSource(
    primary = RemoteManifestSource(
        transport = transport,
        url = settings.manifestUrl ?: Distribution.MANIFEST_URL,
        log = log,
    ),
    fallback = BundledManifestSource(log),
    onFallback = { log.warn("manifest", "Using the bundled manifest: $it") },
)

/**
 * Built per install rather than once, because the installation directory is a
 * setting: an installer holding on to the old path would quietly write the game
 * somewhere the user has already moved away from.
 *
 * The game and the product's files are two installers rather than one, so that
 * a failure in either is reported as itself. [StagedInstaller] is what puts
 * them in the only order they work in.
 */
private fun installerFor(
    settings: LauncherSettings,
    transport: HttpTransport,
    log: LauncherLog,
): Installer {
    val paths = InstallationPaths.of(settings.installationDirectory)
    // One store for both installers: the state file is a single record and two
    // stores writing it would each overwrite the other's half.
    val stateStore = InstallationStateStore(paths.stateFile, log)
    return StagedInstaller(
        game = MojangGameInstaller(
            paths = paths,
            transport = transport,
            stateStore = stateStore,
            platform = HostSystem.platform,
            log = log,
        ),
        mods = ManifestModInstaller(
            paths = paths,
            transport = transport,
            stateStore = stateStore,
            log = log,
        ),
    )
}
