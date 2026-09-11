package dev.untamed.launcher.infrastructure.settings

import dev.untamed.launcher.application.settings.SettingsStore
import dev.untamed.launcher.domain.settings.LaunchBehaviour
import dev.untamed.launcher.domain.settings.LauncherSettings
import dev.untamed.launcher.infrastructure.filesystem.AtomicFiles
import dev.untamed.launcher.infrastructure.logging.LauncherLog
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Settings on disk, as JSON.
 *
 * Every field is optional in the document and filled from the defaults, so a
 * settings file written by an older launcher still loads, and a file a user has
 * edited by hand loses only the field they broke.
 */
class JsonSettingsStore(
    private val file: Path,
    private val defaultInstallationDirectory: String,
    private val log: LauncherLog,
) : SettingsStore {

    override fun load(): LauncherSettings {
        if (!Files.exists(file)) {
            log.info(CATEGORY, "No settings file yet, starting from defaults")
            return LauncherSettings.defaults(defaultInstallationDirectory)
        }
        return runCatching {
            val document = json.decodeFromString<SettingsDocument>(Files.readString(file))
            document.toSettings(defaultInstallationDirectory)
        }.getOrElse { failure ->
            // A broken file is set aside rather than silently overwritten: the
            // user may want to see what they typed, and they should not lose it
            // the moment the launcher saves anything.
            quarantine(failure.message ?: failure::class.simpleName.orEmpty())
            LauncherSettings.defaults(defaultInstallationDirectory)
        }
    }

    override fun save(settings: LauncherSettings) {
        val sanitised = settings.sanitised(defaultInstallationDirectory)
        runCatching {
            AtomicFiles.writeText(file, json.encodeToString(SettingsDocument.of(sanitised)))
            log.info(CATEGORY, "Saved settings to $file")
        }.onFailure { log.error(CATEGORY, "Could not save settings to $file", it) }
    }

    private fun quarantine(reason: String) {
        val stamp = STAMP.format(Instant.now())
        val target = file.resolveSibling("${file.fileName}.invalid-$stamp")
        runCatching { AtomicFiles.move(file, target) }
        log.warn(CATEGORY, "Settings file was unreadable ($reason), moved to $target")
    }

    private companion object {
        const val CATEGORY = "settings"

        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

        val STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())
    }
}

@Serializable
private data class SettingsDocument(
    val schemaVersion: Int = SCHEMA_VERSION,
    val installationDirectory: String? = null,
    val javaExecutable: String? = null,
    val memoryMegabytes: Int? = null,
    val windowWidth: Int? = null,
    val windowHeight: Int? = null,
    val fullscreen: Boolean? = null,
    val extraJvmArguments: String? = null,
    val automaticUpdates: Boolean? = null,
    val launchBehaviour: String? = null,
    val manifestUrl: String? = null,
) {
    fun toSettings(fallbackDirectory: String): LauncherSettings {
        val defaults = LauncherSettings.defaults(fallbackDirectory)
        return LauncherSettings(
            installationDirectory = installationDirectory ?: defaults.installationDirectory,
            javaExecutable = javaExecutable,
            memoryMegabytes = memoryMegabytes ?: defaults.memoryMegabytes,
            windowWidth = windowWidth ?: defaults.windowWidth,
            windowHeight = windowHeight ?: defaults.windowHeight,
            fullscreen = fullscreen ?: defaults.fullscreen,
            extraJvmArguments = extraJvmArguments ?: defaults.extraJvmArguments,
            automaticUpdates = automaticUpdates ?: defaults.automaticUpdates,
            launchBehaviour = launchBehaviour
                ?.let { name -> LaunchBehaviour.entries.firstOrNull { it.name == name } }
                ?: defaults.launchBehaviour,
            manifestUrl = manifestUrl,
        ).sanitised(fallbackDirectory)
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun of(settings: LauncherSettings): SettingsDocument = SettingsDocument(
            schemaVersion = SCHEMA_VERSION,
            installationDirectory = settings.installationDirectory,
            javaExecutable = settings.javaExecutable,
            memoryMegabytes = settings.memoryMegabytes,
            windowWidth = settings.windowWidth,
            windowHeight = settings.windowHeight,
            fullscreen = settings.fullscreen,
            extraJvmArguments = settings.extraJvmArguments,
            automaticUpdates = settings.automaticUpdates,
            launchBehaviour = settings.launchBehaviour.name,
            manifestUrl = settings.manifestUrl,
        )
    }
}
