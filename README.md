# PaneLib

PaneLib is a 100% client-side Fabric UI library for building desktop-quality Minecraft screens entirely from Java code.

It is native and retained-mode. It does not start Chromium, load HTML, parse CSS, or require MCEF. A screen is rasterized only when something changes, uploaded to one reusable GPU texture, and then blitted while idle. Minecraft items and entities render in a separate native layer.

Current release candidate: `dev.pane:panelib:0.9.8`, targeting Minecraft 26.2, Fabric Loader 0.19.3, and Java 25.

## Try the field console

```sh
./gradlew :pane-demo:runClient -Pstudio
```

This opens Atlas in its reserved disposable world. H opens the console; J toggles the live HUD.
The motion studio demonstrates expanding cards, spring retargeting, staggered controls and finite
choreography. The HUD editor exposes live health/position instruments, anchors, scale and reduced
motion. [Motion preview](docs/previews/atlas-motion.gif) · [Live HUD preview](docs/previews/atlas-hud.gif).
See [HUDs and composable motion](docs/huds-and-composable-motion.md) for the reusable APIs.

## What is included

- Crisp arbitrary drawing: flat and per-corner rounded shapes, paths, lines, circles, multi-style text, clipping, transforms, opacity, shadows, glow, two-stop gradients, backdrop blur, grain, dots, stripes, grids, and scanlines.
- Bundled custom fonts without installing anything on the player's computer.
- Opt-in Minecraft component and legacy MOTD formatting without changing literal UI labels.
- Responsive layouts, grids, stacks, clipped smooth scrolling, and virtualized data-heavy lists.
- Buttons, fully restylable buttons, icon buttons, toggles, toggle buttons, sliders, checkboxes, radio options, segmented tabs, badges, removable tags, progress bars, text fields, textareas, password fields, dropdowns, menus, tooltips, dialogs, and toasts.
- Keyboard focus, Tab and Shift-Tab traversal, Enter/Space activation, clipboard editing, pointer caret placement, drag selection, and double-click selection.
- [Configurable input typography, bounded undo/redo, Unicode-safe editing, and custom modal focus scopes](docs/input-and-focus.md).
- Stable consumer action names. PaneLib's internal control actions are isolated and cannot collide with a mod's action strings.
- Typed `UiEvent` callbacks, while preserving the original three-string listener for source compatibility.
- Reusable input-free gameplay HUDs with retained data updates, logical-point anchoring, visibility transitions, and world cleanup.
- Scalar timelines, staggered entrances, spring curves, affine button groups with exact hit polygons, and revision-based drawing caches.
- Finite per-control animations through `ui.animate(...)`; they schedule frames only until complete and obey `UiTheme.motionScale()`.
- Native `ItemStack`, living entity/player, Minecraft texture, and player-skin rendering.
- Transparent title-panorama, in-world, parent-background, and full-parent screen composition.
- Raw key and target-aware click callbacks for arrow navigation, right-click, and true double-click.
- Parent-aware Escape/back navigation and thread-safe async completion invalidation.
- Balanced, high-quality, low-memory, and opt-in typography surface presets, with raster and scroll viewport diagnostics.

The low-level `UiCanvas` remains public, so these components do not lock a mod into PaneLib's default appearance. The same renderer already powers modern dark screens, editorial layouts, terminal UI, blueprint UI, a paper/field-notes theme, and a dense consumer-video layout.

## Repository layout

```text
pane/       dev.pane.ui      library and public API
            dev.pane.mcef    preserved prototype, excluded from every native artifact
pane-demo/  dev.pane.demo    showcase and real-client regression harness
docs/                        consumer documentation
```

The `pane-demo` module depends on the library module like an ordinary mod. Demo classes cannot leak into the library JAR; `verifyPaneArtifact` checks that boundary during `check`.

## Minimal screen

```java
var home = new UiPage("home", "Home", "H", 0xFF1BD96A, ui -> {
    UiRect body = ui.content().inset(24);
    ui.canvas().textMedium("My mod", body.x(), body.y(), 24, ui.theme().text());
    ui.button("save", "Save", "save-config", body.x(), body.y() + 44, 92, 32,
        UiContext.ButtonStyle.PRIMARY, UiContext.Shape.ROUNDED);
});

var document = UiDocument.builder("My mod")
    .brand("My mod")
    .theme(UiTheme.dark())
    .renderSettings(UiRenderSettings.balanced())
    .page(home)
    .build();

var screen = new UiScreen(document, NativeOverlayRenderer.NONE, event -> {
    if (event.type() == UiEventType.ACTION && event.key().equals("save-config")) {
        saveConfig();
    }
});

Minecraft.getInstance().gui.setScreen(screen);
```

Start with [the documentation website](https://sl0oth.github.io/PaneLib/) or [the Markdown guides](docs/index.md). The [Minecraft integration guide](docs/minecraft-integration.md)
covers panorama-backed screens, Escape, raw input, and asynchronous server data. The
[paper-craft recipe](docs/paper-craft.md) maps the PaneLib marketing design directly to native API calls.

## Performance model

- Static pages paint and upload once, then reuse one texture.
- Resizes, state changes, hover changes, or an explicit `invalidate()` trigger a repaint.
- Page, interaction, toast, and custom animations stop scheduling frames when their duration ends.
- The backing `BufferedImage`, pixel array, `DynamicTexture`, font cache, procedural tiles, and decoded Minecraft textures are reused.
- Uploads convert into the reusable native pixel buffer without modifying retained AWT colors.
- Ordinary bounded repaints upload full-width scanline bands with guard rows; virtual-row atlases use bounded uploads.
- Native items and entities do not invalidate the retained surface.
- Delayed tooltips use their own small retained surface, so virtual-list rows keep identical text
  rasterization before, during, and after hover.
- Virtual lists move as one fractional GPU plane with guarded atlas edges, avoiding independently
  rounded rows, uneven separator spacing, and cross-row filtering during scroll.
- Virtual rows recycle a bounded warm pool of images, pixel arrays, and upload buffers, so the
  steady scrolling path allocates no retained textures, atlases, or row storage.
- `UiRenderSettings` places an explicit memory/quality ceiling on the surface.
- `motionScale(0)` disables transition work.

The integration harness verifies that the paint count remains unchanged for five idle seconds.
See the [layered UI iteration](docs/layered-iteration-2026-10-16.md) and
[initial engineering audit](docs/engineering-audit-2026-10-16.md) for measured fixes,
remaining upload/overlay limits, and the `-Pperftest` client benchmark.

## Showcase and verification

```sh
./gradlew runClient
./gradlew runClient -Pstudio
./gradlew runClient -PstudioTest
./gradlew runClient -Pplay -Pgallery
./gradlew runClient -Pplay -Pyoutube
./gradlew runClient -Pplay -Ptextures
./gradlew runClient -PdesignLab
./gradlew runClient -PdesignTest
./gradlew runClient -Pautotest
./gradlew clean build
./gradlew :pane:publishToMavenLocal
```

Use `-PdesignLab` for three translucent, functional visual systems and runtime reduced motion.
See [layered UI and motion](docs/layered-ui-and-motion.md) for group fades, translated input, and upload constraints.

Press `H` for Atlas; its Examples page opens the core showcase, including Discover's 10,000-project virtualized directory. Press `G` for nine distinct visual systems, `Y` for the dense consumer layout, and `T` for Minecraft texture and skin clipping.

`-Pautotest` creates a disposable singleplayer world, exercises controls, opens every core and gallery screen, captures screenshots, resizes the window four times, verifies live item/player rendering paths, and checks zero idle repaints. It also continuously scrolls and reverses the 10,000-row directory, measures GPU-composited row-atlas uploads against full-surface work, clicks a moving row and a recycled far-away row, and verifies that page chrome remains cached. Screenshots are written under `pane-demo/run/screenshots`.

## MCEF

The old MCEF experiment is preserved under `dev.pane.mcef` for a possible optional add-on. It is excluded from the native source set, manifest, dependency graph, runtime, and JAR. PaneLib itself has no browser dependency.

## Release state

`0.9.8` builds on the clean-room-tested API with native backdrop/parent integration, raw input,
thread-safe row invalidation, GPU-composited virtual lists, and hardened resize handling. A separate Fabric mod built the
original public surface from the published documentation and Maven artifact without reading
PaneLib's implementation. Keep the
release candidate version until a public Maven host is selected and Windows/Linux GPU behavior is
profiled; the renderer and public API are usable now.

Known post-1.0 candidates are IME text composition, Minecraft narrator semantics, and broader Windows/Linux GPU profiling. They are not required to reproduce the supplied PaneLib paper marketing system.
