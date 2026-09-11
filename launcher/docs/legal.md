# Legal and distribution constraints

*First-task deliverable 6. Kept in one document, and the user-visible text is
kept in one file, `branding/Branding.kt`, so that both can be reviewed without
reading the whole launcher.*

## What must never be redistributed

The launcher does not ship, mirror, cache for other users, or repackage:

- `minecraft.jar` or any other client or server jar;
- Minecraft assets, sounds, textures or language files;
- Minecraft's libraries;
- any modified build of Minecraft.

Every one of those is obtained per installation, from Mojang's own hosts, after
the user has signed in with their own Microsoft account. That is the difference
between a launcher and a distribution, and it is the constraint that shapes the
installer: the launcher may automate a download that the user is entitled to
make, and may not make it on their behalf once and hand out the result.

The user must own Minecraft: Java Edition. Ownership is checked against Mojang's
entitlement endpoint on every sign-in, not assumed and not configurable.

## What we distribute

Our own code, our own name, our own icons and artwork, and the Untamed
mod jar, which is this repository's work. Fabric Loader and Fabric API are not
redistributed either; they are downloaded from Fabric's own maven with checksums
verified.

No Minecraft artwork, logo, font or UI asset is used as our branding. The theme
is our own.

## Authentication

Only the supported Microsoft flow, in the user's own browser. The launcher never
asks for a Microsoft password, a Minecraft password, or a manually entered
access token, and never offers an offline or cracked mode. See
[authentication.md](authentication.md). The Azure application registration for
the Minecraft launcher API is a prerequisite that has to be applied for. The
sign-in is implemented against it; the client id is the one value a build
supplies, and without one the launcher says so rather than appearing broken.

## Not a Mojang or Microsoft product

The disclaimer is shown permanently in the window frame, on every screen, not
buried in an About dialog:

> Untamed is an unofficial Minecraft mod and launcher and is not
> affiliated with or endorsed by Mojang Studios or Microsoft. Minecraft is a
> trademark of Mojang Studios. You need your own copy of Minecraft: Java Edition
> to play.

The product is named Untamed. It does not use "Minecraft" as part of
its own name, does not imitate the official launcher's appearance, and labels
itself "Unofficial launcher" in the window title and on the home screen.

## Rules for anything added to `Branding.kt`

1. The product must never present itself as a Mojang or Microsoft product.
2. The disclaimer must stay reachable from the UI without hunting for it.
3. No Minecraft artwork, logo, font or asset may be used as our branding.

## Security rules the implementation follows

These come from the brief's section 15 and are listed here because they are
enforced in code rather than by convention:

| Rule | Where it lives |
| --- | --- |
| HTTPS only | `Artifact.problems()` rejects any URL that is not `https://` |
| Verify checksums before installing | every artifact carries a SHA-256; Mojang files carry SHA-1 in the version JSON; Fabric libraries are verified against the sibling `.sha1` |
| Validate manifests | `SurvivalManifest.problems()` runs on every manifest, and a non-empty result rejects the whole document |
| Never execute what is downloaded | every artifact is a jar or zip loaded by Minecraft as data; there is no installer executable anywhere in the flow, including Fabric's |
| No arbitrary remote code execution | the manifest schema contains no argument strings and no command lines, so it cannot influence the process the launcher starts |
| Separate trusted and untrusted content | wire types are separate from domain types, and everything crosses one explicit, validating mapping |
| Only trusted sources | Mojang's hosts, Fabric's maven, and our own distribution host; nothing else is fetched |
| Never log secrets | `LauncherLog` takes no tokens by rule, and the diagnostics report is built without any account data |
| No plaintext secrets | the refresh token is the only value worth persisting, and it is currently kept in memory only, so the user signs in again each launch |

## Open items

- The Azure application registration and its Mojang approval. Sign-in is
  written and cannot run until a client id from an approved registration exists.
- A credential-store binding for the refresh token. The Java standard library
  reaches none of the three, so persisting it means a native dependency.
- The distribution host for the manifest and the mod jar. The bundled manifest
  currently points at a placeholder `.invalid` URL, marked as such in the file.
- Whether the packaged installers should be code-signed. Unsigned MSIs trigger
  SmartScreen warnings, which matters for a launcher people are asked to trust.
