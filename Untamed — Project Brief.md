# Minecraft Untamed — Project Brief

## 1. Project vision

Build a **Minecraft Java Edition untamed mod** that progressively changes core survival mechanics and makes the game more systemic, immersive, and challenging.

The mod should not simply add isolated mechanics such as a thirst bar or temperature bar. The long-term goal is to create **interconnected survival systems** that interact with vanilla Minecraft and with each other.

Examples:

- Rain makes the player wet.
- Being wet reduces effective insulation.
- Reduced insulation causes temperature to drop faster.
- Cold weather can lead to hypothermia.
- Fire provides warmth.
- Rain can extinguish exposed torches.
- Losing reliable fire/light makes bad weather more dangerous.
- Heat increases hydration requirements.
- Backpacks change how much equipment/resources the player can carry.

The overall direction is:

> **Transform Minecraft into a deeper systemic survival game while retaining Minecraft's core identity.**

---

# 2. Target Minecraft version

Target the **latest stable Minecraft Java Edition version**, currently:

- Minecraft **26.2**
- Java **25**
- Fabric as the initial modding platform
- Latest stable Fabric Loader/API compatible with 26.2

Do not target snapshots unless explicitly requested.

Keep version-specific Minecraft/Fabric integration isolated so that future Minecraft updates are easier to handle.

---

# 3. Fabric vs NeoForge decision

The project should initially use **Fabric**.

Reasons:

- Lightweight.
- Excellent for modifying vanilla behavior.
- Mixins are well suited to deep vanilla modifications.
- Good server-side support.
- Good client + server support.
- Large ecosystem.
- Appropriate for experimenting with Minecraft internals.
- Avoids introducing unnecessary framework complexity at the beginning.

However, the architecture should **not tightly couple the gameplay/domain logic to Fabric**.

The project may eventually become large enough that NeoForge's larger framework/API ecosystem becomes attractive. The architecture should therefore make Minecraft/Fabric an integration layer rather than the location of the core game logic.

---

# 4. Architectural principle

The most important architectural rule:

> **Minecraft/Fabric should be an adapter around the game's survival logic, not the game's survival logic itself.**

Avoid putting substantial business/gameplay logic directly inside Mixins, Fabric events, or Minecraft classes.

Prefer:

```text
Minecraft / Fabric
        │
        ▼
Integration / Adapters
        │
        ▼
Survival Domain
        │
        ▼
Game State
```

For example:

```text
Minecraft weather event
        │
        ▼
WeatherAdapter
        │
        ▼
TemperatureSystem
        │
        ▼
PlayerSurvivalState
        │
        ├── Server state
        └── Client synchronization
```

Mixins and Fabric events should remain relatively thin.

---

# 5. Proposed project structure

Use a modular structure along these lines:

```text
src/
├── main/
│   ├── java/
│   │   └── ...
│   │
│   └── resources/
│
├── client/
│   ├── hud/
│   ├── screens/
│   ├── rendering/
│   └── shaders/
│
├── server/
│   ├── systems/
│   ├── persistence/
│   └── networking/
│
├── domain/
│   ├── temperature/
│   ├── hydration/
│   ├── inventory/
│   ├── survival/
│   └── weather/
│
├── minecraft/
│   ├── mixins/
│   ├── blocks/
│   ├── items/
│   ├── entities/
│   └── adapters/
│
└── network/
```

The exact package structure can evolve, but preserve the separation between:

1. Domain/gameplay logic
2. Minecraft integration
3. Server-specific behavior
4. Client-specific behavior
5. Networking
6. Rendering/UI

---

# 6. Server authority

The server should be authoritative for gameplay state.

Examples:

- Player temperature
- Hydration
- Wetness
- Backpack contents
- Survival effects
- Environmental calculations
- Damage caused by survival systems

The client should primarily handle:

- HUD
- UI
- visual effects
- shaders
- animations
- sounds
- presentation

For example:

```text
SERVER

temperature = -12°C
hydration = 35%
wetness = 80%
        │
        │ network synchronization
        ▼
CLIENT

Temperature HUD
Cold visual effect
Frost shader
```

Never trust client-provided gameplay state.

---

# 7. Feature roadmap

Build the project incrementally.

Do not implement all systems simultaneously.

Recommended order:

```text
Phase 1
└── Project foundation

Phase 2
└── Rain extinguishes torches

Phase 3
└── Temperature system

Phase 4
└── Hydration / drinking

Phase 5
└── Wetness + environmental interactions

Phase 6
└── Inventory / backpack system

Phase 7
└── Client HUD and shaders

Phase 8
└── Advanced survival mechanics

Phase 9
└── Balancing / polish / compatibility
```

---

# 8. Feature 1 — Rain extinguishes torches

The first prototype should be intentionally small.

## Desired behavior

A torch exposed to rain should turn off.

Example:

```text
Torch
  ↓
Rain begins
  ↓
Is torch exposed to sky?
  ↓
YES
  ↓
Torch becomes unlit
```

When the rain stops, decide later whether:

- torches automatically relight, or
- the player must manually relight them.

Prefer designing this as a configurable mechanic.

## Important Minecraft detail

Vanilla torches do not have a normal `LIT` blockstate like campfires do.

Possible implementations:

### Option A — Replace torch with an unlit torch block

Create:

```text
LitTorch
UnlitTorch
```

Rain transitions:

```text
LitTorch → UnlitTorch
```

### Option B — Custom torch implementation

Create a custom torch block with:

```text
LIT = true/false
```

and different light emission values.

Prefer the second approach if it provides a cleaner foundation for future mechanics.

## Lighting

When the block's light emission changes, Minecraft's lighting engine should propagate the lighting change.

The implementation must ensure that:

- lit torch emits normal torch light
- unlit torch emits zero light
- changing state correctly updates lighting
- block updates are synchronized to clients

## Rain detection

Do not scan every torch in the world every tick.

Avoid:

```text
every tick
  for every block in world
      if torch && raining
```

Instead use an event-driven or managed-position approach.

Potential strategies:

- Track managed torch positions.
- React when weather changes.
- React when a torch is placed.
- Periodically process only tracked torches.
- Remove invalid/deleted positions from tracking.

The implementation should prioritize scalability.

---

# 9. Feature 2 — Temperature

Temperature is intended to become one of the core survival systems.

Possible factors:

```text
Player temperature =
    biome temperature
  + altitude
  + time of day
  + weather
  + rain/wetness
  + nearby heat sources
  + armor/clothing insulation
  + player state
```

Do not implement every factor immediately.

Start with a minimal model:

```text
Biome
+
Weather
+
Time
+
Nearby heat source
```

Then evolve it.

---

# 10. Temperature model

Avoid making temperature simply:

```text
if cold -> damage
if hot -> damage
```

Instead introduce meaningful states.

Example:

```text
Temperature
│
├── Freezing
├── Cold
├── Comfortable
├── Hot
└── Overheating
```

Possible consequences:

```text
Cold
 ├── slower regeneration
 ├── movement penalties
 └── increased hunger

Severe cold
 ├── hypothermia
 └── damage

Hot
 ├── increased hydration consumption
 └── fatigue

Extreme heat
 └── heat damage
```

Exact values should remain configurable and subject to balancing.

---

# 11. Hydration / drinking

Add a hydration system analogous to hunger, but more systemic.

Possible mechanics:

```text
Hydration
├── water consumption
├── dehydration
├── drinking from containers
├── water sources
├── purified water
└── environmental effects
```

Hydration consumption can depend on:

- temperature
- sprinting
- physical activity
- biome
- weather
- player condition

Potential progression:

```text
100%
  ↓
Healthy
  ↓
Thirsty
  ↓
Dehydrated
  ↓
Severely dehydrated
```

Avoid making this unnecessarily punishing initially.

---

# 12. Wetness

Wetness should eventually connect the weather and temperature systems.

Example:

```text
Rain
 ↓
Wetness increases
 ↓
Insulation decreases
 ↓
Temperature drops faster
```

Possible drying mechanisms:

- shelter
- fire
- sunlight
- time
- specific items
- clothing

This creates emergent gameplay rather than independent bars.

---

# 13. Inventory overhaul

One major planned feature is to fundamentally change the inventory.

## Core idea

The player should initially only have the **9-slot hotbar**.

The rest of the inventory is not directly available.

Additional storage is unlocked through backpacks.

Concept:

> **The hotbar is what you carry. Backpacks are what you own.**

Example:

```text
Player
├── Hotbar
│   ├── Slot 1
│   ├── ...
│   └── Slot 9
│
└── Backpack
    ├── Storage
    ├── Capacity
    └── Contents
```

## Backpack progression

Eventually support:

- small backpack
- medium backpack
- large backpack
- specialized backpacks
- weight/capacity restrictions
- equipment slots
- backpack upgrades

Do not implement all of this initially.

Start with:

```text
Hotbar only
+
One basic backpack
```

---

# 14. Inventory architecture

Do not make vanilla `Inventory` the long-term source of truth for backpack contents.

Prefer a dedicated model:

```text
PlayerSurvivalState
└── BackpackInventory
      ├── ItemStack[]
      ├── capacity
      └── metadata
```

Minecraft's vanilla inventory should be treated as an integration/compatibility layer where necessary.

Potential architecture:

```text
Backpack domain
       │
       ▼
BackpackStorage
       │
       ▼
Minecraft adapter
       │
       ▼
Player
```

This will make future changes easier.

---

# 15. Client inventory UI

Because the mod can modify both client and server, the client should receive a custom inventory UI.

The goal is to make the vanilla inventory screen effectively disappear/replaced by:

```text
┌──────────────────────────────┐
│          Backpack             │
│                              │
│  [ ][ ][ ][ ][ ][ ][ ][ ]    │
│  [ ][ ][ ][ ][ ][ ][ ][ ]    │
│  [ ][ ][ ][ ][ ][ ][ ][ ]    │
│                              │
│       Backpack equipment     │
└──────────────────────────────┘
```

The hotbar remains visible during gameplay.

Opening the inventory should open the custom backpack UI.

The server remains authoritative over contents.

---

# 16. Networking

Create explicit packets for client/server communication.

Examples:

```text
OpenBackpack
BackpackContents
MoveBackpackItem
EquipBackpack
PlayerSurvivalState
```

The server validates all inventory operations.

Never trust:

```text
client says:
"I moved this item to this slot"
```

without server-side validation.

---

# 17. Client shaders / visual effects

Client-side rendering should be separate from gameplay logic.

Examples:

```text
Very cold
    ↓
ColdnessRenderer
    ↓
Frost / vignette / color effect
```

Possible effects:

- cold/frost
- overheating
- dehydration
- darkness
- wetness
- environmental effects

The server should communicate the state.

The client decides how to present it.

---

# 18. HUD

Eventually add a survival HUD containing:

```text
Health
Hunger
Temperature
Hydration
Possibly:
Wetness
Stamina
Fatigue
```

Do not necessarily display everything permanently.

A major design goal should be to avoid turning the screen into a dashboard.

Consider contextual visibility.

---

# 19. System interactions

The long-term goal is for systems to interact.

Example scenario:

```text
Player travels during rain
        ↓
Gets wet
        ↓
Temperature drops
        ↓
Player becomes cold
        ↓
Needs fire
        ↓
Torch is exposed to rain
        ↓
Torch extinguishes
        ↓
Player needs shelter
        ↓
Builds protected fire
        ↓
Dries clothes
        ↓
Temperature recovers
```

Another:

```text
Hot biome
   ↓
Higher body temperature
   ↓
Higher hydration consumption
   ↓
Player drinks more
   ↓
Water availability becomes important
```

This systemic design is more important than simply adding individual mechanics.

---

# 20. Design principles

## Avoid feature isolation

Do not implement:

```text
temperature = independent bar
hydration = independent bar
wetness = independent bar
```

Prefer:

```text
weather
  ↓
wetness
  ↓
insulation
  ↓
temperature
  ↓
hydration / health / fatigue
```

## Prefer simulation over arbitrary penalties

Avoid excessive:

```java
if (temperature < 10) {
    damagePlayer();
}
```

Prefer meaningful physical/gameplay relationships.

## Server authoritative

All meaningful gameplay state belongs to the server.

## Thin Minecraft integration

Mixins/events should call domain systems rather than contain complex logic.

## Configurable

Important gameplay constants should eventually be configurable.

Examples:

- temperature thresholds
- hydration consumption
- backpack capacities
- rain extinguishing probability
- drying rate
- heat-source radius

## Testable

Core systems should be testable without starting an entire Minecraft client whenever possible.

---

# 21. Modding approach

Use Fabric's normal hierarchy:

```text
Fabric Loader
    ↓
Fabric API
    ↓
Your mod
    ↓
Mixins where necessary
```

Use Fabric APIs when they provide the appropriate extension point.

Use Mixins when vanilla Minecraft does not expose an appropriate hook.

Avoid Mixins as the default solution for everything.

Keep Mixins small:

```java
@Inject(...)
private void onSomething(...) {
    survivalSystem.handleSomething(...);
}
```

rather than putting the entire system inside the Mixin.

---

# 22. Compatibility philosophy

The mod should aim to modify Minecraft deeply while preserving compatibility with vanilla concepts where practical.

However, this is an **overhaul**, so breaking vanilla assumptions is acceptable when required.

Examples:

- Vanilla inventory may no longer be the primary inventory.
- Torches may have a new lit/unlit state.
- Survival may have new player state.
- Vanilla progression may be modified.

Do not sacrifice the architecture merely to preserve every vanilla behavior.

---

# 23. Development strategy

Build vertically rather than implementing infrastructure for hypothetical future features.

Recommended first milestone:

## Milestone 1

Create a working Fabric 26.2 mod that:

1. Runs on a dedicated server.
2. Runs on a client.
3. Registers a custom torch.
4. Detects rain.
5. Detects whether a torch is exposed.
6. Extinguishes the torch.
7. Correctly updates its light level.
8. Synchronizes state between server and client.
9. Does not scan the entire world every tick.

Once this works, establish reusable infrastructure for future survival systems.

---

# 24. Suggested development milestones

### M1 — Technical foundation

- Fabric project
- Minecraft 26.2
- Java 25
- Client/server launch
- Basic logging
- Basic test setup
- Mod metadata

### M2 — Rain + torch

- Custom torch state
- Rain detection
- Sky exposure
- Lighting updates
- Placement handling
- Weather transitions
- Performance-safe tracking

### M3 — Temperature prototype

- Player survival state
- Temperature value
- Biome influence
- Weather influence
- Basic temperature update loop
- Basic effects

### M4 — Hydration

- Hydration state
- Drinking
- Water sources
- Dehydration states

### M5 — Environmental simulation

- Wetness
- Drying
- Fire warmth
- Temperature ↔ hydration interaction
- Weather interactions

### M6 — Inventory

- Disable/limit vanilla inventory
- Backpack item
- Backpack storage
- Persistence
- Custom UI
- Networking

### M7 — Client experience

- Survival HUD
- Temperature visualization
- Hydration visualization
- Shaders
- Environmental effects

### M8 — Advanced survival

Potential additions:

- insulation
- clothing
- stamina
- fatigue
- sleep
- diseases
- food spoilage
- water purification
- environmental hazards
- specialized backpacks
- body temperature zones

Only add these once the core systems are stable.

---

# 25. Important engineering constraint

Do not prematurely build a massive framework.

Start with real gameplay.

The code should evolve toward:

```text
             Minecraft
                 │
        ┌────────┴────────┐
        │                 │
    Integration        Rendering
        │                 │
        ▼                 ▼
   Survival Core      Client UI
        │
   ┌────┼────┬────┐
   ▼    ▼    ▼    ▼
Temp  Water Wet  Inventory
```

The survival core should remain as independent as reasonably possible from Fabric/Minecraft implementation details.

---

# 26. Long-term vision

This should eventually feel less like:

> "Minecraft with a few extra survival bars"

and more like:

> **A systemic survival game built on Minecraft's world, building, crafting, and multiplayer foundation.**

The key differentiator should be **interactions between mechanics**.

Weather, temperature, hydration, equipment, inventory, lighting, shelter, fire, and exploration should influence one another and create emergent survival decisions.

The mod should remain technically interesting as well as fun to play.

---

# 27. First implementation task

Start by creating the Fabric 26.2 project and implement **only the rain-extinguishes-torch prototype**.

Before adding temperature, hydration, inventory, or shaders, establish:

- project structure
- server/client separation
- domain/integration separation
- persistence approach
- networking approach
- Mixin conventions
- testing conventions

Then implement the torch feature end-to-end.

Do not implement future systems until the first vertical slice is working.