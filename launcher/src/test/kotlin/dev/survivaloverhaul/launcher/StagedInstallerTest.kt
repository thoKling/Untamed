package dev.survivaloverhaul.launcher

import dev.survivaloverhaul.launcher.application.installation.CancellationSignal
import dev.survivaloverhaul.launcher.application.installation.GameInstaller
import dev.survivaloverhaul.launcher.application.installation.InstallationOutcome
import dev.survivaloverhaul.launcher.application.installation.InstallationProgress
import dev.survivaloverhaul.launcher.application.installation.ModInstaller
import dev.survivaloverhaul.launcher.application.installation.StagedInstaller
import dev.survivaloverhaul.launcher.domain.manifest.SurvivalManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StagedInstallerTest {

    @Test
    fun `installs the game before the mod`() {
        val order = mutableListOf<String>()
        val installer = StagedInstaller(
            game = game(order, InstallationOutcome.Completed("Minecraft is installed")),
            mods = mods(order, InstallationOutcome.Completed("the mod is installed")),
        )

        installer.install(Fixtures.manifest(), CancellationSignal.NEVER) {}

        assertEquals(listOf("game", "mods"), order)
    }

    @Test
    fun `reports both halves when both succeed`() {
        val installer = StagedInstaller(
            game = game(mutableListOf(), InstallationOutcome.Completed("Minecraft is installed")),
            mods = mods(mutableListOf(), InstallationOutcome.Completed("the mod is installed")),
        )

        val outcome = installer.install(Fixtures.manifest(), CancellationSignal.NEVER) {}

        assertEquals(
            InstallationOutcome.Completed("Minecraft is installed. the mod is installed"),
            outcome,
        )
    }

    /**
     * Downloading a mod for a game that is not there produces a `mods/` folder
     * that looks right and a launcher that still cannot start anything.
     */
    @Test
    fun `does not install the mod when the game failed`() {
        val order = mutableListOf<String>()
        val failure = InstallationOutcome.Failed("Minecraft could not be installed")
        val installer = StagedInstaller(
            game = game(order, failure),
            mods = mods(order, InstallationOutcome.Completed("the mod is installed")),
        )

        val outcome = installer.install(Fixtures.manifest(), CancellationSignal.NEVER) {}

        assertEquals(failure, outcome)
        assertTrue("mods" !in order, "the mod installer should not have run")
    }

    @Test
    fun `a cancelled game stops the run`() {
        val order = mutableListOf<String>()
        val installer = StagedInstaller(
            game = game(order, InstallationOutcome.Cancelled),
            mods = mods(order, InstallationOutcome.Completed("the mod is installed")),
        )

        val outcome = installer.install(Fixtures.manifest(), CancellationSignal.NEVER) {}

        assertEquals(InstallationOutcome.Cancelled, outcome)
        assertEquals(listOf("game"), order)
    }

    /**
     * The game did land, and saying so would be true. It is not what the user
     * pressed the button for, so the failure is what they are told about.
     */
    @Test
    fun `reports the mod's failure rather than the game's success`() {
        val failure = InstallationOutcome.Failed("the mod could not be downloaded")
        val installer = StagedInstaller(
            game = game(mutableListOf(), InstallationOutcome.Completed("Minecraft is installed")),
            mods = mods(mutableListOf(), failure),
        )

        val outcome = installer.install(Fixtures.manifest(), CancellationSignal.NEVER) {}

        assertEquals(failure, outcome)
    }

    private fun game(order: MutableList<String>, outcome: InstallationOutcome) =
        object : GameInstaller {
            override fun install(
                manifest: SurvivalManifest,
                cancellation: CancellationSignal,
                onProgress: (InstallationProgress) -> Unit,
            ): InstallationOutcome {
                order += "game"
                return outcome
            }
        }

    private fun mods(order: MutableList<String>, outcome: InstallationOutcome) =
        object : ModInstaller {
            override fun install(
                manifest: SurvivalManifest,
                cancellation: CancellationSignal,
                onProgress: (InstallationProgress) -> Unit,
            ): InstallationOutcome {
                order += "mods"
                return outcome
            }
        }
}
