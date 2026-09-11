package dev.untamed.launcher.domain.minecraft

/** A file a version document points at, with whatever it says about it. */
data class RemoteFile(
    val url: String,
    val sha1: String? = null,
    val sizeBytes: Long = 0,
)

/**
 * One entry of a version document's `libraries` list.
 *
 * Two shapes arrive here. Mojang's entries carry a `downloads.artifact` block
 * with a path, URL, size and SHA-1. Fabric's carry a coordinate and the base
 * URL of a maven repository and nothing else, which is why [repositoryUrl]
 * exists and why [download] is nullable.
 *
 * A Fabric library therefore has no digest in the document it came from. The
 * installer fetches the `.sha1` maven publishes beside the artifact and
 * verifies against that, so no artifact is ever written unverified.
 */
data class Library(
    val name: String,
    val download: RemoteFile? = null,
    val explicitPath: String? = null,
    val repositoryUrl: String? = null,
    val rules: List<Rule> = emptyList(),
) {
    val coordinate: MavenCoordinate? get() = MavenCoordinate.parse(name)

    /** Where the artifact lives under `libraries/`, or null if it cannot be placed. */
    val relativePath: String? get() = explicitPath ?: coordinate?.path

    /** Where to fetch it from, or null if the document said nothing useful. */
    val url: String?
        get() = download?.url ?: repositoryUrl?.let { base ->
            relativePath?.let { "${base.trimEnd('/')}/$it" }
        }

    /**
     * Two entries are the same library when they name the same artifact,
     * whatever version each carries. Fabric's profile overrides several of
     * Minecraft's libraries this way, and the child's version has to win.
     */
    val identity: String
        get() = coordinate?.let { "${it.group}:${it.artifact}:${it.classifier.orEmpty()}" } ?: name

    /**
     * Whether this entry is a native binary rather than a jar of classes.
     *
     * In 26.2 natives are ordinary rule-gated libraries and the only thing that
     * marks one is a `natives-…` maven classifier. The rules decide whether the
     * entry applies to this machine; this decides what has to be unpacked
     * before the game can find it.
     */
    val isNative: Boolean get() = coordinate?.classifier?.startsWith("natives") == true
}

/**
 * One entry of a version document's `arguments` list.
 *
 * Two shapes arrive: a bare string, and an object carrying rules and either one
 * value or several. Both become this, so an argument is rule-gated by exactly
 * the same evaluation a library is, features included.
 */
data class ArgumentTemplate(
    val values: List<String>,
    val rules: List<Rule> = emptyList(),
)

data class AssetIndexReference(
    val id: String,
    val url: String,
    val sha1: String? = null,
    val sizeBytes: Long = 0,
    val totalSizeBytes: Long = 0,
)

/** Where log4j's configuration comes from, and the argument that points at it. */
data class LoggingConfiguration(
    val argument: String,
    val fileId: String,
    val file: RemoteFile,
)

/**
 * A Minecraft version document, reduced to what installing and starting it
 * requires.
 *
 * The launcher writes these documents to disk verbatim, exactly where
 * Minecraft's own layout expects them, and reads them back from there when it
 * starts the game. Nothing is remembered between the two: the file on disk is
 * the one that was verified against Mojang's digest, so it is the one that
 * decides how the game runs.
 */
data class VersionProfile(
    val id: String,
    val inheritsFrom: String? = null,
    val mainClass: String? = null,
    val javaMajorVersion: Int? = null,
    val assetsId: String? = null,
    val assetIndex: AssetIndexReference? = null,
    val client: RemoteFile? = null,
    val libraries: List<Library> = emptyList(),
    val logging: LoggingConfiguration? = null,
    /** `release` or `snapshot`, which the game is told about as `version_type`. */
    val type: String? = null,
    val jvmArguments: List<ArgumentTemplate> = emptyList(),
    val gameArguments: List<ArgumentTemplate> = emptyList(),
) {
    /**
     * This profile resolved against the one it inherits from.
     *
     * Fabric's profile is a delta: an id, a main class, a handful of libraries
     * and nothing else. Everything it does not state comes from the Minecraft
     * version it inherits from, and its own libraries take precedence where
     * both name the same artifact.
     */
    fun inheriting(parent: VersionProfile): VersionProfile {
        val overridden = libraries.map { it.identity }.toSet()
        return copy(
            inheritsFrom = null,
            mainClass = mainClass ?: parent.mainClass,
            javaMajorVersion = javaMajorVersion ?: parent.javaMajorVersion,
            assetsId = assetsId ?: parent.assetsId,
            assetIndex = assetIndex ?: parent.assetIndex,
            client = client ?: parent.client,
            libraries = libraries + parent.libraries.filterNot { it.identity in overridden },
            logging = logging ?: parent.logging,
            type = type ?: parent.type,
            // Arguments are added to, not replaced. Fabric's profile contributes
            // the few JVM arguments its loader needs and expects Minecraft's own
            // to still be there, so the parent's come first and the child's
            // after, which is also the order that lets a child override a
            // property the parent set.
            jvmArguments = parent.jvmArguments + jvmArguments,
            gameArguments = parent.gameArguments + gameArguments,
        )
    }

    /** The libraries that apply to [platform], in classpath order. */
    fun librariesFor(platform: HostPlatform): List<Library> =
        libraries.filter { Rules.allows(it.rules, platform) }

    /** The libraries that have to be unpacked before the game can load them. */
    fun nativeLibrariesFor(platform: HostPlatform): List<Library> =
        librariesFor(platform).filter { it.isNative }
}
