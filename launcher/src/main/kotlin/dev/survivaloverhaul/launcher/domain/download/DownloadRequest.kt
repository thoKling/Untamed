package dev.survivaloverhaul.launcher.domain.download

/**
 * The digests the launcher meets. Mojang publishes SHA-1 for everything in a
 * version manifest and Fabric's maven publishes SHA-1 beside each artifact;
 * this project's own manifest uses SHA-256. Both are here because verifying an
 * artifact against the digest its publisher actually offers is worth more than
 * insisting on one algorithm and skipping the check when it is absent.
 */
enum class ChecksumAlgorithm(val javaName: String, val hexLength: Int) {
    SHA1("SHA-1", 40),
    SHA256("SHA-256", 64),
}

data class Checksum(val algorithm: ChecksumAlgorithm, val value: String) {

    val isWellFormed: Boolean
        get() = value.length == algorithm.hexLength && value.all { it in HEX_DIGITS }

    fun matches(actual: String): Boolean = value.equals(actual, ignoreCase = true)

    override fun toString(): String = "${algorithm.javaName}:${value.lowercase()}"

    companion object {
        private const val HEX_DIGITS = "0123456789abcdefABCDEF"

        fun sha1(value: String): Checksum = Checksum(ChecksumAlgorithm.SHA1, value)

        fun sha256(value: String): Checksum = Checksum(ChecksumAlgorithm.SHA256, value)
    }
}

/**
 * One file the launcher intends to fetch, described without reference to the
 * filesystem: [destination] is a relative, `/`-separated path under the
 * installation directory, which the infrastructure layer resolves.
 *
 * Keeping the destination relative and textual is what lets the planner that
 * builds these be pure, and it is also where the containment check lives. A
 * path that could climb out of the installation directory is rejected here,
 * before anything joins it to a real directory.
 */
data class DownloadRequest(
    val label: String,
    val url: String,
    val destination: String,
    val checksum: Checksum?,
    val sizeBytes: Long = 0,
) {
    /**
     * Everything wrong with this request. Non-empty means it is not downloaded:
     * a request the launcher cannot vouch for is a failure, never a file that
     * quietly lands unverified.
     */
    fun problems(): List<String> = buildList {
        if (!url.startsWith("https://")) add("'$label' is not served over https")
        if (checksum != null && !checksum.isWellFormed) {
            add("'$label' has an unusable ${checksum.algorithm.javaName} digest")
        }
        val segments = destination.split('/')
        when {
            destination.isBlank() -> add("'$label' has no destination")
            destination.startsWith('/') -> add("'$label' has an absolute destination")
            segments.any { it.isBlank() || it == "." || it == ".." } ->
                add("'$label' has a destination that leaves the installation directory")

            segments.any { '\\' in it || ':' in it } ->
                add("'$label' has a destination with an unusable name")
        }
    }
}

/**
 * A batch of files with a common purpose, so progress can be reported per
 * stage rather than as one undifferentiated bar of ten thousand assets.
 */
data class DownloadBatch(
    val requests: List<DownloadRequest>,
) {
    val totalBytes: Long get() = requests.sumOf { it.sizeBytes }

    val isEmpty: Boolean get() = requests.isEmpty()

    fun problems(): List<String> = requests.flatMap { it.problems() }

    companion object {
        val EMPTY = DownloadBatch(emptyList())
    }
}
