package dev.survivaloverhaul.launcher.infrastructure.launch

import dev.survivaloverhaul.launcher.infrastructure.logging.LauncherLog
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

/**
 * Unpacks the native binaries a launch needs out of the jars they ship in.
 *
 * This is the one place the launcher takes a file apart instead of writing it
 * down whole. The jars were downloaded and verified against Mojang's digests by
 * the installer, and nothing here is executed by the launcher: the JVM loads the
 * unpacked libraries later, on the game's behalf.
 *
 * Entries are flattened to their file names. `java.library.path` is a list of
 * directories and is not searched recursively, so a `glfw.dll` left at
 * `windows/x64/org/lwjgl/glfw/glfw.dll` inside the natives directory is a
 * library the JVM will not find.
 */
object NativeLibraries {

    /**
     * Unpacks every archive into [destination] and returns what could not be
     * done. An empty list means the game has its natives.
     *
     * Idempotent by size, so a second launch copies nothing. Size rather than a
     * digest because the archive it came from was already verified and because
     * this runs while the user is waiting for a window to appear.
     */
    fun extract(archives: List<String>, destination: Path, log: LauncherLog): List<String> {
        val problems = mutableListOf<String>()
        runCatching { Files.createDirectories(destination) }.onFailure {
            return listOf("could not create $destination: ${it.message ?: it::class.simpleName}")
        }

        var written = 0
        for (archive in archives) {
            val path = Path.of(archive)
            if (!Files.isRegularFile(path)) {
                problems += "the native library $archive is not installed"
                continue
            }
            runCatching { written += extractOne(path, destination) }.onFailure {
                problems += "could not unpack $archive: ${it.message ?: it::class.simpleName}"
            }
        }

        log.info(
            CATEGORY,
            "Natives ready in $destination: ${archives.size} archives, $written files written",
        )
        return problems
    }

    private fun extractOne(archive: Path, destination: Path): Int {
        var written = 0
        ZipFile(archive.toFile()).use { zip ->
            for (entry in zip.entries()) {
                if (!isExtractable(entry.name)) continue
                val target = destination.resolve(fileName(entry.name))
                // The archive was verified, so an existing file of the same size
                // is the same file. Rewriting it would also fail on Windows once
                // a previous run of the game has it mapped.
                if (Files.exists(target) && Files.size(target) == entry.size) continue
                zip.getInputStream(entry).use { input ->
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
                written++
            }
        }
        return written
    }

    /**
     * Whether an entry is a file worth unpacking.
     *
     * Internal so a test can cover it without a zip file, because this is the
     * check that decides what an archive is allowed to write. Directories carry
     * nothing, `META-INF/` is signing metadata that native loading never reads,
     * and a name that is nothing but path traversal is refused outright even
     * though flattening already leaves it nowhere to go.
     */
    internal fun isExtractable(entryName: String): Boolean {
        if (entryName.isBlank()) return false
        if (entryName.endsWith("/") || entryName.endsWith("\\")) return false
        if (entryName.startsWith("META-INF/", ignoreCase = true)) return false
        // Refused for what it says rather than for what it could do here.
        // Flattening already leaves a traversing name nowhere to go, and a check
        // that relies on that is a check that breaks when the flattening changes.
        val segments = entryName.split('/', '\\')
        if (segments.any { it == "." || it == ".." }) return false
        return fileName(entryName).isNotBlank()
    }

    /** The last segment of an entry name, whichever separator it was written with. */
    private fun fileName(entryName: String): String =
        entryName.substringAfterLast('/').substringAfterLast('\\')

    private const val CATEGORY = "launch"
}
