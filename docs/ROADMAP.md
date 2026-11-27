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

The floor catalog describes floor themes, seeds, radius and difficulty without allocating Minecraft dimensions. The initial policy permits only sequential boss-based unlocking; NPC unlocks are configurable at policy construction. NPC utility decisions and bounded memories operate in pure Java. These are testable primitives, not in-game terrain, NPCs, or persistent world saves. Values are provisional balancing defaults. A retained event ID is deduplicated; evicted memories can no longer contribute to relationship scores. Save serialization, authoritative boss event sources and social event dispatch are later integration tasks.

Track results separately: implemented, Java verified, packaged/compiled, Minecraft runtime verified, visually verified. Never claim a playable feature from a unit test.
