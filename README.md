# Survival Overhaul

A systemic survival overhaul for Minecraft Java Edition, built on Fabric.

The goal is not another row of bars on the HUD. Weather, fire, wetness,
temperature, hydration and equipment are meant to feed one another, so that
survival decisions come out of the interaction between systems rather than out
of individual penalties. The full design lives in
[Minecraft Survival Overhaul — Project Brief.md](Minecraft%20Survival%20Overhaul%20—%20Project%20Brief.md).

## What is implemented

Milestones 1 and 2 of the brief: the technical foundation, and the first
vertical slice of gameplay.

Rain puts torches out. A torch under open sky, in a biome where rain actually
falls, has a chance each sweep of going dark. It stops emitting light, the
lighting engine reacts, and nearby players hear it hiss. Whether it comes back
on by itself is a config switch; by default the player has to relight it with
flint and steel.

Torches are also crafted unlit, so light is something the player lights rather
than something crafting hands over.

Nothing from milestones 3 and later exists yet. There is no temperature,
hydration, wetness or backpack code, by design: the brief asks for the first
slice to work end to end before any of that is built.

## Launcher

The mod ships with its own desktop launcher, in [launcher/](launcher/). It
installs Minecraft, Fabric, this mod and its dependencies, keeps them current,
and starts the game, so a player never has to install Fabric or place a jar by
hand.

```
./gradlew -p launcher run
```

It is an independent Gradle build inside this repository, so Fabric Loom and the
Compose plugin never share a classpath. Milestones 1 to 5 are done: the
application shell, settings, logging, the release manifest and the planner, and
on top of them the installation itself. It fetches the release manifest from the
distribution host with the bundled copy as a fallback, downloads and verifies
Minecraft, its libraries and assets, the Fabric profile, the mod and its
dependencies, removes what a previous version left behind, and finds the Java
runtime the game needs. It signs the player in through Microsoft's own login page
in their own browser, checks with Mojang that the account owns Java Edition, and
starts the game as that player. There is no offline account and there will not be
one. Sign-in needs a client id from an approved Azure application registration,
which is the one value a build has to supply. See
[launcher/README.md](launcher/README.md).

## Layout

The package layout follows the separation the brief asks for. The direction of
dependency is strictly downward.

| Package | Role | May depend on |
| --- | --- | --- |
| `domain` | Survival rules and state. Plain Java. | nothing |
| `integration` | Blocks, adapters, mixins, config IO. | `domain` |
| `server` | Lifecycle, scheduling, per-world services. | `domain`, `integration` |
| `network` | Packet definitions and registration. | `domain` |
| `client` (in `src/client`) | HUD, tooltips, rendering. | everything above |

`domain` imports no Minecraft class anywhere. That is what makes the rules
testable without a game instance, and what would make a future NeoForge port a
matter of rewriting `integration` rather than rewriting the mod.

The client source set is compiled separately and stripped from a dedicated
server, so a client-only reference in common code fails at build time instead of
crashing a server at runtime.

## How the torch mechanic works

```
chunk loads ──► ChunkFireIndexer ──► FireIndex ◄── SurvivalTorchBlock.onPlace
                                        │
              every N ticks             ▼
        WorldFireService.tick ──► FireSweep ──► FireDousingSystem
                                        │              (decides)
                                        ├── MinecraftFireProbe   (reads world)
                                        └── MinecraftFireMutator (writes world)
```

Three things carry most of the weight.

**The torch is one block with a `lit` property**, not two blocks swapped for one
another. Light emission comes from a light-level function on the block properties,
so flipping `lit` makes Minecraft re-light the area and send the change to
clients on its own. The mod never touches the lighting engine.

**The world is never scanned.** Positions enter the index when a chunk loads and
when a torch is placed, and leave when a chunk unloads or when a sweep finds
nothing there. Chunk-load indexing walks only those chunk sections whose block
palette mentions a survival torch, so a chunk without torches is rejected almost
for free. Sweeps take a bounded slice of the index and resume where the previous
one stopped, and a world with clear skies and no torches waiting to dry does not
sweep at all.

**Vanilla torches are redirected at placement.** A mixin swaps the vanilla torch
state for the survival one, which covers every route a player has to placing a
torch without touching the vanilla block. Note that a torch is placed by
`StandingAndWallBlockItem`, not by a plain `BlockItem`, and that subclass
overrides `getPlacementState` without calling `super`, so both are hooked.

**A torch is crafted unlit.** The mod overrides the vanilla torch recipe to yield
`survivaloverhaul:unlit_torch`, so coal and a stick no longer buy light outright. To
light one, either place it and use flint and steel on it, or hold the stack and
click any open flame, which lights the whole stack at once. What counts as a
flame is the block tag `survivaloverhaul:lights_torches`, so a data pack can add to it,
and a tagged block that has a `lit` property has to actually be burning. Breaking a torch returns it in the
state it was in, an unlit torch for a dark one and an ordinary vanilla torch for a
burning one, and pick-block follows the same rule. The unlit torch sits next to
the vanilla torch in the creative menu. The lit survival torch item stays out of
the menu, because there it would only be a duplicate of the vanilla torch.

## Configuration

Written to `config/survivaloverhaul.json` on first run.

| Key | Default | Meaning |
| --- | --- | --- |
| `torches.enabled` | `true` | Master switch for the mechanic |
| `torches.extinguishChance` | `0.35` | Chance per sweep that a rained-on torch goes out |
| `torches.thunderstormMultiplier` | `2.0` | Applied to that chance during a thunderstorm |
| `torches.relightWhenDry` | `false` | Whether rain-doused torches relight themselves |
| `torches.checkIntervalTicks` | `40` | Ticks between sweeps, per world |
| `torches.maxChecksPerInterval` | `512` | Approximate positions inspected per sweep |
| `torches.convertVanillaPlacement` | `true` | Whether placing a vanilla torch yields the survival torch |

A malformed file is never fatal and never overwritten. Defaults are used for that
session, so a typo cannot stop a server from booting or destroy a hand-tuned
file. Out-of-range values are clamped rather than trusted.

Only a torch the weather put out is relit by the weather. One a player doused on
purpose stays dark.

## Building

```
./gradlew build
```

The Gradle wrapper is committed, so no Gradle install is needed. Compilation
targets Java 25 through a Gradle toolchain, which Gradle resolves from any JDK 25
on the machine. Gradle itself can run on an older JDK. Check what it can see with
`./gradlew javaToolchains`.

Run the client or a dedicated server from the Gradle tasks `runClient` and
`runServer`.

To build and install into a real Minecraft instance in one step, use
`deploy.ps1`. It reads the artifact name from `gradle.properties`, copies the jar
into `%APPDATA%\.minecraft\mods`, and deletes any older build of the mod left
there. Pass `-MinecraftDir` for a non-default instance and `-SkipBuild` to
install what is already in `build/libs`.

## Testing

```
./gradlew test
```

The domain layer has no Minecraft dependency, so its tests are plain JUnit and
run in about a second. `FireSweepTest` drives a whole sweep against an in-memory
fake world, which is as close to end to end as the mod gets without launching
the game.

## Version coordinates and mappings

Every Minecraft, Loader, Fabric API and Loom version is declared in
`gradle.properties` and nowhere else. The values there were resolved against the
Fabric version endpoint and the Fabric maven, and the project builds against
them.

There is no Yarn mappings dependency, because Yarn is not published for the
Minecraft 26.x line. Loom uses Minecraft's official mappings instead, so all
Minecraft-facing source is written in official names: `ServerLevel` rather than
`ServerWorld`, `LevelChunk` rather than `WorldChunk`, `BlockBehaviour.Properties`
rather than `AbstractBlock.Settings`. One class is not what a Mojang-mappings
veteran would expect: `ResourceLocation` is named `Identifier` here, in
`net.minecraft.resources`.

There is also no remap step. Minecraft 26.x runs under official mappings
directly, so `build/libs/survivaloverhaul-<version>.jar` is the shippable artifact
and there is no separate remapped jar to look for.

## Surfaces to re-check on a Minecraft update

These are the places where Minecraft's own API has changed shape in recent
releases. They are marked in the source with a `Version-sensitive` comment and
are all confined to `integration` and `client`, which is the point of the layout.

| Where | What can move |
| --- | --- |
| `SurvivalTorchBlock`, `SurvivalWallTorchBlock` | `updateShape` parameter list |
| `SurvivalTorchBlock` | the use-with-item hook's name and return type |
| Both torch blocks | membership tests: `BlockState.is(Block)` and `ItemStack.is(Item)` were removed in 26.2, so the code compares `getBlock()` and `getItem()` directly |
| `ModBlocks` | the registry-key-in-properties registration pattern, via `setId` |
| `TorchRulesPayload` | payload type, stream codec and the clientbound registry name |
| `FireServices` | Fabric renamed `ServerWorldEvents` to `ServerLevelEvents`, and the chunk-load handler gained a third argument |
| `StandingAndWallBlockItemMixin` | torches are placed by `StandingAndWallBlockItem`, whose `getPlacementState` override never calls `super`, so injecting on `BlockItem` alone misses them |
| `IgnitionSources` | `BlockState.is(TagKey)` on its own was removed in 26.2, leaving only the overload that also takes a predicate |
| `survivaloverhaul.mixins.json` | `compatibilityLevel` must match the compiled class version |

Render layers are deliberately absent from that list. Since 26.x the layer is
derived from the texture's own transparency, so the client registers nothing and
`BlockRenderLayerMap` no longer exists.

If a build fails after an update, start here.

## Next

Milestone 3 in the brief is the temperature prototype. The pieces this slice
establishes that it should reuse: per-world services on the server lifecycle,
the port-and-adapter split between rules and world access, config records with a
sanitising step, and the payload registration in `network`.
