package dev.untamed.launcher

import dev.untamed.launcher.application.installation.FallbackManifestSource
import dev.untamed.launcher.application.installation.ManifestResult
import dev.untamed.launcher.application.installation.ManifestSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FallbackManifestSourceTest {

    @Test
    fun `uses the published manifest when it is usable`() {
        var bundledWasRead = false
        val source = FallbackManifestSource(
            primary = source(ManifestResult.Loaded(Fixtures.manifest(), "remote")),
            fallback = {
                bundledWasRead = true
                ManifestResult.Loaded(Fixtures.manifest(), "bundled")
            },
        )

        val result = source.load()

        assertEquals("remote", result.origin)
        assertTrue(!bundledWasRead, "the bundled copy should not be read when the remote works")
    }

    @Test
    fun `falls back when the host cannot be reached`() {
        val source = FallbackManifestSource(
            primary = source(ManifestResult.Unavailable("could not reach the host", "remote")),
            fallback = source(ManifestResult.Loaded(Fixtures.manifest(), "bundled")),
        )

        val result = source.load()

        assertIs<ManifestResult.Loaded>(result)
        assertEquals("bundled (offline copy)", result.origin)
    }

    /**
     * A rejected remote manifest is corrupt or hostile, and in both cases the
     * pinned copy inside the launcher is the better of the two.
     */
    @Test
    fun `falls back when the published manifest is rejected`() {
        val source = FallbackManifestSource(
            primary = source(ManifestResult.Rejected(listOf("no usable sha256"), "remote")),
            fallback = source(ManifestResult.Loaded(Fixtures.manifest(), "bundled")),
        )

        val result = source.load()

        assertIs<ManifestResult.Loaded>(result)
        assertEquals("bundled (offline copy)", result.origin)
    }

    @Test
    fun `says why it fell back`() {
        val reasons = mutableListOf<String>()
        val source = FallbackManifestSource(
            primary = source(ManifestResult.Unavailable("could not reach the host", "remote")),
            fallback = source(ManifestResult.Loaded(Fixtures.manifest(), "bundled")),
            onFallback = { reasons += it },
        )

        source.load()

        assertEquals(1, reasons.size)
        assertTrue("could not reach the host" in reasons.single(), reasons.single())
    }

    /**
     * Both broken is a real state, and the launcher has to report it rather
     * than dress the second failure up as the first.
     */
    @Test
    fun `reports the bundled failure when neither can be read`() {
        val source = FallbackManifestSource(
            primary = source(ManifestResult.Unavailable("could not reach the host", "remote")),
            fallback = source(ManifestResult.Unavailable("not in the jar", "bundled")),
        )

        val result = source.load()

        assertIs<ManifestResult.Unavailable>(result)
        assertEquals("not in the jar", result.reason)
        assertEquals("bundled", result.origin)
    }

    private fun source(result: ManifestResult): ManifestSource = ManifestSource { result }
}
