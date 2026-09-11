package dev.survivaloverhaul.launcher

import dev.survivaloverhaul.launcher.infrastructure.account.Pkce
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PKCE is the whole reason a launcher can be a public client with no secret in
 * it, so the properties it depends on are worth asserting rather than assuming.
 */
class PkceTest {

    /**
     * RFC 7636 allows 43 to 128 characters from an unreserved set. A verifier
     * outside that range, or with padding in it, is refused by Microsoft at the
     * exchange, which is the worst place to find out.
     */
    @Test
    fun `the verifier is a legal length and carries no padding`() {
        val verifier = Pkce.challenge().verifier

        assertTrue(verifier.length in 43..128, "length ${verifier.length}")
        assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' }, verifier)
    }

    /**
     * The challenge is what travels through the browser; the verifier is what
     * does not. If the challenge were not the digest of the verifier, the
     * exchange would fail after the user had already signed in.
     */
    @Test
    fun `the challenge is the base64url SHA-256 of the verifier`() {
        val pair = Pkce.challenge()

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(pair.verifier.toByteArray(Charsets.US_ASCII))
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)

        assertEquals(expected, pair.challenge)
    }

    @Test
    fun `every sign-in gets its own verifier and its own state`() {
        val verifiers = List(TRIALS) { Pkce.challenge().verifier }
        val states = List(TRIALS) { Pkce.state() }

        assertEquals(TRIALS, verifiers.toSet().size)
        assertEquals(TRIALS, states.toSet().size)
    }

    @Test
    fun `the state is long enough not to be guessed`() {
        val state = Pkce.state()

        assertTrue(state.length >= 32, "length ${state.length}")
    }

    private companion object {
        const val TRIALS = 50
    }
}
