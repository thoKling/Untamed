package dev.survivaloverhaul.launcher.infrastructure.manifest

import dev.survivaloverhaul.launcher.application.installation.ManifestResult
import dev.survivaloverhaul.launcher.application.installation.ManifestSource
import dev.survivaloverhaul.launcher.branding.Branding
import dev.survivaloverhaul.launcher.infrastructure.http.HttpTransport
import dev.survivaloverhaul.launcher.infrastructure.http.TransportResult
import dev.survivaloverhaul.launcher.infrastructure.logging.LauncherLog

/**
 * The manifest as the distribution host currently publishes it.
 *
 * This is what makes a new release reach an installed launcher: the versions,
 * URLs and checksums all arrive from here, so shipping a new mod build is a
 * manifest edit rather than a new launcher.
 *
 * The document is unsigned and unverified at transport level, which is why
 * nothing acts on it until [ManifestDocuments] has validated the whole of it,
 * and why the addresses it can send the launcher to are limited to https URLs
 * whose contents are checked against a digest the same document published. It
 * cannot redirect the launcher at Mojang's or Fabric's endpoints, because those
 * are constants the manifest has no field for.
 *
 * One attempt, not three. This runs while the window is opening, the bundled
 * copy is already inside the jar, and making someone wait through a retry
 * backoff for a document the launcher can do without is the wrong trade.
 */
class RemoteManifestSource(
    private val transport: HttpTransport,
    private val url: String = Distribution.MANIFEST_URL,
    private val launcherVersion: String = Branding.LAUNCHER_VERSION,
    private val log: LauncherLog,
) : ManifestSource {

    override fun load(): ManifestResult {
        log.info(CATEGORY, "Fetching the release manifest from $url")
        return when (val result = transport.getText(url, attempts = 1)) {
            is TransportResult.Success -> read(result.value)
            is TransportResult.Failure -> ManifestResult.Unavailable(result.reason, url)
            // Nothing cancels this, since it is not the installation. Treating
            // it as unavailable keeps the mapping total rather than assuming so.
            TransportResult.Cancelled -> ManifestResult.Unavailable("cancelled", url)
        }
    }

    private fun read(text: String): ManifestResult =
        when (val result = ManifestDocuments.read(text, url, launcherVersion)) {
            is ManifestResult.Loaded -> result.also {
                val manifest = it.manifest
                log.info(
                    CATEGORY,
                    "Manifest loaded from $url: " +
                        "Minecraft ${manifest.game.minecraftVersion}, " +
                        "Fabric ${manifest.game.fabricLoaderVersion}, " +
                        "mod ${manifest.mod.version}",
                )
            }

            is ManifestResult.Rejected -> result.also {
                log.warn(CATEGORY, "Remote manifest rejected: ${it.problems.joinToString("; ")}")
            }

            is ManifestResult.Unavailable -> result
        }

    private companion object {
        const val CATEGORY = "manifest"
    }
}
