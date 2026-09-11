package dev.survivaloverhaul.launcher

import dev.survivaloverhaul.launcher.domain.minecraft.HostPlatform
import dev.survivaloverhaul.launcher.domain.minecraft.RuleAction
import dev.survivaloverhaul.launcher.infrastructure.minecraft.VersionDocuments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The documents are trimmed to the shape the real ones have, keys and nesting
 * included. A launcher that reads these wrongly installs the wrong files, and
 * that is not something a live download would report clearly.
 */
class VersionDocumentsTest {

    @Test
    fun `reads the version list and finds one version in it`() {
        val text = """
            {
              "latest": { "release": "26.2", "snapshot": "26.3-pre1" },
              "versions": [
                {
                  "id": "26.2",
                  "type": "release",
                  "url": "https://piston-meta.mojang.com/v1/packages/abc/26.2.json",
                  "sha1": "abc",
                  "complianceLevel": 1
                }
              ]
            }
        """.trimIndent()

        val index = VersionDocuments.parseIndex(text)

        assertEquals("26.2", index.latestRelease)
        assertEquals(
            "https://piston-meta.mojang.com/v1/packages/abc/26.2.json",
            index.find("26.2")?.url,
        )
        assertNull(index.find("1.20.1"))
    }

    @Test
    fun `reads a minecraft version document`() {
        val profile = VersionDocuments.parseProfile(MINECRAFT)

        assertEquals("26.2", profile.id)
        assertEquals(25, profile.javaMajorVersion)
        assertEquals("26", profile.assetsId)
        assertEquals("26", profile.assetIndex?.id)
        assertEquals(30_000_000L, profile.client?.sizeBytes)
        assertEquals("client-1.12.xml", profile.logging?.fileId)
    }

    @Test
    fun `reads a native library as a rule-gated artifact`() {
        val profile = VersionDocuments.parseProfile(MINECRAFT)
        val native = profile.libraries.first { it.name.contains("natives") }

        assertEquals(RuleAction.ALLOW, native.rules.single().action)
        assertEquals("windows", native.rules.single().os?.name)
        assertEquals("x86_64", native.rules.single().os?.architecture)
        assertEquals(
            "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar",
            native.relativePath,
        )

        val selected = profile.librariesFor(HostPlatform("windows", "x86_64", "10.0"))
        assertEquals(2, selected.size)
        assertTrue(profile.librariesFor(HostPlatform("linux", "x86_64", "6.8")).size == 1)
    }

    @Test
    fun `reads a fabric profile as a delta with no digests of its own`() {
        val profile = VersionDocuments.parseProfile(FABRIC)

        assertEquals("fabric-loader-0.19.5-26.2", profile.id)
        assertEquals("26.2", profile.inheritsFrom)
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", profile.mainClass)
        assertNull(profile.client)

        val loader = profile.libraries.first { it.name.startsWith("net.fabricmc:fabric-loader") }
        assertNull(loader.download)
        assertEquals(
            "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar",
            loader.url,
        )
    }

    /**
     * 26.x runs under Minecraft's official mappings, so Fabric's profile no
     * longer carries an intermediary library. This asserts the absence, because
     * a launcher written against an older Fabric would look for one and fail.
     */
    @Test
    fun `expects no intermediary library for a 26 dot x profile`() {
        val profile = VersionDocuments.parseProfile(FABRIC)

        assertTrue(profile.libraries.none { it.name.contains("intermediary") })
    }

    @Test
    fun `ignores keys it has never heard of`() {
        val profile = VersionDocuments.parseProfile(
            """{ "id": "26.2", "somethingNew": { "nested": [1, 2, 3] } }""",
        )

        assertEquals("26.2", profile.id)
    }

    @Test
    fun `reads an asset index and keeps the shared objects apart from the names`() {
        val index = VersionDocuments.parseAssetIndex(
            id = "26",
            text = """
                {
                  "objects": {
                    "minecraft/sounds/a.ogg": { "hash": "${"a".repeat(40)}", "size": 120 },
                    "minecraft/lang/en_gb.json": { "hash": "${"b".repeat(40)}", "size": 300 }
                  }
                }
            """.trimIndent(),
        )

        assertEquals("26", index.id)
        assertEquals(2, index.objects.size)
        assertEquals(420L, index.totalBytes)
        assertEquals("assets/indexes/26.json", index.relativePath)
        val sound = index.objects.first { it.name.endsWith("a.ogg") }
        assertEquals("assets/objects/aa/${"a".repeat(40)}", sound.relativePath)
    }

    private companion object {

        val MINECRAFT = """
            {
              "id": "26.2",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "javaVersion": { "component": "java-runtime-gamma", "majorVersion": 25 },
              "assets": "26",
              "assetIndex": {
                "id": "26",
                "sha1": "${"a".repeat(40)}",
                "size": 400,
                "totalSize": 700000000,
                "url": "https://piston-meta.mojang.com/v1/packages/aaa/26.json"
              },
              "downloads": {
                "client": {
                  "sha1": "${"b".repeat(40)}",
                  "size": 30000000,
                  "url": "https://piston-data.mojang.com/v1/objects/bbb/client.jar"
                },
                "server": {
                  "sha1": "${"c".repeat(40)}",
                  "size": 50000000,
                  "url": "https://piston-data.mojang.com/v1/objects/ccc/server.jar"
                }
              },
              "libraries": [
                {
                  "downloads": {
                    "artifact": {
                      "path": "com/mojang/logging/1.5.10/logging-1.5.10.jar",
                      "sha1": "${"d".repeat(40)}",
                      "size": 15000,
                      "url": "https://libraries.minecraft.net/com/mojang/logging/1.5.10/logging-1.5.10.jar"
                    }
                  },
                  "name": "com.mojang:logging:1.5.10"
                },
                {
                  "downloads": {
                    "artifact": {
                      "path": "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar",
                      "sha1": "${"e".repeat(40)}",
                      "size": 180000,
                      "url": "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar"
                    }
                  },
                  "name": "org.lwjgl:lwjgl:3.3.3:natives-windows",
                  "rules": [
                    { "action": "allow", "os": { "name": "windows", "arch": "x86_64" } }
                  ]
                }
              ],
              "logging": {
                "client": {
                  "argument": "-Dlog4j.configurationFile=${'$'}{path}",
                  "file": {
                    "id": "client-1.12.xml",
                    "sha1": "${"f".repeat(40)}",
                    "size": 900,
                    "url": "https://piston-data.mojang.com/v1/objects/fff/client-1.12.xml"
                  },
                  "type": "file"
                }
              }
            }
        """.trimIndent()

        val FABRIC = """
            {
              "id": "fabric-loader-0.19.5-26.2",
              "inheritsFrom": "26.2",
              "releaseTime": "2026-01-01T00:00:00+00:00",
              "type": "release",
              "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
              "arguments": { "jvm": [], "game": [] },
              "libraries": [
                { "name": "net.fabricmc:sponge-mixin:0.16.6+mixin.0.8.7", "url": "https://maven.fabricmc.net/" },
                { "name": "net.fabricmc:fabric-loader:0.19.5", "url": "https://maven.fabricmc.net/" }
              ]
            }
        """.trimIndent()
    }
}
