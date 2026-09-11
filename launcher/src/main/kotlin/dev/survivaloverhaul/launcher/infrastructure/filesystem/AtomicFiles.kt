package dev.survivaloverhaul.launcher.infrastructure.filesystem

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Writes that either happen completely or not at all.
 *
 * Every file the launcher owns is written this way. A launcher that is killed
 * halfway through writing its settings, its state file or a downloaded jar
 * should start again with the previous version intact rather than with a
 * truncated one.
 */
object AtomicFiles {

    fun writeText(target: Path, content: String) {
        writeBytes(target, content.toByteArray(Charsets.UTF_8))
    }

    fun writeBytes(target: Path, content: ByteArray) {
        val directory = target.parent ?: error("cannot write to a path with no parent: $target")
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, target.fileName.toString(), ".tmp")
        try {
            Files.write(temporary, content)
            move(temporary, target)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /** Moves [source] onto [target], atomically where the filesystem allows it. */
    fun move(source: Path, target: Path) {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            // Windows refuses an atomic move across volumes, and a temporary
            // directory can end up on another volume. A plain replace is still
            // better than writing in place.
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
