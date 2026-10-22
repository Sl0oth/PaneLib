# PaneLib

PaneLib is a 100% client-side Fabric UI library for building desktop-quality Minecraft screens entirely from Java code.

It is native and retained-mode. It does not start Chromium, load HTML, parse CSS, or require MCEF. A screen is rasterized only when something changes, uploaded to one reusable GPU texture, and then blitted while idle. Minecraft items and entities render in a separate native layer.

Latest beta: [`1.0.0-beta.1`](https://github.com/Sl0oth/PaneLib/releases/tag/v1.0.0-beta.1) on GitHub Releases, for Minecraft 26.2, Fabric Loader
0.19.3+, Fabric API and Java 25. It adds liquid glass, GPU-animated groups of controls, region-only
repaints and the PaneOS showcase. Developers can use `dev.pane:panelib:1.0.0-beta.1` from the
anonymous Maven repository at `https://sl0oth.github.io/PaneLib/maven/`, or download the runtime and
starter from the release page. See [installation](docs/installation.md). The submitted 0.9.8 snapshot is preserved. GitHub releases
do not update Modrinth.

## First screen in your own mod

Use the complete [standalone starter](examples/first-screen/README.md): a Fabric entrypoint,
five controls, an opening key, real Save/Cancel, and packaged-JAR validation. It compiles against
the runtime JAR without Maven Local or a dependency on this repository's source projects.

PaneLib paints custom controls with Java2D into retained GPU textures. Minecraft items/entities
remain native overlays. The library alone intentionally adds no screen, command, or key binding.

## Try the examples

```sh
./gradlew :pane-demo:runExamples
```

This opens the showcase launcher at the title screen, the one place to reach every example:
PaneOS (a whole Mac-like desktop with liquid glass and working apps), Wayfarer's game menu, and Pane Mail's inbox,
plus the Wayfarer and vanilla HUD recipes. Esc returns to the launcher from each; H reopens it and
J toggles the selected HUD after you create or join a world. It needs an installed Java 25, but no
Python launcher, private save, or Mac-specific Java path. See the [example index](examples/README.md).

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

The low-level `UiCanvas` remains public, so these components do not lock a mod into PaneLib's default appearance. The same renderer already powers modern dark screens, editorial layouts, terminal UI, blueprint UI, a paper/field-notes theme, a dense mail inbox, and a chat layout.

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

var screen = UiScreen.create(document, NativeOverlayRenderer.NONE, (owner, event) -> {
    if (event.type() == UiEventType.ACTION && event.key().equals("save-config")) {
        saveConfig();
    }
});

Minecraft.getInstance().gui.setScreen(screen);
```

Start with [the documentation website](https://sl0oth.github.io/PaneLib/) or [the Markdown guides](docs/index.md). The [Minecraft integration guide](docs/minecraft-integration.md)
covers panorama-backed screens, Escape, raw input, and asynchronous server data.
[Moving an existing mod to PaneLib](docs/migrating-to-panelib.md) explains what to keep, what to
replace, and where adoption still needs care. The
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
See the [performance guide](docs/performance.md) for measurement methods, remaining limits, and the `-Pperftest` client benchmark.

## Examples and verification

Open the curated example shelf with Java 25:

```sh
./gradlew :pane-demo:runExamples
```

PaneOS, Wayfarer and Pane Mail have distinct visual styles and all open from that one launcher. See the [example index](examples/README.md). It needs no private save, Python launcher or Mac-specific Java path. H opens the launcher; J toggles the selected HUD.

The [independent starter](examples/first-screen/README.md) compiles against the packaged library and is the smallest place to begin your own mod.

Developer verification is separate:

```sh
./gradlew :pane:build :pane-demo:build
./gradlew :pane-demo:runExamples -PexampleTest
./gradlew :pane-demo:runClient -Pisolated -PpaneOsTest
./gradlew :pane-demo:runClient -Pisolated -PgmailTest
```

The examples fixture creates a fresh disposable world. Focused `-Pisolated` fixtures (API, review,
popup, animation, editing, editor, dialog, GPU layer, PaneOS, idle/toast performance and more)
exercise the library in a real client; see [performance](docs/performance.md) and
[layered UI and motion](docs/layered-ui-and-motion.md).

## MCEF

The old MCEF experiment is preserved under `dev.pane.mcef` for a possible optional add-on. It is excluded from the native source set, manifest, dependency graph, runtime, and JAR. PaneLib itself has no browser dependency.

## Release state

Latest release: [`1.0.0-beta.1`](https://github.com/Sl0oth/PaneLib/releases/tag/v1.0.0-beta.1), a GitHub prerelease published from this source. The earlier
`0.9.9-beta.1` artifacts remain unchanged; both versions are served by Maven. Each release has its own identity;
the coordinate is also checked by the local packager against root `gradle.properties`.

Root `gradle.properties` drives the runtime, sources, Javadoc, starter descriptor and Maven
coordinates for the current source version. Published beta.1 and the preserved submitted 0.9.8
snapshot are never overwritten with newer bytes.

Prepare and verify local release artifacts with:

```sh
./gradlew prepareRelease
./gradlew verifyRelease
```

The output includes matching runtime/source/Javadoc artifacts, a standalone starter ZIP, a local
Maven repository ZIP, checksums, and a release manifest. This is a local packaging dry run;
it creates no public Maven endpoint, Git tag, GitHub release, or Modrinth upload. See
[installation and repository consumption](docs/installation.md) and the
[local release verification guide](docs/releasing.md).

A separate Fabric consumer validates the packaged library instead of depending on this repository's
implementation sources. Known adoption gaps include IME composition, narrator semantics, large
popup lists, and broader Windows/Linux GPU profiling. See [source status](docs/status.md) and the
[developer adoption audit](docs/audits/developer-adoption-2026-10-21.md). Modrinth approval has not been
independently verified.
