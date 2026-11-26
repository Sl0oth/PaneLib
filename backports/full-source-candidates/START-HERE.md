# Use a standalone PaneLib backport

Choose the exact Minecraft version in `targets.json`. Its entry gives the PaneLib filename and SHA-256, required Java version, and tested Fabric API coordinate. These instructions apply to the full-source Fabric candidates for 1.16.3 through 26.1.2. Consult the accompanying validation matrix for release status.

## Build your first mod

Install Python 3 and a JDK 25 to run the Gradle wrapper. Install the game JDK listed in `targets.json` as well when it differs. Use a new project directory. For example, in the extracted starter kit:

```sh
python3 starter.py 1.20.1 "/path/to/the/downloaded/panelib.jar" ./my-settings
cd my-settings
./gradlew build
./gradlew productionClient
```

On Windows use `py -3` and `gradlew.bat`. The first Gradle invocation needs internet access for Fabric, Minecraft and Gradle dependencies. If Gradle cannot locate your JDK, supply its installation path with `-Porg.gradle.java.installations.paths=/path/to/jdk`. The project opens a settings screen at the title menu. Enter a name, toggle hints and click Save. Relaunch to read the saved settings. Edit `src/main/java/example/pane/FirstScreen.java` to make it your own. `./gradlew runClient` is the mapped development launch. `productionClient` runs the packaged consumer and the original candidate in a separate instance.

The generated folder is self-contained. Its Gradle build uses the exact file in `libs/`, checks the SHA-256 and depends on it without bundling it. Minecraft 1.x uses Mojang mappings, Loom's remapping plugin and `modImplementation files(paneJar)`. The 26.1 family uses the unobfuscated Loom plugin and `implementation files(paneJar)`. Copy these declarations, including the Minecraft, Loader and Fabric API versions, from the generated build when adding PaneLib to an existing project.

## Install and distribute

Install Fabric Loader 0.20.1 for the selected game, then place the separate PaneLib candidate, matching Fabric API and `build/libs/pane-consumer-1.0.0.jar` in that profile's `mods` directory. The older macOS ARM clients marked `native: true` were exercised using Loom’s macOS native support in the production launcher. The starter declares all runtime dependencies in `fabric.mod.json`. Distribute your own mod separately. The library alone adds no settings screen or key binding.

The upstream 26.2 starter pins its Minecraft, Fabric API and Loom configuration. Its published Maven coordinate resolves the 26.2 release. A backport file has its own version suffix. Selecting a backport file with the original starter's default version reproduces a version mismatch; its exact-version override also rejects the plus sign in backport versions. Its Gradle dependencies still select 26.2.

## Version-specific APIs

* Obtain the current screen with `Minecraft.getInstance().screen` on these ports. Open a PaneLib page with `UiScreen.open` or `UiScreen.of(...).open()`.
* Before 1.21.9 Minecraft screen input uses primitive mouse and keyboard arguments. From 1.21.9 the relevant screen methods accept Minecraft input event objects. On 1.21.9–1.21.11 the character event also takes modifiers. On 26.1.x it takes the code point alone. Portable control painters can usually avoid direct native event handling entirely.
* Native preedit is available on 26.1.x. All 1.x targets in this set, including 1.21.11, receive committed characters. Physical keyboard and OS input-method behavior requires testing on the intended desktop.
* Through 1.20.1 the world-independent avatar API accepts a skin texture and renders the classic model. Newer targets use their native player-skin type. Passing `null` selects the native default. The in-world entity slot accepts a live `LivingEntity`.
* Minecraft identifiers and vanilla HUD element names vary. Use the named types and available elements for the target's mappings. PaneLib exposes `UiHud.attach`, `setInteractive`, `replaceVanilla` and `close` for its HUD lifecycle. The validation matrix distinguishes each operation exercised.


## Optional rendering mods

Use the mod versions recorded for your exact game in the platform matrix distributed with these candidates. Sodium 0.5.13 on Minecraft 1.20.1 requires LWJGL 3.3.1. The macOS Loom production task substituted 3.3.2 and Sodium stopped startup. A normal production installation using Mojang’s 3.3.1 libraries passed with the same PaneLib file. On macOS 1.16.5, Iris 1.2.7 rendered the diagnostic pack. Iris 1.4.5 failed to compile its center-depth shader with and without PaneLib.

## Linux ARM64

The Linux x86-64 profiles used Mojang’s listed LWJGL versions. In the ARM64 VM, Minecraft 1.16.3 through 1.18.2 needed a matching set of LWJGL 3.2.3 Java libraries and ARM64 natives. The upstream 3.2.2 set lacks those natives.

On ARM64 Minecraft 26.1, 26.1.1 and 26.1.2, the bundled OpenAL library crashed during shutdown. A stock Fabric 26.1.2 control reproduced it. Graphical tests exited cleanly using a private OpenAL Soft 1.25.2 build with hardware audio backends disabled. Audible sound on that configuration is unverified. The platform report identifies the tested runtimes for each game.
