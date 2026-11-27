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

## v0.1.6 foundation acceptance — verified on cloud CI

- Release source commit `1e13fc26afd3ac9cbdcf16db59b93fb208860b93`, main build [46076471652](https://github.com/anjo7778/sao-mod/actions/runs/46076471652): compilation, JAR metadata, all 5 required GameTests, 441,000 biome samples, 200,000 addresses and 64 castle checks passed.
- Client [46076500068](https://github.com/anjo7778/sao-mod/actions/runs/46076500068): real guardian challenge/death, locked-floor refusal, upper arrival and return with outdoor checks, actual taiga chunk, castle blocks, screenshots. Floor 2 daylight screenshot inspected; previous dark arrival corrected. Castle screenshot viewpoint was obscured by a tree; architectural blocks, not final artistic quality, verified.
- Dedicated server [46076545243](https://github.com/anjo7778/sao-mod/actions/runs/46076545243): real save/restart, active guardian UUID persistence, upper block persistence, production safe arrivals on 8 distant centers, covered arrival rejection, 8 centers unloaded with 0 upper-realm chunks and no forced tickets after 600 ticks. Sampled generation plus arrival search 487–1,449 ms, sampled peak heap 601 MiB on empty cloud server.
- Release JAR `sao-0.1.6.jar`, SHA-256 `0c978e8fb47a36a71e0eb667cc12be35d38654b9a9d8de1e171b88d21992956b`. Visual/persistence probe classes are excluded from the main release. No Windows modpack test yet.

Stage 1 foundation acceptance passes; user gameplay feedback and visual polish remain. Unique authored floor content, NPCs, physical gate art, full world climate and large-scale populated runtime tests are not claimed complete.

| ID | Feature | Status |
|---|---|---|
| A1–A4 | Build, Java, runtime, graphical test infrastructure | Verified v0.1.6 |
| B1–B3 | Preset, floating terrain, castle skeleton | Verified on baseline |
| B4 | Distinct floor environments | 441,000 samples passed; generated taiga verified in upper realm |
| C1 | Persistent progression | v0.1.5 real restart verified saved active encounter UUID, block and progression |
| C2 | Floor travel | v0.1.6 verified real daylight landing, return and covered landing rejection |
| C3 | Boss unlocking | v0.1.5 client test verified real player-attributed death, duplicate prevention and abandon without unlock |
| R1/R2 | On-demand chunks and region addressing | v0.1.6 eight real distant centers and safe arrivals verified; all unloaded; no permanent tickets |
| R3 | Performance | Java and real empty-server generation/unload benchmark verified; long-duration populated modpack performance is later work |

## Architecture and limits

Floor 1 is the Overworld; all upper floors are islands in `sao:upper_floors`. Stable square-spiral addresses use 16,384-block strides and support up to 12,967,202 floor numbers within the world border. This is addressing capacity, not authored content. No per-floor dimension, registry entry or forced chunks. Retain layout version 1 and old terrain settings for existing worlds. New saves have a stable theme by floor number, 13 vanilla biome choices, local biome patches, clear plains in central courtyards, and sand/red sand/snow surface rules. Terrain noise remains seeded. Floor 4 has wetlands rather than custom lakes/oceans, and volcanic/celestial themes use terrestrial placeholder biomes; distinct bespoke climate, architecture, settlements, rivers and dungeons belong to later content work. Daylight lattice roof and pillars are structural primitives, not a finished castle recreation.

Create a fresh Aincrad world for v0.1.6. On non-Peaceful difficulty near the central courtyard, `/sao challenge` starts the current frontier guardian. A real player-attributed death of the exact persisted UUID on the correct floor unlocks one additional floor; random mobs, duplicate deaths, abandoned encounters and environmental deaths cannot unlock. `/sao abandon` resets an encounter without victory. Stale guardians are discarded on tick. Use `/sao travel <number>` for unlocked floors. No physical gate or unique custom boss yet; no command directly grants victory. Singleplayer-first, no personal computer runner. Full modpack performance, detailed visuals and later NPC systems remain unverified.
