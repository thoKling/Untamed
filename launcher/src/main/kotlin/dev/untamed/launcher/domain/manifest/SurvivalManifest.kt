package dev.untamed.launcher.domain.manifest

import dev.untamed.launcher.domain.game.GameConfiguration
import dev.untamed.launcher.domain.versions.Version

/** Which stream of releases an installation follows. */
enum class ReleaseChannel { STABLE, BETA }

/** What a downloaded resource is for, which decides where it is installed. */
enum class ResourceKind { RESOURCE_PACK, SHADER_PACK, CONFIGURATION }

/**
 * A file the launcher downloads, with the checksum that decides whether the
 * download is acceptable. There is no code path that installs an artifact
 * without checking [sha256] first.
 */
data class Artifact(
    val id: String,
    val displayName: String,
    val version: String,
    val url: String,
    val sha256: String,
    val fileName: String,
) {
    fun problems(): List<String> = buildList {
        if (id.isBlank()) add("artifact has no id")
        if (version.isBlank()) add("artifact '$id' has no version")
        if (!url.startsWith("https://")) add("artifact '$id' is not served over https")
        if (!SHA256_PATTERN.matches(sha256)) add("artifact '$id' has no usable sha256")
        if (fileName.isBlank() || fileName.any { it in ILLEGAL_NAME_CHARACTERS }) {
            add("artifact '$id' has an unusable file name")
        }
    }

    private companion object {
        val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")

        // A manifest is untrusted input. A file name is a leaf name and never a
        // path, so anything that could climb out of the install directory is
        // rejected before it is ever joined to a directory.
        val ILLEGAL_NAME_CHARACTERS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    }
}

data class ResourceArtifact(
    val kind: ResourceKind,
    val artifact: Artifact,
)

data class ServerDescriptor(
    val name: String,
    val address: String,
    val port: Int,
    val requiredModVersion: String,
)

/**
 * The whole description of one Untamed release: which Minecraft and
 * Fabric it runs on, which files make it up, and which server it belongs to.
 *
 * This is the shape of the remote manifest, so that shipping a new mod build
 * does not mean shipping a new launcher.
 */
data class SurvivalManifest(
    val schemaVersion: Int,
    val channel: ReleaseChannel,
    val minimumLauncherVersion: String,
    val game: GameConfiguration,
    val mod: Artifact,
    val dependencies: List<Artifact>,
    val resources: List<ResourceArtifact>,
    val server: ServerDescriptor?,
) {
    /** Every downloadable file in the manifest, in installation order. */
    val artifacts: List<Artifact>
        get() = dependencies + mod + resources.map { it.artifact }

    fun supportsLauncher(launcherVersion: String): Boolean =
        Version.atLeast(launcherVersion, minimumLauncherVersion)

    /**
     * Everything wrong with this manifest, empty when it is safe to act on.
     * Called on every manifest the launcher reads, local or remote, before any
     * of it reaches the installer.
     */
    fun problems(): List<String> = buildList {
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            add("manifest schema version $schemaVersion is not supported")
        }
        if (game.minecraftVersion.isBlank()) add("no Minecraft version")
        if (game.javaMajorVersion < 8) add("implausible Java version ${game.javaMajorVersion}")
        if (game.fabricLoaderVersion.isBlank()) add("no Fabric Loader version")
        if (game.fabricApiVersion.isBlank()) add("no Fabric API version")
        if (mod.version != game.untamedVersion) {
            add("mod version ${mod.version} disagrees with the declared game configuration")
        }
        addAll(artifacts.flatMap { it.problems() })
        server?.let {
            if (it.address.isBlank()) add("server has no address")
            if (it.port !in 1..65535) add("server port ${it.port} is out of range")
        }
        val duplicated = artifacts.groupBy { it.fileName }.filterValues { it.size > 1 }.keys
        if (duplicated.isNotEmpty()) add("duplicate file names: ${duplicated.joinToString()}")
    }

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}
