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

## v0.2.6 autonomous reconnaissance (verified initial slice)
Prepared, rested adventurers may independently choose a gate and a short outside survey, spending abstract supply points. Persistent outbound/survey/return phases with fatigue recovery, timeout-to-return, cooldown and observed-vs-abstract completion counts. Loaded actors attempt to navigate actual terrain through gates and only advance on arrival; absent actors progress abstractly. Nearby active expeditions can be projected outside city walls without loading new chunks. No combat, real loot, actual dungeon discovery, full wilderness route or boss unlock. This is a first short scouting slice, not a complete adventuring system. Ledger schema 2 loads schema 1 with an idle trip. Test ordinary routes, cooldown, duplicate epochs, no fake visible arrival and migration. Runtime outside movement and persistence must be separately verified.

Build 46083996434 passed 12 GameTests, 99 simulated starts/returns and 297 route waypoints. Controlled-start client run 46084074631 verified a physical east-gate crossing, survey and return (one fully observed expedition). Dedicated run 46083938242 verified 34 and 9 autonomous abstract completed trips, correct supply costs, no loaded upper chunks, no floor unlock and identical restart digest.

## v0.2.7 knowledge and autonomous direction choice (verified)
Per-adventurer three-mask journal (personal surveyed, physically observed, reported), stable gate directions, bounded storage, recorded only on successful return. Abstract surveys are not terrain observations. Friendly reciprocal encounters can exchange firsthand accounts; hearsay is not forwarded as firsthand. Route choice favors personally unvisited approaches, with risk/discovery preferences choosing known reports vs unknown routes. Existing schema 1/2 ledgers load without invented history. Preparation thresholds match scouting eligibility; no replenishment until the economy has a legitimate source of supplies. `/sao recon <floor>` reports aggregated knowledge; idle social NPC dialogue can mention their own account. This remains a four-gate reconnaissance prototype, not full floor discovery, combat or an economy. Verify journal invariants, NBT, actual two-floor autonomous knowledge exchange and persisted state before acceptance.

Build 46084883609 passed all 13 GameTests and 139,264 journal checks. Dedicated run 46084906239 verified 48 completed autonomous trips, 52 reported directions, 17 multi-direction scouts and identical saved state after restart. No upper chunks loaded or physical observation claims from abstract trips.

## v0.2.8 city architecture pass (not accepted visually)
New city_detail noise settings select richer chunk-local architecture using the original plot geometry. Legacy city noise settings retain v1 generation. Eight styles, actual oriented stair roofs, mixed masonry, timber/plaster, windows, chimneys, bed pairs, role-specific interiors, crenellated gate lintels, decorative plaza arch and planted corners. Vanilla blocks only, no new external mods or resource pack. Keep center and entrances clear, unchanged NPC destination/road/gate geometry, and bounded height; no existing block replacement. Tests verify all seven sampled capital layouts, roof cells, bed pairing and spawn/road clearance. Actual client screenshots and NPC/guardian regression tests still required. Future: organic districts, regional settlements, landmarks, detailed custom assets, service UI. Do not claim genuine sleeping or upper-story interiors from beds or tall facades.

Build 46085786974 passed 13 GameTests and geometry checks, but visual probe 46085805093 failed at the guardian entity lookup. Screenshots showed overly large, dark, sparsely furnished interiors. Do not treat the detailed city as accepted.

## v0.2.9 fixed template library (candidate)
User approved a library-first approach: use authored, frozen building layers, deterministic placement, validated geometry/orientations, actual Minecraft inspection before accepting any template. First eight candidate layouts, smaller footprints and explicit fixtures with light sources. Stable plot coordinates and city routes preserve previous adventurer systems. New generator keys select templates; old saves retain legacy/detail geometry. No new mandatory resource pack or third-party assets. Runtime test must exercise all eight actual builds, light, functional orientation, beds, paths and screenshots. See BUILDING_TEMPLATES.md. Expand toward 30–50 only after quality acceptance. No rotations in this slice; new rotations require their own validation. Stage 2 goals still include regional settlements, long-term adventurer goals and migration.

## v0.2.9 verification and visual rejection
Build 46086601262 passed 13 GameTests. Client run 46086635777 validated cottage and townhouse actual states, lighting and NPC paths but failed inn minimum block light (5, required 6). Screenshots show improved roof geometry but sparse interiors and excessively wide/empty lots. Dedicated run 46086753589 compared 67,112 actual states per floor across 8 styles, preserved exact hashes after restart and retained a test block edit. Library not yet accepted.

## v0.2.10 template v2 candidates
Keep all v1 assets and keys unchanged; new worlds use versioned v2. Add supported pendant lighting and conservative modeled block-light floor of 8; five inn sleeping alcoves, common seating and reception. Preserve all plot identities, service points and roads. Run full actual visual probe again at unchanged or stricter lighting threshold. Runtime save/reload must be retested for v2. Compact/organic city layout and further artistic detailing remain pending; no template accepted solely by math or build.

## v0.2.10 actual acceptance evidence
Build 46087116462 passed 13 GameTests, 135,360 layer cells over v1 and v2, all modeled v2 light checks. Runtime 46087150117 passed all eight floor-one exact block states, minimum actual block light 9–10, real entrance-to-destination path creation, NPC presence, real guardian kill and upper-floor travel. 19 actual screenshots archived. Dedicated 46087169590 preserved 134,224 real block states across two floors after restart plus a test block edit. Visual review: inn has functioning room divisions, roofs/windows coherent and interiors lit; other service rooms remain sparse and roofs repetitive. Structurally verified templates are suitable for layout prototyping; artistic approval and advanced rooms still pending.

## v0.2.11 compact district experiment (pending tests)
Versioned city_districts generator adds exact unchanged v2 cottage/townhouse infill in gaps between original primary plot columns, with deterministic open courts, narrow paving strips and side alleys. Preserve city envelope, main gates/plaza, all canonical plot coordinates and NPC destinations. Primary and extra footprints must not overlap or be clipped; cross streets remain clear and all existing waypoints and doors must connect to the central plaza. Extra homes are scenery until real lodging assignment exists; no inflated NPC counts. Reuse verified templates rather than expand the library prematurely. Actual city density and paths require client regression test. Keep all older generator keys unchanged.

## v0.2.11 structural verification
Build 46087584955 passed 13 GameTests, 316 primary plots + 266 infill across seven sampled capitals, 1,141,532 connected positions and 3,560 existing route waypoints. Starting city 184 buildings; floor 2 65. Runtime district acceptance tracked in run 46087660868.

## v0.2.12 district landmarks (candidate)
Four fixed public-space layouts reuse deliberate empty infill courts without modifying existing v2 buildings. Residential garden, market awnings, guild monument/lecterns, target yard. All fixtures have explicit states and supports; no simulated shop transactions or XP rewards. New city_landmarks generator keys; legacy city_districts keys unchanged. Structural acceptance checks all site boundaries, supported furniture, canopy support, connected walking positions and existing NPC routes. Actual Minecraft block-state, path and visual inspection still required. Region towns, long-term adventurer goals and migration remain Stage 2 work.

## v0.2.13 personal objectives and active district venues (candidate)
Persistent bounded goals (practice points, completed reconnaissance, friendly adventurer conversations) chosen by ambition, with deadlines, completion/expiry counters and cooldowns. Goal credit follows actual practice ledger updates, actual completed trips (abstract vs observed still tracked separately), and accepted reciprocal social encounters. No pretend quests or combat rewards. Landmark-world discretionary routines use public training yards, guild squares, market courts and residential gardens; existing worlds use previous schedules. Rest, health/supply/equipment needs and active scouting take precedence. Connect court routes via north entrance and existing horizontal roads. Goal schema 4 reads schemas 1–3 with empty goal state. Add checks for priority, goal credit/expiration, route endpoints and persistence. Real-world venue path and behavior testing pending. Migration and regional settlements remain pending.

## v0.2.14 upward migration (candidate)
Fix v0.2.13 venue probe failure: local approach routes no longer detour through the plaza; practice/contact goals defer new scouting. First conservative migration: absent actors only, one upward move after completed scouting and personal objective, healthy/rested and adequately supplied, appropriate ambition/courage; only discovered unlocked landmark destination, source floor retains >=12 adventurers, destination <=48. Residence and birthplace separately persisted in population schema 2; legacy schema 1 readable. Lifetime ledger remains under birth floor (no cross-file relocation of progression); current/archived local journals scoped in schema 5; legacy schemas readable. Preserve relationships and goals, assign an existing inn destination. No automatic discovery/unlock, new rewards or visible portal travel. GameTests cover transfer/save/load and journal isolation; dedicated test of autonomous relocation required. Regional settlements remain pending.

## v0.2.13–14 runtime acceptance
Dedicated 46088346505 (v0.2.13 initial) passed 72 adventurers over 58,000 ticks, 144 completed goals, 215 scouting returns, 1,541 sampled venue plans, exact restart state. Client 46088866375 after detour fix passed actual training-yard arrival in 181 ticks and physical practice credit. v0.2.14 build 46089003514 passed 15 GameTests and 44 migration candidate checks. Dedicated 46089099470 used a controlled eligibility fixture, proved locked floor rejection, then actual simulation transfer of one identity with source 47/destination 25, retained memories, practice and scoped journal; identical restart, no upper chunk loading.

## v0.2.15 regional geography (candidate)
Four deterministic hubs per floor: market town (8 buildings), three villages/refuges (4 buildings each). Chunk-aligned envelopes, distinct services, unchanged v2 templates, central connected lanes, no overlapping capital or edge. Versioned new regional density graph and optional biome-source setting keep earlier worlds unchanged. Regional safe zones separate from capital population detection; no synthetic residents or transactions yet. `/sao settlements` lists coordinates, not generated/discovered history. Validate layouts, actual generation/paths, terrain edge and safe zones in Minecraft; regional NPC services and travel remain pending.


## v0.2.16 regional edge candidate

v0.2.15 build [46089487855](https://github.com/anjo7778/sao-mod/actions/runs/46089487855) passed; client [46089542911](https://github.com/anjo7778/sao-mod/actions/runs/46089542911) failed a road/foundation assertion at (-1040,97,0), the west boundary of Ironbrook. The failure did not distinguish obstruction from ground loss. A two-chunk decoration-free biome buffer is a targeted candidate for neighboring vanilla feature spillover; not a confirmed diagnosis or a guarantee against modded features. New presets opt in via a serialized optional flag (legacy defaults false); noise settings, safe-zone bounds, authored templates and existing chunks are unchanged. Geometry checks cover exact buffer boundaries. Repeat the actual client test without weakening any road or template assertion. Regional services remain deferred until runtime verification.

## v0.2.16 runtime acceptance

[Build 46089893175](https://github.com/anjo7778/sao-mod/actions/runs/46089893175) and [client 46089919130](https://github.com/anjo7778/sao-mod/actions/runs/46089919130) passed. All eight actual settlements on Floors 1 and 2: 324,856 exact template states, 40 entrance paths, exact cobblestone road/air checks, safety and decoration buffer checks, 16 screenshots. This is structural/runtime acceptance, not new artistic approval; screenshots not inspected in this continuation.

## v0.2.17 regional resident candidate

Discover a separate roster on visiting an unlocked regional site: 8 market-town residents and 4 per village/refuge (20 per fully visited floor). One identity per actual building, independent ID namespace, unchanged capital IDs and capital-only adventure ledgers. Schema 3 reads schemas 1 and 2 with no invented regional discovery. Local mixed work/rest/social schedules, bounded geometry-aware routes and existing loaded-only projection budget. Local reciprocal memories reuse existing social rules but cross-site exchanges are refused. No real service inventory, paid transactions, offline resident activity, new adventurer rosters or intersettlement travel. Verify real regional NPC presence, movement, dialogue and save/reload before acceptance.
