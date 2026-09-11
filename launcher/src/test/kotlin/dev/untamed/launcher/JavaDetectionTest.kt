package dev.untamed.launcher

import dev.untamed.launcher.domain.java.JavaRuntime
import dev.untamed.launcher.domain.java.JavaRuntimes
import dev.untamed.launcher.domain.java.JavaSelection
import dev.untamed.launcher.domain.java.JavaSource
import dev.untamed.launcher.domain.java.JavaVersionOutput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class JavaDetectionTest {

    private fun runtime(
        major: Int,
        source: JavaSource = JavaSource.INSTALLED,
        executable: String = "/jdk-$major/bin/java",
    ) = JavaRuntime(executable, major, "test build $major", source)

    @Test
    fun `reads a modern version banner`() {
        val output = """
            openjdk version "25.0.1" 2025-10-21
            OpenJDK Runtime Environment Temurin-25.0.1+9 (build 25.0.1+9)
            OpenJDK 64-Bit Server VM Temurin-25.0.1+9 (build 25.0.1+9, mixed mode)
        """.trimIndent()

        val details = JavaVersionOutput.parse(output)

        assertEquals(25, details?.majorVersion)
        assertEquals(
            "OpenJDK Runtime Environment Temurin-25.0.1+9 (build 25.0.1+9)",
            details?.description,
        )
    }

    @Test
    fun `reads the 1 dot 8 spelling as java 8`() {
        val output = """
            java version "1.8.0_402"
            Java(TM) SE Runtime Environment (build 1.8.0_402-b06)
        """.trimIndent()

        assertEquals(8, JavaVersionOutput.parse(output)?.majorVersion)
    }

    @Test
    fun `refuses output it cannot identify`() {
        assertNull(JavaVersionOutput.parse("command not found"))
        assertNull(JavaVersionOutput.parse(""))
        assertNull(JavaVersionOutput.parse("openjdk version \"beta\""))
    }

    @Test
    fun `a configured runtime that works is always the one used`() {
        val selection = JavaRuntimes.select(
            listOf(runtime(25, JavaSource.CONFIGURED), runtime(25, JavaSource.JAVA_HOME)),
            requiredMajor = 25,
        )

        assertIs<JavaSelection.Selected>(selection)
        assertEquals(JavaSource.CONFIGURED, selection.runtime.source)
    }

    @Test
    fun `says so when the configured runtime is too old and offers one that is not`() {
        val selection = JavaRuntimes.select(
            listOf(runtime(21, JavaSource.CONFIGURED), runtime(25, JavaSource.JAVA_HOME)),
            requiredMajor = 25,
        )

        assertIs<JavaSelection.Unsuitable>(selection)
        assertEquals(21, selection.runtime.majorVersion)
        assertEquals(25, selection.alternative?.majorVersion)
    }

    @Test
    fun `prefers the exact version the game asks for over a newer one`() {
        val selection = JavaRuntimes.select(
            listOf(runtime(26), runtime(25), runtime(21)),
            requiredMajor = 25,
        )

        assertIs<JavaSelection.Selected>(selection)
        assertEquals(25, selection.runtime.majorVersion)
    }

    @Test
    fun `falls back to the lowest runtime that is new enough`() {
        val selection = JavaRuntimes.select(listOf(runtime(28), runtime(26)), requiredMajor = 25)

        assertIs<JavaSelection.Selected>(selection)
        assertEquals(26, selection.runtime.majorVersion)
    }

    @Test
    fun `reports nothing usable rather than picking a runtime that is too old`() {
        val selection = JavaRuntimes.select(listOf(runtime(21), runtime(17)), requiredMajor = 25)

        assertIs<JavaSelection.None>(selection)
        assertEquals(25, selection.requiredMajor)
        assertEquals(2, selection.searched)
    }

    @Test
    fun `describes what it found in one line`() {
        val selected = JavaRuntimes.select(listOf(runtime(25)), requiredMajor = 25)

        assertEquals("Java 25 at /jdk-25/bin/java", selected.describe())
    }
}
