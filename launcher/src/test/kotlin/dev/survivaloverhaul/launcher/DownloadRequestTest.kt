package dev.survivaloverhaul.launcher

import dev.survivaloverhaul.launcher.domain.download.Checksum
import dev.survivaloverhaul.launcher.domain.download.DownloadRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadRequestTest {

    private val sha1 = "0".repeat(40)

    private fun request(
        url: String = "https://libraries.example.test/thing.jar",
        destination: String = "libraries/com/example/thing/1.0/thing-1.0.jar",
        checksum: Checksum? = Checksum.sha1(sha1),
    ) = DownloadRequest("thing", url, destination, checksum, 10)

    @Test
    fun `accepts an ordinary library download`() {
        assertEquals(emptyList(), request().problems())
    }

    @Test
    fun `refuses anything not served over https`() {
        assertEquals(1, request(url = "http://libraries.example.test/thing.jar").problems().size)
        assertEquals(1, request(url = "file:///etc/passwd").problems().size)
    }

    @Test
    fun `refuses a destination that leaves the installation directory`() {
        assertTrue(request(destination = "../../evil.jar").problems().isNotEmpty())
        assertTrue(request(destination = "/etc/passwd").problems().isNotEmpty())
        assertTrue(request(destination = "libraries//thing.jar").problems().isNotEmpty())
        assertTrue(request(destination = "").problems().isNotEmpty())
    }

    @Test
    fun `refuses a windows path pretending to be a relative one`() {
        assertTrue(request(destination = "C:\\windows\\system32\\evil.dll").problems().isNotEmpty())
    }

    @Test
    fun `refuses a digest that is not one`() {
        assertTrue(request(checksum = Checksum.sha1("abc")).problems().isNotEmpty())
    }

    @Test
    fun `compares digests without caring about case`() {
        assertTrue(Checksum.sha1(sha1).matches(sha1.uppercase()))
        assertFalse(Checksum.sha1(sha1).matches("1".repeat(40)))
    }
}
