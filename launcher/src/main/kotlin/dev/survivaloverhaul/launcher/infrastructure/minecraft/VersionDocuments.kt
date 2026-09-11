package dev.survivaloverhaul.launcher.infrastructure.minecraft

import dev.survivaloverhaul.launcher.domain.minecraft.ArgumentTemplate
import dev.survivaloverhaul.launcher.domain.minecraft.AssetIndex
import dev.survivaloverhaul.launcher.domain.minecraft.AssetIndexReference
import dev.survivaloverhaul.launcher.domain.minecraft.AssetObject
import dev.survivaloverhaul.launcher.domain.minecraft.Library
import dev.survivaloverhaul.launcher.domain.minecraft.LoggingConfiguration
import dev.survivaloverhaul.launcher.domain.minecraft.OsConstraint
import dev.survivaloverhaul.launcher.domain.minecraft.RemoteFile
import dev.survivaloverhaul.launcher.domain.minecraft.Rule
import dev.survivaloverhaul.launcher.domain.minecraft.RuleAction
import dev.survivaloverhaul.launcher.domain.minecraft.VersionIndex
import dev.survivaloverhaul.launcher.domain.minecraft.VersionIndexEntry
import dev.survivaloverhaul.launcher.domain.minecraft.VersionProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * The wire shapes of Mojang's and Fabric's version documents, kept apart from
 * the domain model for the same reason the release manifest is: these are
 * documents someone else publishes, and every field has to cross one explicit
 * mapping before the rest of the launcher can act on it.
 *
 * Only what installing and starting the game needs is declared. What is not
 * declared is ignored rather than rejected, because Mojang adds fields to these
 * documents on its own schedule.
 */
object VersionDocuments {

    /**
     * Unknown keys are ignored on purpose. Mojang adds fields to these
     * documents without warning, and a launcher that refuses to install because
     * a new key appeared is a launcher that breaks on Mojang's schedule.
     */
    val json: Json = Json { ignoreUnknownKeys = true }

    fun parseIndex(text: String): VersionIndex =
        json.decodeFromString<VersionIndexDocument>(text).toIndex()

    fun parseProfile(text: String): VersionProfile =
        json.decodeFromString<VersionProfileDocument>(text).toProfile()

    fun parseAssetIndex(id: String, text: String): AssetIndex =
        json.decodeFromString<AssetIndexDocument>(text).toIndex(id)
}

@Serializable
internal data class VersionIndexDocument(
    val latest: LatestDocument = LatestDocument(),
    val versions: List<VersionIndexEntryDocument> = emptyList(),
) {
    fun toIndex(): VersionIndex = VersionIndex(
        latestRelease = latest.release.ifBlank { null },
        entries = versions.map {
            VersionIndexEntry(
                id = it.id,
                type = it.type,
                url = it.url,
                sha1 = it.sha1?.ifBlank { null },
            )
        },
    )
}

@Serializable
internal data class LatestDocument(val release: String = "", val snapshot: String = "")

@Serializable
internal data class VersionIndexEntryDocument(
    val id: String = "",
    val type: String = "",
    val url: String = "",
    val sha1: String? = null,
)

@Serializable
internal data class VersionProfileDocument(
    val id: String = "",
    val inheritsFrom: String? = null,
    val mainClass: String? = null,
    val javaVersion: JavaVersionDocument? = null,
    val assets: String? = null,
    val assetIndex: AssetIndexReferenceDocument? = null,
    val downloads: DownloadsDocument = DownloadsDocument(),
    val libraries: List<LibraryDocument> = emptyList(),
    val logging: LoggingDocument? = null,
    val type: String? = null,
    val arguments: ArgumentsDocument? = null,
) {
    fun toProfile(): VersionProfile = VersionProfile(
        id = id,
        inheritsFrom = inheritsFrom?.ifBlank { null },
        mainClass = mainClass?.ifBlank { null },
        javaMajorVersion = javaVersion?.majorVersion?.takeIf { it > 0 },
        assetsId = assets?.ifBlank { null },
        assetIndex = assetIndex?.toReference(),
        client = downloads.client?.toRemoteFile(),
        libraries = libraries.map { it.toLibrary() },
        logging = logging?.client?.toConfiguration(),
        type = type?.ifBlank { null },
        jvmArguments = arguments?.jvm.orEmpty().mapNotNull(::argumentTemplate),
        gameArguments = arguments?.game.orEmpty().mapNotNull(::argumentTemplate),
    )
}

/**
 * The `arguments` block, held as raw JSON because its entries are of two
 * different kinds and kotlinx.serialization has no polymorphism to lean on
 * here: a plain string, or an object with rules and a value.
 *
 * The pre-1.13 `minecraftArguments` string is deliberately not read. This
 * launcher installs the version its manifest pins, that version uses this
 * format, and supporting a format nothing here can install would be code with
 * no way to be right or wrong.
 */
@Serializable
internal data class ArgumentsDocument(
    val jvm: List<JsonElement> = emptyList(),
    val game: List<JsonElement> = emptyList(),
)

/**
 * One argument entry, or null when it is neither of the two shapes. An entry
 * that cannot be read is dropped rather than failing the launch: it is one
 * argument out of dozens, and the launcher reports what it dropped.
 */
private fun argumentTemplate(element: JsonElement): ArgumentTemplate? = when (element) {
    is JsonPrimitive ->
        if (element.isString) ArgumentTemplate(listOf(element.content)) else null

    is JsonObject -> runCatching {
        VersionDocuments.json.decodeFromJsonElement<ConditionalArgumentDocument>(element)
    }.getOrNull()?.toTemplate()

    else -> null
}

@Serializable
internal data class ConditionalArgumentDocument(
    val rules: List<RuleDocument> = emptyList(),
    val value: JsonElement = JsonNull,
) {
    fun toTemplate(): ArgumentTemplate? {
        val values = when (value) {
            is JsonPrimitive -> listOfNotNull(text(value))
            is JsonArray -> value.mapNotNull { text(it) }
            else -> emptyList()
        }
        if (values.isEmpty()) return null
        return ArgumentTemplate(values, rules.map { it.toRule() })
    }

    /** The element as an argument, or null when it is not a string. */
    private fun text(element: JsonElement): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.content
}

@Serializable
internal data class JavaVersionDocument(
    val component: String = "",
    val majorVersion: Int = 0,
)

@Serializable
internal data class DownloadsDocument(val client: RemoteFileDocument? = null)

@Serializable
internal data class RemoteFileDocument(
    val url: String = "",
    val sha1: String? = null,
    val size: Long = 0,
) {
    fun toRemoteFile(): RemoteFile = RemoteFile(url, sha1?.ifBlank { null }, size)
}

@Serializable
internal data class AssetIndexReferenceDocument(
    val id: String = "",
    val url: String = "",
    val sha1: String? = null,
    val size: Long = 0,
    val totalSize: Long = 0,
) {
    fun toReference(): AssetIndexReference =
        AssetIndexReference(id, url, sha1?.ifBlank { null }, size, totalSize)
}

/**
 * A library entry in either of the two shapes that reach the launcher: Mojang's
 * with a `downloads.artifact` block, and Fabric's with a coordinate and the
 * base URL of a maven repository.
 *
 * `downloads.classifiers` is deliberately absent. It is how launchers used to
 * find native binaries, and 26.2 does not use it: natives are ordinary
 * rule-gated library artifacts now. Declaring a field for it would invite code
 * that reads a block no current version publishes.
 */
@Serializable
internal data class LibraryDocument(
    val name: String = "",
    val downloads: LibraryDownloadsDocument? = null,
    val url: String? = null,
    val rules: List<RuleDocument> = emptyList(),
) {
    fun toLibrary(): Library = Library(
        name = name,
        download = downloads?.artifact?.toRemoteFile(),
        explicitPath = downloads?.artifact?.path?.ifBlank { null },
        repositoryUrl = url?.ifBlank { null },
        rules = rules.map { it.toRule() },
    )
}

@Serializable
internal data class LibraryDownloadsDocument(val artifact: LibraryArtifactDocument? = null)

@Serializable
internal data class LibraryArtifactDocument(
    val path: String? = null,
    val url: String = "",
    val sha1: String? = null,
    val size: Long = 0,
) {
    fun toRemoteFile(): RemoteFile = RemoteFile(url, sha1?.ifBlank { null }, size)
}

@Serializable
internal data class RuleDocument(
    val action: String = "allow",
    val os: OsDocument? = null,
    val features: Map<String, Boolean> = emptyMap(),
) {
    fun toRule(): Rule = Rule(
        // Anything that is not the word "allow" is treated as a refusal. An
        // action this launcher does not recognise must never widen what gets
        // installed.
        action = if (action.equals("allow", ignoreCase = true)) {
            RuleAction.ALLOW
        } else {
            RuleAction.DISALLOW
        },
        os = os?.toConstraint(),
        features = features,
    )
}

@Serializable
internal data class OsDocument(
    val name: String? = null,
    val version: String? = null,
    val arch: String? = null,
) {
    fun toConstraint(): OsConstraint = OsConstraint(
        name = name?.ifBlank { null },
        version = version?.ifBlank { null },
        architecture = arch?.ifBlank { null },
    )
}

@Serializable
internal data class LoggingDocument(val client: LoggingClientDocument? = null)

@Serializable
internal data class LoggingClientDocument(
    val argument: String = "",
    val file: LoggingFileDocument = LoggingFileDocument(),
) {
    fun toConfiguration(): LoggingConfiguration? {
        val id = file.id.ifBlank { return null }
        if (file.url.isBlank()) return null
        return LoggingConfiguration(
            argument = argument,
            fileId = id,
            file = RemoteFile(file.url, file.sha1?.ifBlank { null }, file.size),
        )
    }
}

@Serializable
internal data class LoggingFileDocument(
    val id: String = "",
    val url: String = "",
    val sha1: String? = null,
    val size: Long = 0,
)

@Serializable
internal data class AssetIndexDocument(
    val objects: Map<String, AssetObjectDocument> = emptyMap(),
) {
    fun toIndex(id: String): AssetIndex = AssetIndex(
        id = id,
        objects = objects.map { (name, entry) ->
            AssetObject(name = name, hash = entry.hash.lowercase(), sizeBytes = entry.size)
        },
    )
}

@Serializable
internal data class AssetObjectDocument(val hash: String = "", val size: Long = 0)
