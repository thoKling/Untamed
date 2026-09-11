package dev.survivaloverhaul.launcher.infrastructure.installation

import dev.survivaloverhaul.launcher.application.installation.InstallationProbe
import dev.survivaloverhaul.launcher.domain.game.GameConfiguration
import dev.survivaloverhaul.launcher.domain.installation.InstalledComponents
import dev.survivaloverhaul.launcher.infrastructure.filesystem.InstallationPaths
import dev.survivaloverhaul.launcher.infrastructure.logging.LauncherLog
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads an installation directory and reports what is actually there.
 *
 * Every version it reports is backed by a file it found. A component that the
 * state file claims but whose files are gone is reported as absent, so deleting
 * a jar by hand is enough to make the launcher reinstall it rather than
 * something the user has to explain to the launcher.
 */
class FilesystemInstallationProbe(
    private val paths: InstallationPaths,
    private val stateStore: InstallationStateStore,
    private val log: LauncherLog,
) : InstallationProbe {

    override fun inspect(): InstalledComponents {
        val state = stateStore.read()

        val minecraft = state.minecraftVersion?.takeIf { version ->
            Files.isRegularFile(paths.versionJar(version)) &&
                Files.isRegularFile(paths.versionManifest(version))
        }

        // Fabric is installed as a profile that inherits from the Minecraft
        // version, so it only counts as present when that version is too.
        val fabricLoader = state.fabricLoaderVersion?.takeIf { loader ->
            minecraft != null && Files.isRegularFile(
                paths.versionManifest(GameConfiguration.fabricProfileId(minecraft, loader)),
            )
        }

        val fabricApi = state.fabricApi?.takeIf { isPresent(it, paths.modsDirectory) }?.version
        val mod = state.mod?.takeIf { isPresent(it, paths.modsDirectory) }?.version
        val resources = state.resources
            .filter { isPresent(it, paths.resourcePacksDirectory) }
            .associate { it.id to it.version }

        val components = InstalledComponents(
            minecraftVersion = minecraft,
            fabricLoaderVersion = fabricLoader,
            fabricApiVersion = fabricApi,
            survivalOverhaulVersion = mod,
            resourceVersions = resources,
        )
        log.debug(CATEGORY, "Inspected ${paths.root}: $components")
        return components
    }

    /**
     * Whether the file an entry describes is really on disk.
     *
     * Newer entries carry the destination they were installed to, which is the
     * only answer that is right for a shader pack or a config file. Entries
     * written before that field existed fall back to [defaultDirectory], which
     * is where the launcher of that vintage would have put them.
     */
    private fun isPresent(file: InstalledFile, defaultDirectory: Path): Boolean {
        val target = file.path
            ?.let { paths.root.resolve(it) }
            ?: defaultDirectory.resolve(file.fileName)
        return Files.isRegularFile(target)
    }

    private companion object {
        const val CATEGORY = "installation"
    }
}
