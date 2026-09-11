# Untamed launcher

A dedicated desktop launcher for the Untamed mod. One button. It
installs Minecraft, Fabric, the mod and its dependencies, keeps them current,
and starts the game.

> Untamed is an unofficial Minecraft mod and launcher and is not
> affiliated with or endorsed by Mojang Studios or Microsoft. Minecraft is a
> trademark of Mojang Studios. You need your own copy of Minecraft: Java Edition
> to play.

## State: milestones 1 to 5 of 7

The launcher now installs a whole product. It fetches the release manifest from
the distribution host, falls back to the copy inside its own jar when the host
cannot be reached, installs Minecraft 26.2 with its rule-selected libraries and
its assets, installs the Fabric profile and the libraries it names, then installs
the mod, Fabric API and any resource packs into the directories Minecraft loads
them from. Every file is verified against a digest its publisher offers before it
is moved into place. Files a previous version left behind are removed, because
Fabric loads every jar in `mods/` and an old jar beside a new one is the same mod
loaded twice.

It also knows how to start the game. The launch path is complete: the version
documents are read back off disk and resolved through what they inherit from, the
rule-gated argument templates are expanded against this machine and this launch,
the natives are unpacked, and the process is started in the game directory with
its output captured.

The player it starts the game as comes from a real Microsoft sign-in. Sign in
opens Microsoft's own login page in the user's own browser, the authorization
code comes back to a loopback socket bound for the length of that one sign-in,
and the launcher then walks the full chain: Xbox Live, XSTS, Minecraft services,
the entitlement check at Mojang, and the player profile. The launcher never
renders a login form, never asks for a password, and never offers a field to
paste a token into. The ownership check is Mojang's answer about that account,
not an assumption.

Two things about it are worth knowing before running it. The chain needs a
client id from an Azure application registration, supplied at build time or in
the `UNTAMED_CLIENT_ID` environment variable; without one, sign-in is a
button that says so rather than a failure halfway through. And the refresh token
is kept in memory only, so the user signs in again each time the launcher opens.
The alternatives are an OS credential store, which the Java standard library
cannot reach, and a file, which `docs/authentication.md` rules out.

The launcher has no offline account and will not be given one: that is a bypass
of the check that the person playing owns the game, whatever the button says.

| Milestone | | |
| --- | --- | --- |
| 1 | Application shell, settings, logging, manifest, planner | **done** |
| 2 | Minecraft and Fabric installation, Java detection | **done** |
| 3 | Mod installation, remote manifest, checksums | **done** |
| 4 | Microsoft sign-in and ownership check | **done**, needs a client id |
| 5 | Launching the game | **done** |
| 6 | Server status and direct join | |
| 7 | Polish, packaging, diagnostics | |

The distribution host in `Distribution.MANIFEST_URL` is a placeholder until the
download site exists, so the remote fetch fails and the bundled manifest answers.
That is exactly what an outage looks like, which makes it the behaviour worth
having wrong first.

## Building

From the repository root:

```
./gradlew -p launcher run          # start the launcher
./gradlew -p launcher test         # unit tests
./gradlew -p launcher packageMsi   # Windows installer
./gradlew -p launcher packageDeb   # Linux package
```

The launcher is an independent Gradle build inside this repository. Keeping it
separate from the mod build means Fabric Loom's plugin classpath and the Compose
plugin's classpath never meet.

Every version the build needs is in `launcher/gradle.properties` and nowhere
else, the same convention the mod follows. Kotlin and the Compose plugin have to
move together, since the Compose compiler ships inside the Kotlin release.

## Where the launcher keeps its files

| | |
| --- | --- |
| Windows | `%APPDATA%\Untamed` |
| macOS | `~/Library/Application Support/Untamed` |
| Linux | `$XDG_DATA_HOME/untamed`, else `~/.local/share/untamed` |

Settings live there as `settings.json`, logs in `logs/`, and the game is
installed in `game/` unless the user chooses another directory. The game
directory is a separate setting because people keep games on other drives.

## Documentation

| | |
| --- | --- |
| [technology.md](docs/technology.md) | why Kotlin and Compose Desktop, and what it costs |
| [minecraft-requirements.md](docs/minecraft-requirements.md) | what installing and launching 26.2 with Fabric actually requires, verified against the live endpoints |
| [authentication.md](docs/authentication.md) | the legitimate Microsoft sign-in chain, and what the launcher refuses to do instead |
| [manifest.md](docs/manifest.md) | the release manifest and the rules it is validated against |
| [architecture.md](docs/architecture.md) | layers, ports, and how state flows |
| [legal.md](docs/legal.md) | redistribution, branding and security constraints |

## Principles the code holds to

**No version is hard-coded.** Minecraft, Java, Fabric Loader, Fabric API and the
mod version all arrive from the manifest. Moving to a new Minecraft release is a
manifest edit.

**A manifest is untrusted input.** It is validated in full before any of it is
acted on, and a document with any problem is rejected whole rather than used in
part. The bundled copy goes through exactly the same checks as a downloaded one:
shipping a manifest is not the same as vouching for it.

**A degraded launcher still starts.** When the distribution host is unreachable
or answers with something unusable, the launcher falls back to the manifest it
shipped with and says so in the origin it reports. An outage costs updates, not
startup.

**A manifest cannot redirect the launcher.** Mojang's and Fabric's addresses are
constants in the code and have no manifest field, so however a manifest was
obtained it can only point at artifacts that are checked against a digest it
published in the same document.

**The launcher runs nothing it downloaded.** Every artifact it fetches is a jar
or a zip, verified against its publisher's digest before it lands. It starts two
programs, and the operating system chooses both of them from what is already
installed: a Java runtime, once to ask what version it is and once to run
Minecraft, and the user's own browser, to show Microsoft's login page. That
second Java run is the game loading its own jars and the natives unpacked from
them, which is what starting Minecraft means, and the command line it runs under
is built entirely from the version documents Mojang and Fabric published. The release
manifest contributes versions, addresses and checksums, and no argument text at
all, so nothing the product publishes can reach that command line.

**Nothing lands unverified.** The download path refuses a request that carries
no digest, with no exception. Fabric's maven publishes no checksum inside its
profile, so the launcher fetches the `.sha1` beside each artifact and verifies
against that rather than trusting the transfer.

**Derived state is derived.** Installation status is rebuilt by inspecting the
installation, never remembered, so it cannot drift from the files it describes.

**Writes are atomic and input is never destroyed.** A file the launcher cannot
parse is quarantined, not overwritten.

**There is no way to play without signing in.** The only code path that produces
a session is a completed Microsoft sign-in that passed the ownership check. There
is no offline account, no field to paste a token into, and no second
implementation of the accounts port anywhere in the launcher.

**The UI does not lie.** Unfinished areas say which milestone they belong to
instead of showing a tick they have not earned.
