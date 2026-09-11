# Launcher architecture

*First-task deliverable 5. The direction of dependency is strictly downward, the
same discipline the mod side of this repository follows.*

| Package | Role | May depend on |
| --- | --- | --- |
| `domain` | Versions, game configuration, manifest, installation state, settings, the session and the launch plan. Pure Kotlin. | nothing |
| `application` | Ports, and the pure decisions taken across them: planning, diagnostics. | `domain` |
| `infrastructure` | Filesystem, JSON, logging, HTTP, and the one process the launcher starts. | `domain`, `application` |
| `ui` | Compose screens and one observable store. | everything above |
| `branding` | Names, claims and legal notices. | nothing |

`domain` imports no Compose, no coroutines, no `java.nio`, and no serialization
annotations. That is what makes the version comparator, the manifest validator
and the settings sanitiser testable in milliseconds without a window, a network
or a temporary directory.

## Ports

The application layer owns the interfaces; infrastructure implements them; the
composition root in `Main.kt` is the only file that knows which implementation
is in use.

| Port | Implementation | Later |
| --- | --- | --- |
| `ManifestSource` | `FallbackManifestSource` over `RemoteManifestSource` and `BundledManifestSource` | signed manifests |
| `InstallationProbe` | `FilesystemInstallationProbe` | unchanged |
| `SettingsStore` | `JsonSettingsStore` | unchanged |
| `JavaRuntimeProvider` | `SystemJavaRuntimeProvider`, confirms each candidate by running it | offer to download Mojang's own runtime |
| `Installer` | `StagedInstaller`, the game then the product's files | unchanged |
| `GameInstaller` | `MojangGameInstaller`, over `HttpTransport` | unchanged |
| `ModInstaller` | `ManifestModInstaller`, over `HttpTransport` | unchanged |
| `CancellationSignal` | a flag the UI sets and the installer reads | unchanged |
| `GameLauncher` | `ProcessGameLauncher`, the one process the launcher starts | unchanged |
| `PlayerSessions` | read-only view of the session, implemented by `Accounts` | unchanged |
| `Accounts` | `MicrosoftAccounts`, the whole sign-in chain over `HttpTransport` | unchanged |
| `RefreshTokens` | `MemoryRefreshTokens`, the documented fallback | an OS credential store, which needs a native binding |

Adding the remote manifest was a change to one function in `Main.kt`. Nothing
above infrastructure knows where a manifest came from, only whether it loaded,
was rejected, or was unavailable.

## Why installing is two ports and not one

`GameInstaller` and `ModInstaller` are the same interface under different names,
split by what a failure means rather than by what the code does. Minecraft and
Fabric are one thing that either works or does not. The mod and its dependencies
are ordinary files that a perfectly good game can be missing. Keeping them apart
is what lets the launcher report "the game is installed, the mod is not" instead
of one undifferentiated verdict, and it is why `StagedInstaller` can stop before
downloading a mod for a game that never arrived.

Both share `InstallationFiles`, which owns every write, the containment check
that keeps a destination inside the installation directory, and the rule for
whether a file already on disk is the one that was asked for. A containment
check that exists twice is a check that will eventually be two different
checks.

## Why the manifest result is a sealed type

`ManifestResult` is `Loaded`, `Rejected(problems)` or `Unavailable(reason)`,
each carrying its origin. Three outcomes, not a nullable value, because the UI
has to say three different things: what to install, what is wrong with the
publisher's manifest, and that the launcher could not reach it. Collapsing them
into null would produce the vague failure message this brief is trying to avoid.

## State flows one way

`LauncherStore` holds the observable state and is the only thing the screens
talk to. Screens render it and call methods on it; they never touch a store, a
probe or the filesystem. Everything that can block runs on `Dispatchers.IO` and
comes back to the Swing dispatcher, so a slow disk cannot freeze the window.

Installation status is derived, rebuilt by inspecting the installation, never
remembered across runs. Derived state cannot drift away from the thing it
describes.

## Writes are atomic

`AtomicFiles` writes to a temporary file in the target directory and then moves
it, preferring `ATOMIC_MOVE` and falling back to a replacing move where the
filesystem cannot do it. Nothing the launcher owns is ever half-written, which
is the mechanism behind the brief's requirement that a failed update leaves the
previous version usable.

Unreadable input is quarantined rather than overwritten. A corrupt
`settings.json` is renamed to `settings.json.invalid-<stamp>` and defaults are
used, so a hand-edited file is never destroyed by the program that could not
parse it.

## Wire types are not domain types

`infrastructure/manifest/ManifestDocument.kt` holds the `@Serializable` shapes
and maps them to the domain. The published JSON contract and the internal model
can then move independently, and every field crosses one explicit mapping where
validation can stand.

## Installing is decided in one place and performed in another

`GameInstallationPlanner` turns version documents into the exact list of files
an installation needs: the client jar, the libraries the rules select for this
machine, the logging configuration, the asset index and every object in it. It
performs no IO, reads nothing from disk and knows no endpoint, so the two
awkward parts of installing Minecraft, rule-gated library selection and the
content-addressed asset layout, are things a unit test can check rather than
things only a real download can.

`MojangGameInstaller` performs that list. It is plain blocking code: no
coroutines below the UI layer, and cancellation is a `CancellationSignal` the
installer reads between files rather than a cancelled job. That is also what
makes it testable without a scheduler.

Addresses live in `infrastructure/minecraft/Endpoints.kt` as constants. The
release manifest carries versions and checksummed artifacts and no addresses, so
a manifest cannot redirect the launcher at somewhere other than Mojang's and
Fabric's own endpoints, however it was obtained.

## Nothing is written that has not been verified

`HttpTransport.download` refuses a request with no checksum. Not warns: refuses.
Bytes are hashed as they stream into a `.part` file beside the target, checked
against the digest the publisher offers, and only then moved into place, so an
interrupted download cannot leave a plausible-looking file behind.

Fabric's profile is the case that would tempt an exception. Its libraries carry
a coordinate and a repository and no digest at all, so the launcher fetches the
`.sha1` maven publishes beside each artifact first and verifies against that.
`PendingChecksum` is the shape of that two-step fetch, and its existence is what
lets the rule stay absolute.

## Launching is decided in one place and performed in another

The same split as installing, for the same reason. `GameLaunchPlanner` takes a
resolved version profile, a session, a Java runtime, the settings and a
`LaunchLayout` of absolute paths, and returns either a `LaunchPlan` or the list
of reasons there is none. It performs no IO. Every part of launching that a
version document can vary, which is rule-gated arguments, feature flags,
placeholder expansion, classpath order and native selection, is therefore
something a unit test checks rather than something only a real launch reveals.

`ProcessGameLauncher` is what performs that plan. It reads the profile back off
disk and follows `inheritsFrom` with a cycle guard, builds the layout, unpacks
the natives, starts the process in the game directory and waits for it. The
game's own output goes to `logs/game-output.log` beside the launcher log, with
the last lines kept in memory so a crash can be reported with its tail rather
than with an exit code alone.

`LaunchPlan` carries its secrets as a set of values, and `describe()` removes
them from the command by value wherever they appear. The launcher logs the
description and never the command. That is why the plan holds the secrets rather
than the caller: a redaction the caller has to remember to apply is a redaction
that is eventually forgotten.

There is no port for "start the game without an account". `Accounts` has exactly
one implementation and the only method on it that produces a session is a
completed sign-in, so the Play button is disabled by a fact rather than by a
flag. See `authentication.md` for why that is not a gap to be filled in locally.

## Signing in is four pieces, and only two of them do IO

The session types and the accounts port live in `application/account`, beside the
launch port rather than inside it: an account is its own concept, and the launch
path only reads the result.

The part of sign-in where a mistake is a security problem rather than a failed
launch has no IO in it at all. `MicrosoftAuthorization` builds the authorize URL
and reads the redirect, and it is a pure object with a test for every refusal,
because a missing `state` check is an attacker able to hand the launcher their
own authorization code. `XboxErrors` turns the six XSTS refusal codes into
sentences. Both are in `application`, and neither knows a socket exists.

The IO is `MicrosoftAccounts`, which walks the six services and reports each step
as it goes, plus two small infrastructure pieces: the loopback listener that the
browser is redirected back to, and the handoff to whatever browser the user
already uses. Sign-in happens on Microsoft's own page because the address bar is
how a person tells that the page asking for their password is Microsoft's, and an
embedded browser takes that away from them.

Secrets stay out of the log by construction rather than by care: every wire type
that carries a token overrides `toString`, the HTTP response type prints only the
length of its body, and every line the chain logs is a status code or a non-secret
error message.

## What 26.2 changed

Two differences break launchers written against older versions, and both are
handled deliberately rather than discovered at runtime.

Native libraries are no longer carried in a `downloads.classifiers` block keyed
by operating system. They are ordinary rule-gated library artifacts, and some
variants differ from each other by nothing but an `-arm64` suffix on the name,
so selection is by rule evaluation only. No field for `classifiers` is declared,
so nothing can quietly start reading a block no current version publishes.

Fabric's profile carries no `intermediary` library, because 26.x runs under
Minecraft's official mappings. A test asserts that absence.

## The UI says only what it has earned

The primary button reads INSTALL while there is something to install, PLAY when
there is not, and PLAYING while the game runs. PLAY is disabled without a
signed-in account, and the hint beside it gives the reason the session itself
reported rather than a message written into the screen. While a sign-in is
running that hint is the step being waited on, because six services answer in
turn and any of them can be the slow one. The manifest origin says
when the bundled copy answered instead of the distribution host, so "up to date"
never means "could not check". The server panel says "not configured yet". A
launcher that lies about state during development is a launcher whose state
nobody trusts in production.

After an installation the launcher re-inspects the directory rather than marking
the components installed. Derived state is derived, and a successful install is
not a reason to make an exception.

## Layout

```
launcher/
  build.gradle.kts  settings.gradle.kts  gradle.properties
  docs/
  src/main/kotlin/dev/untamed/launcher/
    Main.kt                composition root
    branding/              names, claims, notices
    domain/                versions, game, manifest, installation, settings,
                           downloads, minecraft version documents, java,
                           account, launch
    application/           ports, planners, progress, diagnostics, launch,
                           account
    infrastructure/        filesystem, logging, settings, manifest, http,
                           minecraft documents, installation, java, launch,
                           account
    ui/                    theme, components, home, settings, diagnostics
  src/main/resources/manifest/untamed.json
  src/test/kotlin/...
```

The launcher is an independent Gradle build inside the repository, driven by the
root wrapper with `-p launcher`. Keeping the builds separate means Fabric Loom's
plugin classpath and the Compose plugin's classpath never meet, and the mod's
Java 25 toolchain is unaffected by the launcher's Kotlin version.
