package dev.untamed.launcher.infrastructure.manifest

import dev.untamed.launcher.application.installation.ManifestResult
import dev.untamed.launcher.domain.game.GameConfiguration
import dev.untamed.launcher.domain.manifest.Artifact
import dev.untamed.launcher.domain.manifest.ReleaseChannel
import dev.untamed.launcher.domain.manifest.ResourceArtifact
import dev.untamed.launcher.domain.manifest.ResourceKind
import dev.untamed.launcher.domain.manifest.ServerDescriptor
import dev.untamed.launcher.domain.manifest.SurvivalManifest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The wire format of the manifest, kept separate from the domain model.
 *
 * The manifest is written elsewhere and fetched over the network, so its shape
 * is a contract with the outside world. Keeping it in its own types means the
 * domain model can be refactored without breaking published manifests, and a
 * new manifest field cannot reach the rest of the launcher without passing
 * through the mapping below.
 */
@Serializable
internal data class ManifestDocument(
    val schemaVersion: Int = 0,
    val channel: String = "stable",
    val minimumLauncherVersion: String = "0.0.0",
    val minecraft: MinecraftDocument = MinecraftDocument(),
    val fabric: FabricDocument = FabricDocument(),
    val mod: ArtifactDocument = ArtifactDocument(),
    val dependencies: List<ArtifactDocument> = emptyList(),
    val resources: List<ResourceDocument> = emptyList(),
    val server: ServerDocument? = null,
) {
    fun toManifest(): SurvivalManifest = SurvivalManifest(
        schemaVersion = schemaVersion,
        channel = ReleaseChannel.entries.firstOrNull { it.name.equals(channel, ignoreCase = true) }
            ?: ReleaseChannel.STABLE,
        minimumLauncherVersion = minimumLauncherVersion,
        game = GameConfiguration(
            minecraftVersion = minecraft.version,
            javaMajorVersion = minecraft.javaMajorVersion,
            fabricLoaderVersion = fabric.loader,
            fabricApiVersion = fabric.api,
            untamedVersion = mod.version,
        ),
        mod = mod.toArtifact(),
        dependencies = dependencies.map { it.toArtifact() },
        resources = resources.map { it.toResource() },
        server = server?.toDescriptor(),
    )
}

@Serializable
internal data class MinecraftDocument(
    val version: String = "",
    val javaMajorVersion: Int = 0,
)

@Serializable
internal data class FabricDocument(
    val loader: String = "",
    val api: String = "",
)

@Serializable
internal data class ArtifactDocument(
    val id: String = "",
    val displayName: String = "",
    val version: String = "",
    val url: String = "",
    val sha256: String = "",
    val fileName: String = "",
) {
    fun toArtifact(): Artifact = Artifact(
        id = id,
        displayName = displayName.ifBlank { id },
        version = version,
        url = url,
        sha256 = sha256,
        fileName = fileName,
    )
}

@Serializable
internal data class ResourceDocument(
    val kind: String = "resource_pack",
    @SerialName("artifact") val artifact: ArtifactDocument = ArtifactDocument(),
) {
    fun toResource(): ResourceArtifact = ResourceArtifact(
        kind = when (kind.lowercase()) {
            "shader_pack" -> ResourceKind.SHADER_PACK
            "configuration" -> ResourceKind.CONFIGURATION
            else -> ResourceKind.RESOURCE_PACK
        },
        artifact = artifact.toArtifact(),
    )
}

@Serializable
internal data class ServerDocument(
    val name: String = "",
    val address: String = "",
    val port: Int = 25565,
    val requiredModVersion: String = "",
) {
    fun toDescriptor(): ServerDescriptor = ServerDescriptor(name, address, port, requiredModVersion)
}

/**
 * Turns manifest text into a result, applying every check exactly once.
 *
 * Both manifest sources parse the same format and both have to reject the same
 * documents. Reading is one place rather than two so that the remote source and
 * the bundled one cannot drift into disagreeing about what a valid manifest is,
 * which is precisely the disagreement an attacker would look for.
 */
internal object ManifestDocuments {

    /**
     * The parsed manifest, or the reason it is not usable.
     *
     * [launcherVersion] is checked against the manifest's own
     * `minimumLauncherVersion`. A manifest is allowed to describe a release
     * that this build cannot install correctly, and saying so is much better
     * than installing most of it: the fix is an updated launcher, and the user
     * can only be told that if the launcher notices.
     */
    fun read(text: String, origin: String, launcherVersion: String): ManifestResult {
        val manifest = runCatching { json.decodeFromString<ManifestDocument>(text).toManifest() }
            .getOrElse { failure ->
                val reason = failure.message ?: failure::class.simpleName.orEmpty()
                return ManifestResult.Rejected(listOf("unreadable: $reason"), origin)
            }

        val problems = buildList {
            addAll(manifest.problems())
            if (!manifest.supportsLauncher(launcherVersion)) {
                add(
                    "this release needs launcher ${manifest.minimumLauncherVersion} " +
                        "or newer, this is $launcherVersion",
                )
            }
        }
        if (problems.isNotEmpty()) return ManifestResult.Rejected(problems, origin)
        return ManifestResult.Loaded(manifest, origin)
    }

    // Unknown keys are ignored so that a manifest carrying a field a future
    // launcher understands is still readable by this one.
    private val json = Json { ignoreUnknownKeys = true }
}
