package dev.survivaloverhaul.launcher.infrastructure.account

import dev.survivaloverhaul.launcher.application.account.RefreshTokens

/**
 * The refresh token, for as long as the launcher is running and no longer.
 *
 * This is the documented fallback, not the intended destination. A refresh
 * token is a long-lived credential for a Microsoft account, and the only
 * acceptable places to keep one between runs are the Windows Credential
 * Manager, the macOS Keychain and the Secret Service on Linux. None of the
 * three is reachable from the Java standard library, so binding to them means
 * taking on a native dependency, and that is a decision to make deliberately
 * rather than one to slip into a milestone.
 *
 * Until then the token lives in this process and dies with it, and the user
 * signs in again next time they open the launcher. That costs a browser round
 * trip. The alternative, a token in a file the launcher could read back, costs
 * the account itself to anything that can read the user's files, and the rule
 * in `docs/authentication.md` is that writing it to a plaintext file is not one
 * of the options.
 *
 * Volatile because the sign-in runs on an IO thread and the UI thread asks
 * whether there is anything to restore.
 */
class MemoryRefreshTokens : RefreshTokens {

    @Volatile
    private var token: String? = null

    override fun read(): String? = token

    override fun write(token: String) {
        this.token = token.ifBlank { null }
    }

    override fun clear() {
        token = null
    }

    override val describesPersistence: String =
        "You will be asked to sign in again the next time you open the launcher. " +
            "This launcher does not write your sign-in to disk."
}
