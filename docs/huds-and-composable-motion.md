# HUDs and composable motion

PaneLib now has a reusable input-free HUD renderer, scalar timelines, delayed entrances, affine
button groups, and revision-based drawing caches. These extend the existing `animate`,
`withTranslation`, `withLayer`, and `withOpacity` APIs.

## Build motion from tracks

Keep a stable document-space rectangle covering the **whole sweep**, including spring overshoot
and shadows. Declare tracks before entering a transformed group; a bounding box that follows the
current frame is not a sufficient repaint bound.

```java
UiRect sweep = new UiRect(30, 120, 640, 260);
float open = ui.animate("card-open", expanded ? 1 : 0, 440, UiEasing.SPRING, sweep);
float enter = ui.animateFrom("card-enter", 0, 1, 420, cardIndex * 75,
    UiEasing.SPRING, sweep);
float angle = ui.animate("card-angle", requestedAngle, 600, UiEasing.SPRING, sweep);

ui.withTransform(UiTransform.identity()
    .translate((1 - enter) * -24, 0)
    .scale(.94F + .06F * enter)
    .rotate(angle)
    .opacity(enter), centerX, centerY, moved -> {
        moved.canvas().roundRect(0xC0163D57, x, y, width, height, 14);
        moved.button("card-open", "Open", "open-card", x + 12, y + height - 38, width - 24, 26,
            UiContext.ButtonStyle.PRIMARY, UiContext.Shape.ROUNDED);
    });
```

`animateFrom` starts at the supplied initial value on first use, including its delay. Later target
changes retarget immediately from the current value, without replaying the initial delay. Ordinary
`animate` still adopts its first value without an entrance. Stable IDs identify independent tracks.

`UiTransform` supports translation, positive X/Y scale, rotation around a pivot, and per-operation
opacity. The fluent `scale` helper is uniform; its constructor accepts separate axes. Button hit
regions become exact transformed polygons, so an empty corner of the axis-aligned bounding box
cannot activate the rotated control. Keyboard focus stays with the stable control ID.

Editors, sliders, scroll viewports, and native item/entity slots do **not** support affine groups;
these registrations throw a clear exception. Keep them outside the group or use the existing
`UiContext.withTranslation`. Tooltip/menu anchors use transformed bounding rectangles. A drawing
group is not a modal input barrier. For true group opacity across overlapping primitives, combine
with `withLayer`; transform opacity is multiplied per operation.

## Author a finite sequence

```java
// Keep this immutable recipe in per-screen state or a static constant.
UiTimeline deploy = UiTimeline.starting(0)
    .to(1, 270, UiEasing.SPRING)
    .hold(130)
    .to(.25F, 260, UiEasing.EASE_IN_OUT)
    .hold(80)
    .to(0, 350, UiEasing.EASE_OUT);

float beat = ui.sequence("deploy-track", replayRevision, deploy, sweep);
// Reuse beat for related position, scale, rotation, tint, or other scalar properties.
```

Increment `replayRevision` on a user intent to replay. A replay starts its first segment at the
currently sampled value, rather than jumping to the recipe's original start. Sequences are finite
(up to 64 segments and 60 seconds); after settling they stop scheduling frames. `valueAt(ms)`
provides deterministic sampling for authoring tools and tests.

`SPRING` is a duration-bounded damped curve with restrained overshoot and an exact endpoint. It
preserves position when retargeted; it is not a velocity-preserving physical spring solver.
`setMotionScale(0)` skips ongoing/delayed motion to its final value, including sequences. Prefer
this explicit accessibility control over keeping decorative animation alive forever.

## Cache expensive drawing while moving it

Own one `UiLayer` per simultaneous cache in screen state, then draw it under the transform:

```java
moved.canvas().cachedLayer(labelCache, contentRevision,
    new UiRect(x, y, 320, 100), label -> {
        label.text("Field instrument", x + 16, y + 14, 14, 0xFFF0F9FF);
    });
moved.hit("instrument", "open", x, y, width, height);
```

The callback runs only when its immutable revision, physical size, or raster-quality bucket changes.
The resulting pixels can rotate/scale without rerasterizing fonts and shadow geometry. Opacity
and clipping of the surrounding canvas apply when the cache is drawn, not when it is populated.
Bounds size should stay stable. Register interactive targets every paint outside this drawing-only
cache. Never use a mutable object as a revision and expect mutations to invalidate it.

Call `clear()` when discarding a long-lived cache. `withLayer` remains an uncached group compositor;
use a separate `UiLayer` for each purpose. CPU bitmap transformation still has a cost; it is not a
GPU-only scene graph.

For cold starts, `UiCanvas.prewarmFonts()` performs CPU-only font warmup for SANS/MONO. A consumer
can run it on a startup worker while assets load. Pass registered custom family names after font
registration when needed. This moves initialization work earlier; it does not eliminate that work.

## Attach a gameplay HUD

```java
UiHud status = new UiHud(220, 72, UiTheme.dark(), ui -> {
    float hp = ui.animate("health", (float) ui.numberValue("health", 20), 320, UiEasing.EASE_OUT);
    ui.canvas().roundRect(0xB0081B2B, 1, 1, 218, 70, 12);
    ui.canvas().text("VITALS", 14, 12, 8, 0xFF64D4FF);
    ui.canvas().text(Integer.toString(Math.round(hp)), 14, 30, 18, 0xFFF0F9FF);
}).attach(Identifier.fromNamespaceAndPath("my_mod", "status"));

ClientTickEvents.END_CLIENT_TICK.register(client -> {
    if (client.player != null) status.setValue("health", (double) client.player.getHealth());
});
```

HUD painting reuses `UiContext` drawing, state reads, and motion. Controls can be painted, but no
HUD input handlers exist: use a separate `UiScreen` for editing or actions. The library installs
no key/mouse listeners and never calls `setScreen`. Host mods own data sampling, key bindings, and
configuration. Equal values do not invalidate the retained surface. Native item/entity slots
are not rendered by this HUD API; use canvas primitives/textures for HUD content.

Painters should only draw/read state and motion; attach or close widgets from lifecycle code,
not from inside a painting callback. Updates, attachment, and cleanup belong on the Minecraft client/render thread; enqueue worker
results onto the client thread yourself. IDs must be unique while attached. World changes clear
world values/animation state and release the surfaces even if an ordinary screen or F1 suppresses
HUD rendering. Refresh your data snapshot after a world/session change. `close()` removes the HUD
from the shared registry and permanently releases its resources; create a new object to reattach.
Client shutdown also releases remaining surfaces.

## Placement, visibility, and editing

```java
status.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.TOP_RIGHT, 18, 18, 1));
status.setVisible(false);       // finite exit, then release the texture when that exit is rendered
status.setVisible(true);        // recreate from current values
status.setMotionScale(0);       // explicit reduced motion
status.setShowOnScreens(true);  // optional, e.g. while your own HUD editor is open
```

Sizes/margins use logical OS points and keep their physical size across Minecraft GUI scale
changes. The viewport fitting logic shrinks oversized widgets and clamps anchors to the screen.
Anchor changes use a spring placement track without rerasterizing unchanged content. Visibility
uses a small retained group fade/offset. Ordinary screens hide HUDs by default; F1 inherits vanilla's
HUD condition. `showOnScreens` draws beneath screens, so the host editor must reserve clear preview
space and restore the setting when it closes. There is no automatic collision avoidance with other
mods' HUDs. The demo separates its two widgets and exposes anchor/scale toggles.

Textures are capped at 2x raster quality; one widget is limited to 1024 UI points on each axis.
Use small components rather than a fullscreen HUD texture. Snapshots expose paint/upload/allocation
counters, last and cumulative raster/upload CPU cost, and last rendered bounds. They are diagnostic
values, not GPU fence timings.

## Try Atlas

- `./gradlew :pane-demo:runClient -Pstudio` opens Atlas in a dedicated disposable field world.
- H opens the console; J toggles the HUD. Return to world resumes normal movement/look/attack.
- Motion Studio demonstrates interrupted card expansion, spring retargeting, a finite three-beat
  sequence, animated selection, and staggered controls. Examples opens the existing demos.
- Field HUD exposes visibility, anchors, scale, and reduced motion with a live editor preview.
- `-PstudioTest` runs the full automated world/input/lifecycle checks and quits.
- `-PstudioRecord` runs those checks while sampling actual client frames at 20 Hz.
- `-PstudioPreview` captures just the UI motion segment after clearing setup chat, then quits.
- `python3 tools/export-studio-previews.py` makes the review GIFs using existing Pillow.

The studio worlds (`pane_studio_scratch` and `pane_studio_autotest_20261016`) are reserved disposable
fixtures. The launcher prepares a small field area there; use another world for anything you want
to keep. No personal world is selected automatically.
