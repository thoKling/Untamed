package dev.untamed.launcher.application.installation

import java.util.Locale

/**
 * The phases an installation goes through, in the order they happen.
 *
 * Named after what the user is waiting for rather than after the code doing it,
 * because this text is what a progress line says while several gigabytes move.
 */
enum class InstallationStage(val label: String) {
    PREPARING("Preparing"),
    VERSION_INDEX("Reading Mojang's version list"),
    VERSION_DOCUMENT("Reading the Minecraft version"),
    CLIENT("Downloading Minecraft"),
    LIBRARIES("Downloading libraries"),
    ASSETS("Downloading game assets"),
    FABRIC("Installing Fabric"),
    MODS("Downloading the mod"),
    RESOURCES("Downloading resource packs"),
    CLEANUP("Removing what this update replaces"),
    FINISHING("Finishing"),
}

/**
 * How far an installation has got.
 *
 * Byte counts drive the bar where they are known, because a file count moves in
 * lies: one asset and the client jar are both "one file" and one of them is
 * thirty thousand times larger.
 */
data class InstallationProgress(
    val stage: InstallationStage,
    val detail: String = "",
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val completedBytes: Long = 0,
    val totalBytes: Long = 0,
) {
    val fraction: Float
        get() = when {
            totalBytes > 0 -> (completedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
            totalFiles > 0 -> (completedFiles.toFloat() / totalFiles).coerceIn(0f, 1f)
            else -> 0f
        }

    /** "412 MB of 1.1 GB", or nothing at all while no size is known. */
    fun transferred(): String =
        if (totalBytes <= 0) "" else "${size(completedBytes)} of ${size(totalBytes)}"

    /** A single line for the UI: what is happening, and how much of it is left. */
    fun describe(): String = buildString {
        append(stage.label)
        if (detail.isNotBlank()) append(": ").append(detail)
        if (totalFiles > 1) append(" ($completedFiles/$totalFiles)")
    }

    private companion object {

        /**
         * Megabytes and gigabytes as a download dialog counts them, which is
         * the decimal sense of the words. It matches the figure Mojang's own
         * launcher shows for the same file.
         */
        fun size(bytes: Long): String = when {
            bytes >= 1_000_000_000 -> String.format(Locale.ROOT, "%.1f GB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
            bytes >= 1_000 -> "${bytes / 1_000} kB"
            else -> "$bytes B"
        }
    }
}
