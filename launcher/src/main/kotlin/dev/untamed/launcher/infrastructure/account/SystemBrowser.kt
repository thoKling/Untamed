package dev.untamed.launcher.infrastructure.account

import dev.untamed.launcher.infrastructure.logging.LauncherLog
import java.awt.Desktop
import java.net.URI

/**
 * Hands a URL to whatever browser the user already uses.
 *
 * This is the second and last program the launcher starts, after the Java
 * runtime the game runs on, and like that one it is chosen by the operating
 * system from what is already installed. The launcher neither downloads nor
 * names it.
 *
 * It matters that sign-in happens in the user's own browser rather than in an
 * embedded window: the address bar is how a person can tell that the page
 * asking for their password is Microsoft's, and an embedded browser takes that
 * away from them.
 */
internal class SystemBrowser(private val log: LauncherLog) {

    fun open(url: String): Boolean {
        val desktop = runCatching {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        }.getOrNull()

        if (desktop == null || !desktop.isSupported(Desktop.Action.BROWSE)) {
            log.warn(CATEGORY, "This system offers no way to open a browser")
            return false
        }
        return runCatching { desktop.browse(URI.create(url)) }
            .onFailure { log.warn(CATEGORY, "Could not open a browser: ${it.message}") }
            .isSuccess
    }

    private companion object {
        const val CATEGORY = "account"
    }
}
