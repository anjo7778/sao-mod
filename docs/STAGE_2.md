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

## v0.2.2 entity slice — verified initial client behavior
Custom humanoid entity and client renderer using built-in player textures; independent UUID/name binds to roster. Nearby-only projection budget (4 per reconciliation, 32 around an active player), loaded chunks only, no permanent entity save or forced chunks. Basic schedules and road waypoints, initial work/training item poses, right-click contextual greeting, persistent once-per-day relationship change, rest recovery. No real job output, sleep pose, equipment economy, death, independent guilds, parties, distant simulation or complete city navigation yet. No custom skins yet. User-visible results need actual client probe before acceptance.

Build 46079042677 passed all eight GameTests and 8,462 schedule waypoints. Client run 46079113692 verified 32 visible NPCs, 148 profiles, actual player greeting memory, 12 daytime movements, 32 rest activity transitions, 29 movements after schedule change and preserved profile after leaving. Screenshot confirms player-style rendering; buildings and clothes still provisional.

## v0.2.3 social slice (verified)
- Reciprocal NPC-to-NPC conversations in loaded city space, physical pause/look, same-floor checks, line of sight, personality and trust, local chat only. Three contacts per actor/day, one per pair/day, at most 64 eligible pair checks and one exchange per five-second pass. Uses elapsed game time for NPC social days. Atomically records both memories, bounded to existing 128-event window, with NBT persistence.
- Friendly encounters and disagreements affect trust/friendship. Topic depends on job/ambition, not fake world events. Player dialogue can reference stored rescues and ambitions. Low-supply/poor-equipment adventurers seek real market/forge plots; no free items or trade implementation.
- Still no actual beds, meals, inventory transactions, off-screen routine simulation, unique skins or finished architecture. Client run 46082483181 passed actual autonomous reciprocal conversation, saved social state replay rejection, greetings, movement and schedule transitions.

## Scope decision and v0.2.4 flexible routines

The user agreed that the project must feel like a singleplayer MMORPG, not a colony simulator. Keep generated cities, characters, social memories and relationships. Prioritize autonomous adventurer goals, progression, independent expeditions, gear, companions, quests and competition. Simple service NPCs support the player and other adventurers; do not expand into complex worker production chains or colony management.

Replace universal nighttime rest with stable early, regular, late, nocturnal and flexible preferences derived from existing NPC identities. Rest windows vary by day; unhealthy NPCs prioritize recovery. Awake adventurers can train, socialize or seek needed equipment/supplies at night. Some innkeepers and healers can operate at night. No new identity fields or save migration. Rest means a routine/destination, not a verified sleep pose or fatigue model; outdoor adventuring, emergency exceptions, meals, commerce, actual beds, persistent fatigue and off-screen progression remain future slices. New tests require both awake and resting characters at midnight, daytime rest, deterministic schedules and existing need/route invariants. New build and client results pending.

## v0.2.4 acceptance
Build 46083004230 passed ten GameTests and mixed midnight routine checks; client 46083039202 verified 26 resting, 6 awake and 24 moved after schedule change. Actual reciprocal social interactions and persistence were also verified.

## v0.2.5 independent-world routine slice (pending verification)
Visited, unlocked capitals only; round-robin up to eight adventurers per 20 ticks, one sample per 600 elapsed ticks per actor, skipping backlog instead of granting retroactive rewards. Ledger persists practice rank, fatigue, preparation/rest counts, update count and last activity; fatigue can cause recovery before training. Unrepresented adventurers abstractly follow routines and can converse with nearby-in-plan unrepresented peers via existing reciprocal, daily-budgeted memory rules. Visible characters use actual destination/activity checks for sampled credit. No chunk loads from simulator, no offline clock, no automatic floor unlocking, combat level, real loot, quest, dungeon or guild outcomes. `/sao adventurers <floor>` displays results. Test bounded updates, duplicate epochs and reload, plus real client away-from-city behavior. Full RPG progression and independent expeditions remain separate.

## v0.2.5 client verification
Client 46083423115 passed actual off-screen progression: 84 abstract updates and 52 practice points after the player left, without nearby projections, and ledger NBT round trip. Main build 46083386713 passed 11 GameTests and 578,880 pure routine updates. Dedicated server run 46083513546 passed independent progression of 48 and 24 adventurers on two capitals with zero connected players, no loaded upper chunks, and an identical restart snapshot (144/72 updates, 56/12 practice, 74/36 memories).

## v0.2.6 autonomous reconnaissance (pending verification)
Prepared, rested adventurers may independently choose a gate and a short outside survey, spending abstract supply points. Persistent outbound/survey/return phases with fatigue recovery, timeout-to-return, cooldown and observed-vs-abstract completion counts. Loaded actors attempt to navigate actual terrain through gates and only advance on arrival; absent actors progress abstractly. Nearby active expeditions can be projected outside city walls without loading new chunks. No combat, real loot, actual dungeon discovery, full wilderness route or boss unlock. This is a first short scouting slice, not a complete adventuring system. Ledger schema 2 loads schema 1 with an idle trip. Test ordinary routes, cooldown, duplicate epochs, no fake visible arrival and migration. Runtime outside movement and persistence must be separately verified.
