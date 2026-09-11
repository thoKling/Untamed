package dev.survivaloverhaul.launcher

import dev.survivaloverhaul.launcher.application.installation.ManifestResult
import dev.survivaloverhaul.launcher.infrastructure.manifest.ManifestDocuments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ManifestDocumentsTest {

    @Test
    fun `reads a well-formed manifest`() {
        val result = ManifestDocuments.read(manifestJson(), ORIGIN, "0.1.0")

        assertIs<ManifestResult.Loaded>(result)
        assertEquals("26.2", result.manifest.game.minecraftVersion)
        assertEquals("0.1.0", result.manifest.mod.version)
        assertEquals(ORIGIN, result.origin)
    }

    @Test
    fun `rejects a document that is not a manifest at all`() {
        val result = ManifestDocuments.read("this is not JSON", ORIGIN, "0.1.0")

        assertIs<ManifestResult.Rejected>(result)
        assertTrue(result.problems.single().startsWith("unreadable:"), result.problems.toString())
    }

    /**
     * A release can require a newer launcher than this one. Saying so is much
     * better than installing most of it, because the fix is a launcher update
     * and the user can only make it if they are told.
     */
    @Test
    fun `rejects a release that needs a newer launcher`() {
        val result = ManifestDocuments.read(
            manifestJson(minimumLauncherVersion = "0.9.0"),
            ORIGIN,
            "0.1.0",
        )

        assertIs<ManifestResult.Rejected>(result)
        assertTrue(
            result.problems.any { "needs launcher 0.9.0" in it },
            result.problems.toString(),
        )
    }

    @Test
    fun `accepts a launcher newer than the manifest requires`() {
        val result = ManifestDocuments.read(manifestJson(), ORIGIN, "1.4.0")

        assertIs<ManifestResult.Loaded>(result)
    }

    @Test
    fun `rejects an artifact that is not served over https`() {
        val result = ManifestDocuments.read(
            manifestJson(modUrl = "http://downloads.example.test/survivaloverhaul-0.1.0.jar"),
            ORIGIN,
            "0.1.0",
        )

        assertIs<ManifestResult.Rejected>(result)
        assertTrue(result.problems.any { "https" in it }, result.problems.toString())
    }

    @Test
    fun `rejects an artifact whose file name could climb out of the directory`() {
        val result = ManifestDocuments.read(
            manifestJson(modFileName = "../../evil.jar"),
            ORIGIN,
            "0.1.0",
        )

        assertIs<ManifestResult.Rejected>(result)
        assertTrue(result.problems.any { "file name" in it }, result.problems.toString())
    }

    @Test
    fun `rejects a schema version this launcher does not understand`() {
        val result = ManifestDocuments.read(manifestJson(schemaVersion = 99), ORIGIN, "0.1.0")

        assertIs<ManifestResult.Rejected>(result)
        assertTrue(result.problems.any { "schema version" in it }, result.problems.toString())
    }

    /** A field a future launcher understands must not break this one. */
    @Test
    fun `ignores fields it does not know about`() {
        val text = manifestJson().replaceFirst("{", """{ "somethingNewer": { "a": 1 },""")

        assertIs<ManifestResult.Loaded>(ManifestDocuments.read(text, ORIGIN, "0.1.0"))
    }

    private fun manifestJson(
        schemaVersion: Int = 1,
        minimumLauncherVersion: String = "0.1.0",
        modUrl: String = "https://downloads.example.test/survivaloverhaul-0.1.0.jar",
        modFileName: String = "survivaloverhaul-0.1.0.jar",
    ): String = """
        {
          "schemaVersion": $schemaVersion,
          "channel": "stable",
          "minimumLauncherVersion": "$minimumLauncherVersion",
          "minecraft": { "version": "26.2", "javaMajorVersion": 25 },
          "fabric": { "loader": "0.19.5", "api": "0.160.0+26.2" },
          "mod": {
            "id": "survival-overhaul",
            "displayName": "Survival Overhaul",
            "version": "0.1.0",
            "url": "$modUrl",
            "sha256": "${Fixtures.SHA}",
            "fileName": "$modFileName"
          },
          "dependencies": [
            {
              "id": "fabric-api",
              "displayName": "Fabric API",
              "version": "0.160.0+26.2",
              "url": "https://maven.example.test/fabric-api.jar",
              "sha256": "${Fixtures.SHA}",
              "fileName": "fabric-api-0.160.0+26.2.jar"
            }
          ],
          "resources": [],
          "server": null
        }
    """.trimIndent()

    private companion object {
        const val ORIGIN = "https://downloads.example.test/manifest.json"
    }
}
