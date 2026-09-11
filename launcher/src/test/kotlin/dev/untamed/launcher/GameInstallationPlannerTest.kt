package dev.untamed.launcher

import dev.untamed.launcher.application.installation.GameInstallationPlanner
import dev.untamed.launcher.domain.download.ChecksumAlgorithm
import dev.untamed.launcher.domain.minecraft.AssetIndex
import dev.untamed.launcher.domain.minecraft.AssetIndexReference
import dev.untamed.launcher.domain.minecraft.AssetObject
import dev.untamed.launcher.domain.minecraft.HostPlatform
import dev.untamed.launcher.domain.minecraft.Library
import dev.untamed.launcher.domain.minecraft.LoggingConfiguration
import dev.untamed.launcher.domain.minecraft.OsConstraint
import dev.untamed.launcher.domain.minecraft.RemoteFile
import dev.untamed.launcher.domain.minecraft.Rule
import dev.untamed.launcher.domain.minecraft.RuleAction
import dev.untamed.launcher.domain.minecraft.VersionProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameInstallationPlannerTest {

    private val windows = HostPlatform("windows", "x86_64", "10.0")
    private val linux = HostPlatform("linux", "x86_64", "6.8.0")

    private val hashA = "a".repeat(40)
    private val hashB = "b".repeat(40)

    private fun mojangLibrary(
        name: String,
        path: String,
        rules: List<Rule> = emptyList(),
    ) = Library(
        name = name,
        download = RemoteFile("https://libraries.minecraft.net/$path", hashA, 1_000),
        explicitPath = path,
        rules = rules,
    )

    private fun minecraft(): VersionProfile = VersionProfile(
        id = "26.2",
        mainClass = "net.minecraft.client.main.Main",
        javaMajorVersion = 25,
        assetsId = "26",
        assetIndex = AssetIndexReference(
            id = "26",
            url = "https://piston-meta.example.test/26.json",
            sha1 = hashA,
            sizeBytes = 400,
        ),
        client = RemoteFile("https://piston-data.example.test/client.jar", hashB, 30_000_000),
        libraries = listOf(
            mojangLibrary(
                name = "com.mojang:logging:1.5.10",
                path = "com/mojang/logging/1.5.10/logging-1.5.10.jar",
            ),
            mojangLibrary(
                name = "org.lwjgl:lwjgl:3.3.3:natives-windows",
                path = "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar",
                rules = listOf(Rule(RuleAction.ALLOW, OsConstraint(name = "windows"))),
            ),
            mojangLibrary(
                name = "org.lwjgl:lwjgl:3.3.3:natives-linux",
                path = "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar",
                rules = listOf(Rule(RuleAction.ALLOW, OsConstraint(name = "linux"))),
            ),
        ),
        logging = LoggingConfiguration(
            argument = "-Dlog4j.configurationFile=\${path}",
            fileId = "client-1.12.xml",
            file = RemoteFile("https://piston-data.example.test/client-1.12.xml", hashA, 900),
        ),
    )

    /** Fabric's profile as its meta endpoint returns it: a delta, no digests. */
    private fun fabric(): VersionProfile = VersionProfile(
        id = "fabric-loader-0.19.5-26.2",
        inheritsFrom = "26.2",
        mainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient",
        libraries = listOf(
            Library(
                name = "net.fabricmc:fabric-loader:0.19.5",
                repositoryUrl = "https://maven.fabricmc.net/",
            ),
            Library(
                name = "com.mojang:logging:1.5.11",
                repositoryUrl = "https://maven.fabricmc.net/",
            ),
        ),
    )

    @Test
    fun `takes from the parent everything the child does not state`() {
        val resolved = fabric().inheriting(minecraft())

        assertEquals("fabric-loader-0.19.5-26.2", resolved.id)
        assertNull(resolved.inheritsFrom)
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", resolved.mainClass)
        assertEquals(25, resolved.javaMajorVersion)
        assertEquals("26", resolved.assetsId)
        assertNotNull(resolved.client)
        assertNotNull(resolved.logging)
    }

    @Test
    fun `a fabric library replaces the minecraft one it overrides`() {
        val resolved = fabric().inheriting(minecraft())
        val logging = resolved.libraries.filter { it.name.startsWith("com.mojang:logging") }

        assertEquals(listOf("com.mojang:logging:1.5.11"), logging.map { it.name })
    }

    @Test
    fun `selects natives by rule rather than by name`() {
        val plan = GameInstallationPlanner.libraries(minecraft(), windows)
        val paths = plan.verified.requests.map { it.destination }

        assertTrue(paths.any { it.endsWith("lwjgl-3.3.3-natives-windows.jar") })
        assertTrue(paths.none { it.endsWith("lwjgl-3.3.3-natives-linux.jar") })
        assertEquals(2, plan.count)
        assertTrue(plan.problems.isEmpty())

        val other = GameInstallationPlanner.libraries(minecraft(), linux)
        assertTrue(other.verified.requests.any { it.destination.endsWith("natives-linux.jar") })
    }

    @Test
    fun `a library with no digest waits for the one maven publishes beside it`() {
        val plan = GameInstallationPlanner.libraries(fabric(), windows)

        assertTrue(plan.verified.isEmpty)
        assertEquals(2, plan.pending.size)
        val loader = plan.pending.first {
            it.request.label.startsWith("net.fabricmc:fabric-loader")
        }
        assertEquals(
            "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar",
            loader.request.url,
        )
        assertEquals("${loader.request.url}.sha1", loader.checksumUrl)
        assertEquals(ChecksumAlgorithm.SHA1, loader.algorithm)
        assertEquals(
            "libraries/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar",
            loader.request.destination,
        )
    }

    @Test
    fun `reports a library that says nothing about where it comes from`() {
        val profile = VersionProfile(id = "26.2", libraries = listOf(Library(name = "broken")))
        val plan = GameInstallationPlanner.libraries(profile, windows)

        assertEquals(0, plan.count)
        assertEquals(1, plan.problems.size)
    }

    @Test
    fun `puts the client and the version document where minecraft expects them`() {
        val client = GameInstallationPlanner.client(minecraft(), "26.2")

        assertEquals("versions/26.2/26.2.jar", client?.destination)
        assertEquals(hashB, client?.checksum?.value)
    }

    @Test
    fun `downloads one copy of an object several assets share`() {
        val index = AssetIndex(
            id = "26",
            objects = listOf(
                AssetObject("minecraft/sounds/a.ogg", hashA, 120),
                AssetObject("minecraft/sounds/b.ogg", hashA, 120),
                AssetObject("minecraft/lang/en_gb.json", hashB, 300),
            ),
        )
        val planned = GameInstallationPlanner.assets(index, "https://resources.example.test/")

        assertEquals(2, planned.batch.requests.size)
        assertEquals(420L, planned.batch.totalBytes)
        val first = planned.batch.requests.first()
        assertEquals("assets/objects/aa/$hashA", first.destination)
        assertEquals("https://resources.example.test/aa/$hashA", first.url)
        assertEquals(hashA, first.checksum?.value)
    }

    @Test
    fun `skips an asset whose hash is not a hash`() {
        val index = AssetIndex("26", listOf(AssetObject("bad", "nonsense", 10)))
        val planned = GameInstallationPlanner.assets(index, "https://resources.example.test")

        assertTrue(planned.batch.isEmpty)
        assertEquals(1, planned.problems.size)
    }

    @Test
    fun `every planned file is one the launcher is willing to download`() {
        val profile = fabric().inheriting(minecraft())
        val problems = GameInstallationPlanner.libraries(profile, windows).let { libraries ->
            libraries.verified.problems() + libraries.pending.flatMap { it.request.problems() }
        } + GameInstallationPlanner.assetIndexDocument(profile.assetIndex!!).problems() +
            GameInstallationPlanner.loggingConfiguration(profile)!!.problems()

        assertEquals(emptyList(), problems)
    }
}
