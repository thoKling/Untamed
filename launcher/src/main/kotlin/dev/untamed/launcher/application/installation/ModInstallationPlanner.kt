package dev.untamed.launcher.application.installation

import dev.untamed.launcher.domain.download.Checksum
import dev.untamed.launcher.domain.download.DownloadBatch
import dev.untamed.launcher.domain.download.DownloadRequest
import dev.untamed.launcher.domain.manifest.Artifact
import dev.untamed.launcher.domain.manifest.ResourceArtifact
import dev.untamed.launcher.domain.manifest.ResourceKind
import dev.untamed.launcher.domain.manifest.SurvivalManifest

/**
 * The files the product itself is made of, and the ones it has outgrown.
 *
 * [obsolete] holds relative destinations that an earlier version of the product
 * put there and this one does not want. It is separate from the downloads
 * because removing a file is the one part of installing that destroys
 * something, and it should be visible on its own rather than buried in a batch.
 */
data class PlannedMods(
    val mods: DownloadBatch,
    val resources: DownloadBatch,
    val obsolete: List<String>,
    val problems: List<String>,
) {
    val requests: List<DownloadRequest> get() = mods.requests + resources.requests

    val count: Int get() = mods.requests.size + resources.requests.size

    val totalBytes: Long get() = mods.totalBytes + resources.totalBytes

    val isEmpty: Boolean get() = count == 0 && obsolete.isEmpty()
}

/**
 * Turns a manifest into the exact list of product files an installation needs.
 *
 * Pure, like [GameInstallationPlanner], and for the same reason: where a file
 * lands and whether an older file has to go are decisions, and decisions are
 * worth testing without a disk.
 *
 * Every URL it produces came from the manifest, and the manifest was validated
 * before it reached here. The planner checks each artifact again anyway. It
 * costs nothing, and a rule that only holds because some other component
 * remembered to apply it is not a rule the next person can rely on.
 */
object ModInstallationPlanner {

    const val MODS_DIRECTORY = "mods"
    const val RESOURCE_PACKS_DIRECTORY = "resourcepacks"
    const val SHADER_PACKS_DIRECTORY = "shaderpacks"
    const val CONFIGURATION_DIRECTORY = "config"

    /**
     * The only directories this planner will write into or clean out.
     *
     * Cleanup deletes files, and the list of what to delete is derived from a
     * state file that a user can edit. This set is what stops an entry in that
     * file from pointing the launcher at a version jar, a library, or anything
     * else it did not put there itself.
     */
    val MANAGED_DIRECTORIES: Set<String> = setOf(
        MODS_DIRECTORY,
        RESOURCE_PACKS_DIRECTORY,
        SHADER_PACKS_DIRECTORY,
        CONFIGURATION_DIRECTORY,
    )

    /**
     * What to install, given [previouslyInstalled] relative destinations from
     * the last time the launcher wrote to this directory.
     */
    fun plan(manifest: SurvivalManifest, previouslyInstalled: Set<String>): PlannedMods {
        val mods = mutableListOf<DownloadRequest>()
        val resources = mutableListOf<DownloadRequest>()
        val problems = mutableListOf<String>()

        // Dependencies before the mod, because a mod whose dependency failed to
        // arrive is worse than no mod at all.
        for (dependency in manifest.dependencies) {
            add(dependency, MODS_DIRECTORY, mods, problems)
        }
        add(manifest.mod, MODS_DIRECTORY, mods, problems)
        for (resource in manifest.resources) {
            add(resource.artifact, directoryFor(resource.kind), resources, problems)
        }

        val wanted = (mods + resources).mapTo(HashSet()) { it.destination }
        val obsolete = previouslyInstalled
            .filter { it !in wanted && it.substringBefore('/') in MANAGED_DIRECTORIES }
            .sorted()

        return PlannedMods(
            mods = DownloadBatch(mods),
            resources = DownloadBatch(resources),
            obsolete = obsolete,
            problems = problems,
        )
    }

    /** Where one mod jar belongs, and what the state file records for it. */
    fun modDestination(artifact: Artifact): String = "$MODS_DIRECTORY/${artifact.fileName}"

    fun resourceDestination(resource: ResourceArtifact): String =
        "${directoryFor(resource.kind)}/${resource.artifact.fileName}"

    /**
     * Minecraft decides these names, not this launcher. A resource pack is only
     * offered to the player if it is in `resourcepacks`, and a shader pack only
     * if the shader mod finds it in `shaderpacks`.
     */
    fun directoryFor(kind: ResourceKind): String = when (kind) {
        ResourceKind.RESOURCE_PACK -> RESOURCE_PACKS_DIRECTORY
        ResourceKind.SHADER_PACK -> SHADER_PACKS_DIRECTORY
        ResourceKind.CONFIGURATION -> CONFIGURATION_DIRECTORY
    }

    private fun add(
        artifact: Artifact,
        directory: String,
        into: MutableList<DownloadRequest>,
        problems: MutableList<String>,
    ) {
        val request = DownloadRequest(
            label = artifact.displayName.ifBlank { artifact.id },
            url = artifact.url,
            destination = "$directory/${artifact.fileName}",
            // The manifest publishes SHA-256 for everything in it, so unlike a
            // Fabric library there is never a second request to find a digest.
            checksum = Checksum.sha256(artifact.sha256),
        )
        val faults = artifact.problems() + request.problems()
        if (faults.isEmpty()) into += request else problems += faults
    }
}
