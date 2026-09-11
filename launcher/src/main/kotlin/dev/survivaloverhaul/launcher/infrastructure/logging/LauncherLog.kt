package dev.survivaloverhaul.launcher.infrastructure.logging

import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogLine(
    val timestamp: Instant,
    val level: LogLevel,
    val category: String,
    val message: String,
) {
    fun format(): String = "${FORMATTER.format(timestamp)} [${level.name}] $category: $message"

    private companion object {
        val FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    }
}

/**
 * The launcher's log: a file on disk and the same lines in memory for the
 * diagnostics screen.
 *
 * Nothing derived from an account is ever passed to this class. Access tokens,
 * refresh tokens and Xbox identifiers do not belong in a file a user will paste
 * into a support thread, and the way to guarantee that is for the code that
 * handles them to never call it with them.
 */
class LauncherLog(
    private val directory: Path,
    private val retainedFiles: Int = RETAINED_FILES,
) : AutoCloseable {

    private val lock = Any()
    private val buffer = ArrayDeque<LogLine>()
    private val state = MutableStateFlow<List<LogLine>>(emptyList())
    private val writer: BufferedWriter? = openWriter()

    /** The most recent lines, newest last, for the diagnostics screen. */
    val lines: StateFlow<List<LogLine>> = state.asStateFlow()

    val currentFile: Path get() = directory.resolve(CURRENT_FILE)

    fun debug(category: String, message: String) = write(LogLevel.DEBUG, category, message)

    fun info(category: String, message: String) = write(LogLevel.INFO, category, message)

    fun warn(category: String, message: String) = write(LogLevel.WARN, category, message)

    fun error(category: String, message: String, cause: Throwable? = null) {
        write(LogLevel.ERROR, category, message)
        cause?.let { write(LogLevel.ERROR, category, it.stackTraceToString().trimEnd()) }
    }

    fun recentLines(): List<LogLine> = synchronized(lock) { buffer.toList() }

    override fun close() {
        synchronized(lock) { runCatching { writer?.close() } }
    }

    private fun write(level: LogLevel, category: String, message: String) {
        val line = LogLine(Instant.now(), level, category, message)
        val snapshot = synchronized(lock) {
            buffer.addLast(line)
            while (buffer.size > IN_MEMORY_LINES) buffer.removeFirst()
            runCatching {
                writer?.apply {
                    write(line.format())
                    newLine()
                    flush()
                }
            }
            buffer.toList()
        }
        state.value = snapshot
    }

    /**
     * Opens the current log file, keeping the previous run's file next to it.
     * A launcher that cannot open its log still has to start, so every failure
     * here degrades to logging in memory only.
     */
    private fun openWriter(): BufferedWriter? = runCatching {
        Files.createDirectories(directory)
        rotate()
        Files.newBufferedWriter(
            currentFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
    }.getOrNull()

    private fun rotate() {
        val current = currentFile
        if (Files.exists(current)) {
            val stamp = ROTATION_FORMATTER.format(Instant.now())
            runCatching { Files.move(current, directory.resolve("launcher-$stamp.log")) }
        }
        runCatching {
            val rotated = Files.list(directory).use { stream ->
                stream.filter { it.fileName.toString().startsWith("launcher-") }.toList()
            }
            rotated.sortedByDescending { Files.getLastModifiedTime(it) }
                .drop(retainedFiles)
                .forEach { runCatching { Files.delete(it) } }
        }
    }

    private companion object {
        const val CURRENT_FILE = "launcher.log"
        const val IN_MEMORY_LINES = 500
        const val RETAINED_FILES = 5

        val ROTATION_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())
    }
}
