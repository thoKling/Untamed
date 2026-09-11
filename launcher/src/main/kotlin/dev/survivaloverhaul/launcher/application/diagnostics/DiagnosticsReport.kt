package dev.survivaloverhaul.launcher.application.diagnostics

import dev.survivaloverhaul.launcher.domain.game.GameConfiguration
import dev.survivaloverhaul.launcher.domain.installation.InstallationSnapshot
import dev.survivaloverhaul.launcher.domain.java.JavaSelection
import dev.survivaloverhaul.launcher.domain.settings.LauncherSettings

/**
 * The text behind "Copy diagnostics".
 *
 * Assembled from values that are already on screen, and from nothing else. No
 * credential reaches it: the launch command it prints was redacted by the plan
 * that produced it, and there is no unredacted form for a caller to pass in by
 * mistake. What it does carry, once a launch has happened, is the player name
 * and account id the game was started with, both of which every server the
 * player joins already sees.
 */
object DiagnosticsReport {

    fun build(
        launcherVersion: String,
        hostDescription: String,
        launcherJavaRuntime: String,
        settings: LauncherSettings,
        manifestOrigin: String?,
        game: GameConfiguration?,
        snapshot: InstallationSnapshot,
        java: JavaSelection?,
        recentLogLines: List<String>,
        account: String,
        launchCommand: String? = null,
    ): String = buildString {
        appendLine("Survival Overhaul launcher diagnostics")
        appendLine("launcher version: $launcherVersion")
        appendLine("host: $hostDescription")
        appendLine("launcher runtime: $launcherJavaRuntime")
        appendLine()

        appendLine("[manifest]")
        appendLine("origin: ${manifestOrigin ?: "none loaded"}")
        if (game != null) {
            appendLine("minecraft: ${game.minecraftVersion} (java ${game.javaMajorVersion})")
            appendLine("fabric loader: ${game.fabricLoaderVersion}")
            appendLine("fabric api: ${game.fabricApiVersion}")
            appendLine("survival overhaul: ${game.survivalOverhaulVersion}")
        }
        appendLine()

        appendLine("[installation]")
        appendLine("directory: ${settings.installationDirectory}")
        if (snapshot.statuses.isEmpty()) {
            appendLine("not inspected")
        } else {
            snapshot.statuses.forEach { status ->
                appendLine(
                    "${status.component.name.lowercase()}: ${status.state.name.lowercase()} " +
                        "(required ${status.requiredVersion}, " +
                        "installed ${status.installedVersion ?: "none"})",
                )
            }
        }
        appendLine()

        appendLine("[java]")
        appendLine(java?.describe() ?: "not detected yet")
        if (java is JavaSelection.Selected) {
            appendLine("found via: ${java.runtime.source.name.lowercase()}")
            appendLine("reports: ${java.runtime.description}")
        }
        appendLine()

        appendLine("[settings]")
        appendLine("memory: ${settings.memoryMegabytes} MB")
        appendLine("java executable: ${settings.javaExecutable ?: "automatic"}")
        appendLine("resolution: ${settings.windowWidth}x${settings.windowHeight}")
        appendLine("fullscreen: ${settings.fullscreen}")
        appendLine("jvm arguments: ${settings.extraJvmArguments.ifBlank { "none" }}")
        appendLine("automatic updates: ${settings.automaticUpdates}")
        appendLine("launch behaviour: ${settings.launchBehaviour.name.lowercase()}")
        appendLine()

        appendLine("[account]")
        // Whether a sign-in has happened and who it is, which is the first
        // question any "it will not start" report has to answer. The name is
        // not a secret and the token that goes with it never leaves the
        // session, let alone reaches this report.
        appendLine(account)
        appendLine()

        appendLine("[launch]")
        // Already redacted by the plan that produced it: the launcher has no
        // way to obtain the unredacted command, which is what keeps this
        // section safe to paste.
        appendLine(launchCommand ?: "the game has not been started in this session")
        appendLine()

        appendLine("[log]")
        if (recentLogLines.isEmpty()) {
            appendLine("no lines")
        } else {
            recentLogLines.takeLast(LOG_LINES).forEach(::appendLine)
        }
    }

    private const val LOG_LINES = 120
}
