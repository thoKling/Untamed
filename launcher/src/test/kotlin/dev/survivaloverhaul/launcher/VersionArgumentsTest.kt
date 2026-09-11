package dev.survivaloverhaul.launcher

import dev.survivaloverhaul.launcher.domain.minecraft.HostPlatform
import dev.survivaloverhaul.launcher.domain.minecraft.Rules
import dev.survivaloverhaul.launcher.infrastructure.minecraft.VersionDocuments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The `arguments` block is the one part of a version document with two shapes in
 * the same list: bare strings and rule-gated objects whose value may be one
 * string or several. Reading it wrongly is a game that starts with the wrong
 * window size, the wrong account, or not at all.
 */
class VersionArgumentsTest {

    private val windows = HostPlatform("windows", "x86_64", "10.0")
    private val mac = HostPlatform("osx", "aarch64", "14.0")

    @Test
    fun `reads bare strings and rule-gated objects from the same list`() {
        val profile = VersionDocuments.parseProfile(DOCUMENT)

        assertEquals(
            listOf("-Djava.library.path=\${natives_directory}"),
            profile.jvmArguments.first().values,
        )
        val conditional = profile.jvmArguments.first { it.rules.isNotEmpty() }
        assertEquals(listOf("-XstartOnFirstThread"), conditional.values)
        assertEquals("osx", conditional.rules.single().os?.name)
    }

    @Test
    fun `reads an argument whose value is several strings`() {
        val profile = VersionDocuments.parseProfile(DOCUMENT)
        val resolution = profile.gameArguments.first { it.values.contains("--width") }

        assertEquals(
            listOf("--width", "\${resolution_width}", "--height", "\${resolution_height}"),
            resolution.values,
        )
        assertEquals(
            mapOf("has_custom_resolution" to true),
            resolution.rules.single().features,
        )
    }

    /**
     * Dropped rather than fatal. One unreadable entry out of dozens should cost
     * that argument, not the launch, and the launcher reports what it dropped.
     */
    @Test
    fun `ignores an entry that is neither a string nor an argument object`() {
        val profile = VersionDocuments.parseProfile(DOCUMENT)

        assertTrue(profile.gameArguments.none { it.values.contains("7") })
        assertTrue(profile.gameArguments.none { it.values.isEmpty() })
    }

    @Test
    fun `evaluates argument rules the same way library rules are evaluated`() {
        val profile = VersionDocuments.parseProfile(DOCUMENT)
        val conditional = profile.jvmArguments.first { it.rules.isNotEmpty() }

        assertTrue(Rules.allows(conditional.rules, mac))
        assertFalse(Rules.allows(conditional.rules, windows))
    }

    /**
     * Arguments are added to, not replaced. Fabric's profile contributes what
     * its loader needs and expects Minecraft's own to still be there, so the
     * parent's come first.
     */
    @Test
    fun `appends a child profile's arguments to the ones it inherits`() {
        val parent = VersionDocuments.parseProfile(DOCUMENT)
        val child = VersionDocuments.parseProfile(CHILD)

        val merged = child.inheriting(parent)

        assertEquals(
            listOf("-Djava.library.path=\${natives_directory}", "-XstartOnFirstThread"),
            merged.jvmArguments.dropLast(1).map { it.values.single() },
        )
        assertEquals(
            listOf("-DFabricMcEmu=net.minecraft.client.main.Main"),
            merged.jvmArguments.last().values,
        )
        assertEquals("release", merged.type)
    }

    private companion object {

        val DOCUMENT = """
            {
              "id": "26.2",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "arguments": {
                "jvm": [
                  "-Djava.library.path=${'$'}{natives_directory}",
                  {
                    "rules": [{ "action": "allow", "os": { "name": "osx" } }],
                    "value": "-XstartOnFirstThread"
                  }
                ],
                "game": [
                  "--username",
                  "${'$'}{auth_player_name}",
                  7,
                  {
                    "rules": [
                      { "action": "allow", "features": { "has_custom_resolution": true } }
                    ],
                    "value": [
                      "--width",
                      "${'$'}{resolution_width}",
                      "--height",
                      "${'$'}{resolution_height}"
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val CHILD = """
            {
              "id": "fabric-loader-0.19.5-26.2",
              "inheritsFrom": "26.2",
              "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
              "arguments": {
                "jvm": ["-DFabricMcEmu=net.minecraft.client.main.Main"],
                "game": []
              }
            }
        """.trimIndent()
    }
}
