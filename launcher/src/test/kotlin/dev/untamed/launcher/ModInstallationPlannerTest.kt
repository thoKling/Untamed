package dev.untamed.launcher

import dev.untamed.launcher.application.installation.ModInstallationPlanner
import dev.untamed.launcher.domain.manifest.ResourceArtifact
import dev.untamed.launcher.domain.manifest.ResourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModInstallationPlannerTest {

    @Test
    fun `puts the mod and its dependencies in the mods directory`() {
        val plan = ModInstallationPlanner.plan(Fixtures.manifest(), emptySet())

        assertEquals(
            listOf("mods/fabric-api-0.160.0+26.2.jar", "mods/untamed-0.1.0.jar"),
            plan.mods.requests.map { it.destination },
        )
        assertTrue(plan.problems.isEmpty(), "a valid manifest should plan cleanly")
    }

    @Test
    fun `installs dependencies before the mod that needs them`() {
        val plan = ModInstallationPlanner.plan(Fixtures.manifest(), emptySet())

        val mod = plan.mods.requests
            .indexOfFirst { it.destination.endsWith("untamed-0.1.0.jar") }
        val dependency = plan.mods.requests.indexOfFirst { it.destination.contains("fabric-api") }
        assertTrue(dependency < mod, "Fabric API should be planned before the mod")
    }

    @Test
    fun `sends each kind of resource to the directory Minecraft loads it from`() {
        val manifest = Fixtures.manifest(
            resources = listOf(
                resource(ResourceKind.RESOURCE_PACK, "pack", "pack.zip"),
                resource(ResourceKind.SHADER_PACK, "shaders", "shaders.zip"),
                resource(ResourceKind.CONFIGURATION, "config", "survival.toml"),
            ),
        )

        val plan = ModInstallationPlanner.plan(manifest, previouslyInstalled = emptySet())

        assertEquals(
            listOf("resourcepacks/pack.zip", "shaderpacks/shaders.zip", "config/survival.toml"),
            plan.resources.requests.map { it.destination },
        )
    }

    @Test
    fun `every planned download carries the checksum the manifest published`() {
        val plan = ModInstallationPlanner.plan(
            Fixtures.manifestWithResourcePack(),
            previouslyInstalled = emptySet(),
        )

        assertTrue(plan.requests.isNotEmpty())
        plan.requests.forEach { request ->
            assertEquals(Fixtures.SHA, request.checksum?.value, "${request.label} has no checksum")
        }
    }

    /**
     * The reason cleanup exists. Fabric loads every jar in `mods/`, so leaving
     * the previous version there is the same mod twice, not clutter.
     */
    @Test
    fun `marks the previous version's jar for removal`() {
        val plan = ModInstallationPlanner.plan(
            Fixtures.manifest(),
            previouslyInstalled = setOf(
                "mods/untamed-0.0.9.jar",
                "mods/fabric-api-0.160.0+26.2.jar",
            ),
        )

        assertEquals(listOf("mods/untamed-0.0.9.jar"), plan.obsolete)
    }

    @Test
    fun `keeps a file the manifest still asks for`() {
        val plan = ModInstallationPlanner.plan(
            Fixtures.manifest(),
            previouslyInstalled = setOf("mods/untamed-0.1.0.jar"),
        )

        assertTrue(plan.obsolete.isEmpty(), "the current version must not be deleted")
    }

    /**
     * The state file is editable by hand, so an entry pointing outside the
     * directories the launcher manages must not turn into a deletion.
     */
    @Test
    fun `refuses to remove anything outside the directories it manages`() {
        val plan = ModInstallationPlanner.plan(
            Fixtures.manifest(),
            previouslyInstalled = setOf(
                "versions/26.2/26.2.jar",
                "libraries/net/fabricmc/something.jar",
                "../elsewhere/important.jar",
                "saves/world/level.dat",
            ),
        )

        assertTrue(plan.obsolete.isEmpty(), "cleanup escaped the managed directories")
    }

    @Test
    fun `reports an artifact it cannot install instead of installing it`() {
        val manifest = Fixtures.manifest(
            mod = Fixtures.artifact(url = "http://downloads.example.test/mod.jar"),
        )

        val plan = ModInstallationPlanner.plan(manifest, previouslyInstalled = emptySet())

        assertTrue(plan.problems.isNotEmpty(), "a plain-http artifact should be a problem")
        assertTrue(
            plan.mods.requests.none { it.destination.endsWith("untamed-0.1.0.jar") },
            "a rejected artifact must not also be planned",
        )
    }

    @Test
    fun `a manifest with nothing to change plans no work at all`() {
        val plan = ModInstallationPlanner.plan(
            Fixtures.manifest(dependencies = emptyList(), resources = emptyList()),
            previouslyInstalled = setOf("mods/untamed-0.1.0.jar"),
        )

        assertEquals(1, plan.count, "the mod itself is always planned and verified")
        assertTrue(plan.obsolete.isEmpty())
    }

    private fun resource(kind: ResourceKind, id: String, fileName: String): ResourceArtifact =
        ResourceArtifact(
            kind = kind,
            artifact = Fixtures.artifact(
                id = id,
                version = "1.0.0",
                url = "https://downloads.example.test/$fileName",
                fileName = fileName,
            ),
        )
}
