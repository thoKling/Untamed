package dev.untamed.launcher.application.installation

import dev.untamed.launcher.domain.manifest.SurvivalManifest

/**
 * The whole installation, in the only order it can happen in.
 *
 * The game goes first. The mod is a mod of something, and Fabric API is a
 * library for a loader that has to already be on disk for either of them to
 * mean anything. Installing the product's files into a directory with no game
 * in it would produce a `mods/` folder that looks complete and a launcher that
 * still could not start anything.
 *
 * A failed game stops the run rather than continuing to the mod. Spending
 * another download on files that cannot be used, so that the user can be told
 * about two failures instead of one, helps nobody.
 *
 * Pure composition: it performs no IO of its own, so what it decides can be
 * tested against two fakes rather than against a network.
 */
class StagedInstaller(
    private val game: GameInstaller,
    private val mods: ModInstaller,
) : Installer {

    override fun install(
        manifest: SurvivalManifest,
        cancellation: CancellationSignal,
        onProgress: (InstallationProgress) -> Unit,
    ): InstallationOutcome {
        val installed = game.install(manifest, cancellation, onProgress)
        if (installed !is InstallationOutcome.Completed) return installed

        return when (val added = mods.install(manifest, cancellation, onProgress)) {
            is InstallationOutcome.Completed ->
                InstallationOutcome.Completed("${installed.summary}. ${added.summary}")

            // The game did land, and saying so would be true. It would also be
            // the wrong thing to lead with when the reason the user pressed the
            // button was to get the mod, so the failure is what is reported.
            else -> added
        }
    }
}
