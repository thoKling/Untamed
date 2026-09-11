package dev.untamed.launcher.infrastructure.filesystem

import dev.untamed.launcher.domain.download.ChecksumAlgorithm
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Digests, in the one place that computes them.
 *
 * Every byte the launcher writes into an installation passes through here on
 * its way to disk. Nothing downloaded is trusted because of where it came
 * from; it is trusted because its digest matched the one its publisher stated.
 */
object Digests {

    fun digestFor(algorithm: ChecksumAlgorithm): MessageDigest =
        MessageDigest.getInstance(algorithm.javaName)

    fun of(bytes: ByteArray, algorithm: ChecksumAlgorithm): String =
        hex(digestFor(algorithm).digest(bytes))

    fun of(file: Path, algorithm: ChecksumAlgorithm): String {
        val digest = digestFor(algorithm)
        Files.newInputStream(file).use { stream -> consume(stream, digest) }
        return hex(digest.digest())
    }

    fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            append(HEX[value ushr 4])
            append(HEX[value and 0x0F])
        }
    }

    private fun consume(stream: InputStream, digest: MessageDigest) {
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) return
            digest.update(buffer, 0, read)
        }
    }

    private const val BUFFER_BYTES = 1 shl 16

    private val HEX = "0123456789abcdef".toCharArray()
}
