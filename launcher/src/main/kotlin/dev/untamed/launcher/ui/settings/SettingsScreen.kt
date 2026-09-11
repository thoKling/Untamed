package dev.untamed.launcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.untamed.launcher.application.account.SessionState
import dev.untamed.launcher.branding.Branding
import dev.untamed.launcher.domain.settings.LaunchBehaviour
import dev.untamed.launcher.domain.settings.LauncherSettings
import dev.untamed.launcher.infrastructure.filesystem.HostSystem
import dev.untamed.launcher.infrastructure.filesystem.LauncherDirectories
import dev.untamed.launcher.infrastructure.manifest.Distribution
import dev.untamed.launcher.ui.LauncherStore
import dev.untamed.launcher.ui.Screen
import dev.untamed.launcher.ui.components.Panel
import dev.untamed.launcher.ui.components.QuietButton
import dev.untamed.launcher.ui.components.SectionLabel
import dev.untamed.launcher.ui.components.SettingRow
import dev.untamed.launcher.ui.theme.Palette

@Composable
fun SettingsScreen(store: LauncherStore, modifier: Modifier = Modifier) {
    val settings = store.settings
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InstallationSection(store, settings)
        MinecraftSection(store, settings)
        LauncherSection(store, settings)
        AccountSection(store)
        AboutSection()
    }
}

@Composable
private fun InstallationSection(store: LauncherStore, settings: LauncherSettings) {
    Panel(Modifier.fillMaxWidth()) {
        SectionLabel("Installation")
        Spacer(Modifier.height(12.dp))

        Text(
            text = "Minecraft, Fabric and the mod are installed here. The launcher owns this " +
                "directory and never writes to an existing Minecraft installation.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = settings.installationDirectory,
                onValueChange = { store.update(settings.copy(installationDirectory = it)) },
                singleLine = true,
                label = { Text("Directory") },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            QuietButton(
                label = "Browse",
                onClick = {
                    val chosen = FilePickers.chooseDirectory(
                        title = "Choose the Untamed installation directory",
                        startingAt = settings.installationDirectory,
                    )
                    if (chosen != null) {
                        store.update(settings.copy(installationDirectory = chosen))
                    }
                },
            )
            QuietButton(
                label = "Default",
                onClick = {
                    val fallback = LauncherDirectories.defaultInstallationDirectory.toString()
                    store.update(settings.copy(installationDirectory = fallback))
                },
            )
        }
    }
}

@Composable
private fun MinecraftSection(store: LauncherStore, settings: LauncherSettings) {
    Panel(Modifier.fillMaxWidth()) {
        SectionLabel("Minecraft")
        Spacer(Modifier.height(12.dp))

        val physical = remember { HostSystem.physicalMemoryMegabytes() }
        val memoryDescription =
            if (physical == null) "Given to the game as -Xmx" else "This machine has $physical MB"
        SettingRow(
            label = "Memory: " + settings.memoryMegabytes + " MB",
            description = memoryDescription,
        ) {
            Slider(
                value = settings.memoryMegabytes.toFloat(),
                onValueChange = { store.update(settings.copy(memoryMegabytes = it.toInt())) },
                valueRange = LauncherSettings.MINIMUM_MEMORY_MB.toFloat()..MEMORY_SLIDER_MAX,
                steps = MEMORY_STEPS,
                modifier = Modifier.width(280.dp),
            )
        }

        val requiredJava = store.gameConfiguration?.javaMajorVersion
        // What the launcher found, not what it hopes is there. Leaving the box
        // empty means it picks; the line below then says which one it picked.
        val javaDescription = store.java?.describe() ?: when (requiredJava) {
            null -> "Looking for a Java runtime…"
            else -> "Java $requiredJava is required"
        }
        SettingRow(label = "Java executable", description = javaDescription) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = settings.javaExecutable.orEmpty(),
                    onValueChange = { store.update(settings.copy(javaExecutable = it)) },
                    singleLine = true,
                    placeholder = { Text("Automatic") },
                    modifier = Modifier.width(260.dp),
                )
                Spacer(Modifier.width(8.dp))
                QuietButton(
                    label = "Browse",
                    onClick = {
                        val chosen = FilePickers.chooseFile(
                            title = "Choose a Java executable",
                            startingAt = settings.javaExecutable,
                        )
                        if (chosen != null) {
                            store.update(settings.copy(javaExecutable = chosen))
                            store.redetectJava()
                        }
                    },
                )
                QuietButton(label = "Detect", onClick = store::redetectJava)
            }
        }

        SettingRow(label = "Resolution", description = "The size the game window opens at") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(settings.windowWidth) {
                    store.update(settings.copy(windowWidth = it))
                }
                Spacer(Modifier.width(8.dp))
                Text("x", color = Palette.TextFaint)
                Spacer(Modifier.width(8.dp))
                NumberField(settings.windowHeight) {
                    store.update(settings.copy(windowHeight = it))
                }
            }
        }

        SettingRow(label = "Fullscreen", description = "Start the game in fullscreen") {
            Switch(
                checked = settings.fullscreen,
                onCheckedChange = { store.update(settings.copy(fullscreen = it)) },
            )
        }

        SettingRow(
            label = "JVM arguments",
            description = "Added after the launcher's own arguments, separated by spaces",
        ) {
            OutlinedTextField(
                value = settings.extraJvmArguments,
                onValueChange = { store.update(settings.copy(extraJvmArguments = it)) },
                singleLine = true,
                placeholder = { Text("-XX:+UseG1GC") },
                modifier = Modifier.width(340.dp),
            )
        }
    }
}

@Composable
private fun LauncherSection(store: LauncherStore, settings: LauncherSettings) {
    Panel(Modifier.fillMaxWidth()) {
        SectionLabel("Launcher")
        Spacer(Modifier.height(12.dp))

        SettingRow(
            label = "Automatic updates",
            description = "Check the manifest and update the mod before playing",
        ) {
            Switch(
                checked = settings.automaticUpdates,
                onCheckedChange = { store.update(settings.copy(automaticUpdates = it)) },
            )
        }

        SettingRow(
            label = "When the game starts",
            description = "What this window does once Minecraft is running",
        ) {
            Row {
                LaunchBehaviour.entries.forEach { behaviour ->
                    QuietButton(
                        label = behaviour.label(),
                        selected = settings.launchBehaviour == behaviour,
                        onClick = { store.update(settings.copy(launchBehaviour = behaviour)) },
                    )
                }
            }
        }

        SettingRow(label = "Logs", description = LauncherDirectories.logDirectory.toString()) {
            QuietButton(label = "Open logs", onClick = { store.show(Screen.DIAGNOSTICS) })
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Where the release manifest is fetched from. Leave it empty for the " +
                "address this launcher shipped with. The bundled copy is used whenever " +
                "the address cannot be read.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = settings.manifestUrl.orEmpty(),
            onValueChange = { store.update(settings.copy(manifestUrl = it)) },
            singleLine = true,
            label = { Text("Manifest address") },
            placeholder = { Text(Distribution.MANIFEST_URL) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Who the game would be started as, and the two sentences the user is owed
 * about it: that the password is typed on Microsoft's page and not this one,
 * and what becomes of the sign-in when the launcher closes.
 *
 * The same control is in the top bar, because signing in is something a person
 * does on their way to playing rather than a setting. It is repeated here
 * because this is where someone looks when they want to know what the launcher
 * keeps, and an account they cannot sign out of from that page is a bad answer.
 */
@Composable
private fun AccountSection(store: LauncherStore) {
    val account = store.session
    Panel(Modifier.fillMaxWidth()) {
        SectionLabel("Account")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = when (account) {
                        is SessionState.SignedIn -> "Signed in as ${account.session.userName}"
                        is SessionState.SignedOut -> "Not signed in"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        store.signingIn -> store.signInStage?.message ?: "Signing in…"
                        account is SessionState.SignedOut -> account.reason
                        else -> "This account owns Minecraft: Java Edition."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextFaint,
                )
            }
            Spacer(Modifier.width(12.dp))
            when {
                store.signingIn -> QuietButton(
                    label = "Cancel",
                    onClick = store::cancelSignIn,
                )

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
        Spacer(Modifier.height(12.dp))
        Text(
            text = Branding.ACCOUNT_NOTICE,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = store.sessionPersistence,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
    }
}

@Composable
private fun AboutSection() {
    Panel(Modifier.fillMaxWidth()) {
        SectionLabel("About")
        Spacer(Modifier.height(12.dp))
        Text(
            text = Branding.PRODUCT_NAME + " launcher " + Branding.LAUNCHER_VERSION,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = Branding.DISCLAIMER,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Settings file: " + LauncherDirectories.settingsFile,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextFaint,
        )
    }
}

/**
 * A field that keeps what the user typed while they are typing.
 *
 * Settings are clamped when they are saved, so feeding every keystroke straight
 * back into the field would rewrite "6" as "640" mid-word.
 */
@Composable
private fun NumberField(value: Int, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            val digits = typed.filter { it.isDigit() }.take(5)
            text = digits
            val parsed = digits.toIntOrNull()
            if (parsed != null) onChange(parsed)
        },
        singleLine = true,
        modifier = Modifier.width(110.dp),
    )
}

private fun LaunchBehaviour.label(): String = when (this) {
    LaunchBehaviour.KEEP_OPEN -> "Stay open"
    LaunchBehaviour.HIDE -> "Hide"
    LaunchBehaviour.CLOSE -> "Close"
}

private const val MEMORY_SLIDER_MAX = 16384f
private const val MEMORY_STEPS = 29
