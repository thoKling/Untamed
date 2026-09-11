package dev.untamed.launcher

import dev.untamed.launcher.domain.game.GameConfiguration
import dev.untamed.launcher.domain.manifest.Artifact
import dev.untamed.launcher.domain.manifest.ReleaseChannel
import dev.untamed.launcher.domain.manifest.ResourceArtifact
import dev.untamed.launcher.domain.manifest.ResourceKind
import dev.untamed.launcher.domain.manifest.SurvivalManifest

/**
 * Manifests for tests to bend. They start valid, so a test that changes one
 * field is testing that field and nothing else.
 */
object Fixtures {

    const val SHA = "e9cda2abe2aa0a72300b65535117559d2d1a8211afe8ba9b819a93cc7c405737"

    fun game(): GameConfiguration = GameConfiguration(
        minecraftVersion = "26.2",
        javaMajorVersion = 25,
        fabricLoaderVersion = "0.19.5",
        fabricApiVersion = "0.160.0+26.2",
        untamedVersion = "0.1.0",
    )

    fun artifact(
        id: String = "untamed",
        version: String = "0.1.0",
        url: String = "https://downloads.example.test/mod/untamed-0.1.0.jar",
        sha256: String = SHA,
        fileName: String = "untamed-0.1.0.jar",
    ): Artifact = Artifact(
        id = id,
        displayName = "Untamed",
        version = version,
        url = url,
        sha256 = sha256,
        fileName = fileName,
    )

    fun manifest(
        game: GameConfiguration = game(),
        mod: Artifact = artifact(),
        dependencies: List<Artifact> = listOf(
            artifact(
                id = "fabric-api",
                version = "0.160.0+26.2",
                url = "https://maven.example.test/fabric-api-0.160.0%2B26.2.jar",
                fileName = "fabric-api-0.160.0+26.2.jar",
            ),
        ),
        resources: List<ResourceArtifact> = emptyList(),
        schemaVersion: Int = SurvivalManifest.SUPPORTED_SCHEMA_VERSION,
        minimumLauncherVersion: String = "0.1.0",
    ): SurvivalManifest = SurvivalManifest(
        schemaVersion = schemaVersion,
        channel = ReleaseChannel.STABLE,
        minimumLauncherVersion = minimumLauncherVersion,
        game = game,
        mod = mod,
        dependencies = dependencies,
        resources = resources,
        server = null,
    )

    fun manifestWithResourcePack(): SurvivalManifest = manifest(
        resources = listOf(
            ResourceArtifact(
                kind = ResourceKind.RESOURCE_PACK,
                artifact = artifact(
                    id = "untamed-textures",
                    version = "1.0.0",
                    url = "https://downloads.example.test/packs/textures-1.0.0.zip",
                    fileName = "untamed-textures-1.0.0.zip",
                ),
            ),
        ),
    )
}
