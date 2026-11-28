# Development roadmap

Singleplayer-first, Minecraft 1.21.1 / NeoForge 21.1.268 / Java 21.
No mandatory external mods initially. Cloud CI only; no personal computer runner.

## Acceptance gates

1. Foundation: Gradle build, reproducible packaged mod, pure Java simulation tests.
2. Minecraft integration: headless GameTests, save-backed floor progression and a playable first floor with bounded terrain.
3. Travel: safe return, boss-gated unlocks, generated floor identity, unloading and save/reload; test a scalable storage strategy before committing to thousands of registered dimensions.
4. Adventurers: persistent identity, needs, equipment, goals, relationships, event-driven memory, contextual conversations; near-player entities and budgeted distant simulation.
5. Parties and guilds: actual recruitment, leadership, resource accounting, quests, raids, rivalries and configurable NPC floor clearing.
6. World content: towns, labyrinths, bosses, resource tiers, modded equipment integration and optional Epic Fight adapter.
7. Visual QA: cloud client feasibility, controlled camera/animation captures, separate visual acceptance.
8. Performance: large population simulation, long-running save tests, runtime CPU/memory benchmarks, 1,000+ generated floors.

## Current scope

The floor catalog describes floor themes, seeds, radius and difficulty. Stage 1 now prototypes a single shared upper realm with stable region addresses and on-demand chunk generation; see [Stage 1](STAGE_1.md). The initial policy permits only sequential boss-based unlocking; NPC unlocks are configurable at policy construction. NPC utility decisions and bounded memories operate in pure Java. The NPC engine remains a pure Java primitive; Minecraft terrain and save-backed floor policy are separate Stage 1 implementations. Values are provisional balancing defaults. A retained event ID is deduplicated; evicted memories can no longer contribute to relationship scores. Stage 1 adds a real guardian death event and a versioned encounter identity; v0.1.4 restart verification succeeded. NPC social event dispatch and custom bosses remain later tasks.

Track results separately: implemented, Java verified, packaged/compiled, Minecraft runtime verified, visually verified. Never claim a playable feature from a unit test.

## Stage 2 — Living world

D: generated settlements, safe plazas, roads and gameplay service locations. F: autonomous MMORPG-style adventurers with persistent identities, goals, needs and decisions. G: contextual conversations, social memories, friendships and rivalries. H: flexible play-style routines, preparation, rest, training and independent exploration. The central experience is a populated singleplayer MMORPG: adventurers pursue their own goals, quests, levels, gear, parties, guilds and floor progression. Resident service NPCs (merchants, innkeepers, smiths, healers) support this loop without MineColonies-style production management, complex professions, construction logistics or mandatory work schedules. Keep SAO standalone; no Colony Overhaul or Autopilot systems imported. See [Stage 2 tracker](STAGE_2.md).
Independent world rule: while the Minecraft world ticks, visited adventurers should advance their own goals with bounded off-screen simulation and consistent loaded-entity behavior. Abstract routine practice and social relationships are the first slice; combat, inventory, parties, quests, guilds and dungeon progression require their own authoritative systems. No pretend loot or direct unlock grants. Closing or pausing the integrated server pauses simulation.

## RPG progression and combat direction

See [RPG system design](RPG_SYSTEM.md) for the agreed progression, weapon compatibility and non-hardcore defeat direction, plus the proposed combat mechanics and implementation gates. Treat numerical balance examples as provisional, and preserve working Stage 2 behavior until its replacement is runtime-tested.

## Aincrad UI direction

[Approved SAO Utils reference and native interface plan](AINCRAD_UI.md). Build the gesture and real character-profile vertical slice early, alongside shared RPG progression, without blocking runtime fixes or inventing unimplemented gameplay state.
