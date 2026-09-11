package dev.untamed.launcher.domain.game

/**
 * The version coordinates of one Untamed installation.
 *
 * Nothing else in the launcher is allowed to hard-code a Minecraft, Java,
 * Fabric or mod version. Every one of them arrives here, from the manifest, so
 * that moving the product to a new Minecraft release is a manifest edit rather
 * than a code change.
 */
data class GameConfiguration(
    val minecraftVersion: String,
    val javaMajorVersion: Int,
    val fabricLoaderVersion: String,
    val fabricApiVersion: String,
    val untamedVersion: String,
) {
    /**
     * The version id Minecraft's own launcher format uses for a Fabric profile,
     * and therefore the directory name under `versions/`.
     */
    val fabricProfileId: String
        get() = fabricProfileId(minecraftVersion, fabricLoaderVersion)

    companion object {
        fun fabricProfileId(minecraftVersion: String, fabricLoaderVersion: String): String =
            "fabric-loader-$fabricLoaderVersion-$minecraftVersion"
    }
}
