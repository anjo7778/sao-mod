# Stage 1 — A / B / C / R
Statuses denote source implementation, not a claim of successful runtime testing.

| ID | Feature | Source status |
|---|---|---|
| A1 | Automated builds and jar checks | Existing CI verified on v0.1.1 |
| A2 | Pure Java tests | Existing checks; radial geometry stress check added |
| A3 | Headless Minecraft GameTests | Two tests written; need CI results |
| A4 | Graphical client tests | Experimental cloud probe on separate branch; awaiting run |
| B1 | Selectable Aincrad preset | Prototype implemented |
| B2 | Circular floating terrain | Prototype implemented, 4000-block radius, noise-based height, void below |
| B3 | Boundaries, ceiling, castle pillars | Partial: terrain edge only |
| B4 | Higher-floor terrain | Catalog only; not generated |
| C1 | Saved world progression | Overworld SavedData, NBT round-trip test; full restart not yet tested |
| C2 | Teleportation and safe returns | Planned |
| C3 | Boss-gated unlocking | Policy + saved data API; no live boss source yet |
| R1 | On-demand floor generation | Catalog calculates descriptors on demand |
| R2 | Chunk/dimension management | Uses normal Minecraft chunk loading for first floor; further architecture pending |
| R3 | Benchmarks | Pure Java geometry sample loop; runtime measurements pending |

## Prototype

In Create New World choose Aincrad - Floor One (Prototype). Do not open an existing save to change its generation. The preset replaces only the Overworld; standard world presets remain available. Nether/End remain vanilla. Fixed plains, stone slab, grass and dirt, seeded surface noise. The void is dangerous. No roof, pillars, waterways, custom towns or travel yet. Vanilla feature/structure placement around the edge, spawn safety, and terrain appearance must be tested before claiming playable quality. `/sao progress` and `/sao floor <number>` only inspect progression and catalog descriptions, not teleport or grant victories. The current API accepts server-authoritative boss outcomes once the boss system exists. No external dependencies yet. No self-hosted runner.

## A4 visual probe
The isolated GitHub-hosted workflow uses Xvfb and software OpenGL to start an actual Minecraft client, create a deterministic creative test world using the Aincrad preset, confirm its generator and settled spawn, and capture a screenshot. It fails if the world never becomes ready. Normal clients never activate the probe; its dedicated run directory is ignored. The workflow is not a substitute for visual inspection, shader testing, or real GPU benchmarks. It is not a release gate while experimental.
