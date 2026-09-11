# Architecture notes

Working notes on why the code is shaped the way it is. The gameplay design lives
in the project brief; this file is about structure.

## The one rule

Minecraft is an adapter around the survival logic, not the place the survival
logic lives.

Concretely: `dev.untamed.domain` contains no `net.minecraft` import, and
never will. Anything that needs to read or change the world does it through a
port defined in `domain` and implemented in `integration`.

The two ports so far:

- `FireSourceProbe` reads world state into a `FireObservation`.
- `FireSourceMutator` writes a decision back into the world.

`FireDousingSystem` sees neither a world nor a block. It sees an observation and
a random roll, and returns a `FireAction`. That is why every branch of the rain
rules has a unit test.

## Why not persist the torch index

The obvious design saves tracked positions to a `PersistentState` and reloads
them. It was rejected.

A saved index can disagree with the world. Torches placed by world generation,
by structures, or by another mod would never appear in it, and an index written
before a world edit would point at blocks that no longer exist.

Indexing on chunk load has neither problem. The index is derived state, rebuilt
from the world every time a chunk arrives, so it cannot drift. The cost is a
palette check per chunk section, which rejects almost every section immediately.

## Scheduling

Per-world, not global. Each `ServerLevel` gets a `WorldFireService` with its own
index and its own sweep cursor, so an overworld full of torches cannot starve the
Nether, and an unloaded dimension costs nothing.

A sweep takes whole chunk buckets until it meets its budget, then stops and
remembers where it was. The budget is therefore approximate: a sweep may overrun
by up to the size of one chunk's bucket rather than splitting a chunk across two
sweeps.

## Server authority

The server owns every gameplay decision. The client is told two booleans about
the torch rules, purely so a tooltip can say whether a doused torch will come
back on its own. Nothing the client holds can change what happens in a world.

The pattern to keep as more systems arrive: state flows server to client, and
client-to-server packets are requests that the server validates before acting on.

## Mixin conventions

- A mixin is a hand-off, not a place to think. `BlockItemMixin` is nine lines and
  delegates to `TorchSubstitution`.
- Prefer a Fabric event where one exists. The only mixin in the mod exists
  because block placement has no suitable event.
- Prefix injected methods with `untamed$`.
- One mixin class per target class.

## Adding the next system

The torch slice is meant to be the template. For temperature, the shape would be:

1. `domain/temperature` with the model, its states, and a config record.
2. Ports for whatever it must read, in the same style as `FireSourceProbe`.
3. Adapters in `integration/world` implementing those ports against Minecraft.
4. A per-world or per-player service in `server`, hooked to the lifecycle in the
   same place `FireServices` is.
5. A payload in `network` carrying the resulting state to the client.
6. Presentation in `src/client`, which decides how the state looks and nothing
   else.

Resist adding shared abstraction between systems until two of them actually need
the same thing.
