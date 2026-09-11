package dev.untamed.launcher.infrastructure.installation

import dev.untamed.launcher.infrastructure.filesystem.AtomicFiles
import dev.untamed.launcher.infrastructure.logging.LauncherLog
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The launcher's record of what it put in an installation directory.
 *
 * It is a record, not the truth. The truth is the files themselves, which is
 * why [FilesystemInstallationProbe] checks every entry against the disk before
 * believing it. A state file that disagrees with the directory loses.
 */
@Serializable
data class InstallationState(
    val schemaVersion: Int = SCHEMA_VERSION,
    val minecraftVersion: String? = null,
    val fabricLoaderVersion: String? = null,
    val fabricApi: InstalledFile? = null,
    val mod: InstalledFile? = null,
    val resources: List<InstalledFile> = emptyList(),
    val installedAt: String? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
        val EMPTY = InstallationState()
    }
}

/**
 * One file the launcher installed, and where it put it.
 *
 * [path] is the destination relative to the installation directory. It exists
 * because a resource can land in any of several directories, and cleanup has to
 * remove the file that is actually there rather than the one it would guess at.
 * It is nullable so that a state file written by an older launcher still reads;
 * for those entries the reader falls back to the directory the kind implies.
 */
@Serializable
data class InstalledFile(
    val id: String,
    val version: String,
    val fileName: String,
    val path: String? = null,
)

class InstallationStateStore(
    private val file: Path,
    private val log: LauncherLog,
) {

    fun read(): InstallationState {
        if (!Files.exists(file)) return InstallationState.EMPTY
        return runCatching { json.decodeFromString<InstallationState>(Files.readString(file)) }
            .getOrElse {
                log.warn(CATEGORY, "Installation state at $file is unreadable, treating as empty")
                InstallationState.EMPTY
            }
    }

    fun write(state: InstallationState) {
        runCatching { AtomicFiles.writeText(file, json.encodeToString(state)) }
            .onFailure { log.error(CATEGORY, "Could not write installation state to $file", it) }
    }

    private companion object {
        const val CATEGORY = "installation"
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
