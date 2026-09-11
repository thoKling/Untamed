package dev.untamed.launcher

import dev.untamed.launcher.application.installation.InstallationPlanner
import dev.untamed.launcher.domain.installation.Component
import dev.untamed.launcher.domain.installation.ComponentState
import dev.untamed.launcher.domain.installation.InstalledComponents
import dev.untamed.launcher.domain.installation.StepAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstallationPlannerTest {

    private val manifest = Fixtures.manifest()

    private val installed = InstalledComponents(
        minecraftVersion = "26.2",
        fabricLoaderVersion = "0.19.5",
        fabricApiVersion = "0.160.0+26.2",
        untamedVersion = "0.1.0",
    )

    @Test
    fun `an empty directory needs everything installed`() {
        val snapshot = InstallationPlanner.inspect(manifest, InstalledComponents.NOTHING)

        assertTrue(snapshot.statuses.all { it.state == ComponentState.MISSING })
        val plan = InstallationPlanner.plan(snapshot)
        assertEquals(4, plan.steps.size)
        assertTrue(plan.steps.all { it.action == StepAction.INSTALL })
    }

    @Test
    fun `a matching installation plans no work at all`() {
        val snapshot = InstallationPlanner.inspect(manifest, installed)

        assertTrue(snapshot.isComplete)
        assertTrue(InstallationPlanner.plan(snapshot).isUpToDate)
    }

    @Test
    fun `only the component that moved is updated`() {
        val snapshot = InstallationPlanner.inspect(
            manifest,
            installed.copy(untamedVersion = "0.0.9"),
        )

        val plan = InstallationPlanner.plan(snapshot)
        assertEquals(1, plan.steps.size)
        assertEquals(Component.UNTAMED, plan.steps.single().component)
        assertEquals(StepAction.UPDATE, plan.steps.single().action)
        assertEquals("0.1.0", plan.steps.single().targetVersion)
    }

    @Test
    fun `a version newer than the manifest is still a change to make`() {
        val snapshot = InstallationPlanner.inspect(
            manifest,
            installed.copy(untamedVersion = "0.9.9"),
        )

        assertEquals(
            ComponentState.OUTDATED,
            snapshot.statusOf(Component.UNTAMED)?.state,
        )
    }

    @Test
    fun `resources are only reported when the manifest has any`() {
        val without = InstallationPlanner.inspect(manifest, installed)
        assertEquals(null, without.statusOf(Component.RESOURCES))

        val withPack = InstallationPlanner.inspect(Fixtures.manifestWithResourcePack(), installed)
        assertEquals(
            ComponentState.MISSING,
            withPack.statusOf(Component.RESOURCES)?.state,
        )
    }

    @Test
    fun `an uninspected snapshot plans nothing rather than a full reinstall`() {
        val plan = InstallationPlanner.plan(
            dev.untamed.launcher.domain.installation.InstallationSnapshot.NOT_INSPECTED,
        )

        assertTrue(plan.isUpToDate)
    }
}
