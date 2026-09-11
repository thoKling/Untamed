package dev.survivaloverhaul.launcher.branding

import dev.survivaloverhaul.launcher.BuildInfo

/**
 * Every user-visible name, claim and legal notice, in one file.
 *
 * Kept isolated on purpose. The launcher is an unofficial third-party product,
 * and the rules it has to respect are easier to review when the text that
 * makes the claims is not scattered through the UI.
 *
 * Rules that apply to anything added here:
 *  - the product must never present itself as a Mojang or Microsoft product;
 *  - the disclaimer must stay reachable from the UI without hunting for it;
 *  - no Minecraft artwork, logo, font or asset may be used as our branding.
 */
object Branding {

    const val PRODUCT_NAME: String = "Survival Overhaul"

    const val TAGLINE: String = "Your survival adventure"

    val LAUNCHER_VERSION: String get() = BuildInfo.VERSION

    /**
     * Sent to Minecraft as the launcher brand, which is how a crash report or a
     * support request says where the game was started from.
     */
    const val LAUNCHER_BRAND: String = "survival-overhaul-launcher"

    const val UNOFFICIAL_LABEL: String = "Unofficial launcher"

    const val DISCLAIMER: String =
        "Survival Overhaul is an unofficial Minecraft mod and launcher and is not " +
            "affiliated with or endorsed by Mojang Studios or Microsoft. " +
            "Minecraft is a trademark of Mojang Studios. " +
            "You need your own copy of Minecraft: Java Edition to play."

    /**
     * Shown wherever the launcher explains why it needs a Microsoft sign-in.
     * The launcher never asks for a password and never stores one.
     */
    const val ACCOUNT_NOTICE: String =
        "Signing in happens through Microsoft's own login page. This launcher never " +
            "sees or stores your password."
}
