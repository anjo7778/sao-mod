# Stage 2 — Living world (D, F, G, H)

Stage 1 v0.1.6 remains the verified baseline. Experimental v0.2.0 is developed on stage2/settlements. No user testing required for development; cloud CI and real client verification before main integration.

| ID | Task | Source status |
|---|---|---|
| D1 | Town of Beginnings, central spawn | 608×608 plan, flat terrain, explicit plaza spawn, per-chunk building generation |
| D2 | Capital on each upper floor | 384×384 layout and theme material palette |
| D3 | Regional towns, villages, outposts | Pending |
| D4 | City safe zones | Spawn placement and entity-caused damage restrictions; guardian outside wall |
| D5 | Roads, gates, districts and buildings | Prototype grid, seven role-based lot types |
| D6 | Teleport plazas | Existing gated travel targets center; physical portal UI pending |
| D7 | Furnished and functional buildings | Basic interior fixtures; real jobs, service and detailed designs pending |
| F1–F4 | Identity, persistent needs, goals, decisions | v0.2.1 experimental roster branch adds save-backed immutable profiles, visited-capital discovery, needs and utility decisions; physical entities pending |
| G1–G3 | Relationships, memories and dialogue | Persisted bounded memories and relationship calculations; live social event dispatch and conversations pending |
| H1–H3 | Schedules, movement, daily activities | Pending |

Capital generation uses stable floor addressing, a city biome, a blended flat density region and one decoration feature writing only the requested chunk. No global placement task or per-floor dimension. The first city uses 608-block square and upper capitals 384 blocks; these sizes are provisional. No NPC populations, real shops, terrain-adapted organic street plans, canals or detailed handcrafted designs yet. Current feature can be affected by other mods' worldgen and neighboring feature spillover. Safety rules cover standard hostile spawn placement and entity-caused attacks, not arbitrary commands, all modded damage sources, environmental damage or total grief protection. Guardian encounters must occur outside safe zones. New worlds only for new generation. Verify actual spawn, adjacent chunks, interiors, city protection, guardian progression, travel and save/reload before acceptance.

## NPC roster prototype

100 residents + 48 adventurers for Floor 1; 36 + 24 for a typical upper capital. Counts are tuning defaults, not loaded entity counts. Generate only when the player visits an unlocked capital; no global preallocation or automatic distant AI tick. Versioned separate SavedData stores all visited profiles and mutable state snapshots. Test deterministic generation, identities, memory idempotency, needs and NBT round trip. No automatic conversation, real NPC rendering or actual job actions yet. Cloud test results required before merging.

## v0.2.0 settlement verification
Build 46077361153 passed compilation, layout checks (100 starting plots; 36 sampled upper plots; seven roles) and 6 GameTests. Actual graphical run 46077450200 passed city spawn, floor 1 and floor 2 generated plaza, roads, wall/gate, doorway, furnishing, hostile-spawn event rejection, player-attributed damage cancellation, guardian challenge outside safe zone, real death unlock and upper capital arrival. Screenshots show a plain repetitive building prototype; architectural detail and organic city plans still need work. Not yet verified on the new NPC branch.

## v0.2.1 roster verification
Run 46077737385 passed 60,088 generated NPC records across 1,000 capitals and all 7 Minecraft GameTests, including the population/memory NBT round trip.

## v0.2.2 entity slice (pending CI)
Custom humanoid entity and client renderer using built-in player textures; independent UUID/name binds to roster. Nearby-only projection budget (4 per reconciliation, 32 around an active player), loaded chunks only, no permanent entity save or forced chunks. Basic schedules and road waypoints, initial work/training item poses, right-click contextual greeting, persistent once-per-day relationship change, rest recovery. No real job output, sleep pose, equipment economy, death, independent guilds, parties, distant simulation or complete city navigation yet. No custom skins yet. User-visible results need actual client probe before acceptance.
