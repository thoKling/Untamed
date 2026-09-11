package dev.survivaloverhaul.launcher.application.installation

import dev.survivaloverhaul.launcher.domain.java.JavaRuntime
import dev.survivaloverhaul.launcher.domain.installation.InstalledComponents
import dev.survivaloverhaul.launcher.domain.manifest.SurvivalManifest

/**
 * Where a manifest came from and whether it can be acted on.
 *
 * A manifest is untrusted input, so the only way to obtain one is through a
 * result that forces the caller to deal with it being rejected.
 */
sealed interface ManifestResult {
    val origin: String

    data class Loaded(val manifest: SurvivalManifest, override val origin: String) : ManifestResult

    data class Rejected(val problems: List<String>, override val origin: String) : ManifestResult

    data class Unavailable(val reason: String, override val origin: String) : ManifestResult
}

/**
 * A place a manifest can be read from.
 *
 * There are three: the copy bundled with the launcher, the copy published by
 * the distribution host, and the composite that prefers the second and falls
 * back to the first. Nothing above this port knows which one answered, only
 * what the answer says about itself through [ManifestResult.origin].
 */
fun interface ManifestSource {
    fun load(): ManifestResult
}

/**
 * Reads an installation directory and reports the versions it finds. Returning
 * plain version strings rather than a verdict is deliberate: deciding what the
 * versions mean is [InstallationPlanner]'s job, and that decision is pure.
 */
fun interface InstallationProbe {
    fun inspect(): InstalledComponents
}

/**
 * Finds the Java runtimes on this machine.
 *
 * The port takes the configured executable rather than reading settings itself,
 * so that what it looks at is visible at the call site and so a test can ask
 * for a search with no configured runtime at all.
 */
fun interface JavaRuntimeProvider {
    fun detect(configuredExecutable: String?): List<JavaRuntime>
}

/**
 * A long operation the user is allowed to change their mind about.
 *
 * A plain predicate rather than a coroutine job: nothing below the UI layer in
 * this launcher knows about coroutines, and an installer that can be cancelled
 * without one is an installer that can be tested without one.
 */
fun interface CancellationSignal {
    fun isCancelled(): Boolean

    companion object {
        val NEVER = CancellationSignal { false }
    }
}

/** What became of an installation attempt. */
sealed interface InstallationOutcome {

    /** Everything the installer was asked for is on disk and verified. */
    data class Completed(val summary: String) : InstallationOutcome

    /**
     * [reason] is one sentence for the user; [detail] is for the log. Keeping
     * them apart is what stops a stack trace becoming a status line.
     */
    data class Failed(val reason: String, val detail: String? = null) : InstallationOutcome

    data object Cancelled : InstallationOutcome
}

/**
 * Makes some part of an installation directory match the manifest.
 *
 * Blocking by design. The caller decides which thread it runs on, which is what
 * keeps coroutines out of every layer below the UI.
 *
 * The two ports below split the work by what a failure means rather than by
 * what the code happens to do. Minecraft and Fabric are one thing that either
 * works or does not. The mod and its dependencies are ordinary files that a
 * perfectly good game can be missing. Keeping them apart is what lets the
 * launcher say "the game is installed, the mod is not" instead of one
 * undifferentiated verdict over everything.
 */
interface Installer {
    fun install(
        manifest: SurvivalManifest,
        cancellation: CancellationSignal,
        onProgress: (InstallationProgress) -> Unit,
    ): InstallationOutcome
}

/**
 * Installs the game the manifest describes: Minecraft itself, its libraries and
 * assets, and the Fabric profile that runs on top of them.
 */
interface GameInstaller : Installer

/**
 * Installs the product's own files: the mod, the dependencies it needs and any
 * resource packs, into the directories Minecraft loads them from.
 *
 * It also removes what a previous version left behind. Fabric loads every jar
 * in `mods/`, so an old mod jar sitting beside the new one is not an untidy
 * directory, it is the same mod loaded twice and a crash before the main menu.
 */
interface ModInstaller : Installer
