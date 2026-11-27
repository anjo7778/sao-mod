# Stage 1 — A / B / C / R

## Confirmed v0.1.4 baseline
- Main CI run 46074714712: successful compilation, JAR checks, 40,020 simulation assertions, 200,000 address cases, 64 castle checks and 4 required GameTests.
- Graphical run 46074763323: fresh world, safe spawn, actual generated castle pillar/ceiling, locked travel refusal, synthetic victory, Floor 2 landing and Floor 1 return.
- Dedicated-server restart run 46074892525: progression and a modified upper-floor block survived a real graceful shutdown and restart.
- Test Bridge run 46074058019: 4 cloud scenarios passed including v0.1.3 SAO terrain. Cloud performance samples do not predict real GPU FPS.

## v0.1.5 verification
- Main CI 46076003821: compilation, 5 required GameTests, 441,000 biome samples covering 13 palette entries.
- Client CI 46076098874: guardian challenge, duplicate rejection, abandonment, actual player-attributed entity death unlocked Floor 2, real travel and return, actual generated taiga biome on Floor 2; the Floor 2 screenshot nevertheless appeared enclosed/dark.
- Dedicated CI 46076176219: progression, modified upper-floor block and active encounter UUID survived restart. Eight separated floor centers generated including floors 1,000, 10,000 and 12,967,202. All eight unloaded with zero remaining upper-realm chunks and no forced tickets after 600 ticks. Sampled new chunk generation 574–1,142 ms, sampled peak heap 570 MiB on the empty cloud server; not a modpack benchmark.

## v0.1.6 implementation (verification pending)

| ID | Feature | Status |
|---|---|---|
| A1–A4 | Build, Java, runtime, graphical test infrastructure | Verified on baseline; repeat for new changes |
| B1–B3 | Preset, floating terrain, castle skeleton | Verified on baseline |
| B4 | Distinct floor environments | 441,000 samples passed; generated taiga verified in upper realm |
| C1 | Persistent progression | v0.1.5 real restart verified saved active encounter UUID, block and progression |
| C2 | Floor travel | Actual v0.1.5 travel passed but dark arrival screenshot exposed an unsafe surface check; v0.1.6 rejects covered ground, dangerous blocks and cliffs; visual rerun required |
| C3 | Boss unlocking | v0.1.5 client test verified real player-attributed death, duplicate prevention and abandon without unlock |
| R1/R2 | On-demand chunks and region addressing | Eight real distant centers generated, all unloaded; no permanent tickets |
| R3 | Performance | Java and real empty-server generation/unload benchmark verified; long-duration populated modpack performance is later work |

## Architecture and limits

Floor 1 is the Overworld; all upper floors are islands in `sao:upper_floors`. Stable square-spiral addresses use 16,384-block strides and support up to 12,967,202 floor numbers within the world border. This is addressing capacity, not authored content. No per-floor dimension, registry entry or forced chunks. Retain layout version 1 and old terrain settings for existing worlds. New saves have a stable theme by floor number, 13 vanilla biome choices, local biome patches, clear plains in central courtyards, and sand/red sand/snow surface rules. Terrain noise remains seeded. Floor 4 has wetlands rather than custom lakes/oceans, and volcanic/celestial themes use terrestrial placeholder biomes; distinct bespoke climate, architecture, settlements, rivers and dungeons belong to later content work. Daylight lattice roof and pillars are structural primitives, not a finished castle recreation.

Create a fresh Aincrad world for v0.1.6. On non-Peaceful difficulty near the central courtyard, `/sao challenge` starts the current frontier guardian. A real player-attributed death of the exact persisted UUID on the correct floor unlocks one additional floor; random mobs, duplicate deaths, abandoned encounters and environmental deaths cannot unlock. `/sao abandon` resets an encounter without victory. Stale guardians are discarded on tick. Use `/sao travel <number>` for unlocked floors. No physical gate or unique custom boss yet; no command directly grants victory. Singleplayer-first, no personal computer runner. Full modpack performance, detailed visuals and later NPC systems remain unverified.

## v0.1.6 graphical probe
The client verifies open sky and surface height at settled destinations and the server checks the same production safe-arrival predicate after teleport. The probe repeats real guardian challenge/death, upper biome verification, and return. Inspect the screenshots as well as assertions.
