# PaneLib examples

A short collection of distinct interfaces. Every public launch uses Fabric Loom and a Java 25 toolchain; install Java 25 and run from this repository's root. First launch downloads Minecraft/Fabric dependencies. Windows uses `gradlew.bat` in place of `./gradlew`.

```sh
./gradlew :pane-demo:runExamples
```

The shelf opens at the title screen. H reopens it, N opens Gather, M opens Pane Mail and J toggles your selected example HUD. Selecting a HUD before entering a world arms it; Minecraft still owns creating/joining worlds. Finish the first-run accessibility screen if shown.

| Example | Direct command | What it demonstrates |
| --- | --- | --- |
| [Wayfarer menu](wayfarer/README.md) | `./gradlew :pane-demo:runWayfarer` | World-free player preview, photo fitting, coordinated scaling, styled dialogs |
| [Gather](discord-inbox/README.md) | `./gradlew :pane-demo:runGather` | Dense app layout, editable messages, scroll following, styled popup triggers |
| [Pane Mail](gmail-inbox/README.md) | `./gradlew :pane-demo:runMail` | Light inbox, selection, search and local compose flow |
| [Wayfarer HUD](wayfarer-hud/README.md) | `./gradlew :pane-demo:runWayfarerHud` | Textured gameplay UI, native equipment, scrolling compass, vanilla restoration |
| Vanilla HUD | `./gradlew :pane-demo:runVanillaHud` | Small copyable native text/item/vanilla texture recipe |

Each command has its own profile under `pane-demo/run-examples/`; no existing world or generated argument file is required. No public launch stages a screenshot, changes inventory, or sends messages. Example data is local and fictional.

## Start your own mod

[First screen](first-screen/README.md) is the independent starter: it compiles against the packaged PaneLib JAR, saves real settings and restores its parent screen. The showcase source is deliberately richer; start with the starter or `VanillaHudExample` when learning the API.

## Retired examples and tests

YouTube is archived outside the source set and no longer compiled or bound to a key. Briarkeep, Bramble and the old Atlas/design galleries are retired from the public lineup. Historical design material and narrow regression fixtures remain available; they are not featured examples. No library features were removed.

`tools/fixtures/` contains internal frozen-client regression launchers. These are separate from consumer launch commands. Run `./gradlew :pane-demo:runExamples -PexampleTest` for the new gallery and live HUD flow in a newly generated disposable world. Detailed evidence lives in `docs/audits/`.
