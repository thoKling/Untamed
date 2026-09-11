# The release manifest

*First-task deliverable 4, and the brief's section 16: shipping a new mod build
must not mean shipping a new launcher.*

## Shape

```json
{
  "schemaVersion": 1,
  "channel": "stable",
  "minimumLauncherVersion": "0.1.0",
  "minecraft": { "version": "26.2", "javaMajorVersion": 25 },
  "fabric":    { "loader": "0.19.5", "api": "0.160.0+26.2" },
  "mod": {
    "id": "survival-overhaul",
    "displayName": "Survival Overhaul",
    "version": "0.1.0",
    "url": "https://.../survivaloverhaul-0.1.0.jar",
    "sha256": "e9cda2ab...",
    "fileName": "survivaloverhaul-0.1.0.jar"
  },
  "dependencies": [ { "id": "fabric-api", "...": "same shape as mod" } ],
  "resources":    [ { "kind": "resource_pack", "artifact": { "...": "" } } ],
  "server": null
}
```

The copy bundled at `resources/manifest/survival-overhaul.json` is the one this
build was released against. The launcher fetches the same document over HTTPS
from `Distribution.MANIFEST_URL` and falls back to the bundled copy when the host
is unreachable, so a distribution outage degrades to "cannot update" rather than
"cannot start". It falls back on a rejected manifest too, not only an unreachable
one: a document that fails validation is corrupt or hostile, and the pinned copy
inside the launcher is the better of the two. The origin the launcher reports
says which one answered.

Both copies are read by the same code and validated by the same rules. A
manifest that shipped inside the launcher is not more trustworthy than one it
downloaded, only older.

The remote fetch is a single attempt with no retry backoff. It runs while the
window is opening and the bundled copy is already in the jar, so waiting through
a retry for a document the launcher can do without is the wrong trade. Every
other download keeps the full retry policy.

A user can point the launcher at a different address from the Launcher section of
the settings screen. A value that is not HTTPS is not silently dropped: the
transport refuses it and the fallback reports why, because a launcher quietly
using a manifest other than the one on screen is worse than one that complains.

The mod URL in the bundled copy is a placeholder against a `.invalid` host,
marked as such in the file, so installing the mod fails until the distribution
host is real. The checksum for Fabric API is real.

## Why each field is there

**`schemaVersion`** is checked for equality, not compatibility. A launcher that
meets a schema it does not know refuses the manifest instead of guessing at the
half it recognises.

**`minimumLauncherVersion`** lets a future release require a launcher that can
actually install it, for instance once shader packs or a second mod loader
exist. It is a lower bound, so a newer launcher keeps working. It is enforced
when the manifest is read: a release this build cannot install correctly is
rejected with that as the reason, because the fix is a launcher update and the
user can only make it if they are told.

**Versions as data, never as code.** `GameConfiguration` carries the Minecraft,
Java, Fabric Loader, Fabric API and mod versions, and nothing in the launcher is
allowed to hard-code any of them. Moving the product to Minecraft 26.3 is a
manifest edit.

**`dependencies` and `resources` are explicit lists**, which is the brief's
section 7. Every downloadable file in the product is one `Artifact` with an id,
a version, an HTTPS URL, a SHA-256 and a leaf file name. There is no code path
that downloads a URL that did not come from this list, and no code path that
installs an artifact without checking its checksum.

**`channel`** is what a future beta stream hangs off. It is read and nothing
displays it yet.

**`server`** is a descriptor only: name, address, port, required mod version. It
carries nothing the server itself should be authoritative about, which is why
the launcher cannot be used to change how the server behaves.

## Validation, because a manifest is untrusted input

`SurvivalManifest.problems()` runs on every manifest the launcher reads, local
or remote, before any of it reaches the installer. A non-empty result rejects
the whole document; the launcher does not act on the parts that happened to
validate. What it enforces:

| Rule | What it stops |
| --- | --- |
| URL must start with `https://` | a downgrade to plain HTTP, and `file:` or `jar:` URLs |
| SHA-256 must be 64 hex characters | an artifact that would be installed unverified |
| file name must be a leaf, no `/ \ : * ? " < > \|` | path traversal out of the install directory |
| no two artifacts share a file name | one artifact silently overwriting another |
| mod version must equal the declared game configuration | a manifest that installs one version and reports another |
| server port in 1..65535 | nonsense that would reach a socket call |
| schema version must be the supported one | acting on a document written for a different launcher |

Two things are deliberately absent from the schema. There are no JVM argument
strings and no command lines, so a compromised manifest cannot influence the
process the launcher starts. And there are no executables: every artifact is a
jar or a zip that Minecraft loads as data. The launcher does not run anything it
downloads.

## Installation state, which is a different file

`survival-overhaul.json` inside the installation directory records what was
installed. It is not trusted either. `FilesystemInstallationProbe` cross-checks
every entry against a real file on disk, so a jar deleted by hand reports as
missing rather than as present. The manifest says what should be there; the
state file plus the disk says what is.

`InstallationPlanner` compares the two and produces a plan. Exact-version match
means ready; anything else, including a version newer than the manifest, is a
change to make, because a rollback is as much a change as an upgrade.
