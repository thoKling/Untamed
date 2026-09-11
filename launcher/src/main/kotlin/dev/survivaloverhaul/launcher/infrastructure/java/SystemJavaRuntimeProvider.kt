package dev.survivaloverhaul.launcher.infrastructure.java

import dev.survivaloverhaul.launcher.application.installation.JavaRuntimeProvider
import dev.survivaloverhaul.launcher.domain.java.JavaRuntime
import dev.survivaloverhaul.launcher.domain.java.JavaSource
import dev.survivaloverhaul.launcher.domain.java.JavaVersionOutput
import dev.survivaloverhaul.launcher.infrastructure.filesystem.HostSystem
import dev.survivaloverhaul.launcher.infrastructure.filesystem.OperatingSystem
import dev.survivaloverhaul.launcher.infrastructure.logging.LauncherLog
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Finds the Java runtimes on this machine.
 *
 * The order is the one the brief asks for: the configured executable first,
 * then `JAVA_HOME`, then the runtime the launcher is itself running on, then
 * the places this platform installs Java. Mojang's own per-version runtimes are
 * included, because someone who has played Minecraft before very likely has the
 * right one already and downloading another copy would be waste.
 *
 * Every candidate is confirmed by running it and reading what it says about
 * itself. A directory that looks like a JDK is not evidence that it contains a
 * working one, and launching the game with a runtime that turns out to be a
 * broken symlink produces exactly the unexplained failure this launcher exists
 * to avoid.
 *
 * Running `java -version` is the one place the launcher starts a process it did
 * not download. That is the distinction the project holds to: nothing fetched
 * from the network is ever executed, and a Java runtime already installed on
 * the machine was not fetched by this launcher.
 */
class SystemJavaRuntimeProvider(
    private val log: LauncherLog,
    private val timeoutSeconds: Long = TIMEOUT_SECONDS,
) : JavaRuntimeProvider {

    override fun detect(configuredExecutable: String?): List<JavaRuntime> {
        val candidates = LinkedHashMap<String, JavaSource>()

        fun offer(path: Path?, source: JavaSource) {
            val executable = path?.normalize()?.toString() ?: return
            candidates.putIfAbsent(executable, source)
        }

        configuredExecutable?.takeIf { it.isNotBlank() }
            ?.let { offer(Path.of(it), JavaSource.CONFIGURED) }
        System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }
            ?.let { offer(executableIn(Path.of(it)), JavaSource.JAVA_HOME) }
        System.getProperty("java.home")?.takeIf { it.isNotBlank() }
            ?.let { offer(executableIn(Path.of(it)), JavaSource.LAUNCHER_RUNTIME) }
        installedRoots().forEach { offer(executableIn(it), JavaSource.INSTALLED) }

        val found = candidates.mapNotNull { (executable, source) -> identify(executable, source) }
        log.info(
            CATEGORY,
            "Looked at ${candidates.size} Java candidates, confirmed ${found.size}: " +
                found.joinToString { "${it.majorVersion} at ${it.executable}" }.ifBlank { "none" },
        )
        return found
    }

    /**
     * Runs the executable and reads the version banner.
     *
     * Null for anything that does not answer, answers something unreadable, or
     * takes too long. A runtime that cannot say what it is does not get used.
     */
    private fun identify(executable: String, source: JavaSource): JavaRuntime? {
        val file = Path.of(executable)
        if (!Files.isRegularFile(file)) return null
        return runCatching {
            // Java writes its version banner to stderr, so the two streams are
            // merged rather than reading standard output alone.
            val process = ProcessBuilder(executable, "-version")
                .redirectErrorStream(true)
                .start()
            // Waited on before the output is read, so that an executable which
            // never exits is killed by the timeout instead of blocking the read
            // forever. A version banner is far too small to fill a pipe.
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                log.warn(CATEGORY, "$executable did not answer in time")
                return null
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val details = JavaVersionOutput.parse(output) ?: return null
            JavaRuntime(
                executable = executable,
                majorVersion = details.majorVersion,
                description = details.description,
                source = source,
            )
        }.getOrElse {
            log.debug(CATEGORY, "$executable could not be run: ${it.message}")
            null
        }
    }

    private fun executableIn(home: Path): Path = home.resolve("bin").resolve(executableName())

    private fun executableName(): String =
        if (HostSystem.operatingSystem == OperatingSystem.WINDOWS) "java.exe" else "java"

    /**
     * Every directory on this platform that usually holds a Java installation,
     * one level deep. Deliberately a fixed list of well-known locations rather
     * than a search of the disk: scanning a filesystem for JDKs is slow, and it
     * finds copies inside other applications that were never meant to be used.
     */
    private fun installedRoots(): List<Path> = when (HostSystem.operatingSystem) {
        OperatingSystem.WINDOWS -> childrenOf(
            path(System.getenv("ProgramFiles"), "Java"),
            path(System.getenv("ProgramFiles"), "Eclipse Adoptium"),
            path(System.getenv("ProgramFiles"), "Microsoft"),
            path(System.getenv("ProgramFiles"), "Zulu"),
            path(System.getenv("ProgramFiles"), "Amazon Corretto"),
            path(System.getenv("LOCALAPPDATA"), "Programs", "Eclipse Adoptium"),
        ) + minecraftRuntimes(path(System.getenv("APPDATA"), ".minecraft", "runtime"))

        OperatingSystem.MAC_OS -> childrenOf(Path.of("/Library/Java/JavaVirtualMachines"))
            .map { it.resolve("Contents/Home") } +
            minecraftRuntimes(userHome()?.resolve("Library/Application Support/minecraft/runtime"))

        else -> childrenOf(
            Path.of("/usr/lib/jvm"),
            Path.of("/usr/java"),
            userHome()?.resolve(".sdkman/candidates/java"),
        ) + minecraftRuntimes(userHome()?.resolve(".minecraft/runtime"))
    }

    /**
     * Mojang's own runtimes, which sit two directories below the runtime folder
     * as `<component>/<platform>/<component>`, with the JVM home inside.
     */
    private fun minecraftRuntimes(root: Path?): List<Path> {
        val components = childrenOf(root)
        val platforms = childrenOf(components)
        val homes = childrenOf(platforms)
        // macOS packages its runtime as a bundle; both layouts are offered and
        // the ones that do not exist are dropped when the executable is checked.
        return homes + homes.map { it.resolve("jre.bundle/Contents/Home") }
    }

    private fun childrenOf(vararg roots: Path?): List<Path> = childrenOf(roots.toList())

    private fun childrenOf(roots: List<Path?>): List<Path> = roots.filterNotNull()
        .filter { Files.isDirectory(it) }
        .flatMap { root ->
            runCatching {
                Files.list(root).use { stream ->
                    stream.filter { Files.isDirectory(it) }.toList()
                }
            }.getOrDefault(emptyList())
        }

    private fun path(base: String?, vararg more: String): Path? =
        base?.takeIf { it.isNotBlank() }?.let { Path.of(it, *more) }

    private fun userHome(): Path? = System.getProperty("user.home")
        ?.takeIf { it.isNotBlank() }
        ?.let { Path.of(it) }

    private companion object {
        const val CATEGORY = "java"
        const val TIMEOUT_SECONDS = 5L
    }
}
