package dev.survivaloverhaul.launcher.domain.minecraft

enum class RuleAction { ALLOW, DISALLOW }

/**
 * The `os` block of a rule. Every field is optional and an absent field matches
 * anything, which is how Mojang writes them.
 */
data class OsConstraint(
    val name: String? = null,
    val version: String? = null,
    val architecture: String? = null,
)

data class Rule(
    val action: RuleAction,
    val os: OsConstraint? = null,
    val features: Map<String, Boolean> = emptyMap(),
) {
    fun matches(platform: HostPlatform, features: Map<String, Boolean>): Boolean {
        os?.let { constraint ->
            if (constraint.name != null && !constraint.name.equals(platform.osName, true)) {
                return false
            }
            if (constraint.architecture != null &&
                constraint.architecture.lowercase() !in platform.architectureNames
            ) {
                return false
            }
            // Mojang writes os.version as a regular expression, such as "^10\.".
            // A pattern that will not compile matches nothing rather than
            // throwing: a manifest is untrusted input and must not be able to
            // stop an installation with a bad regex.
            if (constraint.version != null) {
                val pattern = runCatching { Regex(constraint.version) }.getOrNull() ?: return false
                if (!pattern.containsMatchIn(platform.osVersion)) return false
            }
        }
        return this.features.all { (feature, required) ->
            features.getOrDefault(feature, false) == required
        }
    }
}

/**
 * Whether a rule-gated entry applies to this machine.
 *
 * The algorithm is Mojang's own: no rules means allowed, otherwise start from
 * disallowed and let every matching rule set the verdict, last match winning.
 *
 * This matters more in 26.2 than it used to. Native libraries are no longer
 * carried in a `classifiers` block keyed by operating system; they are ordinary
 * library entries separated only by their rules, and some variants differ by
 * nothing but a `-arm64` suffix on the name. Selecting by rule rather than by
 * matching on names is the only correct way to read the 26.2 library list.
 */
object Rules {

    fun allows(
        rules: List<Rule>,
        platform: HostPlatform,
        features: Map<String, Boolean> = emptyMap(),
    ): Boolean {
        if (rules.isEmpty()) return true
        var allowed = false
        for (rule in rules) {
            if (rule.matches(platform, features)) {
                allowed = rule.action == RuleAction.ALLOW
            }
        }
        return allowed
    }
}
