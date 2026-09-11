package dev.untamed.launcher.domain.minecraft

/**
 * The machine a version manifest's rules are evaluated against.
 *
 * A value rather than a lookup of the running system, so rule evaluation is a
 * pure function of its inputs and every combination of operating system and
 * architecture can be tested without running on one.
 *
 * [osName] uses Mojang's vocabulary: `windows`, `linux`, `osx`.
 */
data class HostPlatform(
    val osName: String,
    val architecture: String,
    val osVersion: String,
) {
    /**
     * Every spelling of this architecture a rule might use.
     *
     * Mojang's own rules say `x86`, `x86_64` and `aarch64`, but older manifests
     * and Fabric's profile have used `x64` and `arm64` for the same machines.
     * Matching against a set rather than one string means a library is not
     * silently skipped because a publisher chose the other spelling.
     */
    val architectureNames: Set<String> = when (architecture) {
        "x86_64", "x64", "amd64" -> setOf("x86_64", "x64", "amd64")
        "aarch64", "arm64" -> setOf("aarch64", "arm64")
        "x86", "i386", "x32" -> setOf("x86", "i386", "x32")
        else -> setOf(architecture)
    }

    override fun toString(): String = "$osName/$architecture"
}
