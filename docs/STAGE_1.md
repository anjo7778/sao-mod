# Stage 1 — A / B / C / R

Source status is distinct from verified test results. v0.1.2 baseline CI: successful compile, metadata checks, 40,020 simulation assertions, 6.25 million geometry samples, 2 required Minecraft GameTests. v0.1.3 main CI passed 3 GameTests and 200,000 address cases. Cloud visual probe verified first-floor spawn, rejection of locked Floor 2, and failed after synthetic unlock because the upper destination heightmap was queried before generation. v0.1.4 explicitly loads destination chunks; new runtime verification required.

| ID | Feature | Status |
|---|---|---|
| A1 | Automated builds and JAR checks | v0.1.2 CI verified |
| A2 | Pure Java tests | v0.1.2 verified; v0.1.3 addressing tests added |
| A3 | Headless Minecraft GameTests | 2 passed v0.1.2; new upper-floor density test added |
| A4 | Graphical client tests | v0.1.2 spawn verified; Test Bridge 0.1.0 cloud terrain, animation, benchmark and SAO terrain runs passed; separate v0.1.4 travel test pending |
| B1 | Selectable Aincrad preset | v0.1.2 and v0.1.3 client spawn verified; new castle settings pending |
| B2 | Circular floating terrain | v0.1.2/v0.1.3 density tests passed; v0.1.3 Test Bridge surveyed rim |
| B3 | Boundaries, ceiling, castle pillars | v0.1.4 open lattice ceiling, tapered support towers, outer ring implemented; new tests pending |
| B4 | Higher-floor terrain | v0.1.3 shared realm/density GameTest passed; plains placeholder biome |
| C1 | Saved world progression | v0.1.2 NBT round trip passed; full restart pending |
| C2 | Teleportation and safe returns | Locked destination rejection verified in v0.1.3; explicit chunk load and bounded arrival scan added in v0.1.4; travel round trip pending |
| C3 | Boss-gated unlocking | Saved policy, no live boss source yet |
| R1 | On-demand floor generation | Catalog verified; address/density prototype for upper realm |
| R2 | Chunk/dimension management | One shared realm; no per-floor dimensions; live chunk eviction tests pending |
| R3 | Benchmarks | Geometry and addressing tests; runtime CPU/memory pending |

## Upper realm architecture

Floor 1 remains the Overworld. All upper floors use one additional dimension, `sao:upper_floors`. Fixed 16,384-block region stride and square-spiral coordinates keep large empty gaps between islands. Layout version 1 supports 12,967,202 floor numbers (including Floor 1) within Minecraft's current world border. This is an addressing capacity, not a claim that millions of unique playable floors have been tested or authored. Normal Minecraft chunk loading applies; no force-loading all floors or allocation of a dimension for each floor. More advanced biome generation, individual floor lighting, climate and dungeon identities remain future work. A shared realm also needs explicit isolation of floor events and effects. Do not change addressing constants for existing saves without migration.

## Prototype instructions

Create a NEW world with Aincrad - Floor One (Prototype). The old v0.1.2 first-floor generator is preserved, but existing worlds might not gain the upper realm; do not attempt conversion on valued saves. Vanilla Nether and End remain. `/sao progress` and `/sao floor <number>` inspect state. `/sao travel <number>` allows only unlocked and supported floors from a SAO floor, searches central safe ground without overwriting blocks, and refuses unavailable destinations. A periodic server guard attempts to return a player who enters a locked upper region to Floor 1. This is an initial gameplay restriction, not security against administrators or arbitrary mod code. No live boss exists yet, so ordinary players start with only Floor 1 accessible; no debug victory command is added. Teleport cooldown, physical gates, robust emergency arrival, real save/restart and traversal tests are still pending. Upper-floor biomes are fixed plains while the terrain profiles vary. v0.1.4 new saves have open stone-brick roof ribs at y272–288, support towers on a 384-block grid and an outer ring; a central open courtyard prevents the roof obstructing spawn and travel. These are architectural primitives, not finished decorative builds. Old noise settings are unchanged. Full ceiling lighting, under-roof decoration, biome diversity, settlements, and visual inspection remain pending. Cloud-only CI, no personal-computer runner.

## Cloud probe
The isolated v0.1.4 graphical workflow checks the new preset, first-floor spawn, actual generated pillar and roof blocks, a castle viewpoint, locked travel rejection, synthetic victory, upper-floor arrival and return. These are test-world-only actions; no production unlock command is added.
