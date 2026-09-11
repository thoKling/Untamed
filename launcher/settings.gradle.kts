// The launcher is an independent Gradle build that happens to live next to the
// mod. It is not a subproject of it: the mod build applies Fabric Loom, and
// keeping the two plugin classpaths apart avoids resolving Loom for a desktop
// application that has nothing to do with Minecraft's build.
//
// Build it with the wrapper at the repository root:  ./gradlew -p launcher run
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }

    // Plugin versions are declared once, in gradle.properties. The plugins
    // block of a Kotlin build script only accepts literals, so the versions are
    // bound here instead and the build script asks for the plugins by id alone.
    val kotlinVersion = providers.gradleProperty("kotlin_version").get()
    val composeVersion = providers.gradleProperty("compose_version").get()

    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.serialization") version kotlinVersion
        id("org.jetbrains.compose") version composeVersion
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Compose Multiplatform pulls androidx collection/annotation/lifecycle
        // artifacts, which are published only to Google's maven.
        google()
    }
}

rootProject.name = "survival-overhaul-launcher"
