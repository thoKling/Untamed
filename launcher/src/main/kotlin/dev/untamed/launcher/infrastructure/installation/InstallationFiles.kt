package dev.untamed.launcher.infrastructure.installation

import dev.untamed.launcher.application.installation.CancellationSignal
import dev.untamed.launcher.application.installation.InstallationOutcome
import dev.untamed.launcher.application.installation.InstallationProgress
import dev.untamed.launcher.application.installation.InstallationStage
import dev.untamed.launcher.domain.download.DownloadBatch
import dev.untamed.launcher.domain.download.DownloadRequest
import dev.untamed.launcher.infrastructure.filesystem.Digests
import dev.untamed.launcher.infrastructure.filesystem.InstallationPaths
import dev.untamed.launcher.infrastructure.http.HttpTransport
import dev.untamed.launcher.infrastructure.http.TransportResult
import dev.untamed.launcher.infrastructure.logging.LauncherLog
import java.nio.file.Files
import java.nio.file.Path

/**
 * How an installer leaves early.
 *
 * The rest of the launcher returns results rather than throwing, and every
 * installer returns one too. Inside one, an installation is a long sequence of
 * steps with the same two ways of not working, and threading a result type
 * through every one of them would bury the sequence under its own error
 * handling. The exception never escapes [runInstallation].
 */
internal class Abort(val outcome: InstallationOutcome) :
    RuntimeException(null, null, false, false)

/**
 * Runs an installation and turns every way it can end into one outcome.
 *
 * The catch-all is deliberate. An installer touches the network, the disk and
 * documents written by someone else, and the failure that matters least is the
 * one nobody predicted: it still has to become a sentence under the button
 * rather than a stack trace on a console the user does not have.
 */
internal fun runInstallation(
    log: LauncherLog,
    category: String,
    block: () -> InstallationOutcome,
): InstallationOutcome = try {
    block()
} catch (abort: Abort) {
    abort.outcome
} catch (error: Exception) {
    log.error(category, "Installation failed", error)
    InstallationOutcome.Failed(
        "The installation could not be completed.",
        error.message ?: error::class.simpleName,
    )
}

/**
 * Turns per-chunk callbacks into progress the UI can render without being
 * flooded. Compose recomposes on every state change, and a 64 KB read from a
 * fast connection happens far more often than a screen refreshes.
 */
internal class ProgressReporter(private val emit: (InstallationProgress) -> Unit) {
    private var stage = InstallationStage.PREPARING
    private var detail = ""
    private var files = 0
    private var totalFiles = 0
    private var bytes = 0L
    private var totalBytes = 0L
    private var lastEmitted = 0L

    fun stage(stage: InstallationStage, detail: String, totalBytes: Long = 0) {
        this.stage = stage
        this.detail = detail
        this.files = 0
        this.totalFiles = 0
        this.bytes = 0
        this.totalBytes = totalBytes
        publish(force = true)
    }

    fun begin(stage: InstallationStage, files: Int, bytes: Long) {
        this.stage = stage
        this.detail = ""
        this.files = 0
        this.totalFiles = files
        this.bytes = 0
        this.totalBytes = bytes
        publish(force = true)
    }

    fun detail(detail: String) {
        this.detail = detail
        publish(force = false)
    }

    fun advanceBytes(count: Long) {
        bytes += count
        publish(force = false)
    }

    fun finishFile() {
        files += 1
        publish(force = totalFiles > 0 && files == totalFiles)
    }

    private fun publish(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastEmitted < INTERVAL_MILLIS) return
        lastEmitted = now
        emit(
            InstallationProgress(
                stage = stage,
                detail = detail,
                completedFiles = files,
                totalFiles = totalFiles,
                completedBytes = bytes,
                totalBytes = totalBytes,
            ),
        )
    }

    private companion object {
        const val INTERVAL_MILLIS = 120L
    }
}

/**
 * Everything an installer does to the disk, in one place.
 *
 * Both installers fetch verified files into an installation directory, and both
 * have to keep every write inside it. Sharing the code is not tidiness: a
 * containment check or a "is this file already correct" rule that exists twice
 * is a rule that will eventually be two different rules.
 */
internal class InstallationFiles(
    private val paths: InstallationPaths,
    private val transport: HttpTransport,
    private val log: LauncherLog,
    private val category: String,
) {

    /** Fetches a document into memory, or ends the installation. */
    fun text(url: String, cancellation: CancellationSignal, what: String): String =
        when (val result = transport.getText(url, cancellation)) {
            is TransportResult.Success -> result.value
            is TransportResult.Failure -> {
                result.cause?.let { log.error(category, result.reason, it) }
                abort("Could not $what.", result.reason)
            }

            TransportResult.Cancelled -> throw Abort(InstallationOutcome.Cancelled)
        }

    fun downloadAll(
        batch: DownloadBatch,
        cancellation: CancellationSignal,
        reporter: ProgressReporter,
        verifyExisting: Boolean,
    ) {
        for (request in batch.requests) {
            if (cancellation.isCancelled()) throw Abort(InstallationOutcome.Cancelled)
            reporter.detail(request.label)
            val kept = fetch(request, cancellation, verifyExisting) { reporter.advanceBytes(it) }
            if (kept) reporter.advanceBytes(request.sizeBytes)
            reporter.finishFile()
        }
    }

    /**
     * Puts one file where it belongs, downloading it only if it is not already
     * there and correct. Returns true when the existing file was kept.
     *
     * Skipping correct files is what makes a second launch cheap and what makes
     * a failed installation resumable: everything that arrived before the
     * failure is still there and still verified.
     */
    fun fetch(
        request: DownloadRequest,
        cancellation: CancellationSignal,
        verifyExisting: Boolean,
        onBytes: (Long) -> Unit = {},
    ): Boolean {
        val target = resolve(request.destination)
        if (isAlreadyCorrect(request, target, verifyExisting)) {
            log.debug(category, "Keeping ${request.destination}")
            return true
        }
        return when (val result = transport.download(request, target, cancellation, onBytes)) {
            is TransportResult.Success -> false
            is TransportResult.Failure -> {
                result.cause?.let { log.error(category, result.reason, it) }
                abort("Could not install ${request.label}.", result.reason)
            }

            TransportResult.Cancelled -> throw Abort(InstallationOutcome.Cancelled)
        }
    }

    /** Removes a file the installation has outgrown. Returns true if it went. */
    fun delete(destination: String): Boolean {
        val target = resolve(destination)
        return runCatching { Files.deleteIfExists(target) }
            .onFailure { log.warn(category, "Could not remove $destination: ${it.message}") }
            .getOrDefault(false)
    }

    fun createDirectories(directories: List<Path>) {
        directories.forEach { Files.createDirectories(it) }
    }

    /**
     * Resolves a planned destination against the installation directory, and
     * refuses anything that would land outside it. The planners already reject
     * such a path; this is the same check at the point where a name becomes a
     * real file, which is the one place it cannot be forgotten.
     */
    fun resolve(destination: String): Path {
        val root = paths.root.toAbsolutePath().normalize()
        val target = root.resolve(destination).normalize()
        if (!target.startsWith(root)) {
            abort("A file would be written outside the installation directory.", destination)
        }
        return target
    }

    fun abort(reason: String, detail: String?): Nothing {
        log.error(category, listOfNotNull(reason, detail).joinToString(" "))
        throw Abort(InstallationOutcome.Failed(reason, detail))
    }

    private fun isAlreadyCorrect(
        request: DownloadRequest,
        target: Path,
        verifyExisting: Boolean,
    ): Boolean {
        if (!Files.isRegularFile(target)) return false
        val size = runCatching { Files.size(target) }.getOrElse { return false }
        if (request.sizeBytes > 0 && size != request.sizeBytes) return false
        val checksum = request.checksum ?: return false
        if (!verifyExisting) return request.sizeBytes > 0
        val actual = runCatching { Digests.of(target, checksum.algorithm) }
            .getOrElse { return false }
        return checksum.matches(actual)
    }
}
