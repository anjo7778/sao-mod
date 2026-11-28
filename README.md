# SAO Mod

A Sword Art Online inspired mod for **Minecraft Java Edition 1.21.1** using **NeoForge**.

Stage 1 introduces the experimental Aincrad world preset, circular floating terrain, saved progression and headless Minecraft GameTests, alongside the existing simulation primitives. See [Stage 1 status](docs/STAGE_1.md) and the [development roadmap](docs/ROADMAP.md). The preset is experimental. New v0.1.6 worlds include a castle skeleton with stone-brick ceiling ribs, supporting towers and daylight courtyards. Existing v0.1.3 terrain settings remain available to existing saves; create a fresh test world for the new architecture. New saves have an upper-floor realm and progression-checked prototype travel. A live guardian challenge now unlocks the next floor after a valid player kill. No autonomous NPC entities yet.

## Working through ChatGPT / Codex

1. Give ChatGPT / Codex access to this repository and describe the feature you want.
2. Have it implement the change, check the build, and save the code to GitHub.
3. GitHub Actions builds the mod whenever code is pushed. Pull requests also get a build check.
4. Open **Actions → Build SAO Mod**, select a successful run, and download the **sao-mod-minecraft-1.21.1-...** artifact at the bottom of the run page.
5. Extract the downloaded ZIP and place **sao-0.2.15.jar** (or the newer version) in the `mods` folder of a Minecraft **1.21.1 NeoForge** instance. Restart Minecraft to load it.

Java and Gradle are installed automatically on the GitHub runner. You do not need to install an IDE or run Gradle yourself to use this workflow. Minecraft still needs to be installed locally to play and test the mod. GitHub account Actions limits apply; the project does not require a paid service or API key.

You can also start a fresh build from **Actions → Build SAO Mod → Run workflow**. Download artifacts within 30 days, or run the build again to produce a new one. GitHub sign-in and repository access are required to download these artifacts.

## Project versions

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.268 (pinned build target) |
| Java | 21 |
| Gradle wrapper | 9.2.1 |
| ModDevGradle | 2.0.148 |
| Mod ID | `sao` |
| Java package | `dev.anjo7778.sao` |
| Mod version | 0.2.22 |

## Local builds (optional)

With a Java 21 JDK installed:

```powershell
.\gradlew.bat --no-daemon build
```

On Linux or macOS:

```sh
./gradlew --no-daemon build
```

The build runs `verifySimulation`, `verifyGeometry`, `verifyFloorLayout`, `verifyCastleGeometry`, `verifyFloorBiomes` and `verifyModJar` as part of `check`; it fails if a simulation assertion or packaging check fails. The finished JAR appears in `build/libs/`. The wrapper downloads the configured Gradle version automatically. The first build also downloads Minecraft and NeoForge dependencies and can take several minutes.

For headless runtime checks, run `./gradlew --no-daemon runGameTestServer`. For local development, the Gradle tasks `runClient` and `runServer` start development instances. Use a separate test world when trying new gameplay changes.

## Where to make changes

- `src/main/java/dev/anjo7778/sao/`: Java source.
- `src/main/templates/META-INF/neoforge.mods.toml`: mod metadata.
- `gradle.properties`: mod name, version, and dependency versions.
- `build.gradle`: NeoForge development and packaging setup.
- `.github/workflows/build.yml`: GitHub build and downloadable JAR artifact.

## Template credits

Based on the [official NeoForge 1.21.1 ModDevGradle MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle), commit `d69737d705926b200c23769f0a674ff6379badb4`. Its MIT notice is preserved in `TEMPLATE_LICENSE.txt`. The new mod's default license metadata is **All Rights Reserved**.

## Stage 1 gameplay loop

Create a new Aincrad world to use the new regional biome palette. Existing saves retain their earlier generator settings. At the center of your highest unlocked floor, use `/sao challenge` on non-Peaceful difficulty to fight its persistent Ravager-based guardian. A real player-attributed kill of that exact guardian unlocks one floor. Use `/sao travel 2` after clearing Floor 1; `/sao travel 1` returns. `/sao abandon` cancels an active encounter, without unlocking anything. No debug unlock command. Boss design and balance are preliminary; unique monsters, arenas, boss bars, NPC raids and physical gates are later work.

Travel now accepts only open-sky, solid-surface arrival points with a walkable neighborhood; the old downward scan could choose an enclosed cavity. If the courtyard is obstructed, travel refuses rather than overwriting the world.

## Stage 2 city prototype

New worlds only: a 608×608-block Town of Beginnings and 384×384 upper-floor capitals, generated per decorated chunk. The central plaza is the initial spawn and unlocked-floor arrival target. Four districts, walled gates, role-based building shells and basic furnishings are initial templates, not working merchants or residents. The city biome has no natural spawns; hostile spawn placement and player/enemy damage across city boundaries are restricted. Start guardian challenges outside the walls within 192 blocks of the perimeter. See [Stage 2](docs/STAGE_2.md). See the NPC prototype below. v0.1.6 saves retain their original noise settings and biome source. New test world recommended.

## NPC population foundation (experimental)

The first visit to each unlocked capital creates persistent resident/adventurer profiles only for that capital. `/sao population` reports saved profiles. These have deterministic identities, names, occupations, assigned locations, personalities, ambitions, needs and bounded social memories. v0.2.2 adds temporary physical projections, initial schedules and greetings; actual combat, commerce, migration and abstract progression remain pending. Identity generator version 1 must not change for already-discovered capitals.

## v0.2.2 NPC prototype

In a generated capital, wait a few seconds: up to 32 nearby profiles materialize as humanoid mobs using local vanilla player skins. They follow initial day/night schedules and bounded local navigation, and can greet a player who right-clicks with an empty hand. Greetings persist once per NPC/player/day and influence relationships. No arbitrary language model or network skin calls. The roster is authoritative; projections are unsaved, invulnerable and carry display-only items (no drops). Unloaded/distant NPCs are not yet simulated over time. No real job output, combat, sleep animation, trade or independent expeditions; no claim of final NPC AI. New build needs runtime verification.

## v0.2.3 local social life

Nearby idle or preparing NPCs can hold short reciprocal conversations, stop and look at each other, and remember friendly exchanges or occasional disagreements. Each pair can meet once per elapsed in-game day, each NPC at most three such encounters per day, and at most one pair per local interaction pass. Memories persist in the existing roster schema. Only nearby players hear the short exchange; no global chat spam. Adventurers with low supplies/equipment seek a market/forge rather than receiving free goods. No actual buying, crafting or repair yet. Initial player dialogue can also reflect stored rescue memories and ambitions. All features require runtime verification.

## v0.2.4 individual routines

Characters have stable early/regular/late/nocturnal/flexible rest preferences with daily variation. Some stay active at night, train, socialize or seek supplies; some rest by day. Unhealthy characters prioritize recovery. This is schedule diversity only, not actual fatigue, sleeping poses, nighttime quests or combat. The project is an autonomous MMORPG adventurer simulation, not a colony-production management mod. Service NPCs support adventurers.

## v0.2.5 autonomous adventurer routine simulation

Visited capitals are processed in bounded round-robin batches (up to eight adventurers per 20 server ticks). Unrepresented adventurers can abstractly practice, prepare, rest and socialize while the world ticks; loaded NPCs are sampled at their actual scheduled destinations instead. Persistent practice points/ranks, fatigue and routine counts are separate from future combat XP and real items. Fatigue can override individual schedules. `/sao adventurers <floor>` reports observed simulation progress even when elsewhere. No chunk force-loading, offline time progression, retroactive reward bursts, fake inventory, dungeon discoveries or boss victories. Unvisited capitals are not allocated. Actual runtime validation pending.

## v0.2.6 independent scouting

Prepared adventurers can start short reconnaissance trips outside a capital on their own. Four gate directions, outbound checkpoint, nearby survey point and return checkpoint. Visible actors use loaded terrain navigation and must arrive before advancing; absent actors can complete the same stages abstractly. Persistent trip counters distinguish fully observed from abstract/mixed returns. A trip costs eight abstract supply points, has fatigue/health early-return rules and cooldown; no real items, combat rewards, dungeon discovery, guilds or boss wins yet. Actor projections remain temporary and invulnerable. `/sao adventurers <floor>` includes scouting status. New runtime verification pending.

## v0.2.7 exploration knowledge

Adventurers retain compact journals of surveyed gate approaches, distinguishing actual observed routes, abstract personal trips and secondhand reports. Future trips prioritize previously unvisited directions; discovery-minded and cautious characters prefer unknown and reported approaches respectively. Friendly same-floor conversations exchange only firsthand accounts. Right-click social dialogue can mention actual journal entries; `/sao recon <floor>` summarizes known reports. Old saves preserve trip totals without invented location records. Low supplies/equipment now trigger preparation at the same thresholds as scouting eligibility. Resupply still requires the later economy system; no free goods. No actual mapped dungeon locations, monster combat or loot. Runtime verification pending.

## v0.2.8 detailed capital architecture

Fresh Aincrad worlds use a versioned detailed capital generator, with eight role-specific styles, mixed stone/plaster/timber facades, directional stair roofs, chimneys, windows, paired beds, improved furnishings, gate lintels, masonry detailing and plaza landscaping. Uses only vanilla blocks; no resource pack or external mod required. Existing v0.2.x capital saves retain their previous generator keys and geometry; no retroactive world edits. Plot positions, sizes, doors, city limits, spawn, routes and NPC identities remain stable. Bed blocks are furnished but autonomous sleep behavior is still pending. This is a modular visual improvement, not organic urban planning or finished handcrafted cities. Runtime visual validation pending.

## v0.2.9 fixed capital building templates

New Aincrad worlds select immutable, versioned, text-layer blueprints for eight distinct building types. Explicit block positions, supported lights, directional stairs, aligned bed parts, correctly oriented furnaces and authored furniture arrangements replace per-lot procedural building shells. Smaller footprints fit inside the existing city plots with unchanged NPC destination coordinates, a clear entry aisle and garden space. The city still assembles plots procedurally, without a resource pack. Older city_detail and city generator keys retain their original geometry. This is a first candidate library, not a fully visually approved set; see [Building template QA](docs/BUILDING_TEMPLATES.md). NPC bed use and advanced services remain pending.

## v0.2.10 lighting and inn layout revision

New worlds select fixed v2 blueprints. Existing v1 files and generator keys remain unchanged. The inn gains five separated sleeping alcoves, a common room and reception counter; all models receive supported lighting based on a conservative air-only propagation check. No resource pack required. Actual runtime visual approval remains pending; the city grid is still too spacious for the intended final capital.

## v0.2.11 compact capital districts

New Aincrad worlds use versioned district generation with additional cottage/townhouse infill, narrower contrasting road surfaces, side alleys and paved neighborhood courts. All reused building layers are unchanged v2 models. Existing primary plot identities, service destinations, gates, spawn and cross-street geometry are preserved; secondary homes do not yet add NPC residents. Original v1/v2 generator settings and files remain frozen. Geometric/route checks and actual Minecraft inspection required; this is a denser grid, not yet a fully organic city.

## v0.2.12 distinctive public courts

New Aincrad worlds use a versioned landmark layout with four fixed public-space designs in reserved empty neighborhood courts: northwest residential garden, southwest covered market stalls, northeast guild square with monument/lecterns, and southeast target-practice yard. The unchanged v2 building templates and primary NPC assignments remain. Courts have supported lights, flower planters, explicit furniture and awning supports, no fluid placement or generated entities. These are physical amenities, not yet functional merchant, guild or training services. Preserve all older generator keys. Structural connectivity and actual in-game checks are required before visual approval.

## v0.2.13 personal goals and venue activities

In landmark worlds adventurers choose persistent practice, scouting or social-contact objectives based on ambition and feasibility. Goals progress from real ledger changes or accepted reciprocal friendly conversations only; deadlines/cooldowns allow replanning. Practice and social goals bias discretionary schedules, not rest, emergency preparation or active trips. A subset visits training yards, guild squares, market courts or gardens. Venue-specific routes use existing connectors, and training actors look toward targets. Loaded activity credit requires actual matching plan and arrival; off-screen routines use the same decisions. No combat XP, fake rewards, trade or guild membership. Old worlds retain existing schedules; old ledgers load with empty goals. Tests and actual client verification required.

## v0.2.22 upward migration prototype
Eligible offscreen adventurers in landmark worlds can independently relocate once to the next already discovered and unlocked capital after completing a personal goal and scout trip. Bound source and destination population; retain stable identity, needs, memories, goal/practice history, and separate local scouting journals. The lifetime ledger stays keyed to birthplace; residence is saved in the population file. No new unlock, physical portal animation, or generated terrain from migration. This is a one-way initial slice, not unrestricted floor travel. Nearby training routes avoid plaza detours; active practice/social objectives defer new scouting starts. Runtime acceptance pending.

## v0.2.15 regional settlements

Fresh Aincrad worlds add four regional hubs to every supported floor: Crossroads Market, Hearthvale, Ironbrook and Wayfarer's Rest. Compact town and village layouts reuse unchanged v2 templates; chunk-aligned sites blend into the terrain and have public lanes, plaza lights and safe-zone protections. `/sao settlements` lists locations on the current floor. Capital identities, city layout and prior generator settings remain unchanged. No regional residents, live trading, long-range NPC pathfinding or teleport services yet. Runtime verification required.

## Experimental Stage 2 regional settlements

New v0.2.22 Aincrad worlds include four regional settlements per floor using the existing v2 template library. A 32-block decoration-free biome buffer is intended to protect the settlement edge from nearby vanilla world generation without extending the combat safe zone. Existing saves retain their serialized biome-source setting and are not regenerated. Regional runtime acceptance is pending; see [Stage 2](docs/STAGE_2.md).

Visited regional settlements now have separate save-backed residents, one per building, with local home/work/rest/social schedules and loaded-only humanoid projections. Merchants, innkeepers and smiths are local characters, not a working transaction economy yet. Regional population and runtime verification are pending.

Regional adventurer journeys are an experimental abstract-travel slice: absent adventurers may choose a regional destination, spend supply points, travel over elapsed simulation epochs and return. At the destination they can be projected as the same character using local paths, without forced chunks. `/sao journeys <floor>` reports activity. Actual wilderness walking and service transactions are not implemented.

NPC economy prototype: 100 starting Col per adventurer, finite shop stock, paid supply replenishment and equipment-condition repairs. NPC-only simulation units, not player items or inventory trading. Transactions require an open local merchant and verified arrival when visible; distant transactions can run without loaded chunks. `/sao economy <floor>` reports sales and funds. 

NPC guild economy extension: each discovered capital has a finite 1,200 Col treasury and wholesale reserves of 256 supply packs and 128 repair units. A newly completed reconnaissance earns 10 Col if the treasury can fund it; an exhausted shop can buy up to six reserve units with its own receipts when a buyer arrives. These are simulation units and a finite bootstrap reserve, not farmed or crafted Minecraft items. No retroactive rewards or free restocking. Income from combat, player transactions, renewable goods and actual quest contracts are still pending.

Physical melee hunt prototype: prepared projected swordsmen, knights and assassins on a real scouting survey may accept one nearby vanilla hostile monster as a guild quarry. The actual attributed death pays 10 Col from the same finite treasury, with durable condition wear and bounded nonlethal injury/retreat. No simulated kill, generated hunt mob, custom model, boss unlock, combat XP, permanent death or player quest UI yet. Runtime verification pending.

Class combat candidate: distinct melee intervals/reach, knight damage reduction, and archers using actual noncollectible vanilla arrows, charged against existing supply points. Arrows are restricted to their live quarry contract, expire after the encounter or owner unload, and cannot intentionally damage other actors. No magic, custom monsters or item-based ammunition yet. Runtime acceptance pending.
