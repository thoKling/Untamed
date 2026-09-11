package dev.survivaloverhaul.launcher.infrastructure.account

import dev.survivaloverhaul.launcher.application.account.Accounts
import dev.survivaloverhaul.launcher.application.account.AuthorizationResponse
import dev.survivaloverhaul.launcher.application.account.MicrosoftAuthorization
import dev.survivaloverhaul.launcher.application.account.RefreshTokens
import dev.survivaloverhaul.launcher.application.account.SessionState
import dev.survivaloverhaul.launcher.application.account.XboxErrors
import dev.survivaloverhaul.launcher.application.installation.CancellationSignal
import dev.survivaloverhaul.launcher.domain.account.PlayerSession
import dev.survivaloverhaul.launcher.domain.account.SignInStage
import dev.survivaloverhaul.launcher.infrastructure.http.HttpTransport
import dev.survivaloverhaul.launcher.infrastructure.http.TransportResult
import dev.survivaloverhaul.launcher.infrastructure.http.WebResponse
import dev.survivaloverhaul.launcher.infrastructure.logging.LauncherLog
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The whole legitimate sign-in: Microsoft, Xbox Live, XSTS, Minecraft services,
 * the entitlement check, and the profile.
 *
 * Six round trips, each of which can refuse for its own ordinary reason, so
 * each one has its own failure sentence. The user is told which step is
 * happening while it happens, because "signing in…" for twenty seconds with a
 * browser window open elsewhere tells them nothing.
 *
 * What this class will not do is as much a part of it as what it does. There is
 * no path through it that produces a [PlayerSession] without a completed
 * ownership check, no parameter that skips a step, and no branch that invents a
 * name or a UUID. When it fails, the launcher stays signed out and says why.
 *
 * Blocking throughout. The caller owns the thread, and the UI keeps painting
 * because it calls this from an IO dispatcher.
 */
internal class MicrosoftAccounts(
    private val transport: HttpTransport,
    private val refreshTokens: RefreshTokens,
    private val log: LauncherLog,
    private val browser: SystemBrowser = SystemBrowser(log),
    private val clientId: String = AzureApplication.clientId,
) : Accounts {

    /**
     * Written by whichever thread ran the sign-in, read by the UI thread on
     * every recomposition, so it is volatile rather than merely private.
     */
    @Volatile
    private var state: SessionState = initialState()

    override fun current(): SessionState = state

    override fun signOut() {
        refreshTokens.clear()
        state = SessionState.SignedOut(SIGN_IN_PROMPT)
        log.info(CATEGORY, "Signed out, and the refresh token was discarded")
    }

    override fun restore(): SessionState {
        if (clientId.isBlank()) return remember(SessionState.SignedOut(NOT_CONFIGURED))
        val refreshToken = refreshTokens.read()
            ?: return remember(SessionState.SignedOut(SIGN_IN_PROMPT))

        log.info(CATEGORY, "Restoring the previous session from its refresh token")
        val form = mapOf(
            "client_id" to clientId,
            "refresh_token" to refreshToken,
            "grant_type" to "refresh_token",
            "scope" to AccountEndpoints.SCOPE,
        )
        val token = microsoftToken(form) ?: run {
            // The token is gone or was revoked. Keeping it would mean trying
            // the same failing request on every start.
            refreshTokens.clear()
            return remember(SessionState.SignedOut(SIGN_IN_PROMPT))
        }
        return remember(sessionFrom(token) {})
    }

    override fun signIn(
        cancellation: CancellationSignal,
        progress: (SignInStage) -> Unit,
    ): SessionState {
        if (clientId.isBlank()) return remember(SessionState.SignedOut(NOT_CONFIGURED))

        val listener = runCatching { LoopbackRedirect(log) }.getOrElse { error ->
            log.error(CATEGORY, "Could not open the sign-in listener", error)
            return remember(
                SessionState.SignedOut(
                    "This launcher could not open the local port that Microsoft sends you " +
                        "back to. Something else on this machine may be blocking it.",
                ),
            )
        }

        return listener.use { redirect ->
            remember(authorize(redirect, cancellation, progress))
        }
    }

    /**
     * The browser half: send the user to Microsoft, wait for the redirect, and
     * turn the code into a token. Split out so that the listener is closed on
     * every path out of [signIn], including the refusals.
     */
    private fun authorize(
        redirect: LoopbackRedirect,
        cancellation: CancellationSignal,
        progress: (SignInStage) -> Unit,
    ): SessionState {
        val pkce = Pkce.challenge()
        val state = Pkce.state()
        val url = MicrosoftAuthorization.authorizeUrl(
            endpoint = AccountEndpoints.AUTHORIZE,
            clientId = clientId,
            redirectUri = redirect.redirectUri,
            scope = AccountEndpoints.SCOPE,
            challenge = pkce.challenge,
            state = state,
        )

        progress(SignInStage.OPENING_BROWSER)
        log.info(CATEGORY, "Opening Microsoft sign-in, waiting on ${redirect.redirectUri}")
        if (!browser.open(url)) {
            return SessionState.SignedOut(
                "This launcher could not open a browser, and signing in to Microsoft " +
                    "needs one. Sign-in happens on Microsoft's own page, never here.",
            )
        }

        progress(SignInStage.WAITING_FOR_BROWSER)
        val target = redirect.awaitRedirect(SIGN_IN_TIMEOUT, cancellation)
            ?: return SessionState.SignedOut(
                if (cancellation.isCancelled()) {
                    "Sign-in was cancelled."
                } else {
                    "Sign-in timed out after ${SIGN_IN_TIMEOUT.toMinutes()} minutes. " +
                        "Try again when you are ready to finish it in the browser."
                },
            )

        val response = MicrosoftAuthorization.read(target, state)
        val code = when (response) {
            is AuthorizationResponse.Denied -> {
                log.info(CATEGORY, "Sign-in did not complete: ${response.reason}")
                return SessionState.SignedOut(response.reason)
            }

            is AuthorizationResponse.Granted -> response.code
        }

        progress(SignInStage.EXCHANGING_CODE)
        val token = microsoftToken(
            mapOf(
                "client_id" to clientId,
                "code" to code,
                "code_verifier" to pkce.verifier,
                "grant_type" to "authorization_code",
                "redirect_uri" to redirect.redirectUri,
                "scope" to AccountEndpoints.SCOPE,
            ),
        ) ?: return SessionState.SignedOut(
            "Microsoft would not complete the sign-in. Try again.",
        )

        return sessionFrom(token, progress)
    }

    /**
     * Everything after Microsoft: Xbox Live, XSTS, Minecraft, ownership, and
     * the profile. Shared by an interactive sign-in and a restored one, because
     * a refreshed Microsoft token has to walk exactly the same chain.
     */
    private fun sessionFrom(
        microsoft: MicrosoftTokenDocument,
        progress: (SignInStage) -> Unit,
    ): SessionState {
        if (microsoft.refreshToken.isNotBlank()) refreshTokens.write(microsoft.refreshToken)

        progress(SignInStage.XBOX_LIVE)
        val xbox = xboxLive(microsoft.accessToken) ?: return SessionState.SignedOut(
            "Xbox Live did not accept the Microsoft sign-in. Try again in a few minutes.",
        )

        progress(SignInStage.XSTS)
        val xsts = when (val result = xsts(xbox.token)) {
            is Step.Failed -> return SessionState.SignedOut(result.reason)
            is Step.Done -> result.value
        }

        progress(SignInStage.MINECRAFT)
        val minecraft = minecraftToken(xsts) ?: return SessionState.SignedOut(
            "Minecraft services did not accept this Xbox Live account. Try again later.",
        )

        progress(SignInStage.OWNERSHIP)
        when (val ownership = checkOwnership(minecraft.accessToken)) {
            is Step.Failed -> return SessionState.SignedOut(ownership.reason)
            is Step.Done -> Unit
        }

        progress(SignInStage.PROFILE)
        val profile = when (val result = profile(minecraft.accessToken)) {
            is Step.Failed -> return SessionState.SignedOut(result.reason)
            is Step.Done -> result.value
        }

        log.info(CATEGORY, "Signed in as ${profile.name}")
        return SessionState.SignedIn(
            PlayerSession(
                userName = profile.name,
                uuid = profile.id,
                accessToken = minecraft.accessToken,
                userType = "msa",
                xuid = xsts.xuid,
                clientId = clientId,
            ),
        )
    }

    /** Microsoft's token endpoint, for both grant types. Form-encoded, as it wants. */
    private fun microsoftToken(form: Map<String, String>): MicrosoftTokenDocument? {
        val response = send(
            url = AccountEndpoints.TOKEN,
            body = form.entries.joinToString("&") { (name, value) ->
                "${encode(name)}=${encode(value)}"
            },
            contentType = FORM_CONTENT_TYPE,
        ) ?: return null

        if (!response.isSuccessful) {
            val error = AccountDocuments.error(response.body)
            log.warn(CATEGORY, "Microsoft refused the token request: ${error.message()}")
            return null
        }
        return runCatching { AccountDocuments.microsoftToken(response.body) }
            .onFailure { log.error(CATEGORY, "Microsoft's token response was unreadable", it) }
            .getOrNull()
    }

    private fun xboxLive(microsoftAccessToken: String): XboxTokenDocument? {
        val body = buildJsonObject {
            putJsonObject("Properties") {
                put("AuthMethod", "RPS")
                put("SiteName", "user.auth.xboxlive.com")
                // The "d=" prefix is what marks this as a Microsoft account
                // token rather than an Xbox one. Without it the request is
                // refused with no useful reason.
                put("RpsTicket", "d=$microsoftAccessToken")
            }
            put("RelyingParty", "http://auth.xboxlive.com")
            put("TokenType", "JWT")
        }
        val response = send(AccountEndpoints.XBOX_AUTHENTICATE, body.toString()) ?: return null
        if (!response.isSuccessful) {
            log.warn(CATEGORY, "Xbox Live refused the sign-in with HTTP ${response.status}")
            return null
        }
        return runCatching { AccountDocuments.xboxToken(response.body) }
            .onFailure { log.error(CATEGORY, "Xbox Live's response was unreadable", it) }
            .getOrNull()
            ?.takeIf { it.token.isNotBlank() && it.userHash.isNotBlank() }
    }

    /**
     * The step with the failures worth naming. An account with no Xbox profile
     * and a child account not yet in a family both land here as a 401 with a
     * number in the body, and both are ordinary rather than broken.
     */
    private fun xsts(xboxToken: String): Step<XboxTokenDocument> {
        val body = buildJsonObject {
            putJsonObject("Properties") {
                put("SandboxId", "RETAIL")
                putJsonArray("UserTokens") { add(xboxToken) }
            }
            put("RelyingParty", AccountEndpoints.RELYING_PARTY)
            put("TokenType", "JWT")
        }
        val response = send(AccountEndpoints.XSTS_AUTHORIZE, body.toString())
            ?: return Step.Failed("Xbox Live could not be reached. Check the connection.")

        if (!response.isSuccessful) {
            val error = AccountDocuments.error(response.body)
            val reason = XboxErrors.describe(error.xboxErrorCode)
            log.warn(CATEGORY, "XSTS refused the sign-in: code ${error.xboxErrorCode}")
            return Step.Failed(reason)
        }
        val token = runCatching { AccountDocuments.xboxToken(response.body) }.getOrNull()
        return if (token == null || token.token.isBlank()) {
            Step.Failed("Xbox Live's answer could not be read. Try again in a few minutes.")
        } else {
            Step.Done(token)
        }
    }

    private fun minecraftToken(xsts: XboxTokenDocument): MinecraftTokenDocument? {
        val body = buildJsonObject {
            put("identityToken", "XBL3.0 x=${xsts.userHash};${xsts.token}")
        }
        val response = send(AccountEndpoints.MINECRAFT_LOGIN, body.toString()) ?: return null
        if (!response.isSuccessful) {
            log.warn(CATEGORY, "Minecraft services refused the login: HTTP ${response.status}")
            return null
        }
        return runCatching { AccountDocuments.minecraftToken(response.body) }
            .getOrNull()
            ?.takeIf { it.accessToken.isNotBlank() }
    }

    /**
     * Ownership is asked, never assumed, and a launcher that cannot get an
     * answer stays signed out rather than guessing in the user's favour.
     */
    private fun checkOwnership(minecraftToken: String): Step<Unit> {
        val response = send(AccountEndpoints.ENTITLEMENTS, body = null, token = minecraftToken)
            ?: return Step.Failed(
                "The ownership check could not reach Minecraft services. Try again later.",
            )
        if (!response.isSuccessful) {
            log.warn(CATEGORY, "The entitlement check answered HTTP ${response.status}")
            return Step.Failed(
                "Minecraft services would not answer the ownership check. Try again later.",
            )
        }
        val entitlements = runCatching { AccountDocuments.entitlements(response.body) }
            .getOrNull()
            ?: return Step.Failed("The ownership answer could not be read. Try again later.")

        log.info(CATEGORY, "Entitlements: ${entitlements.describe()}")
        if (!entitlements.ownsJavaEdition()) {
            return Step.Failed(
                "This Microsoft account does not own Minecraft: Java Edition. " +
                    "Sign in with the account that bought it, or buy it at minecraft.net.",
            )
        }
        return Step.Done(Unit)
    }

    private fun profile(minecraftToken: String): Step<ProfileDocument> {
        val response = send(AccountEndpoints.PROFILE, body = null, token = minecraftToken)
            ?: return Step.Failed("The player profile could not be read. Try again later.")

        // A 404 here is not an error in the launcher or in the account. It is
        // an account that owns the game but has never set up Java Edition, and
        // saying "you do not own it" would send the user somewhere useless.
        if (response.status == NOT_FOUND) {
            return Step.Failed(
                "This account owns Minecraft: Java Edition but has no player profile yet. " +
                    "Open the official Minecraft launcher once to choose a name.",
            )
        }
        if (!response.isSuccessful) {
            log.warn(CATEGORY, "The profile request answered HTTP ${response.status}")
            return Step.Failed("Minecraft services would not return the profile. Try again later.")
        }
        val profile = runCatching { AccountDocuments.profile(response.body) }.getOrNull()
        return if (profile == null || profile.id.isBlank() || profile.name.isBlank()) {
            Step.Failed("The player profile could not be read. Try again later.")
        } else {
            Step.Done(profile)
        }
    }

    /**
     * One request, with the transport's failure collapsed to null.
     *
     * Every caller here has its own sentence for "this step did not answer",
     * and the transport's own wording is about files and downloads.
     */
    private fun send(
        url: String,
        body: String?,
        contentType: String = JSON_CONTENT_TYPE,
        token: String? = null,
    ): WebResponse? {
        val headers = token?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()
        return when (val result = transport.exchange(url, body, contentType, headers)) {
            is TransportResult.Success -> result.value
            is TransportResult.Failure -> {
                log.warn(CATEGORY, "A sign-in step failed: ${result.reason}")
                null
            }

            TransportResult.Cancelled -> null
        }
    }

    private fun remember(next: SessionState): SessionState {
        state = next
        return next
    }

    private fun initialState(): SessionState =
        if (clientId.isBlank()) {
            SessionState.SignedOut(NOT_CONFIGURED)
        } else {
            SessionState.SignedOut(SIGN_IN_PROMPT)
        }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    /** One step's answer: what it produced, or the sentence the user is shown. */
    private sealed interface Step<out T> {
        data class Done<T>(val value: T) : Step<T>
        data class Failed(val reason: String) : Step<Nothing>
    }

    private companion object {
        const val CATEGORY = "account"
        const val NOT_FOUND = 404
        const val JSON_CONTENT_TYPE = "application/json"
        const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded"

        /**
         * How long a sign-in may sit waiting for the browser. Long enough to
         * find a password and a second factor, short enough that a forgotten
         * sign-in does not hold a socket open for the evening.
         */
        val SIGN_IN_TIMEOUT: Duration = Duration.ofMinutes(5)

        const val SIGN_IN_PROMPT = "Sign in with Microsoft to play."

        val NOT_CONFIGURED = AzureApplication.NOT_CONFIGURED
    }
}
