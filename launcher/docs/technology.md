# Choice of desktop technology

*First-task deliverable 1. Decided before any launcher code was written.*

## The decision

**Kotlin/JVM with Compose Multiplatform for Desktop**, built by Gradle,
packaged with `jpackage` through the Compose Gradle plugin.

## What the choice had to satisfy

The brief names the criteria: desktop support with Windows first and Linux
desirable, filesystem and process control, HTTP, packaging and distribution,
performance and startup time, and long-term maintainability. Two more come out
of the job itself. The launcher has to start a JVM with a long, precisely
ordered classpath and argument list, and it has to live beside a Fabric mod that
is already a Gradle/JVM project.

## The candidates

| Stack | Why it was considered | Why it lost |
| --- | --- | --- |
| Electron / Tauri + web UI | Fastest UI iteration, huge component ecosystem | Two toolchains to maintain, and the part that matters most, launching a JVM with a 70-entry classpath, would be done from a language that has no relationship to the JVM. Tauri also needs a Rust toolchain, which is not installed on this machine. |
| C# / WPF or Avalonia | Excellent Windows desktop story | Windows-first in practice, and it adds a third language to a repository that is already Java and Gradle. |
| Java + JavaFX | Same JVM, mature | The UI layer is heavier to write and to theme than Compose, and the brief explicitly does not want an enterprise-looking application. |
| **Kotlin + Compose Desktop** | Same JVM and same build system as the mod | Chosen. |

## Why the JVM wins here specifically

The launcher's central job is to build a Minecraft process: resolve libraries,
apply the rules in the version JSON, assemble a classpath, expand argument
templates, and start Java with the right working directory. Doing that from the
JVM means the version JSON, the classpath separator, the natives layout and the
launched process are all in one runtime's terms. A Node or Rust launcher would
spend its life describing the JVM from outside.

Everything else the brief needs is in the platform: `java.nio.file` for atomic
installs, `java.net.http` for downloads with no dependency, `MessageDigest` for
SHA-256 verification, `ProcessBuilder` for launch, `Desktop.browse` to put the
Microsoft sign-in page in the user's own browser, and `com.sun.net.httpserver`
for the loopback socket the sign-in is redirected back to.

Sharing the build system with the mod is a real saving, not a tidiness argument.
The same convention holds on both sides: every version coordinate is declared in
a `gradle.properties` and nowhere else.

## What it costs

Startup is the honest weakness. A Compose Desktop application starts slower than
a native binary, and the packaged runtime is roughly 80-120 MB because
`jpackage` embeds a trimmed JVM. Both are acceptable for a launcher that then
starts Minecraft, and `jlink` module trimming is already configured.

The other cost is version coupling: since Kotlin 2.0 the Compose compiler ships
inside the Kotlin release, so Kotlin and the Compose plugin have to move
together. The pinned pairing is Kotlin 2.4.20 with Compose Multiplatform 1.9.3.
If that pairing is rejected at build time, the fallback is to drop Kotlin to the
2.3.x line rather than to move Compose.

## Packaging

`jpackage`, driven by `compose.desktop.nativeDistributions`, produces an MSI on
Windows and a Deb on Linux, each with its own bundled runtime, so a user needs
no Java installed to run the launcher. The JVM that *runs Minecraft* is a
separate concern and is handled by `JavaRuntimeProvider` from milestone 2.

Note that `jpackage` refuses an MSI version that starts with `0`, which is why
`package_version` is tracked separately from `launcher_version`.
