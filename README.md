# SAO Mod

A Sword Art Online inspired mod for **Minecraft Java Edition 1.21.1** using **NeoForge**.

Stage 1 introduces the experimental Aincrad world preset, circular floating terrain, saved progression and headless Minecraft GameTests, alongside the existing simulation primitives. See [Stage 1 status](docs/STAGE_1.md) and the [development roadmap](docs/ROADMAP.md). The preset is experimental. New v0.1.6 worlds include a castle skeleton with stone-brick ceiling ribs, supporting towers and daylight courtyards. Existing v0.1.3 terrain settings remain available to existing saves; create a fresh test world for the new architecture. New saves have an upper-floor realm and progression-checked prototype travel. A live guardian challenge now unlocks the next floor after a valid player kill. No autonomous NPC entities yet.

## Working through ChatGPT / Codex

1. Give ChatGPT / Codex access to this repository and describe the feature you want.
2. Have it implement the change, check the build, and save the code to GitHub.
3. GitHub Actions builds the mod whenever code is pushed. Pull requests also get a build check.
4. Open **Actions → Build SAO Mod**, select a successful run, and download the **sao-mod-minecraft-1.21.1-...** artifact at the bottom of the run page.
5. Extract the downloaded ZIP and place **sao-0.2.4.jar** (or the newer version) in the `mods` folder of a Minecraft **1.21.1 NeoForge** instance. Restart Minecraft to load it.

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
| Mod version | 0.2.4 |

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
