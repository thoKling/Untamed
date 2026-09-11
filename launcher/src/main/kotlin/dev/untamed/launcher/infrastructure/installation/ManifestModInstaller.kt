package dev.untamed.launcher.infrastructure.installation

import dev.untamed.launcher.application.installation.CancellationSignal
import dev.untamed.launcher.application.installation.InstallationOutcome
import dev.untamed.launcher.application.installation.InstallationProgress
import dev.untamed.launcher.application.installation.InstallationStage
import dev.untamed.launcher.application.installation.ModInstallationPlanner
import dev.untamed.launcher.application.installation.ModInstaller
import dev.untamed.launcher.application.installation.PlannedMods
import dev.untamed.launcher.domain.manifest.Artifact
import dev.untamed.launcher.domain.manifest.SurvivalManifest
import dev.untamed.launcher.infrastructure.filesystem.InstallationPaths
import dev.untamed.launcher.infrastructure.http.HttpTransport
import dev.untamed.launcher.infrastructure.logging.LauncherLog
import java.time.Instant

/**
 * Installs the product's own files: the mod, Fabric API, and any resource or
 * shader packs and configuration the manifest names.
 *
 * Nothing here decides where a file goes or what is stale; that is
 * [ModInstallationPlanner], which is pure. This class does the three things
 * that cannot be pure: download and verify, remove what the update replaces,
 * and write down what happened.
 *
 * The order is download, then delete. It is the safe order: a run that fails
 * halfway leaves the previous version's files still in place and still loading,
 * whereas deleting first would turn a failed download into an installation with
 * no mod in it at all.
 */
class ManifestModInstaller(
    private val paths: InstallationPaths,
    transport: HttpTransport,
    private val stateStore: InstallationStateStore,
    private val log: LauncherLog,
) : ModInstaller {

    private val files = InstallationFiles(paths, transport, log, CATEGORY)

    override fun install(
        manifest: SurvivalManifest,
        cancellation: CancellationSignal,
        onProgress: (InstallationProgress) -> Unit,
    ): InstallationOutcome = runInstallation(log, CATEGORY) {
        runInstall(manifest, cancellation, ProgressReporter(onProgress))
    }

    private fun runInstall(
        manifest: SurvivalManifest,
        cancellation: CancellationSignal,
        reporter: ProgressReporter,
    ): InstallationOutcome {
        val previous = stateStore.read()
        val plan = ModInstallationPlanner.plan(manifest, destinationsIn(previous))

        // A manifest that reached here was validated, so a planning problem is
        // a bug or a document that changed under us. Either way it is not
        // something to install around.
        if (plan.problems.isNotEmpty()) {
            plan.problems.forEach { log.warn(CATEGORY, "Mod planning problem: $it") }
            files.abort(
                "The release manifest describes a file the launcher cannot install.",
                plan.problems.joinToString("; "),
            )
        }

        log.info(
            CATEGORY,
            "Installing ${plan.mods.requests.size} mods and " +
                "${plan.resources.requests.size} resources into ${paths.root}",
        )
        files.createDirectories(
            listOf(
                paths.modsDirectory,
                paths.configDirectory,
                paths.resourcePacksDirectory,
                paths.shaderPacksDirectory,
            ),
        )

        reporter.begin(
            InstallationStage.MODS,
            files = plan.mods.requests.size,
            bytes = plan.mods.totalBytes,
        )
        // Every artifact carries a SHA-256, so an existing file is re-hashed
        // rather than trusted. A mod jar is the one file in the installation
        // most likely to have been swapped by hand.
        files.downloadAll(plan.mods, cancellation, reporter, verifyExisting = true)

        if (plan.resources.requests.isNotEmpty()) {
            reporter.begin(
                InstallationStage.RESOURCES,
                files = plan.resources.requests.size,
                bytes = plan.resources.totalBytes,
            )
            files.downloadAll(plan.resources, cancellation, reporter, verifyExisting = true)
        }

        removeObsolete(plan, reporter)

        reporter.stage(InstallationStage.FINISHING, "recording what was installed")
        recordState(manifest, previous)

        val summary = "${manifest.mod.displayName} ${manifest.mod.version} is installed"
        log.info(CATEGORY, summary)
        return InstallationOutcome.Completed(summary)
    }

    /**
     * Removes the files the previous version left that this one does not want.
     *
     * This is not tidiness. Fabric loads every jar in `mods/`, so an old mod jar
     * sitting beside the new one is the same mod loaded twice and a crash before
     * the main menu.
     *
     * A file that will not delete is logged and the installation continues. The
     * alternative is failing an installation whose downloads all succeeded, and
     * the usual cause is a file the user has open rather than anything wrong
     * with the release.
     */
    private fun removeObsolete(plan: PlannedMods, reporter: ProgressReporter) {
        if (plan.obsolete.isEmpty()) return
        reporter.begin(InstallationStage.CLEANUP, files = plan.obsolete.size, bytes = 0)
        for (destination in plan.obsolete) {
            reporter.detail(destination)
            if (files.delete(destination)) {
                log.info(CATEGORY, "Removed $destination, which this update replaces")
            }
            reporter.finishFile()
        }
    }

    /**
     * The destinations the last run installed, as the planner understands them.
     *
     * Entries written before the state file recorded a path are read as living
     * in the directory that launcher would have used, so an upgrade still knows
     * what to clean up rather than silently leaking the old jar.
     */
    private fun destinationsIn(state: InstallationState): Set<String> = buildSet {
        listOfNotNull(state.fabricApi, state.mod).forEach {
            add(it.path ?: "${ModInstallationPlanner.MODS_DIRECTORY}/${it.fileName}")
        }
        state.resources.forEach {
            add(it.path ?: "${ModInstallationPlanner.RESOURCE_PACKS_DIRECTORY}/${it.fileName}")
        }
    }

    /**
     * Records the product's files and leaves Minecraft and Fabric alone. The
     * state file is one record shared with the game installer, and this half of
     * the installation knows nothing about that half.
     */
    private fun recordState(manifest: SurvivalManifest, previous: InstallationState) {
        // The state file records Fabric API separately because the home screen
        // reports it as a component of its own. It is found by the id the
        // manifest gives it, not by position, so adding a second dependency
        // does not quietly rename what the launcher thinks Fabric API is.
        val fabricApi = manifest.dependencies.firstOrNull { it.id == FABRIC_API_ID }

        stateStore.write(
            previous.copy(
                schemaVersion = InstallationState.SCHEMA_VERSION,
                fabricApi = fabricApi?.let {
                    record(it, ModInstallationPlanner.modDestination(it))
                },
                mod = record(
                    manifest.mod,
                    ModInstallationPlanner.modDestination(manifest.mod),
                ),
                resources = manifest.resources.map {
                    record(it.artifact, ModInstallationPlanner.resourceDestination(it))
                },
                installedAt = Instant.now().toString(),
            ),
        )
    }

    private fun record(artifact: Artifact, destination: String): InstalledFile = InstalledFile(
        id = artifact.id,
        version = artifact.version,
        fileName = artifact.fileName,
        path = destination,
    )

    private companion object {
        const val CATEGORY = "mods"
        const val FABRIC_API_ID = "fabric-api"
    }
}
