package dev.survivaloverhaul.launcher.domain.minecraft

/**
 * One line of Mojang's version manifest: which version, and where its own
 * document lives together with that document's SHA-1.
 *
 * The digest is the reason the launcher goes through the index at all rather
 * than guessing the version document's URL. It is the only published digest
 * for that document, and every file the installation is built from is reached
 * through it.
 */
data class VersionIndexEntry(
    val id: String,
    val type: String,
    val url: String,
    val sha1: String?,
)

data class VersionIndex(
    val latestRelease: String?,
    val entries: List<VersionIndexEntry>,
) {
    fun find(id: String): VersionIndexEntry? = entries.firstOrNull { it.id == id }

    companion object {
        val EMPTY = VersionIndex(null, emptyList())
    }
}
