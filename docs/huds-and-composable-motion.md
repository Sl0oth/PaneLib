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

### GPU-composited layers

`cachedLayer` still repaints the page on every animation frame; only the group's own content is
reused. When a group moves, scales, rotates or fades as a whole, `ui.gpuLayer` lets the GPU do it:

```java
boolean open = ui.booleanValue("details", false);
ui.gpuLayer("details-card", new UiRect(46, 56, 208, 120), detailsRevision,
    open ? new UiTransform(0, 0, 1, 1, 0, 1) : new UiTransform(0, 24, .96F, .96F, 0, 0),
    260, UiEasing.EASE_OUT, card -> {
        card.shadow(0x60000000, 10, 0, 3, 60, 70, 180, 90, 12);
        card.roundRect(0xFF5B9BE3, 60, 70, 180, 90, 12);
    });
```

The painter runs into the layer's own texture only when the revision, size or raster quality
changes. A different target on a later paint starts a new animation from the transform on screen,
and every frame of it is composited by the GPU with no page repaint or upload. Transforms pivot on
the centre of the bounds, so include shadows and a small transparent margin in them. Layers are
drawing-only (register controls outside them) and sit above the page and virtual lists, below
native item slots and popups. In HUDs, virtual-list rows, affine groups or under a manual canvas
transform the group is simply drawn in place at its target.

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
configuration. Equal values do not invalidate the retained surface. Treat complex HUD values as
immutable snapshots (records or copied lists); mutating an object in place and setting it again
can compare equal to the retained value. For a deliberately mutable external model, call
`status.invalidate()` after changing it on the client thread.

## Native items and previews in a HUD

Use the optional native callback with the same slot IDs and helpers as a screen:

```java
UiHud equipment = new UiHud(100, 56, UiTheme.dark(), ui -> {
    ui.canvas().fill(0xFF302A21, 0, 0, 100, 56);
    ui.nativeSlot("held", 12, 10, 36, 36);
}, overlay -> {
    var player = Minecraft.getInstance().player;
    if (player != null) overlay.item("held", player.getMainHandItem());
}).attach(Identifier.fromNamespaceAndPath("my_mod", "equipment"));
```

`overlay.entity(...)` and `overlay.playerPreview(...)` also work. PaneLib owns placement,
GUI/DPI conversion, the rectangular HUD clip, and visibility. No separate Fabric renderer or
snapshot-based transform is needed. Slots use HUD-local UI points; low-level `bounds()` retains
the screen API's physical-pixel contract. The callback's `pageId()` is `"hud"`.
Missing or fully clipped slots are skipped. Existing partially clipped-slot limitations remain.

The callback runs on visible render frames even when the canvas is retained, so inventory/entity
updates remain live without repainting the panel. Built-in item/entity/player helpers inherit the
HUD's fade. Native content is composited above the canvas; this is not an arbitrary offscreen
group compositor. The supported `text` helper also inherits fade. Raw `graphics()` operations do not automatically inherit it.
`nativeItemCount()`, `nativeEntityCount()` and `nativeTextCount()` report the latest frame's helper submissions and
clear when suppressed/closed. F1, ordinary screens, `showOnScreens`, hide/show, world changes and
close apply to both layers. HUD callbacks do not handle input; `pointerBlocked()` is true beneath
an open screen. Attach/close/update from lifecycle code, never during the native callback.

## Scroll a compass beneath a fixed marker

Sample the unrounded heading into a dedicated HUD value every client tick. Use `animateAngle`
to interpolate along the shortest angular path, including 359° → 0° → 1°:

```java
float heading = ui.animateAngle("bearing", (float) ui.numberValue("heading", 0),
    90, UiEasing.EASE_OUT);
// For each visible fixed 5-degree bearing, draw its tick/label at:
// centerX + (unwrappedBearing - heading) * pixelsPerDegree.
// Clip the moving strip, then draw the stationary center marker above it.
```

The return value is in [0, 360). Stable IDs retain position when retargeted; reduced motion
adopts the target immediately. The track stops repainting when settled. Use separate IDs for
angle/scalar/sequence tracks. Wayfarer's compass demonstrates the moving strip; changing only
the displayed number is not a scrolling compass.

## HUD lifecycle

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

All nine anchors are supported: the four corners, `CENTER`, `TOP_CENTER`, `BOTTOM_CENTER`,
`CENTER_LEFT`, and `CENTER_RIGHT`. Centered axes ignore their corresponding margin; edge-aligned
axes use an inset in logical points. Scaling uses the final fitted widget size, so a centered
compass or equipment bar stays centered at .5–2x placement scale without reading window width.

```java
compass.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.TOP_CENTER, 0, 18, 1));
equipment.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.BOTTOM_CENTER, 0, 18, 1));

// Optional responsive recipe. The callback receives logical point dimensions, not GUI pixels.
equipment.setPlacementPolicy(viewport -> new UiHudPlacement(
    UiHudPlacement.Anchor.BOTTOM_CENTER, 0, 18, viewport.width() < 800 ? .75F : 1));
```

`setPlacementPolicy` resolves on the next visible frame and caches the result until the logical
viewport dimensions change. Stable frames do not allocate a viewport or invoke the callback.
Reinstall the policy when captured settings change; `invalidate()` refreshes pixels, not placement
settings. Callbacks run on the client render thread, must return non-null placement, and should
not render or mutate HUDs. A failed callback propagates its error and does not replace the prior
placement/cache. `placement()` returns the most recently resolved value (the prior placement
before the first policy resolution). Calling `setPlacement(...)` removes the policy. Disabled,
fully hidden and closed widgets do not resolve policies; showing one resolves any pending change.
Position-only changes reuse retained pixels and native slots. Size changes may resize the raster within its quality budget.

For a multi-widget HUD, share an authored recipe between policies. Wayfarer's
`WayfarerHudLayout` uses corner/edge-center anchors at roomy sizes; compact layouts put the compass
above the two upper panels and the belt below the lower panels, then reduce a shared scale to fit.
It reserves a clear center crosshair and is designed for logical viewports of 640×440 and larger.
Smaller windows still get per-widget viewport clamping, but the recipe does not promise a readable,
collision-free seven-widget arrangement there. This is an example composition, not a general
layout solver or collision avoidance against other mods. Placement motion can be disabled with
`setMotionScale(0)` when transitions should move immediately.

HUD raster resolution follows effective displayed size, including placement scale and viewport fitting.
Defaults retain the previous 2x/2048×2048 ceiling. Opt into higher ceilings per widget with
`hud.setRenderSettings(UiRenderSettings.highQuality())`, or select `lowMemory()`. One widget is
limited to 1024 UI points on each axis. `hud.rasterQuality()` reports desired/actual scale and
pixel dimensions; `upscaled()` reports a deliberate budget limit. `hud.rasterBytes()` reports one
RGBA surface's bytes, not total CPU+GPU+scratch memory. Shrinking widgets releases oversized
buffers; unchanged position/GUI scale/quality settings do not force uploads. Higher ceilings
are explicit: no global default budget increase. Wayfarer opts into highQuality (3x ceiling).
Use small components rather than a fullscreen HUD texture. Snapshots expose paint/upload/allocation
counters, last and cumulative raster/upload CPU cost, and last rendered bounds. They are diagnostic
values, not GPU fence timings.

## Let players move HUDs

Mark each HUD players may move, then open `UiHudEditor`. PaneLib draws an outline round every
movable HUD; players drag them, and Arrow keys nudge the last one picked. Dropping a HUD picks the
nearest corner, edge or centre as its anchor, so it stays in place when the window changes size.
Done or Escape hands each moved HUD's new placement to your callback, for you to save:

```java
status.setEditable("Vitals", UiHudPlacement.topLeft(), placement -> config.save("vitals", placement));

// From a button or key binding:
Minecraft.getInstance().gui.setScreen(new UiHudEditor(parentScreen));
```

**Reset** in the editor puts every HUD back at the defaults you passed. `new UiHudEditor(parent,
theme)` matches the toolbar to your mod's colours. Nothing is saved for you: restore the placement
with `setPlacement` when your mod starts.

### Offsets, sizes and GUI pixels

- `new UiHudPlacement(anchor, marginX, marginY, scale, offsetX, offsetY)`, or `placement.offset(x, y)`,
  moves a HUD from where its anchor puts it, in either direction, such as 20 below the centre.
  Margins can't do that: a centred axis ignores its margin.
- `hud.setSize(width, height)` resizes a HUD, such as a label whose text grew.
- `hud.setScalesWithGui(true)` makes the size, margins and offsets Minecraft GUI pixels. The HUD
  then grows with the GUI Scale option like vanilla's own HUD, which suits mods porting HUD code
  written in GUI coordinates. Pixels stay sharp either way.

## Replace vanilla HUD elements safely

Declare the vanilla elements each widget replaces; loading PaneLib or attaching a normal HUD
does not hide anything. Use Fabric's public `VanillaHudElements` identifiers:

```java
equipment.replaceVanilla(VanillaHudElements.HOTBAR, VanillaHudElements.HELD_ITEM_TOOLTIP);
status.replaceVanilla(VanillaHudElements.HEALTH_BAR, VanillaHudElements.ARMOR_BAR);

equipment.setEnabled(false); // immediate off: release its surface and restore vanilla
equipment.setEnabled(true);  // resume from retained values and visible preference
equipment.setVisible(false); // animated hide: restore vanilla when the exit fade finishes
equipment.setVisible(true);
equipment.replaceVanilla(); // remove claims, keep drawing the widget
equipment.close();           // permanent: release claims, surface, values and registry ownership
```

`replaceVanilla(...)` replaces the whole declaration, rather than adding to it. Duplicate IDs
are harmless; invalid/null identifiers fail without changing the existing declaration. It can be
called before `attach`; unattached HUDs never suppress vanilla. `replacedVanillaElements()` returns
an immutable declaration. `enabled()` is false after close; a closed HUD cannot be reattached or
claim elements again. Keep lifecycle calls on the client thread outside rendering callbacks.

Each widget owns its claims. If multiple HUDs claim the same element, vanilla remains suppressed
while any eligible owner is presented. Hide/close one widget without releasing another widget's
claim. For a multi-widget toggle, call `setEnabled` on each widget from one client-thread action;
The Wayfarer HUD demonstrates this with J. Disable immediately cancels pending fades/motion;
it does not overwrite the widget's values or `visible` preference.

Ordinary screens suppress custom HUDs by default, so the original callbacks resume behind those
screens. `setShowOnScreens(true)` keeps the widget and its claims active there. F1 still uses
Minecraft/Fabric's normal visibility gates. Without a world/player, no claim is active. World
changes release retained surfaces/values/motion; enabled/visible preferences and declarations
survive, allowing an enabled replacement to resume on rejoin. Disable explicitly if it should
stay off across sessions. Close is permanent.

PaneLib installs one delegating wrapper for each claimed registry ID. When no eligible owner
claims it, the wrapper calls the original callback chain. Close removes owner references; it
does not reinstall an old callback over a later mod's registration. Earlier wrappers are part of
the suppressed/restored chain; later wrappers may draw their own content around ours. A mod that
discards the original callback or removes the registry element can bypass this integration;
PaneLib does not override that mod. These wrappers do not change Fabric's vanilla render gates.

## Vanilla visual style

HUDs are not restricted to PaneLib's dark/smooth examples. Use `minecraftTexture` or
`minecraftTextureRegion` with Minecraft's resource textures, square fills/borders, and native
item slots for a vanilla-looking composition. Resource-pack reloads refresh cached textures.
Minecraft texture copies currently use nearest-neighbor sampling within the canvas, while the
final retained HUD texture uses linear filtering; integer sizing helps avoid resampling blur.

Use `overlay.text(slotId, text)` for Minecraft's actual font renderer. Reserve the matching
`ui.nativeSlot(id, x, y, width, lineHeight)` in the retained painter. Plain strings and styled/
translated Components are supported; `UiMinecraftTextStyle` adds ARGB color, shadow, alignment,
and ellipsis/clip behavior. PaneLib owns coordinates, scaling, clipping and HUD fade. Resource-pack
font/language reloads invalidate measured lines automatically. Stable labels reuse a bounded cache.

There is still no complete built-in vanilla widget theme. `UiCanvas.text` and retained text editors
continue to use Java2D/custom TTF fonts; native labels are a separate supported renderer, with the
existing native-slot composition rules. See [native Minecraft text](native-minecraft-text.md) for
a copyable example, custom resource fonts, diagnostics, scope and the real-Minecraft test command.

## Try the supported examples

`./gradlew :pane-demo:runExamples` opens the showcase launcher; choose **Wayfarer HUD** (textured)
or **Vanilla HUD** (native font/item recipe), create or join a world, then use J to toggle it. The
launcher does not create a world, position the camera, modify inventory, or need a private save.
H reopens it. See the [example index](../examples/README.md).
