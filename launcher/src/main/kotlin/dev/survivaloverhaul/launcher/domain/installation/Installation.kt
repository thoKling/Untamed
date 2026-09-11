package dev.survivaloverhaul.launcher.domain.installation

/** The parts of an installation the launcher tracks separately. */
enum class Component {
    MINECRAFT,
    FABRIC_LOADER,
    FABRIC_API,
    SURVIVAL_OVERHAUL,
    RESOURCES,
}

/** How one component compares to what the manifest asks for. */
enum class ComponentState {
    /** Nothing has been inspected yet. */
    UNKNOWN,

    /** Not present on disk. */
    MISSING,

    /** Present, but not the version the manifest pins. */
    OUTDATED,

    /** Present at the pinned version. */
    READY,
}

data class ComponentStatus(
    val component: Component,
    val state: ComponentState,
    val requiredVersion: String,
    val installedVersion: String?,
)

/**
 * What the launcher believes is on disk right now.
 *
 * Derived state, rebuilt by inspecting the installation rather than remembered
 * across runs, so it cannot drift away from the files it describes.
 */
data class InstallationSnapshot(
    val statuses: List<ComponentStatus>,
) {
    val isComplete: Boolean
        get() = statuses.isNotEmpty() && statuses.all { it.state == ComponentState.READY }

    val isInspected: Boolean
        get() = statuses.isNotEmpty() && statuses.none { it.state == ComponentState.UNKNOWN }

    fun statusOf(component: Component): ComponentStatus? =
        statuses.firstOrNull { it.component == component }

    companion object {
        val NOT_INSPECTED = InstallationSnapshot(emptyList())
    }
}

/** What the launcher found on disk, as raw versions, before any judgement. */
data class InstalledComponents(
    val minecraftVersion: String? = null,
    val fabricLoaderVersion: String? = null,
    val fabricApiVersion: String? = null,
    val survivalOverhaulVersion: String? = null,
    val resourceVersions: Map<String, String> = emptyMap(),
) {
    companion object {
        val NOTHING = InstalledComponents()
    }
}

enum class StepAction { INSTALL, UPDATE }

data class InstallationStep(
    val component: Component,
    val action: StepAction,
    val targetVersion: String,
)

/**
 * The work that would bring an installation up to the manifest. An empty plan
 * is the normal case on a second launch, which is the point: nothing is
 * downloaded again when nothing changed.
 */
data class InstallationPlan(
    val steps: List<InstallationStep>,
) {
    val isUpToDate: Boolean get() = steps.isEmpty()
}
