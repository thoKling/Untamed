package dev.untamed.launcher

import dev.untamed.launcher.application.launch.GameLaunchPlanner
import dev.untamed.launcher.application.launch.LaunchPreparation
import dev.untamed.launcher.domain.account.PlayerSession
import dev.untamed.launcher.domain.java.JavaRuntime
import dev.untamed.launcher.domain.java.JavaSource
import dev.untamed.launcher.domain.launch.LaunchLayout
import dev.untamed.launcher.domain.launch.LaunchPlan
import dev.untamed.launcher.domain.minecraft.ArgumentTemplate
import dev.untamed.launcher.domain.minecraft.AssetIndexReference
import dev.untamed.launcher.domain.minecraft.HostPlatform
import dev.untamed.launcher.domain.minecraft.Library
import dev.untamed.launcher.domain.minecraft.LoggingConfiguration
import dev.untamed.launcher.domain.minecraft.OsConstraint
import dev.untamed.launcher.domain.minecraft.RemoteFile
import dev.untamed.launcher.domain.minecraft.Rule
import dev.untamed.launcher.domain.minecraft.RuleAction
import dev.untamed.launcher.domain.minecraft.VersionProfile
import dev.untamed.launcher.domain.settings.LauncherSettings
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The command line is the launcher's least observable output: it is either
 * right or the game fails with something that reads like a Minecraft problem.
 * These cover the parts a version document can vary, with a profile shaped like
 * the merged 26.2 one rather than a convenient one.
 */
class GameLaunchPlannerTest {

    private val windows = HostPlatform("windows", "x86_64", "10.0")

    private val session = PlayerSession(
        userName = "Steve",
        uuid = "11112222333344445555666677778888",
        accessToken = "an-access-token",
        xuid = "2535000000000000",
        clientId = "a-client-id",
    )

    private val java = JavaRuntime(
        executable = "C:/java/bin/java.exe",
        majorVersion = 25,
        description = "OpenJDK 25",
        source = JavaSource.CONFIGURED,
    )

    private val settings = LauncherSettings(
        installationDirectory = "C:/game",
        memoryMegabytes = 6144,
        windowWidth = 1600,
        windowHeight = 900,
    )

    private val layout = LaunchLayout(
        gameDirectory = "C:/game",
        assetsDirectory = "C:/game/assets",
        librariesDirectory = "C:/game/libraries",
        nativesDirectory = "C:/game/natives/fabric-loader-0.19.5-26.2",
        clientJar = "C:/game/versions/26.2/26.2.jar",
        pathSeparator = ";",
        loggingConfiguration = "C:/game/assets/log_configs/client-1.12.xml",
    )

    private val profile = VersionProfile(
        id = "fabric-loader-0.19.5-26.2",
        mainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient",
        assetIndex = AssetIndexReference(id = "26", url = "https://example.invalid/26.json"),
        type = "release",
        libraries = listOf(
            library("com.mojang:logging:1.5.10", "com/mojang/logging/1.5.10/logging-1.5.10.jar"),
            library(
                name = "org.lwjgl:lwjgl:3.3.3:natives-windows",
                path = "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar",
                os = "windows",
            ),
            library(
                name = "org.lwjgl:lwjgl:3.3.3:natives-linux",
                path = "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar",
                os = "linux",
            ),
        ),
        logging = LoggingConfiguration(
            argument = "-Dlog4j.configurationFile=\${path}",
            fileId = "client-1.12.xml",
            file = RemoteFile("https://example.invalid/client-1.12.xml"),
        ),
        jvmArguments = listOf(
            ArgumentTemplate(listOf("-Djava.library.path=\${natives_directory}")),
            ArgumentTemplate(listOf("-Dminecraft.launcher.brand=\${launcher_name}")),
            ArgumentTemplate(listOf("-cp", "\${classpath}")),
            ArgumentTemplate(
                values = listOf("-XstartOnFirstThread"),
                rules = listOf(Rule(RuleAction.ALLOW, OsConstraint(name = "osx"))),
            ),
        ),
        gameArguments = listOf(
            ArgumentTemplate(listOf("--username", "\${auth_player_name}")),
            ArgumentTemplate(listOf("--uuid", "\${auth_uuid}")),
            ArgumentTemplate(listOf("--accessToken", "\${auth_access_token}")),
            ArgumentTemplate(listOf("--version", "\${version_name}")),
            ArgumentTemplate(listOf("--gameDir", "\${game_directory}")),
            ArgumentTemplate(listOf("--assetIndex", "\${assets_index_name}")),
            ArgumentTemplate(
                values = listOf(
                    "--width",
                    "\${resolution_width}",
                    "--height",
                    "\${resolution_height}",
                ),
                rules = listOf(
                    Rule(RuleAction.ALLOW, features = mapOf("has_custom_resolution" to true)),
                ),
            ),
            ArgumentTemplate(
                values = listOf("--demo"),
                rules = listOf(Rule(RuleAction.ALLOW, features = mapOf("is_demo_user" to true))),
            ),
            ArgumentTemplate(listOf("--quickPlayPath", "\${quickPlayPath}")),
        ),
    )

    @Test
    fun `fills every placeholder the version document names`() {
        val arguments = plan().arguments

        assertContains(arguments, "-Djava.library.path=C:/game/natives/fabric-loader-0.19.5-26.2")
        assertContains(arguments, "-Dminecraft.launcher.brand=untamed-launcher")
        assertContains(
            arguments,
            "-Dlog4j.configurationFile=C:/game/assets/log_configs/client-1.12.xml",
        )
        assertEquals("Steve", arguments[arguments.indexOf("--username") + 1])
        assertEquals(session.uuid, arguments[arguments.indexOf("--uuid") + 1])
        assertEquals("fabric-loader-0.19.5-26.2", arguments[arguments.indexOf("--version") + 1])
        assertEquals("C:/game", arguments[arguments.indexOf("--gameDir") + 1])
        assertEquals("26", arguments[arguments.indexOf("--assetIndex") + 1])
    }

    @Test
    fun `puts the main class between the jvm arguments and the game arguments`() {
        val plan = plan()
        val mainClass = plan.arguments.indexOf("net.fabricmc.loader.impl.launch.knot.KnotClient")

        assertEquals(java.executable, plan.command.first())
        assertTrue(plan.arguments.indexOf("-cp") < mainClass)
        assertTrue(plan.arguments.indexOf("--username") > mainClass)
    }

    /**
     * The client jar has to be last, and only the natives for this machine may
     * be on the path. A Linux native on a Windows classpath is a library that
     * loads and then fails inside the game.
     */
    @Test
    fun `builds the classpath from this platform's libraries with the client jar last`() {
        val arguments = plan().arguments
        val classpath = arguments[arguments.indexOf("-cp") + 1].split(";")

        assertEquals(
            listOf(
                "C:/game/libraries/com/mojang/logging/1.5.10/logging-1.5.10.jar",
                "C:/game/libraries/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar",
                "C:/game/versions/26.2/26.2.jar",
            ),
            classpath,
        )
    }

    @Test
    fun `lists only the native archives this platform needs`() {
        assertEquals(
            listOf("C:/game/libraries/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar"),
            plan().nativeArchives,
        )
    }

    @Test
    fun `leaves out arguments whose rules do not match this machine`() {
        assertFalse(plan().arguments.contains("-XstartOnFirstThread"))
    }

    /**
     * A feature nobody declared is false, which is the whole reason demo mode
     * cannot arrive by accident from a document that offers it.
     */
    @Test
    fun `treats an undeclared feature as off`() {
        assertFalse(plan().arguments.contains("--demo"))
    }

    @Test
    fun `passes the window size only when the game is not fullscreen`() {
        val windowed = plan().arguments
        assertEquals("1600", windowed[windowed.indexOf("--width") + 1])
        assertEquals("900", windowed[windowed.indexOf("--height") + 1])

        val fullscreen = plan(settings = settings.copy(fullscreen = true)).arguments
        assertFalse(fullscreen.contains("--width"))
        assertContains(fullscreen, "--fullscreen")
    }

    /**
     * The pair is dropped together. Passing `--quickPlayPath` with nothing
     * behind it would have the client read the next argument as its value.
     */
    @Test
    fun `drops a whole argument group when it names a placeholder with no value`() {
        val preparation = prepare()
        val ready = assertIs<LaunchPreparation.Ready>(preparation)

        assertFalse(ready.plan.arguments.contains("--quickPlayPath"))
        assertTrue(ready.warnings.single().contains("quickPlayPath"))
    }

    @Test
    fun `asks for the memory in the settings and puts the user's arguments last`() {
        val arguments = plan(settings = settings.copy(extraJvmArguments = "-XX:+UseZGC")).arguments
        val mainClass = arguments.indexOf("net.fabricmc.loader.impl.launch.knot.KnotClient")

        assertContains(arguments, "-Xmx6144M")
        assertTrue(arguments.indexOf("-XX:+UseZGC") > arguments.indexOf("-Xmx6144M"))
        assertTrue(arguments.indexOf("-XX:+UseZGC") < mainClass)
    }

    /**
     * The token is in the command, because the game needs it, and out of every
     * form of the command anything else is allowed to see.
     */
    @Test
    fun `hides the credentials in the description and nowhere else`() {
        val plan = plan()

        assertContains(plan.arguments, "an-access-token")
        assertFalse(plan.describe().contains("an-access-token"))
        assertFalse(plan.describe().contains("2535000000000000"))
        assertTrue(plan.describe().contains(LaunchPlan.HIDDEN))
        // The player name is not a secret and stays readable, which is what
        // makes a pasted command worth reading at all.
        assertTrue(plan.describe().contains("Steve"))
    }

    @Test
    fun `refuses a version document that cannot start anything`() {
        val preparation = prepare(
            profile = profile.copy(mainClass = null, assetIndex = null, assetsId = null),
        )
        val refused = assertIs<LaunchPreparation.Refused>(preparation)

        assertEquals(2, refused.problems.size)
        assertTrue(refused.problems.any { it.contains("main class") })
        assertTrue(refused.problems.any { it.contains("asset index") })
    }

    @Test
    fun `refuses a library that does not say where it lives`() {
        val preparation = prepare(
            profile = profile.copy(libraries = listOf(Library(name = "not-a-coordinate"))),
        )
        val refused = assertIs<LaunchPreparation.Refused>(preparation)

        assertTrue(refused.problems.single().contains("not-a-coordinate"))
    }

    private fun library(name: String, path: String, os: String? = null) = Library(
        name = name,
        explicitPath = path,
        rules = os?.let { listOf(Rule(RuleAction.ALLOW, OsConstraint(name = it))) }.orEmpty(),
    )

    private fun prepare(
        profile: VersionProfile = this.profile,
        settings: LauncherSettings = this.settings,
        platform: HostPlatform = windows,
    ): LaunchPreparation = GameLaunchPlanner.plan(
        profile = profile,
        versionId = "fabric-loader-0.19.5-26.2",
        session = session,
        java = java,
        settings = settings,
        layout = layout,
        platform = platform,
        launcherName = "untamed-launcher",
        launcherVersion = "1.0.0",
    )

    private fun plan(
        profile: VersionProfile = this.profile,
        settings: LauncherSettings = this.settings,
        platform: HostPlatform = windows,
    ): LaunchPlan = assertIs<LaunchPreparation.Ready>(prepare(profile, settings, platform)).plan
}
