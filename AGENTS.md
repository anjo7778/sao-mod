# SAO Mod

This is a Sword Art Online inspired Minecraft Java Edition mod for Minecraft 1.21.1 and NeoForge. Use Java 21 and the checked-in Gradle wrapper.

- Keep Minecraft at 1.21.1 and use NeoForge APIs unless the user requests a migration.
- Use the `dev.anjo7778.sao` Java package and the `sao` mod namespace.
- Implement only requested gameplay features. The initial project is a loadable foundation.
- Keep client-only code out of common classes so dedicated servers can load the mod.
- Preserve the full Gradle wrapper, including `gradle-wrapper.jar`, and the GitHub Actions build.
- Run `./gradlew --no-daemon build` (or `gradlew.bat --no-daemon build` on Windows) for code or build changes and inspect the resulting JAR metadata.
- State clearly whether a change was compiled, tested in Minecraft, or left unverified. Never claim gameplay testing from a compilation alone.
- Keep generated caches, build directories, local paths, and credentials out of commits.
- Update the README when implemented behavior or build instructions change.
