# Wayfarer — live expedition HUD

Worn metal, woven fabric, paper details and gold markings tie this HUD to the Wayfarer menu. Health, food, armor, coordinates, altitude, heading, level and equipment read the actual player. Quest/glider labels are fictional example content.

From the repository root, with Java 25 installed:

```sh
./gradlew :pane-demo:runExamples
```

On Windows use `gradlew.bat`. Create or join a world normally. J toggles the HUD and restores Minecraft's elements; H opens the example shelf, where you can switch to the vanilla HUD or remove the example HUD. No Python, existing save, camera staging, inventory changes or commands are required by this live path.

The recipe uses `UiHud`, native item slots, `replaceVanilla`, responsive placement policies, `UiRenderSettings.highQuality()`, and `animateAngle` for the scrolling compass. Each widget receives only the state it needs. `ExampleHudController` owns attachment, switching and cleanup; `WayfarerHudShowcase.tickLive` only samples state.

For a much smaller copyable recipe, see `VanillaHudExample.create()` and choose **Vanilla HUD**
in the launcher.

That supplementary panel uses vanilla textures, live native items and Minecraft's font through `overlay.text`. It does not replace health or inventory behavior. A complete vanilla widget theme is not part of this example.

## Developer verification

`./gradlew :pane-demo:runExamples -PexampleTest` creates its own disposable world from scratch and verifies live ownership, switching, toggling and inventory preservation.

Deterministic capture fixtures remain separate in `tools/fixtures/wayfarer-hud.py` and create a scratch world when their isolated profile has no save. Compatibility commands such as `python3 examples/wayfarer-hud/launch.py --restoration-test` build first, then run those fixtures. They are not the public launch path. `--test`, `--font-test`, `--quality-test`, `--placement-test` and `--efficiency-test` are separate invocations.

Materials: [source credits](materials/LICENSE-AND-SOURCE.md). Earlier capture history is archived under `examples/archive/notes/`. See [the example index](../README.md) and `docs/audits/` for evidence and limitations.
