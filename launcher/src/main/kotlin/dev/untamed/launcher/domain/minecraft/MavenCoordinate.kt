package dev.untamed.launcher.domain.minecraft

/**
 * A `group:artifact:version[:classifier][@extension]` coordinate and the
 * repository path it maps to.
 *
 * Mojang's own libraries carry an explicit `path`, so this is not needed for
 * them. Fabric's profile does not: its library entries carry a name and a
 * repository base URL and nothing else, so the launcher has to derive both the
 * download URL and the place on disk itself. Deriving it here, purely, is what
 * makes that derivation testable.
 */
data class MavenCoordinate(
    val group: String,
    val artifact: String,
    val version: String,
    val classifier: String? = null,
    val extension: String = "jar",
) {
    val fileName: String
        get() = buildString {
            append(artifact).append('-').append(version)
            classifier?.let { append('-').append(it) }
            append('.').append(extension)
        }

    /** The repository-relative path, `/`-separated, as maven lays it out. */
    val path: String
        get() = "${group.replace('.', '/')}/$artifact/$version/$fileName"

    companion object {
        /**
         * Parses a coordinate, or returns null when the text is not one.
         *
         * Null rather than an exception, because the text comes from a document
         * the launcher did not write and an unreadable library entry should
         * fail that installation with a message, not crash the launcher.
         */
        fun parse(coordinate: String): MavenCoordinate? {
            val withoutExtension = coordinate.substringBefore('@')
            val extension = coordinate.substringAfter('@', "jar").ifBlank { "jar" }
            val parts = withoutExtension.split(':')
            if (parts.size < 3) return null
            if (parts.take(3).any { it.isBlank() }) return null
            // A coordinate becomes a path under the installation directory, so
            // nothing that could climb out of it is accepted.
            if (parts.any { it == "." || it == ".." || '/' in it || '\\' in it }) return null
            return MavenCoordinate(
                group = parts[0],
                artifact = parts[1],
                version = parts[2],
                classifier = parts.getOrNull(3)?.ifBlank { null },
                extension = extension,
            )
        }
    }
}
