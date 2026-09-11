package dev.survivaloverhaul.launcher.application.installation

import dev.survivaloverhaul.launcher.domain.download.Checksum
import dev.survivaloverhaul.launcher.domain.download.ChecksumAlgorithm
import dev.survivaloverhaul.launcher.domain.download.DownloadBatch
import dev.survivaloverhaul.launcher.domain.download.DownloadRequest
import dev.survivaloverhaul.launcher.domain.minecraft.AssetIndex
import dev.survivaloverhaul.launcher.domain.minecraft.AssetIndexReference
import dev.survivaloverhaul.launcher.domain.minecraft.HostPlatform
import dev.survivaloverhaul.launcher.domain.minecraft.VersionIndexEntry
import dev.survivaloverhaul.launcher.domain.minecraft.VersionProfile

/**
 * An artifact whose publisher put the digest beside the file rather than in the
 * document that names it.
 *
 * Fabric's profile lists libraries with a coordinate and a repository and no
 * checksum at all. Rather than install them unverified, the launcher fetches
 * the `.sha1` maven publishes next to each artifact and verifies against that.
 * This type is the shape of that two-step fetch, and its existence is what lets
 * the download executor refuse, with no exceptions, any request that arrives
 * without a digest.
 */
data class PendingChecksum(
    val request: DownloadRequest,
    val checksumUrl: String,
    val algorithm: ChecksumAlgorithm,
)

data class PlannedLibraries(
    val verified: DownloadBatch,
    val pending: List<PendingChecksum>,
    val problems: List<String>,
) {
    val count: Int get() = verified.requests.size + pending.size
}

data class PlannedAssets(
    val batch: DownloadBatch,
    val problems: List<String>,
)

/**
 * Turns version documents into the exact list of files an installation needs.
 *
 * Pure. It performs no IO, reads nothing from disk and knows no endpoint: the
 * URLs it builds come from the documents it is given and from base addresses
 * passed in. That is what makes the awkward parts of installing Minecraft, the
 * rule-gated library selection and the content-addressed asset layout, things
 * a unit test can check rather than things only a real download can.
 *
 * Deciding what is already present is not its job either. The executor skips
 * files that are already on disk and correct, which keeps this function's
 * output the same whether an installation is fresh or nearly complete.
 */
object GameInstallationPlanner {

    fun versionDocument(entry: VersionIndexEntry): DownloadRequest = DownloadRequest(
        label = "Minecraft ${entry.id} version document",
        url = entry.url,
        destination = "versions/${entry.id}/${entry.id}.json",
        checksum = entry.sha1?.let(Checksum::sha1),
        sizeBytes = 0,
    )

    fun client(profile: VersionProfile, versionId: String): DownloadRequest? =
        profile.client?.let { file ->
            DownloadRequest(
                label = "Minecraft $versionId client",
                url = file.url,
                destination = "versions/$versionId/$versionId.jar",
                checksum = file.sha1?.let(Checksum::sha1),
                sizeBytes = file.sizeBytes,
            )
        }

    /**
     * The libraries this machine needs, split by whether the document that
     * named them also carried a digest.
     *
     * Selection is by rule evaluation only. In 26.2 native libraries are
     * ordinary rule-gated entries rather than a `classifiers` block, and some
     * variants differ from each other by nothing but an architecture suffix on
     * the name, so anything that matched on names would install the wrong ones.
     */
    fun libraries(profile: VersionProfile, platform: HostPlatform): PlannedLibraries {
        val verified = mutableListOf<DownloadRequest>()
        val pending = mutableListOf<PendingChecksum>()
        val problems = mutableListOf<String>()

        for (library in profile.librariesFor(platform)) {
            val path = library.relativePath
            val url = library.url
            if (path == null || url == null) {
                problems += "library '${library.name}' does not say where it comes from"
                continue
            }
            val request = DownloadRequest(
                label = library.name,
                url = url,
                destination = "libraries/$path",
                checksum = library.download?.sha1?.let(Checksum::sha1),
                sizeBytes = library.download?.sizeBytes ?: 0,
            )
            if (request.checksum != null) {
                verified += request
            } else {
                pending += PendingChecksum(request, "$url.sha1", ChecksumAlgorithm.SHA1)
            }
        }
        return PlannedLibraries(DownloadBatch(verified), pending, problems)
    }

    /**
     * Log4j's configuration file. Minecraft is started with an argument that
     * points at it, so it is part of the installation rather than of launching.
     */
    fun loggingConfiguration(profile: VersionProfile): DownloadRequest? =
        profile.logging?.let { logging ->
            DownloadRequest(
                label = "logging configuration",
                url = logging.file.url,
                destination = "assets/log_configs/${logging.fileId}",
                checksum = logging.file.sha1?.let(Checksum::sha1),
                sizeBytes = logging.file.sizeBytes,
            )
        }

    fun assetIndexDocument(reference: AssetIndexReference): DownloadRequest = DownloadRequest(
        label = "asset index ${reference.id}",
        url = reference.url,
        destination = "assets/indexes/${reference.id}.json",
        checksum = reference.sha1?.let(Checksum::sha1),
        sizeBytes = reference.sizeBytes,
    )

    /**
     * Every asset object, fetched from [baseUrl] on the same two-character path
     * it is stored under. The hash is both the address and the digest, so an
     * object that is present is by definition the object that was asked for.
     */
    fun assets(index: AssetIndex, baseUrl: String): PlannedAssets {
        val requests = mutableListOf<DownloadRequest>()
        val problems = mutableListOf<String>()
        val seen = HashSet<String>(index.objects.size)
        val base = baseUrl.trimEnd('/')

        for (asset in index.objects) {
            if (!asset.isWellFormed) {
                problems += "asset '${asset.name}' has an unusable hash"
                continue
            }
            // Several names share one object in every Minecraft asset index.
            // Downloading it once is the whole point of content addressing.
            if (!seen.add(asset.hash)) continue
            requests += DownloadRequest(
                label = asset.name,
                url = "$base/${asset.remotePath}",
                destination = asset.relativePath,
                checksum = Checksum.sha1(asset.hash),
                sizeBytes = asset.sizeBytes,
            )
        }
        return PlannedAssets(DownloadBatch(requests), problems)
    }
}
