package dev.survivaloverhaul.launcher.application.launch

import dev.survivaloverhaul.launcher.domain.account.PlayerSession
import dev.survivaloverhaul.launcher.domain.game.GameConfiguration
import dev.survivaloverhaul.launcher.domain.java.JavaRuntime

/** What became of a launch attempt. */
sealed interface LaunchOutcome {

    /** The game ran and closed the way a game closes. */
    data object Finished : LaunchOutcome

    /**
     * The game started and exited badly. [detail] carries the last of its output
     * because that, and not the exit code, is what says why.
     */
    data class Crashed(val exitCode: Int, val detail: String) : LaunchOutcome

    /**
     * The game was never started. [reason] is one sentence for the user and
     * [detail] is for the log, the same split the installer makes.
     */
    data class Refused(val reason: String, val detail: String? = null) : LaunchOutcome
}

/**
 * Starts the game and waits for it to exit.
 *
 * Blocking, like the installers, and for the same reason: the caller decides
 * which thread it runs on and no layer below the UI knows about coroutines.
 * Waiting rather than returning at startup is deliberate too, because the
 * launcher has to know when to bring its own window back.
 *
 * [onStarted] is handed the command line with every secret already removed. It
 * is the only view of the command anything outside this port is given.
 */
interface GameLauncher {
    fun run(
        game: GameConfiguration,
        session: PlayerSession,
        java: JavaRuntime,
        onStarted: (String) -> Unit,
    ): LaunchOutcome
}
