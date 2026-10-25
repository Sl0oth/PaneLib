# PaneLib examples

A short collection of distinct interfaces. Every public launch uses Fabric Loom and a Java 25 toolchain; install Java 25 and run from this repository's root. First launch downloads Minecraft/Fabric dependencies. Windows uses `gradlew.bat` in place of `./gradlew`.

```sh
./gradlew :pane-demo:runExamples
```

This is the one way in: a launcher at the title screen that opens every example. Esc returns to it
from each example, H reopens it at any time and J toggles the selected example HUD. Selecting a HUD
before entering a world arms it; Minecraft still owns creating/joining worlds. Finish the first-run
accessibility screen if shown.

| Example | What it demonstrates |
| --- | --- |
| PaneOS | A Mac-like desktop: liquid glass dock, widgets, Control Center, Spotlight and working windowed apps |
| [Wayfarer menu](wayfarer/README.md) | World-free player preview, photo fitting, coordinated scaling, styled dialogs |
| [Pane Mail](gmail-inbox/README.md) | Light inbox, selection, search and local compose flow |
| [Wayfarer HUD](wayfarer-hud/README.md) | Textured gameplay UI, native equipment, scrolling compass, vanilla restoration |
| Vanilla HUD | Small copyable native text/item/vanilla texture recipe |

The launcher uses its own profile under `pane-demo/run-examples/`; no existing world or generated
argument file is required. It never stages a screenshot, changes inventory, or sends messages.
Example data is local and fictional.

## Start your own mod

[First screen](first-screen/README.md) is the independent starter: it compiles against the packaged PaneLib JAR, saves real settings and restores its parent screen. The showcase source is deliberately richer; start with the starter or `VanillaHudExample` when learning the API.

## Retired examples and tests

On October 22 the demo module was trimmed to the examples above. Gather, Briarkeep, Bramble, the
Atlas/Studio benchmark, Design Lab, the style gallery and the small feature showcases were removed,
together with fixtures that only exercised them. No library features were removed.

`tools/fixtures/` contains internal frozen-client regression launchers. These are separate from consumer launch commands. Run `./gradlew :pane-demo:runExamples -PexampleTest` for the new gallery and live HUD flow in a newly generated disposable world.
