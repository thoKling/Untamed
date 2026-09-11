package dev.survivaloverhaul.launcher.infrastructure.account

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * One sign-in's proof that the redirect coming back belongs to the request that
 * went out.
 *
 * [verifier] never leaves the launcher until the code is exchanged, and only
 * [challenge], its SHA-256, travels through the browser. That is the whole of
 * PKCE, and it is what lets a desktop application be a public client with no
 * secret at all: an attacker who intercepts the authorization code cannot spend
 * it without a verifier they never saw.
 */
internal class PkceChallenge(val verifier: String, val challenge: String)

internal object Pkce {

    /**
     * RFC 7636 allows 43 to 128 characters. 64 bytes of randomness encoded is
     * 86 of them, comfortably inside the range and well past guessable.
     */
    private const val VERIFIER_BYTES = 64

    private const val STATE_BYTES = 24

    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    fun challenge(): PkceChallenge {
        val verifier = randomText(VERIFIER_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return PkceChallenge(verifier = verifier, challenge = encoder.encodeToString(digest))
    }

    /**
     * The value the redirect has to carry back. Random per sign-in, so a
     * response from any other one is recognisably not this launcher's.
     */
    fun state(): String = randomText(STATE_BYTES)

    private fun randomText(bytes: Int): String {
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        return encoder.encodeToString(buffer)
    }
}
