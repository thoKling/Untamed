package dev.survivaloverhaul.launcher

import dev.survivaloverhaul.launcher.domain.settings.LauncherSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LauncherSettingsTest {

    private val fallback = "/tmp/survival-overhaul"

    @Test
    fun `memory is clamped to what a jvm can be given`() {
        val tiny = LauncherSettings(fallback, memoryMegabytes = 16).sanitised(fallback)
        val huge = LauncherSettings(fallback, memoryMegabytes = 999_999).sanitised(fallback)

        assertEquals(LauncherSettings.MINIMUM_MEMORY_MB, tiny.memoryMegabytes)
        assertEquals(LauncherSettings.MAXIMUM_MEMORY_MB, huge.memoryMegabytes)
    }

    @Test
    fun `an empty installation directory falls back rather than pointing at nothing`() {
        val settings = LauncherSettings("   ").sanitised(fallback)
        assertEquals(fallback, settings.installationDirectory)
    }

    @Test
    fun `a blank java executable means automatic, not an empty path`() {
        val settings = LauncherSettings(fallback, javaExecutable = "  ").sanitised(fallback)
        assertNull(settings.javaExecutable)
    }

    @Test
    fun `a window smaller than the minimum is grown, not accepted`() {
        val settings =
            LauncherSettings(fallback, windowWidth = 1, windowHeight = 1).sanitised(fallback)

        assertEquals(LauncherSettings.MINIMUM_WIDTH, settings.windowWidth)
        assertEquals(LauncherSettings.MINIMUM_HEIGHT, settings.windowHeight)
    }

    @Test
    fun `jvm arguments split on whitespace and drop the gaps`() {
        val settings = LauncherSettings(fallback, extraJvmArguments = "  -Xss2M   -XX:+UseG1GC ")
            .sanitised(fallback)

        assertEquals(listOf("-Xss2M", "-XX:+UseG1GC"), settings.extraJvmArgumentList())
    }
}
