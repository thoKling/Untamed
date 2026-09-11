package dev.survivaloverhaul.launcher.infrastructure.minecraft

/**
 * Every address the launcher fetches the game from, in one file.
 *
 * They are constants rather than manifest fields on purpose. The release
 * manifest carries versions and checksummed artifacts and no addresses, so a
 * manifest cannot redirect the launcher at somewhere other than Mojang's and
 * Fabric's own endpoints, however it was obtained.
 */
object Endpoints {

    /** Mojang's list of every version, with each version document's SHA-1. */
    const val VERSION_INDEX = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"

    /** Where asset objects are served from, on the same path they are stored. */
    const val ASSET_OBJECTS = "https://resources.download.minecraft.net"

    private const val FABRIC_META = "https://meta.fabricmc.net/v2"

    /**
     * The Fabric launcher profile for one Minecraft and loader pair.
     *
     * Fabric meta returns a document in Mojang's own version-JSON format, which
     * is why there is no Fabric installer executable in this launcher and why
     * there should not be: everything it would do is reading this document and
     * resolving the libraries it names.
     */
    fun fabricProfile(minecraftVersion: String, loaderVersion: String): String =
        "$FABRIC_META/versions/loader/$minecraftVersion/$loaderVersion/profile/json"
}
