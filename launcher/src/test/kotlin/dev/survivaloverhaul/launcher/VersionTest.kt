package dev.survivaloverhaul.launcher

import dev.survivaloverhaul.launcher.domain.versions.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionTest {

    @Test
    fun `orders release numbers`() {
        assertTrue(Version.parse("26.2") > Version.parse("26.1"))
        assertTrue(Version.parse("0.19.5") > Version.parse("0.19.4"))
        assertTrue(Version.parse("1.0") > Version.parse("0.9.9"))
    }

    @Test
    fun `treats a missing segment as zero`() {
        assertEquals(0, Version.parse("26.2").compareTo(Version.parse("26.2.0")))
    }

    @Test
    fun `ignores build metadata, as fabric api uses it for the game version`() {
        assertEquals(
            0,
            Version.parse("0.160.0+26.2").compareTo(Version.parse("0.160.0+26.3")),
        )
        assertTrue(Version.parse("0.161.0+26.2") > Version.parse("0.160.0+26.2"))
    }

    @Test
    fun `a release outranks its own pre-release`() {
        assertTrue(Version.parse("1.0.0") > Version.parse("1.0.0-beta.1"))
        assertTrue(Version.parse("1.0.0-beta.2") > Version.parse("1.0.0-beta.1"))
    }

    @Test
    fun `unparseable text still compares without throwing`() {
        val nonsense = Version.parse("not-a-version")
        assertFalse(nonsense.isWellFormed)
        assertTrue(Version.parse("0.1.0") > nonsense)
        assertEquals("not-a-version", nonsense.raw)
    }

    @Test
    fun `minimum launcher version is a lower bound, not an equality`() {
        assertTrue(Version.atLeast("0.2.0", "0.1.0"))
        assertTrue(Version.atLeast("0.1.0", "0.1.0"))
        assertFalse(Version.atLeast("0.0.9", "0.1.0"))
    }
}
