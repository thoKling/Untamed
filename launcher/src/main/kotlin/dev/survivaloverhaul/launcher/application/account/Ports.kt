package dev.survivaloverhaul.launcher.application.account

import dev.survivaloverhaul.launcher.application.installation.CancellationSignal
import dev.survivaloverhaul.launcher.domain.account.PlayerSession
import dev.survivaloverhaul.launcher.domain.account.SignInStage

/**
 * Who the launcher would start the game as.
 *
 * There is no third case. Either a Microsoft sign-in and an ownership check
 * have both completed and produced a [PlayerSession], or they have not and the
 * launcher says why. Nothing here can be constructed into a session, which is
 * what makes an offline mode impossible rather than merely absent.
 */
sealed interface SessionState {

    data class SignedIn(val session: PlayerSession) : SessionState

    /** [reason] is shown to the user, so it says what to do, not what failed. */
    data class SignedOut(val reason: String) : SessionState
}

/**
 * The account the launcher is currently acting for.
 *
 * Read-only and instant: it answers from what the launcher already knows and
 * never goes to the network. The launch path depends on this and nothing more,
 * so nothing about how a session is obtained reaches it.
 */
fun interface PlayerSessions {
    fun current(): SessionState
}

/**
 * Obtaining a session, and giving one up.
 *
 * Blocking, like every other port below the UI: sign-in is a chain of six round
 * trips and the caller decides which thread waits on them. [progress] is called
 * as each one starts, because a user staring at a browser window deserves to
 * know which of them is taking the time.
 *
 * There is deliberately no method here that produces a session from anything
 * but a completed sign-in. No token parameter, no user name parameter, no
 * offline variant. The interface is the place where that would have to be
 * added, and it is not there to be added to.
 */
interface Accounts : PlayerSessions {

    /**
     * Signs in interactively. Opens the user's browser at Microsoft's own page
     * and waits for the redirect to come back to a loopback socket.
     */
    fun signIn(
        cancellation: CancellationSignal = CancellationSignal.NEVER,
        progress: (SignInStage) -> Unit = {},
    ): SessionState

    /**
     * Signs in from a refresh token kept since the last run, if there is one.
     * Never opens a browser: a launcher that opens a login page on startup
     * without being asked is a launcher people learn to click through.
     */
    fun restore(): SessionState

    /** Forgets the session and the refresh token behind it. */
    fun signOut()
}

/**
 * Where the one value worth keeping between launches lives.
 *
 * The refresh token is the only long-lived secret in the chain, which is why it
 * is the only one with a port. Everything else is minutes old and stays in
 * memory until the next step consumes it.
 *
 * A store that cannot keep a secret must keep nothing. There is no
 * implementation of this interface that writes to a plain file, and the
 * fallback when the platform offers no protected store is to remember nothing
 * and ask the user to sign in again.
 */
interface RefreshTokens {

    fun read(): String?

    fun write(token: String)

    fun clear()

    /** What the user is told about signing in again, in one sentence. */
    val describesPersistence: String
}
