# SAO Mod

A Sword Art Online inspired mod for **Minecraft Java Edition 1.21.1** using **NeoForge**.

Stage 1 introduces the experimental Aincrad world preset, circular floating terrain, saved progression and two headless Minecraft GameTests, alongside the existing simulation primitives. See [Stage 1 status](docs/STAGE_1.md) and the [development roadmap](docs/ROADMAP.md). The preset is experimental; no NPC entities or travel to higher floors yet.

## Working through ChatGPT / Codex

1. Give ChatGPT / Codex access to this repository and describe the feature you want.
2. Have it implement the change, check the build, and save the code to GitHub.
3. GitHub Actions builds the mod whenever code is pushed. Pull requests also get a build check.
4. Open **Actions → Build SAO Mod**, select a successful run, and download the **sao-mod-minecraft-1.21.1-...** artifact at the bottom of the run page.
5. Extract the downloaded ZIP and place **sao-0.1.2.jar** (or the newer version) in the `mods` folder of a Minecraft **1.21.1 NeoForge** instance. Restart Minecraft to load it.

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
| Mod version | 0.1.2 |

## Local builds (optional)

With a Java 21 JDK installed:

```powershell
.\gradlew.bat --no-daemon build
```

On Linux or macOS:

```sh
./gradlew --no-daemon build
```

The build runs `verifySimulation`, `verifyGeometry` and `verifyModJar` as part of `check`; it fails if a simulation assertion or packaging check fails. The finished JAR appears in `build/libs/`. The wrapper downloads the configured Gradle version automatically. The first build also downloads Minecraft and NeoForge dependencies and can take several minutes.

For headless runtime checks, run `./gradlew --no-daemon runGameTestServer`. For local development, the Gradle tasks `runClient` and `runServer` start development instances. Use a separate test world when trying new gameplay changes.

## Where to make changes

- `src/main/java/dev/anjo7778/sao/`: Java source.
- `src/main/templates/META-INF/neoforge.mods.toml`: mod metadata.
- `gradle.properties`: mod name, version, and dependency versions.
- `build.gradle`: NeoForge development and packaging setup.
- `.github/workflows/build.yml`: GitHub build and downloadable JAR artifact.

## Template credits

Based on the [official NeoForge 1.21.1 ModDevGradle MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle), commit `d69737d705926b200c23769f0a674ff6379badb4`. Its MIT notice is preserved in `TEMPLATE_LICENSE.txt`. The new mod's default license metadata is **All Rights Reserved**.
