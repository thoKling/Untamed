package dev.survivaloverhaul.launcher.domain.account

/**
 * The player the game is started as.
 *
 * There is deliberately no way to invent one of these. Every field comes from a
 * completed Microsoft sign-in and an ownership check, and the launcher offers
 * no offline account, no "play without signing in" and no way to paste a token
 * in by hand. Those are all the same thing wearing different words, and the
 * thing is a bypass of the check that the person playing owns the game.
 *
 * [accessToken] is a secret with one destination: the argument list of the
 * process being started. It is never logged, never written to the state file
 * and never included in a diagnostics report, which is why [toString] does not
 * carry it either.
 */
data class PlayerSession(
    val userName: String,
    val uuid: String,
    val accessToken: String,
    val userType: String = "msa",
    val xuid: String = "",
    val clientId: String = "",
) {
    override fun toString(): String = "PlayerSession($userName, $uuid)"
}
