# Wayfarer — main menu

An original expedition menu with a live Minecraft player, a photographic lookout, large type and gold navigation. This is a UI example, not a playable Wayfarer game.

From the repository root, with Java 25 installed:

```sh
./gradlew :pane-demo:runExamples
```

On Windows use `gradlew.bat`. No world or Python installation is needed. Finish Minecraft's first-run accessibility screen if shown. Escape returns to Minecraft; H reopens the selected example.

Try selecting a route, changing equipment, opening the expedition briefing and adjusting the options. These actions change local example state only.

The source is `pane-demo/src/main/java/dev/pane/demo/WayfarerShowcase.java` and `WayfarerPlayerPreview.java`. It now uses `UiScaling` for coordinated layout/input/native preview sizing, `UiImageOptions` for filtered photo fitting, `UiPlayerPreview` for the avatar, and styled dialog/button APIs. No custom per-control scale multipliers remain.

The bundled background, skin and material provenance are described in [asset credits](../ASSET_CREDITS.md). See the [full example index](../README.md) and [live HUD](../wayfarer-hud/README.md).

Developer regression: `python3 examples/wayfarer/launch.py --test` builds and runs the isolated 28-check fixture. The normal `launch.py` and macOS `Launch.command` forward to Gradle.
