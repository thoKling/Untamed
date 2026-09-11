package dev.survivaloverhaul.launcher.application.account

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** What came back to the loopback socket when the browser was redirected. */
sealed interface AuthorizationResponse {

    /** The user signed in. [code] is single-use and is exchanged immediately. */
    data class Granted(val code: String) : AuthorizationResponse

    /**
     * No code. A cancelled sign-in, a refused consent, or a response that did
     * not match the request. [reason] is a sentence for the user.
     */
    data class Denied(val reason: String) : AuthorizationResponse
}

/**
 * The first leg of the chain: the authorization request the browser is sent to
 * and the redirect that comes back.
 *
 * Pure on purpose. This is the part of sign-in with no IO in it at all, and it
 * is also the part where a mistake is a security problem rather than a failed
 * launch: a missing `state` check is an attacker able to hand the launcher
 * their own authorization code, and a missing `code_challenge` is a public
 * client with nothing protecting its redirect.
 */
object MicrosoftAuthorization {

    /**
     * Where the browser is sent.
     *
     * `response_mode=query` because the code has to arrive at the loopback
     * socket as a request the launcher can read. A fragment response never
     * leaves the browser.
     */
    fun authorizeUrl(
        endpoint: String,
        clientId: String,
        redirectUri: String,
        scope: String,
        challenge: String,
        state: String,
    ): String {
        val parameters = listOf(
            "client_id" to clientId,
            "response_type" to "code",
            "redirect_uri" to redirectUri,
            "response_mode" to "query",
            "scope" to scope,
            "state" to state,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            // Without this, a second sign-in silently reuses the account the
            // browser is already signed in to, and a user trying to change
            // account has no way to.
            "prompt" to "select_account",
        )
        return endpoint + "?" + parameters.joinToString("&") { (name, value) ->
            "$name=${encode(value)}"
        }
    }

    /**
     * Reads the redirect the browser made.
     *
     * [requestTarget] is the request line's path and query as the loopback
     * socket received it. [expectedState] is what this launcher sent; a
     * response carrying anything else is not this launcher's sign-in and is
     * refused before the code in it is looked at.
     */
    fun read(requestTarget: String, expectedState: String): AuthorizationResponse {
        val query = parameters(requestTarget)

        // Checked first, and checked even on an error response: the only safe
        // thing to do with a response that is not ours is to stop reading it.
        if (query["state"] != expectedState) {
            return AuthorizationResponse.Denied(
                "The sign-in response did not match the request this launcher made. " +
                    "Nothing was accepted from it. Try signing in again.",
            )
        }

        query["error"]?.let { error ->
            val description = query["error_description"].orEmpty()
            return AuthorizationResponse.Denied(describe(error, description))
        }

        val code = query["code"]
        if (code.isNullOrBlank()) {
            return AuthorizationResponse.Denied(
                "Microsoft sent no authorization code back. Try signing in again.",
            )
        }
        return AuthorizationResponse.Granted(code)
    }

    /**
     * The two refusals that are ordinary get their own sentence. Everything
     * else carries Microsoft's own description, because a launcher paraphrasing
     * an error it has never seen helps nobody.
     */
    private fun describe(error: String, description: String): String = when (error) {
        "access_denied" ->
            "The sign-in was cancelled, or this launcher was not given permission."

        "consent_required" ->
            "Microsoft needs you to approve this launcher's access before it can sign you in."

        else -> {
            val detail = description.replace('+', ' ').lines().first().trim()
            if (detail.isBlank()) "Microsoft refused the sign-in ($error)."
            else "Microsoft refused the sign-in: $detail"
        }
    }

    /**
     * The query of a redirect, decoded.
     *
     * Repeated names keep the first value. Nothing here is a list, and a
     * duplicate parameter is the shape of an attempt to smuggle a second value
     * past a check that read the first.
     */
    private fun parameters(requestTarget: String): Map<String, String> {
        val query = requestTarget.substringAfter('?', "")
        if (query.isBlank()) return emptyMap()
        val pairs = query.split('&').mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val name = decode(pair.substringBefore('='))
            val value = decode(pair.substringAfter('=', ""))
            name to value
        }
        return pairs.reversed().toMap()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8) }
            .getOrDefault(value)
}
