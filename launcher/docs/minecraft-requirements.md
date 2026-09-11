# What installing and launching Minecraft 26.2 with Fabric actually requires

*First-task deliverable 2. Every fact here was checked against the live Mojang
and Fabric endpoints while writing it, not recalled. It is the specification
milestones 2 and 5 are built against.*

*Everything here is now implemented. Installing: the version list, the version
document, the client jar, the rule-selected libraries, the assets, the logging
configuration, the Fabric profile and its libraries, Java detection, and the
mod, Fabric API and resource packs in the directories Minecraft loads them from.
Launching: profile resolution, argument expansion, the classpath, the natives
and the process itself. The account the game is launched as comes from a real
Microsoft sign-in, specified in [authentication.md](authentication.md).*

## Version coordinates

| Piece | Value | Where it was confirmed |
| --- | --- | --- |
| Minecraft | 26.2, current release, no snapshot | Mojang version manifest v2 |
| Java | major 25, component `java-runtime-epsilon` | the 26.2 version JSON's `javaVersion` block |
| Fabric Loader | 0.19.5, marked stable for 26.2 | Fabric meta v2 loader list |
| Fabric API | 0.160.0+26.2 | Fabric maven, which also publishes the `.sha256` |
| Untamed | 0.1.0 | this repository's `gradle.properties` |

These live in the manifest, not in code. See [manifest.md](manifest.md).

## Installing Minecraft itself

1. **Version manifest.** `version_manifest_v2.json` lists every version with a
   per-version JSON URL and that URL's SHA-1. The launcher picks the entry whose
   `id` matches the manifest's Minecraft version and verifies the document it
   downloads against the listed hash before parsing it.
2. **Client jar.** The version JSON's `downloads.client` gives URL, size and
   SHA-1. This file is downloaded per installation and **never redistributed**.
3. **Libraries.** Each entry carries a Maven-style `path`, URL, size and SHA-1,
   and may carry `rules`. Rules are evaluated in order, last match wins, with
   `os.name` in Mojang's own vocabulary (`windows`, `linux`, `osx`) and
   `os.arch` in Mojang's (`x86`, `x86_64`, `aarch64`).
4. **Asset index.** `assetIndex` gives a JSON of `objects`, each with a hash and
   size. An object is stored at `assets/objects/<first two hex chars>/<hash>`
   and downloaded from `https://resources.download.minecraft.net/` on the same
   path. Objects are content-addressed, so an existing correct file is never
   re-fetched.
5. **Logging configuration.** `logging.client` supplies an XML file and a
   `-Dlog4j.configurationFile=` argument template.

## Two things that changed in 26.2 and will break a launcher written from memory

**Natives are no longer `classifiers`.** Older version JSONs put native
libraries under `downloads.classifiers` with a `natives` map keyed by OS, and
launchers extracted them into a natives directory. The 26.2 JSON has neither.
Native libraries are ordinary rule-gated library artifacts, and the installer
must select them by evaluating rules like any other library. There are 72
library entries in the 26.2 client, and some variants differ only by a name
suffix such as `-arm64`, so selection has to use the rules rather than string
matching on the artifact name.

**No `intermediary` library in the Fabric profile.** The Fabric 26.2 profile
does not carry an intermediary mappings library, because 26.x runs under
Minecraft's official mappings. A classpath builder that assumes intermediary is
present will not find it. This matches the mod side of this repository, which
uses official mappings and has no remap step.

## Adding Fabric

Fabric meta v2 returns a launcher profile directly:

```
https://meta.fabricmc.net/v2/versions/loader/<minecraft>/<loader>/profile/json
```

The document is in Mojang's own version-JSON format, with:

- `id` of the form `fabric-loader-<loader>-<minecraft>`, which becomes the
  directory name under `versions/` and the version the launcher starts;
- `inheritsFrom` pointing at the plain Minecraft version, so the vanilla JSON
  still has to be installed and merged;
- `mainClass` `net.fabricmc.loader.impl.launch.knot.KnotClient`;
- a `libraries` list served from `https://maven.fabricmc.net/`. These entries
  carry no SHA, so the installer fetches the sibling `.sha1` from the same maven
  path and verifies against that. No artifact is installed unverified.

There is no Fabric installer executable involved, and there should not be. The
launcher writes the profile JSON and resolves its libraries itself, which keeps
it inside the rule that downloaded files are data, never executables.

Fabric API is not part of the loader. It is an ordinary mod jar that goes in
`mods/`, and in this project it is a manifest dependency with a pinned checksum.

## Java

The 26.2 version JSON asks for `java-runtime-epsilon`, major 25. Mojang
publishes that runtime per platform through its own java-runtime index
(epsilon 25.0.1 at the time of writing), so a future automatic Java provider has
a legitimate first-party source and does not need to bundle or redistribute a
JDK.

Milestone 1 does none of this. It defines the `JavaRuntimeProvider` seam and
lets the user point at a Java executable by hand. Milestone 2 adds detection:
`JAVA_HOME`, the launcher's own runtime, the platform's usual install locations,
then a validated `java -version`. Downloading a runtime from Mojang comes later
still, and only behind the same checksum discipline as everything else.

## Directory layout the installer targets

```
<installation directory>/
  versions/<id>/<id>.json          vanilla and fabric profiles
  versions/<id>/<id>.jar           client jar, never redistributed
  libraries/<maven path>           libraries, content-verified
  assets/indexes, assets/objects   content-addressed assets
  natives/                         extracted natives, per version
  mods/                            Fabric API, Untamed
  config/  resourcepacks/  shaderpacks/
  untamed.json           what this launcher installed
```

`untamed.json` is the launcher's own record. It is a claim, not a
truth: the installation probe cross-checks every entry against a real file
before believing it, so deleting a jar by hand shows up as missing rather than
as installed.

## Launching

The version JSON's `arguments.jvm` and `arguments.game` are rule-gated templates
containing `${...}` placeholders that the launcher fills: classpath, natives
directory, game directory, asset index, and the account values the sign-in
produced.
Memory, window size and extra JVM arguments come from settings. The launcher
brand goes in as `untamed-launcher` so a crash report says where the
game was started from.

No argument the launcher constructs may come from an unvalidated manifest field.
That is why the manifest carries versions and checksummed artifacts, and no
argument strings.

The Fabric profile is read back off disk and resolved through its `inheritsFrom`
before any of this happens, so the templates being expanded are Minecraft's own
followed by Fabric's. The main class is Fabric's, and the jar the classpath ends
with is still the vanilla client at `versions/26.2/26.2.jar`.

A placeholder with no value drops the whole argument group it appears in, not
just itself, because these arrive as flag and value pairs and a flag left alone
would read the next argument as its value. The launcher reports each drop rather
than failing the launch.

The natives named by the rule-selected libraries are unpacked into
`natives/<version id>/` and flattened to their file names, because
`java.library.path` is a list of directories and is not searched recursively.
The archives stay on the classpath as well: a natives jar carries the Java
classes that load the binaries alongside the binaries themselves.

The access token and the XUID are in the command line, because the game needs
them there, and are removed by value from every other form of the command: the
log, the diagnostics report, and the copy the UI shows.
