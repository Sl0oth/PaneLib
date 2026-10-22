# Layered UI and motion

PaneLib can leave the Minecraft panorama or world visible through its retained texture. Use
`UiScreenOptions.backdrop(...)`, then paint ARGB colors with partial alpha; do not fill the whole
screen with an opaque theme background unless the design calls for it. Keep text itself opaque
on a readable translucent backing. Native Minecraft content is a separate composition layer.

## Choose the right opacity operation

`canvas.withOpacity(alpha, painter)` multiplies the current alpha for **each drawing operation**.
Nested calls multiply, then restore their parent alpha. Overlapping shapes accumulate opacity.

`UiLayer` and `withLayer` provide **one group fade**, so overlapping shapes fade once:

```java
// Own this in the screen/document's painter state, not inside each paint callback.
UiLayer briefingLayer = new UiLayer();

// Inside the page painter; bounds include the entire slide and shadow.
UiRect bounds = new UiRect(x, y, width, height);
float open = ui.animate("briefing", expanded, 220, UiEasing.EASE_IN_OUT, bounds.inset(-18));
ui.withLayer(briefingLayer, open, bounds.inset(-18), group ->
    group.withTranslation((1 - open) * 14, 0, moved -> {
        moved.canvas().roundRect(0xB0182936, x, y, width, height, 14);
        moved.button("briefing-save", "Save", "save", x + 16, y + height - 44, 90, 28,
            UiContext.ButtonStyle.PRIMARY, UiContext.Shape.ROUNDED);
    }));
```

Keep one scratch layer per simultaneous group. Storage is reused at the same physical dimensions;
resize may replace it. `clear()` releases it if the document outlives its screen. The drawing
callback uses the same layout coordinates as the parent. The context version moves hit targets
and clips them to the declared bounds. A group at opacity zero contributes no input targets.
Virtual lists inside such a group rasterize into it; put a large independent scrolling list outside
that group to retain atlas composition. Native item/entity slots remain a separate layer and do
not inherit CPU group opacity.

## Motion and accessibility

The existing `ui.animate` clock uses elapsed time and retargets from its current interpolated value.
Reversing an opening fade/slide does not jump back to an endpoint. Always use stable animation IDs
and bounds that include both positions. Controls already use this clock for finite hover/press
motion. Use `UiContext.withTranslation`, rather than moving only the canvas, for interactive groups.

A host can change motion at runtime with `screen.setMotionScale(0)` and restore it with
`screen.setMotionScale(1)`. `screen.motionScale()` returns the effective multiplier; the immutable
document theme retains its original default. Zero snaps running custom animations on their next
paint, settles scroll targets, disables toast fades, and stops transition scheduling. This is an
explicit user/consumer preference; automatic OS preference detection is not implemented.

## Blur and cost

`backdropBlur` only sees pixels already painted into the **current CPU surface**. It cannot sample
Minecraft's world/panorama or separately composed virtual rows. Apply it before painting a panel,
then paint opaque text afterward. Calling it on an empty scratch layer will not blur the parent.

Blur only blurs retained decoration already painted behind a panel. It is off unless you call it
and only runs on invalidation, with no animation loop of its own. It still
allocates convolution images when visible; reserve it for small panels. Transparency works with
blur disabled. A gameplay-world blur would need a separate GPU integration and is not provided.

## Uploads and overlays

Ordinary partial paints now upload a full-width scanline band, including unchanged guard rows.
This avoids arbitrary-width subrectangle row packing while reducing vertical traffic. Dirty
rectangles spanning the screen height still require a full-height upload. This path has been
checked locally on macOS OpenGL-on-Metal; other backends need verification.

Toasts now use a cropped independent retained texture above page/native content. Fade, expiry,
and placement changes do not rerasterize page chrome or disable the virtual-list atlas. A stationary
toast schedules no frames. While a menu/dialog is open, toast visuals/input are suppressed and
continue expiring. Menus and dialogs still use the older page path and temporarily suspend atlas
composition. Tooltip overlays remain independent.

## Pointer, time and drag driven painting

- `ui.pointer(x, y, w, h)` returns where the pointer is over a region painted from it, in local
  coordinates through translations and affine groups. Moving inside it, or leaving it, repaints only
  that region. PaneOS magnifies its dock toward the cursor this way.
- `ui.refreshAfter(millis, bounds)` repaints `bounds` once after a delay. Call it on every paint that
  should keep refreshing; the earliest request wins. A clock asks for the next second or minute and
  an idle screen otherwise does nothing.
- `ui.dragArea(id, key, x, y, w, h, moves)` makes a title bar draggable. The travel accumulates in
  `key:x` and `key:y`; naming what the drag `moves` lets each step repaint only where the window was
  and where it goes instead of the page.
- `ui.withPresentation(transform, pivotX, pivotY, painter)` paints any group through a transform,
  editors, sliders and scroll viewports included, for an open, close or minimize effect. Nothing
  inside takes input while presented; paint the group normally once the transition settles.
- `screen.requestFocus(id)` focuses a control as soon as a paint registers it, such as a search
  field in a panel the same event opened or an editor still animating in.

PaneOS (`./gradlew :pane-demo:runExamples`, then PaneOS) opens windows with a spring presentation,
minimizes them into the dock, and drags them with bounded repaints.
`-Pisolated -PpaneOsTest` checks windows, drag cost, Control Center, Spotlight, the apps, dock
magnification and settled idle in a real client.
