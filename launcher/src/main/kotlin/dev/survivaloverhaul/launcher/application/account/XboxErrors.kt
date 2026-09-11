package dev.survivaloverhaul.launcher.application.account

/**
 * What an XSTS refusal means, in words the person in front of the launcher can
 * act on.
 *
 * These are not exceptional. A Microsoft account with no Xbox profile attached,
 * and a child account not yet in a family, are both ordinary states for an
 * account that has never played Minecraft on this machine, and both of them
 * arrive as a 401 with a number in it. Showing the number would leave the user
 * searching for it, which is the launcher making its own problem theirs.
 */
object XboxErrors {

    /** No Xbox Live account is attached to this Microsoft account. */
    const val NO_XBOX_ACCOUNT = 2148916233L

    /** Xbox Live is not available in the account's country or region. */
    const val UNAVAILABLE_COUNTRY = 2148916235L

    /** The account needs adult verification, South Korea. */
    const val VERIFICATION_REQUIRED = 2148916236L

    /** The account needs adult verification. */
    const val VERIFICATION_REQUIRED_AGAIN = 2148916237L

    /** A child account that has to be added to a family first. */
    const val CHILD_ACCOUNT = 2148916238L

    /** The account is banned from Xbox Live. */
    const val BANNED = 2148916227L

    fun describe(code: Long?): String = when (code) {
        NO_XBOX_ACCOUNT ->
            "This Microsoft account has no Xbox profile yet. Create one at xbox.com with " +
                "the same account, then sign in again."

        UNAVAILABLE_COUNTRY ->
            "Xbox Live is not available in the country or region set on this Microsoft account."

        VERIFICATION_REQUIRED, VERIFICATION_REQUIRED_AGAIN ->
            "This account needs to complete adult verification at xbox.com before it can " +
                "sign in."

        CHILD_ACCOUNT ->
            "This is a child account. An adult has to add it to a Microsoft family group " +
                "before it can sign in to Xbox Live."

        BANNED ->
            "This account is banned from Xbox Live, so it cannot sign in to Minecraft services."

        null ->
            "Xbox Live refused the sign-in and gave no reason. Try again in a few minutes."

        else ->
            "Xbox Live refused the sign-in (code $code). Signing in to xbox.com with this " +
                "account usually says why."
    }
}
