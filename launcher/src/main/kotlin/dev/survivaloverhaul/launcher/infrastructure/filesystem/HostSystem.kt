package dev.survivaloverhaul.launcher.infrastructure.filesystem

import dev.survivaloverhaul.launcher.domain.minecraft.HostPlatform

/**
 * The machine the launcher is running on.
 *
 * Minecraft's version manifest selects libraries and native binaries with rules
 * written in these terms, so the same vocabulary is used here from the start
 * even though milestone 1 only needs it for directories and diagnostics.
 */
enum class OperatingSystem(val manifestName: String) {
    WINDOWS("windows"),
    LINUX("linux"),
    MAC_OS("osx"),
    UNKNOWN("unknown"),
}

object HostSystem {

    val operatingSystem: OperatingSystem by lazy {
        val name = System.getProperty("os.name").orEmpty().lowercase()
        when {
            name.startsWith("windows") -> OperatingSystem.WINDOWS
            name.startsWith("linux") || name.contains("nix") -> OperatingSystem.LINUX
            name.startsWith("mac") || name.contains("darwin") -> OperatingSystem.MAC_OS
            else -> OperatingSystem.UNKNOWN
        }
    }

    /** Normalised to the names Mojang's rules use: `x64`, `x86`, `arm64`. */
    val architecture: String by lazy {
        when (val arch = System.getProperty("os.arch").orEmpty().lowercase()) {
            "amd64", "x86_64" -> "x64"
            "x86", "i386", "i486", "i586", "i686" -> "x86"
            "aarch64", "arm64" -> "arm64"
            else -> arch.ifBlank { "unknown" }
        }
    }

    /**
     * The same architecture in the spelling Mojang's own rules use. It differs
     * from [architecture], which is what the launcher shows people.
     */
    val manifestArchitecture: String
        get() = when (architecture) {
            "x64" -> "x86_64"
            "arm64" -> "aarch64"
            else -> architecture
        }

    /**
     * This machine as a version manifest's rules describe it. Built once here
     * so that everything which evaluates rules is looking at the same values,
     * and passed in wherever it is needed rather than read from the system, so
     * the rule logic itself stays pure and testable on any host.
     */
    val platform: HostPlatform by lazy {
        HostPlatform(
            osName = operatingSystem.manifestName,
            architecture = manifestArchitecture,
            osVersion = System.getProperty("os.version").orEmpty(),
        )
    }

    val description: String
        get() = "${System.getProperty("os.name")} ${System.getProperty("os.version")} ($architecture)"

    /** The megabytes of physical memory, when the JVM will admit to knowing. */
    fun physicalMemoryMegabytes(): Long? = runCatching {
        val bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        val method = bean.javaClass.getMethod("getTotalMemorySize")
        method.isAccessible = true
        (method.invoke(bean) as Long) / (1024 * 1024)
    }.getOrNull()
}
