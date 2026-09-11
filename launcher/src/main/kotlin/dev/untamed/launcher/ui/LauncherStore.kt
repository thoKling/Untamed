package dev.untamed.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.untamed.launcher.application.account.Accounts
import dev.untamed.launcher.application.account.SessionState
import dev.untamed.launcher.application.diagnostics.DiagnosticsReport
import dev.untamed.launcher.application.installation.CancellationSignal
import dev.untamed.launcher.application.installation.Installer
import dev.untamed.launcher.application.installation.InstallationOutcome
import dev.untamed.launcher.application.installation.InstallationPlanner
import dev.untamed.launcher.application.installation.InstallationProbe
import dev.untamed.launcher.application.installation.InstallationProgress
import dev.untamed.launcher.application.installation.JavaRuntimeProvider
import dev.untamed.launcher.application.installation.ManifestResult
import dev.untamed.launcher.application.installation.ManifestSource
import dev.untamed.launcher.application.launch.GameLauncher
import dev.untamed.launcher.application.launch.LaunchOutcome
import dev.untamed.launcher.application.settings.SettingsStore
import dev.untamed.launcher.branding.Branding
import dev.untamed.launcher.domain.account.SignInStage
import dev.untamed.launcher.domain.game.GameConfiguration
import dev.untamed.launcher.domain.installation.InstallationPlan
import dev.untamed.launcher.domain.installation.InstallationSnapshot
import dev.untamed.launcher.domain.installation.InstallationStep
import dev.untamed.launcher.domain.java.JavaRuntimes
import dev.untamed.launcher.domain.java.JavaSelection
import dev.untamed.launcher.domain.settings.LaunchBehaviour
import dev.untamed.launcher.domain.settings.LauncherSettings
import dev.untamed.launcher.infrastructure.filesystem.HostSystem
import dev.untamed.launcher.infrastructure.logging.LauncherLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen(val title: String) {
    HOME("Play"),
    SETTINGS("Settings"),
    DIAGNOSTICS("Logs"),
}

/**
 * Everything the window shows, and the only place the UI is allowed to change
 * it from.
 *
 * The screens are stateless: they render this object and call methods on it.
 * Reading the manifest, inspecting the installation, looking for Java and
 * installing the game all happen off the UI thread, so neither a slow disk nor
 * a multi-gigabyte download can freeze the window.
 */
class LauncherStore(
    private val settingsStore: SettingsStore,
    private val manifestSourceFactory: (LauncherSettings) -> ManifestSource,
    private val probeFactory: (LauncherSettings) -> InstallationProbe,
    private val installerFactory: (LauncherSettings) -> Installer,
    private val launcherFactory: (LauncherSettings) -> GameLauncher,
    private val accounts: Accounts,
    /** What becomes of the sign-in when the launcher closes, in the user's words. */
    val sessionPersistence: String,
    private val javaRuntimeProvider: JavaRuntimeProvider,
    private val log: LauncherLog,
    private val scope: CoroutineScope,
) {
    var screen by mutableStateOf(Screen.HOME)
        private set

    var settings by mutableStateOf(settingsStore.load())
        private set

    var manifest by mutableStateOf<ManifestResult?>(null)
        private set

    var snapshot by mutableStateOf(InstallationSnapshot.NOT_INSPECTED)
        private set

    var busy by mutableStateOf(false)
        private set

    var installing by mutableStateOf(false)
        private set

    /** How far the installation running right now has got, or null. */
    var progress by mutableStateOf<InstallationProgress?>(null)
        private set

    /** Which Java runtime the game would be launched with, once one is known. */
    var java by mutableStateOf<JavaSelection?>(null)
        private set

    /** A short, transient line under the Play button. Cleared by the next action. */
    var notice by mutableStateOf<String?>(null)
        private set

    /**
     * Who the game would be started as.
     *
     * The only way this becomes a signed-in session is a completed Microsoft
     * sign-in and a passed ownership check. The launcher has no other way to
     * obtain one and deliberately offers none: an offline account is a bypass
     * of the ownership check, whatever it is called on the button.
     */
    var session by mutableStateOf<SessionState>(accounts.current())
        private set

    /** True while a sign-in is waiting on the browser or on a service. */
    var signingIn by mutableStateOf(false)
        private set

    /**
     * Which step of the sign-in is happening. Six services answer in turn and
     * any of them can be the slow one, so the user is told which is being
     * waited on rather than watching one unexplained spinner.
     */
    var signInStage by mutableStateOf<SignInStage?>(null)
        private set

    /** True from the moment the game process starts until it exits. */
    var running by mutableStateOf(false)
        private set

    /**
     * Whether the launcher's own window is on screen. Set from the launch
     * behaviour setting while the game runs, and put back when it exits, so a
     * hidden launcher can never be a launcher the user cannot get back to.
     */
    var windowVisible by mutableStateOf(true)
        private set

    /** Set when the launch behaviour asks the launcher to close itself. */
    var exitRequested by mutableStateOf(false)
        private set

    /** The last launch command with its secrets removed, for the logs screen. */
    var lastLaunchCommand by mutableStateOf<String?>(null)
        private set

    /**
     * Set when the user asks to stop, read by the installer between files. The
     * installer is plain blocking code, so cancellation is a flag it checks
     * rather than a cancelled coroutine.
     *
     * Volatile because it is written on the UI thread and read on the one doing
     * the downloading, and a Stop button that the installer might not notice is
     * not a Stop button.
     */
    @Volatile
    private var cancelRequested = false

    /**
     * The same arrangement for sign-in: the chain is blocking code on an IO
     * thread, and Cancel is a flag it reads while it waits for the browser.
     */
    @Volatile
    private var signInCancelled = false

    val gameConfiguration: GameConfiguration?
        get() = (manifest as? ManifestResult.Loaded)?.manifest?.game

    val manifestOrigin: String? get() = manifest?.origin

    val plan: InstallationPlan get() = InstallationPlanner.plan(snapshot)

    /**
     * Everything an installation would do. Every component the plan can name is
     * something the launcher can now install, so there is no longer a subset of
     * the plan to filter down to.
     */
    val steps: List<InstallationStep> get() = plan.steps

    val canInstall: Boolean
        get() = !busy && !installing && !running && !signingIn &&
            manifest is ManifestResult.Loaded &&
            snapshot.isInspected &&
            steps.isNotEmpty()

    /**
     * Everything the launch needs, present at once: a complete installation, a
     * Java runtime that was confirmed rather than assumed, and a signed-in
     * account. The account is the one that is never there yet, and it is a
     * condition like any other rather than a special case that can be waived.
     */
    val canPlay: Boolean
        get() = !busy && !installing && !running && !signingIn &&
            snapshot.isInspected && snapshot.isComplete && steps.isEmpty() &&
            java is JavaSelection.Selected &&
            session is SessionState.SignedIn

    val primaryLabel: String
        get() = when {
            running -> "PLAYING"
            installing -> "INSTALLING"
            canInstall -> "INSTALL"
            else -> "PLAY"
        }

    val playHint: String
        get() = when (val current = manifest) {
            null -> "Reading the version manifest…"
            is ManifestResult.Unavailable -> "No manifest available: ${current.reason}"
            is ManifestResult.Rejected -> "The manifest was rejected: ${current.problems.first()}"

            is ManifestResult.Loaded -> when {
                running -> "The game is running."
                signingIn -> signInStage?.message ?: "Signing in…"
                installing -> progress?.describe() ?: "Installing…"
                busy -> "Checking the installation…"
                !snapshot.isInspected -> "The installation has not been checked yet"
                steps.isNotEmpty() -> describePendingWork()
                snapshot.isComplete -> readyHint()
                // Inspected, nothing to do, and not complete: a component the
                // launcher does not install. Saying so is better than a hint
                // that implies the button would fix it.
                else -> "The installation is not complete. See the logs for what is missing."
            }
        }

    /**
     * What a complete installation is still waiting for, in the order the user
     * can do something about it. A hint that says "ready" while the button is
     * disabled is worse than no hint at all.
     */
    private fun readyHint(): String {
        val runtime = java
        if (runtime !is JavaSelection.Selected) {
            return runtime?.describe() ?: "Looking for a Java runtime…"
        }
        return when (val account = session) {
            is SessionState.SignedOut -> account.reason
            is SessionState.SignedIn -> "Ready to play as ${account.session.userName}"
        }
    }

    /**
     * Signs in with Microsoft.
     *
     * The browser does the signing in; this waits for it. Everything about the
     * account arrives from that round trip, which is why there is no argument
     * here and no other method that produces a session.
     */
    fun signIn() {
        if (signingIn || running) return
        signingIn = true
        signInCancelled = false
        signInStage = null
        notice = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                accounts.signIn(CancellationSignal { signInCancelled }) { stage ->
                    // From the thread waiting on the browser, so the state
                    // write is hopped back onto the UI thread.
                    scope.launch { if (signingIn) signInStage = stage }
                }
            }
            session = result
            signingIn = false
            signInStage = null
            notice = when (result) {
                is SessionState.SignedIn -> "Signed in as ${result.session.userName}."
                is SessionState.SignedOut -> result.reason
            }
            log.info(CATEGORY, "Sign-in finished: ${describe(result)}")
        }
    }

    /** Stops waiting for the browser. What the user typed there is their own. */
    fun cancelSignIn() {
        if (!signingIn) return
        signInCancelled = true
        notice = "Stopping the sign-in…"
    }

    /**
     * Forgets the session. The game cannot be started afterwards, which is the
     * point: signing out is not a mode the launcher can play in.
     */
    fun signOut() {
        if (signingIn || running) return
        accounts.signOut()
        session = accounts.current()
        notice = "Signed out."
    }

    /** The session as a log line: never the token, only who and whether. */
    private fun describe(state: SessionState): String = when (state) {
        is SessionState.SignedIn -> "signed in as ${state.session.userName}"
        is SessionState.SignedOut -> "signed out (${state.reason})"
    }

    fun show(target: Screen) {
        screen = target
    }

    /** Re-reads the manifest, re-inspects the installation and looks for Java. */
    fun refresh() {
        notice = null
        reinspect()
    }

    /**
     * The same work without clearing the notice, so that what an installation
     * had to say is still on screen while the launcher confirms it.
     */
    private fun reinspect() {
        if (busy || installing) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { manifestSourceFactory(settings).load() }
            manifest = result
            snapshot = when (result) {
                is ManifestResult.Loaded -> withContext(Dispatchers.IO) {
                    val installed = probeFactory(settings).inspect()
                    InstallationPlanner.inspect(result.manifest, installed)
                }

                else -> InstallationSnapshot.NOT_INSPECTED
            }
            java = (result as? ManifestResult.Loaded)?.let { loaded ->
                withContext(Dispatchers.IO) { detectJava(loaded.manifest.game.javaMajorVersion) }
            }
            // Off the UI thread because restoring a session is a question
            // asked over the network, and the answer belongs to the same
            // refresh the rest of the screen comes from. Restoring is silent:
            // it uses a refresh token if there is one and never opens a
            // browser on its own.
            session = withContext(Dispatchers.IO) {
                if (accounts.current() is SessionState.SignedIn) accounts.current()
                else accounts.restore()
            }
            busy = false
            log.info(CATEGORY, "Status refreshed: ${describe()}")
        }
    }

    /**
     * The one thing the big button does.
     *
     * Which of the two it is depends on the installation rather than on a mode
     * the user has to choose: something to install means install it, otherwise
     * play. The label says which, so the button never does the surprising one.
     */
    fun start() {
        when {
            canInstall -> install()
            canPlay -> play()
        }
    }

    /**
     * Starts the game and waits for it to exit.
     *
     * The wait happens on an IO thread, so the launcher's window keeps painting
     * for the whole session. What the launcher does with that window comes from
     * the launch behaviour setting, and whatever it does, the window comes back
     * when the game ends.
     */
    fun play() {
        if (!canPlay) return
        val game = gameConfiguration ?: return
        val runtime = (java as? JavaSelection.Selected)?.runtime ?: return
        val account = (session as? SessionState.SignedIn)?.session ?: return

        running = true
        notice = null
        lastLaunchCommand = null
        applyLaunchBehaviour()
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                launcherFactory(settings).run(game, account, runtime) { command ->
                    // Hopped back onto the UI thread: this arrives from the
                    // thread waiting on the process, and Compose state is only
                    // safe to write from one of them.
                    scope.launch { lastLaunchCommand = command }
                }
            }
            running = false
            windowVisible = true
            notice = describe(outcome)
            log.info(CATEGORY, "Launch finished: $notice")
        }
    }

    /**
     * Hides or closes the launcher, if the user asked for that. The window is
     * only hidden, never disposed, so bringing it back when the game exits is
     * the same window with the same state.
     */
    private fun applyLaunchBehaviour() {
        when (settings.launchBehaviour) {
            LaunchBehaviour.KEEP_OPEN -> Unit
            LaunchBehaviour.HIDE -> windowVisible = false
            LaunchBehaviour.CLOSE -> exitRequested = true
        }
    }

    private fun describe(outcome: LaunchOutcome): String = when (outcome) {
        LaunchOutcome.Finished -> "The game closed."

        is LaunchOutcome.Crashed -> {
            log.warn(CATEGORY, "The game's last output:\n${outcome.detail}")
            "The game stopped unexpectedly (exit code ${outcome.exitCode}). See the logs."
        }

        is LaunchOutcome.Refused -> {
            outcome.detail?.let { log.warn(CATEGORY, it) }
            outcome.reason
        }
    }

    /**
     * Installs everything the plan asks for, then re-inspects rather than
     * assuming it worked. Installation status is derived from the files on disk
     * in every other case, and a successful install is not a reason to make an
     * exception.
     */
    fun install() {
        if (!canInstall) return
        val loaded = manifest as? ManifestResult.Loaded ?: return
        installing = true
        cancelRequested = false
        notice = null
        progress = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                installerFactory(settings).install(
                    manifest = loaded.manifest,
                    cancellation = CancellationSignal { cancelRequested },
                ) { update -> scope.launch { if (installing) progress = update } }
            }
            installing = false
            progress = null
            notice = when (outcome) {
                is InstallationOutcome.Completed -> outcome.summary
                is InstallationOutcome.Failed -> outcome.reason
                InstallationOutcome.Cancelled ->
                    "Installation stopped. Nothing was left half-written."
            }
            log.info(CATEGORY, "Installation finished: $notice")
            reinspect()
        }
    }

    fun cancelInstall() {
        if (!installing) return
        cancelRequested = true
        notice = "Stopping after the file being downloaded…"
    }

    fun update(changed: LauncherSettings) {
        val sanitised = changed.sanitised(settings.installationDirectory)
        val directoryChanged = sanitised.installationDirectory != settings.installationDirectory
        // A different manifest address means a different set of versions to
        // compare against, so what is on screen is stale until it is re-read.
        val manifestChanged = sanitised.manifestUrl != settings.manifestUrl
        settings = sanitised
        scope.launch {
            withContext(Dispatchers.IO) { settingsStore.save(sanitised) }
            if (directoryChanged) {
                val directory = sanitised.installationDirectory
                log.info(CATEGORY, "Installation directory is now $directory")
            }
            if (directoryChanged || manifestChanged) reinspect()
        }
    }

    /**
     * Looks for Java again, on demand.
     *
     * Deliberately not run when the Java path in settings changes: that field
     * changes on every keystroke, and confirming a runtime means starting a
     * process for each candidate. The user asks for it once, when they have
     * finished typing.
     */
    fun redetectJava() {
        val required = gameConfiguration?.javaMajorVersion ?: return
        scope.launch {
            java = withContext(Dispatchers.IO) { detectJava(required) }
            log.info(CATEGORY, "Java: ${java?.describe()}")
        }
    }

    fun diagnostics(): String = DiagnosticsReport.build(
        launcherVersion = Branding.LAUNCHER_VERSION,
        hostDescription = HostSystem.description,
        launcherJavaRuntime = launcherRuntimeDescription(),
        settings = settings,
        manifestOrigin = manifestOrigin,
        game = gameConfiguration,
        snapshot = snapshot,
        java = java,
        recentLogLines = log.recentLines().map { it.format() },
        account = describe(session),
        launchCommand = lastLaunchCommand,
    )

    fun note(message: String) {
        notice = message
    }

    /** The JVM the launcher itself is running on, which is not the game's. */
    private fun launcherRuntimeDescription(): String {
        val vendor = System.getProperty("java.vendor")
        val version = System.getProperty("java.version")
        return "$vendor $version"
    }

    private fun detectJava(requiredMajor: Int): JavaSelection =
        JavaRuntimes.select(javaRuntimeProvider.detect(settings.javaExecutable), requiredMajor)

    private fun describePendingWork(): String {
        val count = steps.size
        val components = if (count == 1) "component" else "components"
        return "$count $components to install into ${settings.installationDirectory}"
    }

    private fun describe(): String = snapshot.statuses.joinToString { status ->
        "${status.component.name.lowercase()}=${status.state.name.lowercase()}"
    }.ifBlank { "nothing inspected" }

    private companion object {
        const val CATEGORY = "launcher"
    }
}
