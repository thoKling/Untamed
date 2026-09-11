package dev.survivaloverhaul.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.survivaloverhaul.launcher.application.account.SessionState
import dev.survivaloverhaul.launcher.branding.Branding
import dev.survivaloverhaul.launcher.infrastructure.logging.LauncherLog
import dev.survivaloverhaul.launcher.ui.components.QuietButton
import dev.survivaloverhaul.launcher.ui.diagnostics.DiagnosticsScreen
import dev.survivaloverhaul.launcher.ui.home.HomeScreen
import dev.survivaloverhaul.launcher.ui.settings.SettingsScreen
import dev.survivaloverhaul.launcher.ui.theme.Palette

/**
 * The window frame: navigation, the current screen, and the notice that has to
 * be on screen wherever the user is.
 */
@Composable
fun LauncherApp(store: LauncherStore, log: LauncherLog) {
    LaunchedEffect(Unit) { store.refresh() }

    Box(Modifier.fillMaxSize().background(Palette.windowBackground)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 22.dp)) {
            TopBar(store)
            Spacer(Modifier.height(22.dp))
            Box(Modifier.weight(1f)) {
                when (store.screen) {
                    Screen.HOME -> HomeScreen(store)
                    Screen.SETTINGS -> SettingsScreen(store)
                    Screen.DIAGNOSTICS -> DiagnosticsScreen(store, log)
                }
            }
            Spacer(Modifier.height(16.dp))
            Disclaimer()
        }
    }
}

@Composable
private fun TopBar(store: LauncherStore) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Screen.entries.forEach { screen ->
            QuietButton(
                label = screen.title,
                selected = store.screen == screen,
                onClick = { store.show(screen) },
            )
        }
        Spacer(Modifier.weight(1f))
        Account(store)
    }
}

/**
 * Who is signed in, and the one button that changes it.
 *
 * Driven by the session rather than written here, so there is one answer to
 * "who is signed in" and the top bar cannot claim something the Play button
 * disagrees with.
 */
@Composable
private fun Account(store: LauncherStore) {
    val account = store.session
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = when (account) {
                is SessionState.SignedIn -> "Player: ${account.session.userName}"
                is SessionState.SignedOut -> "Player: not signed in"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
        )
        Text(
            text = when {
                store.signingIn -> store.signInStage?.message ?: "Signing in…"
                account is SessionState.SignedIn -> Branding.UNOFFICIAL_LABEL
                account is SessionState.SignedOut -> account.reason
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
    }
    Spacer(Modifier.width(12.dp))
    when {
        store.signingIn -> QuietButton(label = "Cancel", onClick = store::cancelSignIn)

        account is SessionState.SignedIn -> QuietButton(
            label = "Sign out",
            enabled = !store.running,
            onClick = store::signOut,
        )

        else -> QuietButton(
            label = "Sign in",
            enabled = !store.running,
            onClick = store::signIn,
        )
    }
}

@Composable
private fun Disclaimer() {
    Text(
        text = Branding.DISCLAIMER,
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextFaint,
    )
}
