package dev.untamed.launcher.infrastructure.installation

import dev.untamed.launcher.application.installation.CancellationSignal
import dev.untamed.launcher.application.installation.GameInstallationPlanner
import dev.untamed.launcher.application.installation.GameInstaller
import dev.untamed.launcher.application.installation.InstallationOutcome
import dev.untamed.launcher.application.installation.InstallationProgress
import dev.untamed.launcher.application.installation.InstallationStage
import dev.untamed.launcher.application.installation.PendingChecksum
import dev.untamed.launcher.domain.download.Checksum
import dev.untamed.launcher.domain.game.GameConfiguration
import dev.untamed.launcher.domain.manifest.SurvivalManifest
import dev.untamed.launcher.domain.minecraft.HostPlatform
import dev.untamed.launcher.domain.minecraft.VersionProfile
import dev.untamed.launcher.infrastructure.filesystem.AtomicFiles
import dev.untamed.launcher.infrastructure.filesystem.InstallationPaths
import dev.untamed.launcher.infrastructure.http.HttpTransport
import dev.untamed.launcher.infrastructure.logging.LauncherLog
import dev.untamed.launcher.infrastructure.minecraft.Endpoints
import dev.untamed.launcher.infrastructure.minecraft.VersionDocuments
import java.nio.file.Files
import java.time.Instant

/**
 * Installs Minecraft and Fabric into the installation directory.
 *
 * The shape of the job is: read Mojang's version list, take the document it
 * points at and verify it against the digest the list published, then install
 * everything that document names. Fabric is the same document format fetched
 * from Fabric meta, written beside it, with its own libraries resolved from
 * Fabric's maven.
 *
 * Nothing here decides anything. Which files an installation needs is decided
 * by [GameInstallationPlanner], which is pure; this class fetches, verifies,
 * writes and reports, which is all the work that cannot be pure.
 *
 * The mod and Fabric API are not installed here. They are ordinary files in
 * `mods/` and belong to [ManifestModInstaller].
 */
class MojangGameInstaller(
    private val paths: InstallationPaths,
    transport: HttpTransport,
    private val stateStore: InstallationStateStore,
    private val platform: HostPlatform,
    private val log: LauncherLog,
) : GameInstaller {

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
        val game = manifest.game
        log.info(CATEGORY, "Installing Minecraft ${game.minecraftVersion} into ${paths.root}")
        reporter.stage(InstallationStage.PREPARING, "creating the installation directory")
        createDirectories()

        val vanilla = installMinecraft(game, cancellation, reporter)
        installFabric(game, vanilla, cancellation, reporter)

        reporter.stage(InstallationStage.FINISHING, "recording what was installed")
        recordState(game)

        val summary = "Minecraft ${game.minecraftVersion} and Fabric Loader " +
            "${game.fabricLoaderVersion} are installed"
        log.info(CATEGORY, summary)
        return InstallationOutcome.Completed(summary)
    }

    // ----------------------------------------------------------------- minecraft

    private fun installMinecraft(
        game: GameConfiguration,
        cancellation: CancellationSignal,
        reporter: ProgressReporter,
    ): VersionProfile {
        val version = game.minecraftVersion

        reporter.stage(InstallationStage.VERSION_INDEX, version)
        val indexText =
            files.text(Endpoints.VERSION_INDEX, cancellation, "read Mojang's version list")
        val index = runCatching { VersionDocuments.parseIndex(indexText) }
            .getOrElse { files.abort("Mojang's version list could not be read.", it.message) }
        val entry = index.find(version)
            ?: files.abort("Minecraft $version is not in Mojang's version list.", null)

        reporter.stage(InstallationStage.VERSION_DOCUMENT, version)
        val document = GameInstallationPlanner.versionDocument(entry)
        if (document.checksum == null) {
            files.abort(
                "Mojang published no checksum for the Minecraft $version version file.",
                null,
            )
        }
        files.fetch(document, cancellation, verifyExisting = true)

        val profile = runCatching {
            VersionDocuments.parseProfile(Files.readString(paths.versionManifest(version)))
        }.getOrElse {
            files.abort("The Minecraft $version version file could not be read.", it.message)
        }

        // The manifest pins the Java version the product supports and Mojang's
        // document states what the release asks for. They should agree; when
        // they do not, the manifest is what the rest of the launcher acts on,
        // so the disagreement is logged rather than silently resolved.
        profile.javaMajorVersion?.let { required ->
            if (required != game.javaMajorVersion) {
                log.warn(
                    CATEGORY,
                    "Minecraft $version asks for Java $required, the manifest declares " +
                        "Java ${game.javaMajorVersion}",
                )
            }
        }

        val client = GameInstallationPlanner.client(profile, version)
            ?: files.abort("The Minecraft $version version file names no client download.", null)
        reporter.stage(InstallationStage.CLIENT, version, totalBytes = client.sizeBytes)
        val kept = files.fetch(client, cancellation, verifyExisting = true) {
            reporter.advanceBytes(it)
        }
        if (kept) reporter.advanceBytes(client.sizeBytes)
        reporter.finishFile()

        installLibraries(profile, "Minecraft", cancellation, reporter)
        GameInstallationPlanner.loggingConfiguration(profile)?.let { logging ->
            files.fetch(logging, cancellation, verifyExisting = true)
        }
        installAssets(profile, cancellation, reporter)
        return profile
    }

    private fun installLibraries(
        profile: VersionProfile,
        what: String,
        cancellation: CancellationSignal,
        reporter: ProgressReporter,
    ) {
        val planned = GameInstallationPlanner.libraries(profile, platform)
        planned.problems.forEach { log.warn(CATEGORY, "$what library problem: $it") }
        if (planned.problems.isNotEmpty()) {
            files.abort(
                "The $what version file describes a library the launcher cannot place.",
                planned.problems.joinToString("; "),
            )
        }
        log.info(
            CATEGORY,
            "$what needs ${planned.count} libraries on $platform " +
                "of ${profile.libraries.size} listed",
        )

        reporter.begin(
            InstallationStage.LIBRARIES,
            files = planned.count,
            bytes = planned.verified.totalBytes,
        )
        files.downloadAll(planned.verified, cancellation, reporter, verifyExisting = true)
        planned.pending.forEach { pending ->
            downloadWithPublishedChecksum(pending, cancellation, reporter)
        }
    }

    /**
     * Fabric's maven publishes no digest inside the profile, so the digest is
     * fetched from the `.sha1` beside the artifact and the download is verified
     * against that. It is one more request per library, and it is what keeps
     * "nothing is written without a digest" true rather than nearly true.
     */
    private fun downloadWithPublishedChecksum(
        pending: PendingChecksum,
        cancellation: CancellationSignal,
        reporter: ProgressReporter,
    ) {
        reporter.detail(pending.request.label)
        val published = files.text(
            pending.checksumUrl,
            cancellation,
            "fetch the checksum for ${pending.request.label}",
        )
        val digest = published.trim().substringBefore(' ').trim()
        val checksum = Checksum(pending.algorithm, digest)
        if (!checksum.isWellFormed) {
            files.abort(
                "The checksum published for ${pending.request.label} is unusable.",
                pending.checksumUrl,
            )
        }
        files.fetch(
            pending.request.copy(checksum = checksum),
            cancellation,
            verifyExisting = true,
        ) { reporter.advanceBytes(it) }
        reporter.finishFile()
    }

    private fun installAssets(
        profile: VersionProfile,
        cancellation: CancellationSignal,
        reporter: ProgressReporter,
    ) {
        val reference = profile.assetIndex
            ?: files.abort("The Minecraft version file names no asset index.", null)

        reporter.stage(InstallationStage.ASSETS, "reading the asset index")
        val document = GameInstallationPlanner.assetIndexDocument(reference)
        files.fetch(document, cancellation, verifyExisting = true)

        val index = runCatching {
            VersionDocuments.parseAssetIndex(
                reference.id,
                Files.readString(files.resolve(document.destination)),
            )
        }.getOrElse { files.abort("The asset index could not be read.", it.message) }

        val planned = GameInstallationPlanner.assets(index, Endpoints.ASSET_OBJECTS)
        planned.problems.forEach { log.warn(CATEGORY, "Asset problem: $it") }
        log.info(CATEGORY, "Asset index ${index.id} holds ${planned.batch.requests.size} objects")

        reporter.begin(
            InstallationStage.ASSETS,
            files = planned.batch.requests.size,
            bytes = planned.batch.totalBytes,
        )
        // Assets are content-addressed: the file name is the digest of the
        // contents, so a file that is present under the right name and the
        // right length is the object the index asked for. Re-hashing tens of
        // thousands of them on every start would cost far more than it protects.
        files.downloadAll(planned.batch, cancellation, reporter, verifyExisting = false)
    }

    // -------------------------------------------------------------------- fabric

    private fun installFabric(
        game: GameConfiguration,
        vanilla: VersionProfile,
        cancellation: CancellationSignal,
        reporter: ProgressReporter,
    ) {
        val profileId = game.fabricProfileId
        reporter.stage(InstallationStage.FABRIC, "Fabric Loader ${game.fabricLoaderVersion}")

        val url = Endpoints.fabricProfile(game.minecraftVersion, game.fabricLoaderVersion)
        val text = files.text(url, cancellation, "fetch the Fabric profile")
        val profile = runCatching { VersionDocuments.parseProfile(text) }
            .getOrElse { files.abort("The Fabric profile could not be read.", it.message) }

        // Fabric meta publishes no digest for this document, so what can be
        // checked is what it says about itself. A profile whose id or parent is
        // not the one that was asked for is not installed under that name.
        if (profile.id != profileId) {
            files.abort(
                "Fabric returned a profile named ${profile.id} rather than $profileId.",
                url,
            )
        }
        if (profile.inheritsFrom != null && profile.inheritsFrom != game.minecraftVersion) {
            files.abort(
                "The Fabric profile is built on Minecraft ${profile.inheritsFrom}, " +
                    "not ${game.minecraftVersion}.",
                url,
            )
        }
        if (profile.mainClass.isNullOrBlank()) {
            files.abort("The Fabric profile names no main class.", url)
        }

        // 26.x runs under Minecraft's official mappings, so the profile carries
        // no intermediary library. Nothing here looks for one; the check is
        // that every library it does carry can be placed and verified.
        installLibraries(profile, "Fabric", cancellation, reporter)

        AtomicFiles.writeText(paths.versionManifest(profileId), text)
        log.info(CATEGORY, "Wrote the Fabric profile $profileId")

        val merged = profile.inheriting(vanilla)
        log.debug(
            CATEGORY,
            "Merged profile $profileId: ${merged.libraries.size} libraries, " +
                "main class ${merged.mainClass}",
        )
    }

    // --------------------------------------------------------------------- state

    private fun createDirectories() {
        files.createDirectories(
            listOf(
                paths.root,
                paths.versionsDirectory,
                paths.librariesDirectory,
                paths.assetsDirectory,
                paths.modsDirectory,
                paths.configDirectory,
                paths.resourcePacksDirectory,
                paths.shaderPacksDirectory,
            ),
        )
    }

    /**
     * Records Minecraft and Fabric, and leaves every other entry alone. The
     * state file is one record shared with the mod installer, and installing
     * the game must not forget what mods are already there.
     */
    private fun recordState(game: GameConfiguration) {
        val existing = stateStore.read()
        stateStore.write(
            existing.copy(
                schemaVersion = InstallationState.SCHEMA_VERSION,
                minecraftVersion = game.minecraftVersion,
                fabricLoaderVersion = game.fabricLoaderVersion,
                installedAt = Instant.now().toString(),
            ),
        )
    }

    private companion object {
        const val CATEGORY = "installer"
    }
}
