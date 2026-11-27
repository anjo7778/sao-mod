# Stage 1 — A / B / C / R

## Confirmed v0.1.4 baseline
- Main CI run 46074714712: successful compilation, JAR checks, 40,020 simulation assertions, 200,000 address cases, 64 castle checks and 4 required GameTests.
- Graphical run 46074763323: fresh world, safe spawn, actual generated castle pillar/ceiling, locked travel refusal, synthetic victory, Floor 2 landing and Floor 1 return.
- Dedicated-server restart run 46074892525: progression and a modified upper-floor block survived a real graceful shutdown and restart.
- Test Bridge run 46074058019: 4 cloud scenarios passed including v0.1.3 SAO terrain. Cloud performance samples do not predict real GPU FPS.

## v0.1.5 implementation (verification pending for new changes)

| ID | Feature | Status |
|---|---|---|
| A1–A4 | Build, Java, runtime, graphical test infrastructure | Verified on baseline; repeat for new changes |
| B1–B3 | Preset, floating terrain, castle skeleton | Verified on baseline |
| B4 | Distinct floor environments | 13 vanilla biome entries with floor-specific stable palettes and local patches; new-world testing pending |
| C1 | Persistent progression | Actual restart passed on baseline; active guardian identity added with schema 1 migration |
| C2 | Floor travel | Actual travel/return passed on baseline; new biome settings supported |
| C3 | Boss unlocking | Persistent named Ravager guardian, identity and floor verification, player-attributed death, cancellation/reset; actual encounter test pending |
| R1/R2 | On-demand chunks and region addressing | Shared upper realm, real Floor 2 generation and persistence verified; multi-floor eviction still needs runtime tests |
| R3 | Performance | Pure Java and preliminary runtime benchmarks; long-duration modpack benchmarks pending |

## Architecture and limits

Floor 1 is the Overworld; all upper floors are islands in `sao:upper_floors`. Stable square-spiral addresses use 16,384-block strides and support up to 12,967,202 floor numbers within the world border. This is addressing capacity, not authored content. No per-floor dimension, registry entry or forced chunks. Retain layout version 1 and old terrain settings for existing worlds. New saves have a stable theme by floor number, 13 vanilla biome choices, local biome patches, clear plains in central courtyards, and sand/red sand/snow surface rules. Terrain noise remains seeded. Floor 4 has wetlands rather than custom lakes/oceans, and volcanic/celestial themes use terrestrial placeholder biomes; distinct bespoke climate, architecture, settlements, rivers and dungeons belong to later content work. Daylight lattice roof and pillars are structural primitives, not a finished castle recreation.

Create a fresh Aincrad world for v0.1.5. On non-Peaceful difficulty near the central courtyard, `/sao challenge` starts the current frontier guardian. A real player-attributed death of the exact persisted UUID on the correct floor unlocks one additional floor; random mobs, duplicate deaths, abandoned encounters and environmental deaths cannot unlock. `/sao abandon` resets an encounter without victory. Stale guardians are discarded on tick. Use `/sao travel <number>` for unlocked floors. No physical gate or unique custom boss yet; no command directly grants victory. Singleplayer-first, no personal computer runner. Full modpack performance, detailed visuals and later NPC systems remain unverified.

## v0.1.5 server probe
Two actual server launches verify save migration schema 2, saved active guardian identity, unlocked floor and changed upper-floor block. The reopened server generates eight separated centers, checks biome/spawn heights, measures chunk generation latency and heap, and waits for the queried centers to unload without permanent tickets. Direct test-only generation does not grant player progression. No full-population stress or GPU claim.
