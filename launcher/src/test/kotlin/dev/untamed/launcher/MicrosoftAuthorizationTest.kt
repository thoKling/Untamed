package dev.untamed.launcher

import dev.untamed.launcher.application.account.AuthorizationResponse
import dev.untamed.launcher.application.account.MicrosoftAuthorization
import java.net.URI
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The half of sign-in where a mistake is a security problem rather than a
 * failed launch, so it is the half that is tested exhaustively.
 */
class MicrosoftAuthorizationTest {

    @Test
    fun `asks for a code with PKCE and an account chooser`() {
        val query = parametersOf(authorizeUrl())

        assertEquals("code", query["response_type"])
        assertEquals("query", query["response_mode"])
        assertEquals("S256", query["code_challenge_method"])
        assertEquals(CHALLENGE, query["code_challenge"])
        assertEquals(STATE, query["state"])
        assertEquals(CLIENT_ID, query["client_id"])
        assertEquals(REDIRECT_URI, query["redirect_uri"])
        assertEquals(SCOPE, query["scope"])
        assertEquals("select_account", query["prompt"])
    }

    /**
     * The redirect and the scope both contain characters that end a query if
     * they travel unencoded, and the redirect has to arrive at Microsoft
     * character for character or the registration will not match it.
     */
    @Test
    fun `encodes the values that would otherwise end the query`() {
        val url = authorizeUrl()

        assertTrue(url.contains("redirect_uri=http%3A%2F%2Flocalhost%3A54321%2F"), url)
        assertTrue(url.contains("scope=XboxLive.signin+offline_access"), url)
    }

    @Test
    fun `a code with the expected state is granted`() {
        val response = MicrosoftAuthorization.read("/?code=abc123&state=$STATE", STATE)

        assertIs<AuthorizationResponse.Granted>(response)
        assertEquals("abc123", response.code)
    }

    @Test
    fun `decodes a code that arrived percent-encoded`() {
        val response = MicrosoftAuthorization.read("/?code=a%2Fb%2Bc&state=$STATE", STATE)

        assertIs<AuthorizationResponse.Granted>(response)
        assertEquals("a/b+c", response.code)
    }

    /**
     * A response carrying someone else's state is someone else's sign-in, and
     * the code in it is theirs. It is refused before it is read.
     */
    @Test
    fun `a code with the wrong state is refused`() {
        val response = MicrosoftAuthorization.read("/?code=abc123&state=somebody-else", STATE)

        assertIs<AuthorizationResponse.Denied>(response)
        assertTrue(response.reason.contains("did not match"), response.reason)
    }

    @Test
    fun `a response with no state at all is refused`() {
        val response = MicrosoftAuthorization.read("/?code=abc123", STATE)

        assertIs<AuthorizationResponse.Denied>(response)
    }

    /**
     * The state is checked before the error is read, so that a refusal cannot
     * be used to get the launcher to act on a response that is not its own.
     */
    @Test
    fun `an error carrying the wrong state is refused as a mismatch`() {
        val response = MicrosoftAuthorization.read("/?error=access_denied&state=theirs", STATE)

        assertIs<AuthorizationResponse.Denied>(response)
        assertTrue(response.reason.contains("did not match"), response.reason)
    }

    @Test
    fun `a cancelled sign-in is explained without Microsoft's wording`() {
        val response = MicrosoftAuthorization.read(
            "/?error=access_denied&error_description=AADSTS65004&state=$STATE",
            STATE,
        )

        assertIs<AuthorizationResponse.Denied>(response)
        assertTrue(response.reason.contains("cancelled"), response.reason)
    }

    /**
     * An error this launcher has never seen carries Microsoft's own
     * description, because paraphrasing one helps nobody.
     */
    @Test
    fun `an unfamiliar error carries the description it came with`() {
        val response = MicrosoftAuthorization.read(
            "/?error=server_error&error_description=Something+broke+at+Microsoft&state=$STATE",
            STATE,
        )

        assertIs<AuthorizationResponse.Denied>(response)
        assertTrue(response.reason.contains("Something broke at Microsoft"), response.reason)
    }

    @Test
    fun `an unfamiliar error with no description still names itself`() {
        val response = MicrosoftAuthorization.read("/?error=server_error&state=$STATE", STATE)

        assertIs<AuthorizationResponse.Denied>(response)
        assertTrue(response.reason.contains("server_error"), response.reason)
    }

    @Test
    fun `a redirect with neither a code nor an error is refused`() {
        val response = MicrosoftAuthorization.read("/?state=$STATE", STATE)

        assertIs<AuthorizationResponse.Denied>(response)
        assertTrue(response.reason.contains("no authorization code"), response.reason)
    }

    @Test
    fun `an empty code is not a code`() {
        val response = MicrosoftAuthorization.read("/?code=&state=$STATE", STATE)

        assertIs<AuthorizationResponse.Denied>(response)
    }

    /**
     * A duplicate parameter is the shape of an attempt to slip a second value
     * past a check that read the first, so the first value is the only one.
     */
    @Test
    fun `a repeated code keeps the first value`() {
        val response = MicrosoftAuthorization.read(
            "/?code=first&code=second&state=$STATE",
            STATE,
        )

        assertIs<AuthorizationResponse.Granted>(response)
        assertEquals("first", response.code)
    }

    @Test
    fun `a redirect with no query at all is refused`() {
        val response = MicrosoftAuthorization.read("/", STATE)

        assertIs<AuthorizationResponse.Denied>(response)
    }

    private fun authorizeUrl(): String = MicrosoftAuthorization.authorizeUrl(
        endpoint = ENDPOINT,
        clientId = CLIENT_ID,
        redirectUri = REDIRECT_URI,
        scope = SCOPE,
        challenge = CHALLENGE,
        state = STATE,
    )

    private fun parametersOf(url: String): Map<String, String> =
        URI.create(url).rawQuery.split('&').associate { pair ->
            pair.substringBefore('=') to
                URLDecoder.decode(pair.substringAfter('='), Charsets.UTF_8)
        }

    private companion object {
        const val ENDPOINT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize"
        const val CLIENT_ID = "00000000-0000-0000-0000-000000000001"
        const val REDIRECT_URI = "http://localhost:54321/"
        const val SCOPE = "XboxLive.signin offline_access"
        const val CHALLENGE = "a-challenge"
        const val STATE = "a-state"
    }
}
