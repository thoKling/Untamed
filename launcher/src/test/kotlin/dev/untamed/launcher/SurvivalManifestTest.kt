package dev.untamed.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A manifest is untrusted input, so these tests are about what the launcher
 * refuses, not about what it accepts.
 */
class SurvivalManifestTest {

    @Test
    fun `a well formed manifest has nothing to complain about`() {
        assertEquals(emptyList(), Fixtures.manifest().problems())
    }

    @Test
    fun `plain http is refused, so a download cannot be tampered with in transit`() {
        val problems = Fixtures.manifest(
            mod = Fixtures.artifact(url = "http://downloads.example.test/mod.jar"),
        ).problems()

        assertTrue(problems.any { it.contains("https") }, problems.toString())
    }

    @Test
    fun `an unusable checksum is refused rather than skipped`() {
        val tooShort = Fixtures.manifest(mod = Fixtures.artifact(sha256 = "abc123")).problems()
        val notHex = Fixtures.manifest(
            mod = Fixtures.artifact(sha256 = "z".repeat(64)),
        ).problems()

        assertTrue(tooShort.any { it.contains("sha256") }, tooShort.toString())
        assertTrue(notHex.any { it.contains("sha256") }, notHex.toString())
    }

    @Test
    fun `a file name that is really a path is refused`() {
        val traversal = Fixtures.manifest(
            mod = Fixtures.artifact(fileName = "../../autorun.jar"),
        ).problems()
        val absolute = Fixtures.manifest(
            mod = Fixtures.artifact(fileName = "C:\\Windows\\System32\\evil.jar"),
        ).problems()

        assertTrue(traversal.any { it.contains("file name") }, traversal.toString())
        assertTrue(absolute.any { it.contains("file name") }, absolute.toString())
    }

    @Test
    fun `the mod artifact has to agree with the declared game configuration`() {
        val problems = Fixtures.manifest(mod = Fixtures.artifact(version = "0.2.0")).problems()

        assertTrue(problems.any { it.contains("disagrees") }, problems.toString())
    }

    @Test
    fun `two artifacts cannot claim the same file`() {
        val problems = Fixtures.manifest(
            dependencies = listOf(Fixtures.artifact(id = "fabric-api")),
        ).problems()

        assertTrue(problems.any { it.contains("duplicate") }, problems.toString())
    }

    @Test
    fun `a future schema version is refused instead of guessed at`() {
        val problems = Fixtures.manifest(schemaVersion = 99).problems()

        assertTrue(problems.any { it.contains("schema version") }, problems.toString())
    }

    @Test
    fun `a manifest can require a newer launcher than this one`() {
        val manifest = Fixtures.manifest(minimumLauncherVersion = "0.4.0")

        assertFalse(manifest.supportsLauncher("0.1.0"))
        assertTrue(manifest.supportsLauncher("0.4.0"))
        assertTrue(manifest.supportsLauncher("1.2.0"))
    }

    @Test
    fun `dependencies are installed before the mod that needs them`() {
        val order = Fixtures.manifestWithResourcePack().artifacts.map { it.id }

        assertEquals(
            listOf("fabric-api", "untamed", "untamed-textures"),
            order,
        )
    }
}
