package dev.survivaloverhaul.launcher.infrastructure.filesystem

import java.nio.file.Path

/**
 * The layout of one Survival Overhaul installation.
 *
 * The directory names match what a Minecraft installation expects, because the
 * game is launched against this directory: it is a Minecraft game directory
 * that the launcher owns, not a private format of our own.
 */
class InstallationPaths(val root: Path) {

    val versionsDirectory: Path get() = root.resolve("versions")
    val librariesDirectory: Path get() = root.resolve("libraries")
    val assetsDirectory: Path get() = root.resolve("assets")
    val nativesDirectory: Path get() = root.resolve("natives")
    val modsDirectory: Path get() = root.resolve("mods")
    val configDirectory: Path get() = root.resolve("config")
    val resourcePacksDirectory: Path get() = root.resolve("resourcepacks")
    val shaderPacksDirectory: Path get() = root.resolve("shaderpacks")

    /** The launcher's own record of what it installed here. */
    val stateFile: Path get() = root.resolve("survival-overhaul.json")

    fun versionDirectory(versionId: String): Path = versionsDirectory.resolve(versionId)

    fun versionManifest(versionId: String): Path =
        versionDirectory(versionId).resolve("$versionId.json")

    fun versionJar(versionId: String): Path = versionDirectory(versionId).resolve("$versionId.jar")

    /**
     * Where one version's native binaries are unpacked.
     *
     * Per version rather than one shared directory. Two versions can ship
     * different builds of the same library, and unpacking both into one place
     * is a game loading the wrong one with nothing on screen to say so.
     */
    fun versionNatives(versionId: String): Path = nativesDirectory.resolve(versionId)

    companion object {
        fun of(directory: String): InstallationPaths = InstallationPaths(Path.of(directory))
    }
}
