package dev.untamed.launcher

import dev.untamed.launcher.application.account.XboxErrors
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every one of these is an ordinary state for an account that has never played
 * Minecraft on this machine, so each has to arrive as something the user can
 * act on rather than as a number to search for.
 */
class XboxErrorsTest {

    @Test
    fun `no Xbox profile says where to make one`() {
        val message = XboxErrors.describe(XboxErrors.NO_XBOX_ACCOUNT)

        assertTrue(message.contains("xbox.com"), message)
        assertFalse(message.contains("2148916233"), message)
    }

    @Test
    fun `a child account says an adult has to act`() {
        val message = XboxErrors.describe(XboxErrors.CHILD_ACCOUNT)

        assertTrue(message.contains("family"), message)
    }

    @Test
    fun `both verification codes give the same answer`() {
        val first = XboxErrors.describe(XboxErrors.VERIFICATION_REQUIRED)
        val second = XboxErrors.describe(XboxErrors.VERIFICATION_REQUIRED_AGAIN)

        assertTrue(first.contains("verification"), first)
        assertTrue(first == second, second)
    }

    @Test
    fun `an unavailable country and a ban both say so`() {
        assertTrue(
            XboxErrors.describe(XboxErrors.UNAVAILABLE_COUNTRY).contains("country"),
        )
        assertTrue(XboxErrors.describe(XboxErrors.BANNED).contains("banned"))
    }

    /**
     * A refusal with no code in the body is the common shape of a service
     * having a bad minute, so it suggests waiting rather than fixing anything.
     */
    @Test
    fun `no code at all asks the user to try later`() {
        val message = XboxErrors.describe(null)

        assertTrue(message.contains("few minutes"), message)
    }

    /**
     * A code this launcher has never seen still has to be printed, because it
     * is the only thing the user could search for or report.
     */
    @Test
    fun `an unknown code is passed through`() {
        val message = XboxErrors.describe(1234L)

        assertTrue(message.contains("1234"), message)
    }

    @Test
    fun `every known code says something different from the fallback`() {
        val fallback = XboxErrors.describe(null)
        val known = listOf(
            XboxErrors.NO_XBOX_ACCOUNT,
            XboxErrors.UNAVAILABLE_COUNTRY,
            XboxErrors.VERIFICATION_REQUIRED,
            XboxErrors.VERIFICATION_REQUIRED_AGAIN,
            XboxErrors.CHILD_ACCOUNT,
            XboxErrors.BANNED,
        )

        known.forEach { code ->
            val message = XboxErrors.describe(code)
            assertTrue(message != fallback, "code $code fell through to the fallback")
            assertFalse(message.contains(code.toString()), message)
        }
    }
}
