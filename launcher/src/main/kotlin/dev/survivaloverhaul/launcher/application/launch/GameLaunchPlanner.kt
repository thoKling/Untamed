package dev.survivaloverhaul.launcher.application.launch

import dev.survivaloverhaul.launcher.domain.account.PlayerSession
import dev.survivaloverhaul.launcher.domain.java.JavaRuntime
import dev.survivaloverhaul.launcher.domain.launch.LaunchLayout
import dev.survivaloverhaul.launcher.domain.launch.LaunchPlan
import dev.survivaloverhaul.launcher.domain.minecraft.ArgumentTemplate
import dev.survivaloverhaul.launcher.domain.minecraft.HostPlatform
import dev.survivaloverhaul.launcher.domain.minecraft.Rules
import dev.survivaloverhaul.launcher.domain.minecraft.VersionProfile
import dev.survivaloverhaul.launcher.domain.settings.LauncherSettings

/** Whether a launch can be attempted, and what it would be. */
sealed interface LaunchPreparation {

    /**
     * [warnings] are the things the planner chose not to pass on, such as an
     * argument naming a placeholder it has no value for. They are worth logging
     * because they explain a game that starts slightly wrong.
     */
    data class Ready(val plan: LaunchPlan, val warnings: List<String>) : LaunchPreparation

    /** [problems] are written for the person reading the launcher's own screen. */
    data class Refused(val problems: List<String>) : LaunchPreparation
}

/**
 * Turns an installed version document into the command line that starts it.
 *
 * Pure, and that is the point of it. Everything difficult about launching
 * Minecraft lives here: rule-gated argument templates, feature flags,
 * placeholder substitution, classpath order. All of it can be checked by a test
 * that starts no process and touches no disk, which is the only practical way to
 * be sure about a command line otherwise observable only by running the game and
 * seeing what it complains about.
 *
 * Every value substituted comes from a version document Mojang or Fabric
 * published, from the launcher's own settings, or from a signed-in session. The
 * release manifest contributes versions and checksums and no argument text at
 * all, so nothing the product publishes can reach the command line.
 */
object GameLaunchPlanner {

    /**
     * [profile] must already be resolved against the version it inherits from,
     * and [versionId] is the id the game is started as, which for a Fabric
     * installation is the Fabric profile rather than the Minecraft version.
     */
    fun plan(
        profile: VersionProfile,
        versionId: String,
        session: PlayerSession,
        java: JavaRuntime,
        settings: LauncherSettings,
        layout: LaunchLayout,
        platform: HostPlatform,
        launcherName: String,
        launcherVersion: String,
    ): LaunchPreparation {
        val problems = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val mainClass = profile.mainClass.orEmpty().trim()
        if (mainClass.isEmpty()) {
            problems += "the version file for $versionId names no main class"
        }
        val assetsIndex = (profile.assetIndex?.id ?: profile.assetsId).orEmpty().trim()
        if (assetsIndex.isEmpty()) {
            problems += "the version file for $versionId names no asset index"
        }

        val classpath = classpath(profile, layout, platform, problems)
        if (problems.isNotEmpty()) return LaunchPreparation.Refused(problems)

        // Features are how a version document asks whether an argument applies
        // to this launch rather than to this machine. Only the ones the launcher
        // supports are named, so every other feature is absent and therefore
        // false, which is what keeps quick-play and demo arguments out of a
        // command line that has no values to fill them with.
        val features = mapOf("has_custom_resolution" to !settings.fullscreen)

        val values = placeholders(
            profile = profile,
            versionId = versionId,
            session = session,
            settings = settings,
            layout = layout,
            classpath = classpath,
            assetsIndex = assetsIndex,
            launcherName = launcherName,
            launcherVersion = launcherVersion,
        )

        val arguments = buildList {
            addAll(expand(profile.jvmArguments, platform, features, values, "JVM", warnings))
            add("-Xmx${settings.memoryMegabytes}M")
            loggingArgument(profile, layout)?.let(::add)
            // The user's own arguments go last among the JVM ones, so that one
            // of them can override a property the version document set. That is
            // the only reason to offer the setting at all.
            addAll(settings.extraJvmArgumentList())
            add(mainClass)
            addAll(expand(profile.gameArguments, platform, features, values, "game", warnings))
            // The only argument the launcher invents. Fullscreen is a setting
            // with no placeholder of its own in the document, and the client
            // reads it as an ordinary option.
            if (settings.fullscreen) add("--fullscreen")
        }

        return LaunchPreparation.Ready(
            plan = LaunchPlan(
                executable = java.executable,
                arguments = arguments,
                workingDirectory = layout.gameDirectory,
                nativesDirectory = layout.nativesDirectory,
                nativeArchives = profile.nativeLibrariesFor(platform).mapNotNull { library ->
                    library.relativePath?.let { join(layout.librariesDirectory, it) }
                },
                // Redaction is by value, so both of these have to be listed even
                // though only one of them is a credential. An xuid identifies the
                // account, and a log is a thing people paste in public.
                secrets = setOfNotNull(
                    session.accessToken.ifBlank { null },
                    session.xuid.ifBlank { null },
                ),
            ),
            warnings = warnings,
        )
    }

    /**
     * Every library this machine needs, then the client jar.
     *
     * Native libraries stay on the classpath as well as being unpacked. They are
     * ordinary jars that happen to contain a binary, several of them carry
     * classes beside it, and leaving them out is a missing class at startup for
     * the sake of tidiness.
     */
    private fun classpath(
        profile: VersionProfile,
        layout: LaunchLayout,
        platform: HostPlatform,
        problems: MutableList<String>,
    ): List<String> {
        val entries = mutableListOf<String>()
        for (library in profile.librariesFor(platform)) {
            val path = library.relativePath
            if (path == null) {
                problems += "the library '${library.name}' does not say where it lives"
                continue
            }
            entries += join(layout.librariesDirectory, path)
        }
        // Last, because a library that shipped an older copy of a Minecraft
        // class must not be shadowed by the client jar before the loader has
        // seen it. This is the order Mojang's own launcher uses.
        entries += layout.clientJar
        return entries.distinct()
    }

    /**
     * The `-Dlog4j.configurationFile` argument, or null when the version names
     * no logging configuration or the installer did not put one on disk. Missing
     * it costs the game its log formatting and nothing else, so it is not a
     * reason to refuse a launch.
     */
    private fun loggingArgument(profile: VersionProfile, layout: LaunchLayout): String? {
        val logging = profile.logging ?: return null
        val file = layout.loggingConfiguration ?: return null
        return logging.argument.replace(LOGGING_PLACEHOLDER, file)
    }

    private fun placeholders(
        profile: VersionProfile,
        versionId: String,
        session: PlayerSession,
        settings: LauncherSettings,
        layout: LaunchLayout,
        classpath: List<String>,
        assetsIndex: String,
        launcherName: String,
        launcherVersion: String,
    ): Map<String, String> = mapOf(
        "natives_directory" to layout.nativesDirectory,
        "launcher_name" to launcherName,
        "launcher_version" to launcherVersion,
        "classpath" to classpath.joinToString(layout.pathSeparator),
        "classpath_separator" to layout.pathSeparator,
        "library_directory" to layout.librariesDirectory,
        "primary_jar" to layout.clientJar,
        "version_name" to versionId,
        // Absent in a Fabric profile and inherited from Minecraft's, but a
        // default is kept anyway: this reaches the game as text on the debug
        // screen, and no launch should fail over a caption.
        "version_type" to (profile.type ?: "release"),
        "game_directory" to layout.gameDirectory,
        "assets_root" to layout.assetsDirectory,
        "assets_index_name" to assetsIndex,
        "auth_player_name" to session.userName,
        "auth_uuid" to session.uuid,
        "auth_access_token" to session.accessToken,
        "auth_xuid" to session.xuid,
        "clientid" to session.clientId,
        "user_type" to session.userType,
        // A legacy field the client still asks for and no longer reads. An empty
        // object is what every current launcher passes.
        "user_properties" to "{}",
        "resolution_width" to settings.windowWidth.toString(),
        "resolution_height" to settings.windowHeight.toString(),
    )

    private fun expand(
        templates: List<ArgumentTemplate>,
        platform: HostPlatform,
        features: Map<String, Boolean>,
        values: Map<String, String>,
        what: String,
        warnings: MutableList<String>,
    ): List<String> = buildList {
        for (template in templates) {
            if (!Rules.allows(template.rules, platform, features)) continue
            val expanded = template.values.map { substitute(it, values) }
            // A group is dropped whole. Arguments come in pairs such as
            // "--width" and its value, and passing the flag with an unresolved
            // placeholder behind it is worse than passing neither.
            if (expanded.any { it == null }) {
                warnings += "dropped the $what argument " +
                    "'${template.values.joinToString(" ")}': it names a value the " +
                    "launcher does not have"
                continue
            }
            addAll(expanded.filterNotNull())
        }
    }

    /**
     * The text with its placeholders filled in, or null when one of them has no
     * value. Null rather than an empty string on purpose: an empty game
     * directory or access token is a launch that fails in a way nobody can read.
     */
    private fun substitute(text: String, values: Map<String, String>): String? {
        if (!text.contains(OPENING)) return text
        var missing = false
        val filled = PLACEHOLDER.replace(text) { match ->
            val value = values[match.groupValues[1]]
            if (value == null) missing = true
            value ?: match.value
        }
        return filled.takeUnless { missing }
    }

    private fun join(directory: String, relative: String): String =
        directory.trimEnd('/', '\\') + "/" + relative.trimStart('/', '\\')

    private val PLACEHOLDER = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)}""")

    private const val OPENING = "\${"

    private const val LOGGING_PLACEHOLDER = "\${path}"
}
