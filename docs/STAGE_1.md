# Stage 1 — A / B / C / R

Source status is distinct from verified test results. v0.1.2 baseline CI: successful compile, metadata checks, 40,020 simulation assertions, 6.25 million geometry samples, 2 required Minecraft GameTests. v0.1.3 changes require their own CI verification.

| ID | Feature | Status |
|---|---|---|
| A1 | Automated builds and JAR checks | v0.1.2 CI verified |
| A2 | Pure Java tests | v0.1.2 verified; v0.1.3 addressing tests added |
| A3 | Headless Minecraft GameTests | 2 passed v0.1.2; new upper-floor density test added |
| A4 | Graphical client tests | Separate experimental branch; first attempt timed out |
| B1 | Selectable Aincrad preset | Source registered; actual new-world spawn unverified |
| B2 | Circular floating terrain | Density test passed v0.1.2; chunk visuals unverified |
| B3 | Boundaries, ceiling, castle pillars | Terrain edge only |
| B4 | Higher-floor terrain | Shared upper realm implemented, pending CI; plains placeholder biome |
| C1 | Saved world progression | v0.1.2 NBT round trip passed; full restart pending |
| C2 | Teleportation and safe returns | Server command prototype, safe landing search, locked-floor guard; runtime travel unverified |
| C3 | Boss-gated unlocking | Saved policy, no live boss source yet |
| R1 | On-demand floor generation | Catalog verified; address/density prototype for upper realm |
| R2 | Chunk/dimension management | One shared realm; no per-floor dimensions; live chunk eviction tests pending |
| R3 | Benchmarks | Geometry and addressing tests; runtime CPU/memory pending |

## Upper realm architecture

Floor 1 remains the Overworld. All upper floors use one additional dimension, `sao:upper_floors`. Fixed 16,384-block region stride and square-spiral coordinates keep large empty gaps between islands. Layout version 1 supports 12,967,202 floor numbers (including Floor 1) within Minecraft's current world border. This is an addressing capacity, not a claim that millions of unique playable floors have been tested or authored. Normal Minecraft chunk loading applies; no force-loading all floors or allocation of a dimension for each floor. More advanced biome generation, individual floor lighting, climate and dungeon identities remain future work. A shared realm also needs explicit isolation of floor events and effects. Do not change addressing constants for existing saves without migration.

## Prototype instructions

Create a NEW world with Aincrad - Floor One (Prototype). The old v0.1.2 first-floor generator is preserved, but existing worlds might not gain the upper realm; do not attempt conversion on valued saves. Vanilla Nether and End remain. `/sao progress` and `/sao floor <number>` inspect state. `/sao travel <number>` allows only unlocked and supported floors from a SAO floor, searches central safe ground without overwriting blocks, and refuses unavailable destinations. A periodic server guard attempts to return a player who enters a locked upper region to Floor 1. This is an initial gameplay restriction, not security against administrators or arbitrary mod code. No live boss exists yet, so ordinary players start with only Floor 1 accessible; no debug victory command is added. Teleport cooldown, physical gates, robust emergency arrival, real save/restart and traversal tests are still pending. Upper-floor biomes are fixed plains while the terrain profiles vary. No castle roof, pillars or settlements yet. Cloud-only CI, no personal-computer runner.
