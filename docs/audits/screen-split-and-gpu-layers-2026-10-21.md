# Screen split and GPU layers — October 21, 2026

Status: development changes for unreleased `0.9.9-beta.2`. No public API was removed; one method
was added (`UiContext.gpuLayer`) plus the `UiScreen.gpuLayerRasterCount()` diagnostic.

## `UiScreen` split

`UiScreen` handled input, focus, menus, dialogs, toasts, tooltips, virtual lists, uploads and
diagnostics in one 3,487-line class. It now keeps input, focus and the paint lifecycle (2,616 lines)
and delegates to package-private parts that each own their state:

| Class | Owns |
| --- | --- |
| `UiVirtualLists` | Row window, atlas, GPU scrolling and visible-row interaction geometry |
| `UiToastOverlay` | Toast queue, stacking, fades, dismiss targets and their texture |
| `UiTooltipOverlay` | Hover tooltip texture |
| `UiPopupPainter` | Menu and dialog drawing; open state and focus stay with the screen |
| `UiRetainedTexture` | CPU raster mirrored into one GPU texture (also used by `UiHud` now) |
| `UiGpuLayers` | GPU-composited layers, below |

Small rectangle helpers that were copied between files (`union`, `intersect`/`clipped`,
`intersects`, `contains`, `expandAndClamp`) became package-private `UiRect` methods, and the
style-fallback helper became the JDK's `Objects.requireNonNullElse` with empty default styles.
Every step was checked against the previous commit's full-page pixel fingerprint (identical) and
the unit suite. Two behaviour details were kept on purpose: the toast measure pass, and the order
in which virtual-row geometry is republished before slider/editor/animation pruning.

## GPU-composited layers

`ui.gpuLayer(id, bounds, revision, target, durationMillis, easing, painter)` paints a group once
into its own texture. A new target on a later paint starts an animation of translation, scale,
rotation and opacity from the transform on screen, and the GPU composites every frame of it: no
page repaint, raster or upload. Opacity uses Minecraft's own blit with a colour tint through a
one-method `@Invoker` mixin. Requests are accepted only from a screen's own page canvas under
context translations; HUDs, virtual-list rows, affine/group layers and manual canvas transforms
draw the group in place at its target, so the result is never wrong, only not accelerated.

Measured in Minecraft (`./gradlew :pane-demo:runClient -Pisolated -PgpuLayerTest`): a retarget costs
exactly one page paint, the following ~600 ms of animation costs zero page paints and zero content
rasters while frames keep presenting, and a revision change rasterizes once. Bounds must include
shadows and a transparent margin; an early fixture showed a shadow cut off at tight bounds.

## Verification

1,284 Java tests pass, including `UiGpuLayerTest` (which paints are accepted, and that the
fallback is pixel-identical to drawing at the target). Seventeen macOS Minecraft fixtures pass:
the new GPU layer fixture plus API, review, popup, animation, editing, resource, editor, dialog,
adoption, idle/toast performance, studio, design lab (with GPU readback), castle, wayfarer,
discord and gmail. Windows remains untested.

Limits: GPU layers are not interactive, do not blur or clip their own content beyond the layer
bounds and an enclosing scroll viewport, and draw beneath native item slots and popups.
