package dev.untamed.launcher

import dev.untamed.launcher.infrastructure.launch.NativeLibraries
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The entry-name check is what decides which of an archive's entries is allowed
 * to become a file. It is covered on its own because the interesting cases are
 * the ones a real natives jar does not contain.
 */
class NativeLibrariesTest {

    @Test
    fun `accepts the binaries a natives jar carries`() {
        assertTrue(NativeLibraries.isExtractable("windows/x64/org/lwjgl/glfw/glfw.dll"))
        assertTrue(NativeLibraries.isExtractable("libglfw.so"))
    }

    @Test
    fun `skips directories and signing metadata`() {
        assertFalse(NativeLibraries.isExtractable("windows/x64/"))
        assertFalse(NativeLibraries.isExtractable("META-INF/MANIFEST.MF"))
        assertFalse(NativeLibraries.isExtractable("META-INF/services/one.Service"))
        assertFalse(NativeLibraries.isExtractable(""))
    }

    /**
     * Flattening already leaves a traversing name nowhere to go, but the name is
     * refused anyway. An archive is a file someone else built, and the check
     * that says so should not depend on a decision made somewhere else.
     */
    @Test
    fun `refuses a name that is nothing but traversal`() {
        assertFalse(NativeLibraries.isExtractable("../../evil"))
        assertFalse(NativeLibraries.isExtractable("windows/.."))
        assertFalse(NativeLibraries.isExtractable("."))
    }
}
