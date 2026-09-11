package dev.survivaloverhaul.launcher.domain.account

/**
 * How far a sign-in has got.
 *
 * Every step is a separate round trip to a different service, and each of them
 * can fail on its own, so the user is told which one is happening rather than
 * watching one spinner for all six. [message] is what the UI shows; there is no
 * second copy of these words anywhere.
 */
enum class SignInStage(val message: String) {
    OPENING_BROWSER("Opening Microsoft sign-in in your browser…"),
    WAITING_FOR_BROWSER("Waiting for you to finish signing in…"),
    EXCHANGING_CODE("Completing the Microsoft sign-in…"),
    XBOX_LIVE("Signing in to Xbox Live…"),
    XSTS("Checking the Xbox Live account…"),
    MINECRAFT("Signing in to Minecraft services…"),
    OWNERSHIP("Checking that this account owns Minecraft: Java Edition…"),
    PROFILE("Reading the player profile…"),
}
