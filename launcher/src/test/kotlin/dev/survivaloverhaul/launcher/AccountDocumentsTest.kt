package dev.survivaloverhaul.launcher

import dev.survivaloverhaul.launcher.infrastructure.account.AccountDocuments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The six documents a sign-in passes through, and the one thing every one of
 * them must never do: print a token.
 */
class AccountDocumentsTest {

    @Test
    fun `reads Microsoft's token response`() {
        val token = AccountDocuments.microsoftToken(
            """
            {
              "token_type": "Bearer",
              "expires_in": 3600,
              "access_token": "an-access-token",
              "refresh_token": "a-refresh-token"
            }
            """.trimIndent(),
        )

        assertEquals("an-access-token", token.accessToken)
        assertEquals("a-refresh-token", token.refreshToken)
        assertEquals(3600, token.expiresInSeconds)
    }

    @Test
    fun `reads the user hash and the XUID out of the display claims`() {
        val token = AccountDocuments.xboxToken(
            """
            {
              "IssueInstant": "2026-01-01T00:00:00.0000000Z",
              "Token": "an-xsts-token",
              "DisplayClaims": { "xui": [ { "uhs": "1234567890", "xid": "2535400000000000" } ] }
            }
            """.trimIndent(),
        )

        assertEquals("an-xsts-token", token.token)
        assertEquals("1234567890", token.userHash)
        assertEquals("2535400000000000", token.xuid)
    }

    /**
     * Xbox Live's own response carries no XUID, only the hash. Missing claims
     * have to read as empty rather than throw, because the step that needs
     * them says so much better than a parse failure would.
     */
    @Test
    fun `a token with no display claims reads as empty rather than failing`() {
        val token = AccountDocuments.xboxToken("""{ "Token": "a-token" }""")

        assertEquals("a-token", token.token)
        assertTrue(token.userHash.isEmpty())
        assertTrue(token.xuid.isEmpty())
    }

    @Test
    fun `reads the Minecraft services token`() {
        val token = AccountDocuments.minecraftToken(
            """{ "access_token": "a-minecraft-token", "expires_in": 86400 }""",
        )

        assertEquals("a-minecraft-token", token.accessToken)
        assertEquals(86400, token.expiresInSeconds)
    }

    /**
     * Ownership is "the list is not empty", not a match against product names:
     * Java Edition arrives under several of them and Mojang adds more.
     */
    @Test
    fun `any entitlement at all is ownership`() {
        val owned = AccountDocuments.entitlements(
            """{ "items": [ { "name": "product_minecraft" }, { "name": "game_minecraft" } ] }""",
        )

        assertTrue(owned.ownsJavaEdition())
        assertTrue(owned.describe().contains("product_minecraft"))
    }

    @Test
    fun `an empty entitlement list is not ownership`() {
        val none = AccountDocuments.entitlements("""{ "items": [] }""")

        assertFalse(none.ownsJavaEdition())
        assertEquals("no entitlements", none.describe())
    }

    @Test
    fun `reads the player profile`() {
        val profile = AccountDocuments.profile(
            """{ "id": "0123456789abcdef0123456789abcdef", "name": "Player" }""",
        )

        assertEquals("0123456789abcdef0123456789abcdef", profile.id)
        assertEquals("Player", profile.name)
    }

    @Test
    fun `reads the XSTS refusal code out of an error body`() {
        val error = AccountDocuments.error(
            """{ "Identity": "0", "XErr": 2148916238, "Message": "", "Redirect": "" }""",
        )

        assertEquals(2148916238L, error.xboxErrorCode)
    }

    @Test
    fun `prefers the description over the code when reading an OAuth error`() {
        val error = AccountDocuments.error(
            """{ "error": "invalid_grant", "error_description": "The token expired." }""",
        )

        assertEquals("The token expired.", error.message())
        assertNull(error.xboxErrorCode)
    }

    @Test
    fun `falls back to the error name when there is no description`() {
        val error = AccountDocuments.error("""{ "error": "invalid_grant" }""")

        assertEquals("invalid_grant", error.message())
    }

    /**
     * A failure is a bad enough moment without the launcher being unable to
     * report it because the body was not the JSON it expected.
     */
    @Test
    fun `a body that is not an error document at all is still readable`() {
        val error = AccountDocuments.error("<html>502 Bad Gateway</html>")

        assertTrue(error.message().isEmpty())
        assertNull(error.xboxErrorCode)
    }

    /**
     * Every token-bearing document overrides toString, because a data class
     * that prints its fields is one careless log line away from writing a
     * credential to a file.
     */
    @Test
    fun `no token-bearing document prints its token`() {
        val microsoft = AccountDocuments.microsoftToken(
            """{ "access_token": "secret-access", "refresh_token": "secret-refresh" }""",
        )
        val xbox = AccountDocuments.xboxToken(
            """{ "Token": "secret-xbox", "DisplayClaims": { "xui": [ { "uhs": "99" } ] } }""",
        )
        val minecraft = AccountDocuments.minecraftToken(
            """{ "access_token": "secret-minecraft" }""",
        )

        listOf(microsoft.toString(), xbox.toString(), minecraft.toString())
            .forEach { printed ->
                assertFalse(printed.contains("secret"), printed)
            }
    }
}
