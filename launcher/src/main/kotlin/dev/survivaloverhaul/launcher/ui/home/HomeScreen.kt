package dev.survivaloverhaul.launcher.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.survivaloverhaul.launcher.branding.Branding
import dev.survivaloverhaul.launcher.domain.installation.Component
import dev.survivaloverhaul.launcher.domain.installation.ComponentState
import dev.survivaloverhaul.launcher.domain.installation.ComponentStatus
import dev.survivaloverhaul.launcher.domain.java.JavaSelection
import dev.survivaloverhaul.launcher.ui.LauncherStore
import dev.survivaloverhaul.launcher.ui.components.Panel
import dev.survivaloverhaul.launcher.ui.components.Pill
import dev.survivaloverhaul.launcher.ui.components.PlayButton
import dev.survivaloverhaul.launcher.ui.components.ProgressBar
import dev.survivaloverhaul.launcher.ui.components.QuietButton
import dev.survivaloverhaul.launcher.ui.components.SectionLabel
import dev.survivaloverhaul.launcher.ui.components.StatusLine
import dev.survivaloverhaul.launcher.ui.theme.Palette

@Composable
fun HomeScreen(store: LauncherStore, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(1.1f).fillMaxHeight()) {
            Brand(store)
            Spacer(Modifier.weight(1f))
            PlayButton(
                enabled = store.canInstall || store.canPlay,
                label = store.primaryLabel,
                onClick = store::start,
                modifier = Modifier.fillMaxWidth().widthIn(max = 380.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = store.notice ?: store.playHint,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSecondary,
            )
            // Only while something is downloading. A bar that is always there,
            // sitting empty or full, stops meaning anything.
            store.progress?.let { progress ->
                Spacer(Modifier.height(12.dp))
                ProgressBar(progress.fraction, Modifier.widthIn(max = 380.dp))
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.widthIn(max = 380.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = progress.transferred(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextFaint,
                    )
                    Spacer(Modifier.weight(1f))
                    QuietButton(label = "Stop", onClick = store::cancelInstall)
                }
            }
        }

        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InstallationPanel(store)
            ServerPanel()
        }
    }
}

@Composable
private fun Brand(store: LauncherStore) {
    Column {
        Text(
            text = Branding.PRODUCT_NAME.uppercase(),
            color = Palette.TextPrimary,
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 5.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = Branding.TAGLINE,
            color = Palette.TextSecondary,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(Branding.UNOFFICIAL_LABEL)
            Spacer(Modifier.width(10.dp))
            store.gameConfiguration?.let { game ->
                Pill("Minecraft ${game.minecraftVersion}", Palette.Ember)
                Spacer(Modifier.width(10.dp))
                Pill("Java ${game.javaMajorVersion}")
            }
        }
    }
}

@Composable
private fun InstallationPanel(store: LauncherStore) {
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Installation")
            Spacer(Modifier.weight(1f))
            QuietButton(
                label = if (store.busy) "Checking…" else "Re-check",
                enabled = !store.busy && !store.installing,
                onClick = store::refresh,
            )
        }
        Spacer(Modifier.height(8.dp))

        val statuses = store.snapshot.statuses
        if (statuses.isEmpty()) {
            StatusLine("Nothing checked yet", store.playHint, Palette.Idle)
        } else {
            statuses.forEach { status ->
                StatusLine(status.label(), status.detail(), status.tone())
            }
        }

        // Java sits with the components because it is one: the game will not
        // start without it, and finding that out at launch is too late.
        store.java?.let { java ->
            StatusLine(
                label = "Java",
                detail = when (java) {
                    is JavaSelection.Selected ->
                        "${java.runtime.majorVersion} from ${java.runtime.source.name.lowercase()}"

                    is JavaSelection.Unsuitable ->
                        "needs ${java.requiredMajor}, chose ${java.runtime.majorVersion}"

                    is JavaSelection.None -> "not found, needs ${java.requiredMajor}"
                },
                tone = when (java) {
                    is JavaSelection.Selected -> Palette.Ready
                    is JavaSelection.Unsuitable -> Palette.Pending
                    is JavaSelection.None -> Palette.Missing
                },
            )
        }
    }
}

@Composable
private fun ServerPanel() {
    Panel(Modifier.fillMaxWidth()) {
        SectionLabel("Server")
        Spacer(Modifier.height(8.dp))
        StatusLine(
            label = "Official server",
            detail = "Not configured yet",
            tone = Palette.Idle,
        )
        Text(
            text = "Server status and direct join arrive in milestone 6.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
    }
}

private fun ComponentStatus.label(): String = when (component) {
    Component.MINECRAFT -> "Minecraft"
    Component.FABRIC_LOADER -> "Fabric Loader"
    Component.FABRIC_API -> "Fabric API"
    Component.SURVIVAL_OVERHAUL -> Branding.PRODUCT_NAME
    Component.RESOURCES -> "Resources"
}

private fun ComponentStatus.detail(): String = when (state) {
    ComponentState.UNKNOWN -> "not checked"
    ComponentState.MISSING -> "missing, needs $requiredVersion"
    ComponentState.OUTDATED -> "${installedVersion.orEmpty()} installed, needs $requiredVersion"
    ComponentState.READY -> requiredVersion
}

private fun ComponentStatus.tone(): Color = when (state) {
    ComponentState.UNKNOWN -> Palette.Idle
    ComponentState.MISSING -> Palette.Missing
    ComponentState.OUTDATED -> Palette.Pending
    ComponentState.READY -> Palette.Ready
}
