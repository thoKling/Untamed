package dev.untamed.launcher.application.installation

import dev.untamed.launcher.domain.installation.Component
import dev.untamed.launcher.domain.installation.ComponentState
import dev.untamed.launcher.domain.installation.ComponentStatus
import dev.untamed.launcher.domain.installation.InstallationPlan
import dev.untamed.launcher.domain.installation.InstallationSnapshot
import dev.untamed.launcher.domain.installation.InstallationStep
import dev.untamed.launcher.domain.installation.InstalledComponents
import dev.untamed.launcher.domain.installation.StepAction
import dev.untamed.launcher.domain.manifest.SurvivalManifest

/**
 * Compares what the manifest requires against what is on disk.
 *
 * Pure, and the only place that decides whether something needs installing.
 * Everything that touches the network or the filesystem lives behind
 * [InstallationProbe] and [GameInstaller]. Which files a component is made of
 * is [GameInstallationPlanner]'s business, not this one's: this decides that
 * Minecraft is missing, that decides what missing Minecraft costs to fix.
 */
object InstallationPlanner {

    fun inspect(manifest: SurvivalManifest, installed: InstalledComponents): InstallationSnapshot {
        val statuses = mutableListOf(
            status(Component.MINECRAFT, manifest.game.minecraftVersion, installed.minecraftVersion),
            status(
                Component.FABRIC_LOADER,
                manifest.game.fabricLoaderVersion,
                installed.fabricLoaderVersion,
            ),
            status(
                Component.FABRIC_API,
                manifest.game.fabricApiVersion,
                installed.fabricApiVersion,
            ),
            status(
                Component.UNTAMED,
                manifest.mod.version,
                installed.untamedVersion,
            ),
        )
        if (manifest.resources.isNotEmpty()) {
            statuses += resourceStatus(manifest, installed)
        }
        return InstallationSnapshot(statuses)
    }

    fun plan(snapshot: InstallationSnapshot): InstallationPlan {
        val steps = snapshot.statuses.mapNotNull { status ->
            when (status.state) {
                ComponentState.MISSING ->
                    InstallationStep(status.component, StepAction.INSTALL, status.requiredVersion)

                ComponentState.OUTDATED ->
                    InstallationStep(status.component, StepAction.UPDATE, status.requiredVersion)

                // An uninspected installation produces no plan rather than a
                // plan to reinstall everything.
                ComponentState.UNKNOWN, ComponentState.READY -> null
            }
        }
        return InstallationPlan(steps)
    }

    /**
     * The manifest pins exact versions, so anything other than an exact match
     * is out of date, including a version that is newer than required. A
     * rollback is as much a change as an upgrade.
     */
    private fun status(
        component: Component,
        required: String,
        installed: String?,
    ): ComponentStatus {
        val state = when {
            installed == null -> ComponentState.MISSING
            installed == required -> ComponentState.READY
            else -> ComponentState.OUTDATED
        }
        return ComponentStatus(component, state, required, installed)
    }

    private fun resourceStatus(
        manifest: SurvivalManifest,
        installed: InstalledComponents,
    ): ComponentStatus {
        val required = manifest.resources.associate { it.artifact.id to it.artifact.version }
        val present = installed.resourceVersions
        val state = when {
            present.isEmpty() -> ComponentState.MISSING
            present == required -> ComponentState.READY
            else -> ComponentState.OUTDATED
        }
        return ComponentStatus(
            component = Component.RESOURCES,
            state = state,
            requiredVersion = describe(required),
            installedVersion = if (present.isEmpty()) null else describe(present),
        )
    }

    private fun describe(versions: Map<String, String>): String =
        versions.entries.sortedBy { it.key }.joinToString { "${it.key} ${it.value}" }
}
