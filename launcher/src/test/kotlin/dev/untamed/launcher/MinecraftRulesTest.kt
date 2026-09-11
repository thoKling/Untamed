package dev.untamed.launcher

import dev.untamed.launcher.domain.minecraft.HostPlatform
import dev.untamed.launcher.domain.minecraft.MavenCoordinate
import dev.untamed.launcher.domain.minecraft.OsConstraint
import dev.untamed.launcher.domain.minecraft.Rule
import dev.untamed.launcher.domain.minecraft.RuleAction
import dev.untamed.launcher.domain.minecraft.Rules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MinecraftRulesTest {

    private val windows = HostPlatform("windows", "x86_64", "10.0")
    private val windowsArm = HostPlatform("windows", "aarch64", "10.0")
    private val linux = HostPlatform("linux", "x86_64", "6.8.0")

    @Test
    fun `an entry with no rules applies everywhere`() {
        assertTrue(Rules.allows(emptyList(), windows))
        assertTrue(Rules.allows(emptyList(), linux))
    }

    @Test
    fun `an unmatched allow rule leaves the entry disallowed`() {
        val rules = listOf(Rule(RuleAction.ALLOW, OsConstraint(name = "osx")))
        assertFalse(Rules.allows(rules, windows))
    }

    @Test
    fun `the last matching rule wins`() {
        val rules = listOf(
            Rule(RuleAction.ALLOW),
            Rule(RuleAction.DISALLOW, OsConstraint(name = "windows")),
        )
        assertFalse(Rules.allows(rules, windows))
        assertTrue(Rules.allows(rules, linux))
    }

    /**
     * The 26.2 case that matters: two natives for the same operating system,
     * separated only by architecture. Selecting the wrong one produces a game
     * that installs cleanly and then fails to start.
     */
    @Test
    fun `separates natives that differ only by architecture`() {
        val intel = listOf(
            Rule(RuleAction.ALLOW, OsConstraint(name = "windows", architecture = "x86_64")),
        )
        val arm = listOf(
            Rule(RuleAction.ALLOW, OsConstraint(name = "windows", architecture = "aarch64")),
        )
        assertTrue(Rules.allows(intel, windows))
        assertFalse(Rules.allows(arm, windows))
        assertTrue(Rules.allows(arm, windowsArm))
        assertFalse(Rules.allows(intel, windowsArm))
    }

    @Test
    fun `accepts either spelling of an architecture`() {
        val rules = listOf(Rule(RuleAction.ALLOW, OsConstraint(architecture = "x64")))
        assertTrue(Rules.allows(rules, windows))
    }

    @Test
    fun `reads os version as a regular expression`() {
        val constraint = OsConstraint(name = "windows", version = "^10\\.")
        val rules = listOf(Rule(RuleAction.ALLOW, constraint))
        assertTrue(Rules.allows(rules, windows))
        assertFalse(Rules.allows(rules, windows.copy(osVersion = "6.1")))
    }

    @Test
    fun `a version pattern that will not compile matches nothing`() {
        val rules = listOf(Rule(RuleAction.ALLOW, OsConstraint(version = "[unclosed")))
        assertFalse(Rules.allows(rules, windows))
    }

    @Test
    fun `matches feature rules against the features in play`() {
        val demo = listOf(
            Rule(RuleAction.ALLOW, features = mapOf("is_demo_user" to true)),
        )
        assertTrue(Rules.allows(demo, windows, mapOf("is_demo_user" to true)))
        assertFalse(Rules.allows(demo, windows, emptyMap()))
    }

    @Test
    fun `lays a coordinate out the way maven does`() {
        val coordinate = MavenCoordinate.parse("net.fabricmc:fabric-loader:0.19.5")
        assertEquals(
            "net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar",
            coordinate?.path,
        )
    }

    @Test
    fun `keeps the classifier and the extension`() {
        val coordinate = MavenCoordinate.parse("org.lwjgl:lwjgl:3.3.3:natives-windows@zip")
        assertEquals("lwjgl-3.3.3-natives-windows.zip", coordinate?.fileName)
    }

    @Test
    fun `refuses a coordinate that could climb out of the installation`() {
        assertNull(MavenCoordinate.parse("..:evil:1.0"))
        assertNull(MavenCoordinate.parse("a/b:evil:1.0"))
        assertNull(MavenCoordinate.parse("net.fabricmc:fabric-loader"))
        assertNull(MavenCoordinate.parse(""))
    }
}
