package dev.untamed.launcher.infrastructure.manifest

import dev.untamed.launcher.application.installation.ManifestResult
import dev.untamed.launcher.application.installation.ManifestSource
import dev.untamed.launcher.branding.Branding
import dev.untamed.launcher.infrastructure.logging.LauncherLog

/**
 * The manifest shipped inside the launcher.
 *
 * It is the pinned fallback: the version of Untamed this build of the
 * launcher was released against. [RemoteManifestSource] is tried first and this
 * one answers when the network does not, which is what lets the launcher start
 * and report honestly while offline.
 *
 * It is validated exactly as strictly as a downloaded one. A manifest that this
 * build shipped with is not more trustworthy than one it fetched; it is only
 * older, and a build that shipped a broken manifest should say so rather than
 * act on it.
 */
class BundledManifestSource(
    private val log: LauncherLog,
    private val resourcePath: String = DEFAULT_RESOURCE,
    private val launcherVersion: String = Branding.LAUNCHER_VERSION,
) : ManifestSource {

    override fun load(): ManifestResult {
        val origin = "bundled:$resourcePath"
        val text = javaClass.getResourceAsStream(resourcePath)?.use { it.readBytes() }
            ?.toString(Charsets.UTF_8)
            ?: return ManifestResult.Unavailable("not found in the launcher jar", origin)
                .also { log.error(CATEGORY, "Bundled manifest $resourcePath is missing") }

        return when (val result = ManifestDocuments.read(text, origin, launcherVersion)) {
            is ManifestResult.Loaded -> result.also { log.info(CATEGORY, describe(it)) }
            is ManifestResult.Rejected -> result.also {
                log.error(CATEGORY, "Bundled manifest rejected: ${it.problems.joinToString("; ")}")
            }

            is ManifestResult.Unavailable -> result
        }
    }

    private fun describe(result: ManifestResult.Loaded): String {
        val manifest = result.manifest
        return "Manifest loaded from ${result.origin}: " +
            "Minecraft ${manifest.game.minecraftVersion}, " +
            "Fabric ${manifest.game.fabricLoaderVersion}, mod ${manifest.mod.version}"
    }

    private companion object {
        const val CATEGORY = "manifest"
        const val DEFAULT_RESOURCE = "/manifest/untamed.json"
    }
}
