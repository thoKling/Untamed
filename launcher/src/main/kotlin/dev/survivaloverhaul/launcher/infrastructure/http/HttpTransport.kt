package dev.survivaloverhaul.launcher.infrastructure.http

import dev.survivaloverhaul.launcher.application.installation.CancellationSignal
import dev.survivaloverhaul.launcher.branding.Branding
import dev.survivaloverhaul.launcher.domain.download.Checksum
import dev.survivaloverhaul.launcher.domain.download.DownloadRequest
import dev.survivaloverhaul.launcher.infrastructure.filesystem.AtomicFiles
import dev.survivaloverhaul.launcher.infrastructure.filesystem.Digests
import dev.survivaloverhaul.launcher.infrastructure.logging.LauncherLog
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * A response whose body is wanted whatever its status was.
 *
 * Used by the sign-in chain, where a 401 carries the reason and a 200 carries
 * the token. Neither is ever logged.
 */
data class WebResponse(val status: Int, val body: String) {

    val isSuccessful: Boolean get() = status in 200..299

    override fun toString(): String = "WebResponse($status, ${body.length} characters)"
}

/** The result of one transfer. Failures carry a sentence, not an exception. */
sealed interface TransportResult<out T> {

    data class Success<T>(val value: T) : TransportResult<T>

    data class Failure(val reason: String, val cause: Throwable? = null) : TransportResult<Nothing>

    data object Cancelled : TransportResult<Nothing>
}

/**
 * The launcher's only route to the network.
 *
 * Everything it enforces, it enforces here rather than at each call site:
 *
 *  - HTTPS only. A plain-HTTP URL is refused, wherever it came from.
 *  - Nothing is written into an installation without a digest that matched.
 *    The check happens on the bytes as they stream past, and a file that fails
 *    it never reaches its destination.
 *  - Writes land atomically. An interrupted download leaves the previous file
 *    in place, never a truncated one.
 *  - Transient failures are retried with a backoff; a digest mismatch is not,
 *    because a server that served the wrong bytes twice is not a network blip.
 */
class HttpTransport(
    private val log: LauncherLog,
    connectTimeout: Duration = Duration.ofSeconds(20),
    private val readTimeout: Duration = Duration.ofSeconds(60),
    private val defaultAttempts: Int = DEFAULT_ATTEMPTS,
) {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        // Mojang's and Fabric's CDNs redirect. NORMAL follows a redirect only
        // when it stays on HTTPS, which is why it is used instead of ALWAYS.
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /**
     * Fetches a small document into memory. Used for indexes and digests.
     *
     * [attempts] is per call because not every document is worth waiting for.
     * A version index the installation cannot proceed without is worth a retry
     * backoff; a release manifest with a pinned copy sitting in the jar is not.
     */
    fun getBytes(
        url: String,
        cancellation: CancellationSignal = CancellationSignal.NEVER,
        attempts: Int = defaultAttempts,
    ): TransportResult<ByteArray> = attempt(url, cancellation, attempts) {
        val response = client.send(requestFor(url), HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() !in SUCCESSFUL) throw HttpStatusException(response.statusCode())
        response.body()
    }

    fun getText(
        url: String,
        cancellation: CancellationSignal = CancellationSignal.NEVER,
        attempts: Int = defaultAttempts,
    ): TransportResult<String> = when (val result = getBytes(url, cancellation, attempts)) {
        is TransportResult.Success -> TransportResult.Success(result.value.toString(Charsets.UTF_8))
        is TransportResult.Failure -> result
        TransportResult.Cancelled -> TransportResult.Cancelled
    }

    /**
     * One request whose body matters whatever the status code was.
     *
     * The download path treats a 401 as a failure with a sentence, which is
     * right for a file and wrong for an account: every refusal in the sign-in
     * chain arrives as a 4xx whose body is the only place the reason is
     * written. So this returns the status and the body and decides nothing.
     *
     * A null [body] is a GET. Nothing here is logged but the address: the
     * request bodies carry authorization codes and tokens, and the response
     * bodies carry more of them.
     */
    fun exchange(
        url: String,
        body: String? = null,
        contentType: String = JSON_CONTENT_TYPE,
        headers: Map<String, String> = emptyMap(),
        cancellation: CancellationSignal = CancellationSignal.NEVER,
        attempts: Int = EXCHANGE_ATTEMPTS,
    ): TransportResult<WebResponse> = attempt(url, cancellation, attempts) {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .timeout(readTimeout)
            .header("User-Agent", USER_AGENT)
            .header("Accept", JSON_CONTENT_TYPE)
        headers.forEach { (name, value) -> builder.header(name, value) }
        if (body == null) {
            builder.GET()
        } else {
            builder.header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, Charsets.UTF_8))
        }
        val response = builder.build().let { request ->
            client.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        }
        WebResponse(response.statusCode(), response.body())
    }

    /**
     * Downloads one file to [target], verifying it as it arrives.
     *
     * A request with no checksum is refused outright. Every caller in this
     * launcher either has a digest from the document that named the file or
     * fetches one from the publisher first, so a missing digest is a bug in the
     * caller rather than a file to install hopefully.
     */
    fun download(
        request: DownloadRequest,
        target: Path,
        cancellation: CancellationSignal = CancellationSignal.NEVER,
        onBytes: (Long) -> Unit = {},
    ): TransportResult<Long> {
        val checksum = request.checksum
            ?: return TransportResult.Failure(
                describe(request, "has no checksum to verify against"),
            )
        val problems = request.problems()
        if (problems.isNotEmpty()) {
            return TransportResult.Failure(describe(request, "was rejected: ${problems.first()}"))
        }

        return attempt(request.url, cancellation, defaultAttempts) {
            val directory = target.parent
                ?: error("a download target must have a parent directory: $target")
            Files.createDirectories(directory)
            val temporary = Files.createTempFile(directory, target.fileName.toString(), ".part")
            try {
                val response =
                    client.send(requestFor(request.url), HttpResponse.BodyHandlers.ofInputStream())
                if (response.statusCode() !in SUCCESSFUL) {
                    throw HttpStatusException(response.statusCode())
                }
                val written = response.body().use { body ->
                    copy(body, temporary, checksum, cancellation, onBytes)
                }
                AtomicFiles.move(temporary, target)
                written
            } finally {
                runCatching { Files.deleteIfExists(temporary) }
            }
        }
    }

    /**
     * Streams the body to a temporary file, hashing as it goes, and refuses to
     * keep it if the digest disagrees. Hashing during the copy rather than in a
     * second pass means a large file is read once, not twice.
     */
    private fun copy(
        body: InputStream,
        temporary: Path,
        checksum: Checksum,
        cancellation: CancellationSignal,
        onBytes: (Long) -> Unit,
    ): Long {
        val digest = Digests.digestFor(checksum.algorithm)
        var total = 0L
        Files.newOutputStream(temporary).use { output ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                if (cancellation.isCancelled()) throw CancelledException()
                val read = body.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                output.write(buffer, 0, read)
                total += read
                onBytes(read.toLong())
            }
        }
        val actual = Digests.hex(digest.digest())
        if (!checksum.matches(actual)) {
            throw ChecksumMismatchException(checksum.value, actual)
        }
        return total
    }

    /**
     * Runs [action], retrying what is worth retrying.
     *
     * A checksum mismatch and a refused URL are final. A timeout, a dropped
     * connection or a server error is not: the content networks this launcher
     * talks to fail that way occasionally, and asking someone to start a
     * multi-gigabyte download again because of one is not acceptable.
     */
    private fun <T> attempt(
        url: String,
        cancellation: CancellationSignal,
        attempts: Int,
        action: () -> T,
    ): TransportResult<T> {
        if (!url.startsWith("https://")) {
            return TransportResult.Failure("refused a URL that is not https: $url")
        }
        var lastFailure: TransportResult.Failure? = null
        for (attempt in 1..attempts) {
            if (cancellation.isCancelled()) return TransportResult.Cancelled
            try {
                return TransportResult.Success(action())
            } catch (_: CancelledException) {
                return TransportResult.Cancelled
            } catch (mismatch: ChecksumMismatchException) {
                log.error(CATEGORY, "$url did not match its checksum: ${mismatch.message}")
                return TransportResult.Failure("the file at $url failed its checksum")
            } catch (status: HttpStatusException) {
                lastFailure =
                    TransportResult.Failure("$url answered HTTP ${status.code}", status)
                // A 404 or a 403 will say the same thing however many times it
                // is asked, and retrying it only makes the failure slower.
                if (status.code in CLIENT_ERRORS) return lastFailure
            } catch (error: IOException) {
                val detail = error.message ?: error::class.simpleName.orEmpty()
                lastFailure =
                    TransportResult.Failure("could not reach $url: $detail", error)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return TransportResult.Cancelled
            }
            if (attempt < attempts) {
                log.warn(CATEGORY, "Attempt $attempt of $attempts failed for $url")
                if (!pause(BACKOFF_MILLIS * attempt, cancellation)) return TransportResult.Cancelled
            }
        }
        return lastFailure ?: TransportResult.Failure("could not fetch $url")
    }

    /** Waits between attempts, staying responsive to cancellation. */
    private fun pause(millis: Long, cancellation: CancellationSignal): Boolean {
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            if (cancellation.isCancelled()) return false
            try {
                Thread.sleep(SLEEP_SLICE_MILLIS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return true
    }

    private fun requestFor(url: String): HttpRequest = HttpRequest.newBuilder(URI.create(url))
        .timeout(readTimeout)
        .header("User-Agent", USER_AGENT)
        .GET()
        .build()

    private fun describe(request: DownloadRequest, problem: String): String =
        "${request.label} $problem"

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    private class ChecksumMismatchException(expected: String, actual: String) :
        IOException("expected $expected, got $actual")

    private class CancelledException : IOException("cancelled")

    private companion object {
        const val CATEGORY = "download"
        const val BUFFER_BYTES = 1 shl 16
        const val DEFAULT_ATTEMPTS = 3

        /**
         * Two, not three. A token exchange spends a single-use code, and a
         * retry after a timeout can only be worth one attempt before the code
         * is gone anyway.
         */
        const val EXCHANGE_ATTEMPTS = 2
        const val JSON_CONTENT_TYPE = "application/json"
        const val BACKOFF_MILLIS = 900L
        const val SLEEP_SLICE_MILLIS = 50L

        val SUCCESSFUL = 200..299
        val CLIENT_ERRORS = 400..499

        /**
         * Identifies the launcher honestly. Mojang's endpoints are public, but
         * an unofficial client that hides what it is gives the people running
         * them no way to tell traffic apart, and being identifiable is part of
         * being a legitimate third-party launcher.
         */
        val USER_AGENT: String =
            "${Branding.LAUNCHER_BRAND}/${Branding.LAUNCHER_VERSION} (unofficial)"
    }
}
