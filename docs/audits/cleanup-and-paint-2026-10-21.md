# Cleanup and paint-cost pass — October 21, 2026

Status: development changes for unreleased `0.9.9-beta.2`. No public API was removed or changed.

## Faster painting

- **Shadows are drawn in one pass.** `UiCanvas.shadow` stacked up to fourteen antialiased
  rounded-rectangle fills. The rings share one colour and are offsets of one rectangle, so the
  stacked result is a single blend with alpha `1 − Π(1 − aᵢ·coverageᵢ)`, a function of the
  pixel's distance from that rectangle. It is now evaluated once per visible device pixel (only
  inside the current clip) and blended once. Rotated, sheared, unevenly scaled or non-SrcOver
  drawing keeps the original ring path. Against the ring reference, 120 size/scale/colour/opacity
  combinations differ by at most 4/255 per premultiplied channel (mean 0.06/255);
  `UiCanvasShadowTest` enforces that bound and exact clipping.
- **Caret and selection changes repaint only their editor.** Arrow keys, Home/End, Page keys,
  Ctrl/Cmd+A, clicking inside a field and drag-selecting used to repaint the whole surface.
  Painters cannot observe the caret or selection, so these now request a partial repaint of the
  editor (or only its virtual row). Value changes still repaint the page because painters may
  read the value elsewhere.
- **One red/blue swap.** The tooltip, virtual-row and dynamic-texture paths each carried an
  older hand-written ARGB↔ABGR conversion. All use `UiPixelTransfer.swapRedBlue` now.
- Fonts are cached by a record key instead of a concatenated string per text call.

Full-page CPU repaint (`./gradlew :pane:pagePaintBenchmark`, 854×480 layout at 2×, cards with
shadows, controls, text and a scroll view), interleaved runs on the same macOS ARM64 machine:

| | Median | p95 |
| --- | ---: | ---: |
| Before | 19.2–20.2 ms | 20.2–22.6 ms |
| After | 9.9–10.8 ms | 10.6–11.8 ms |

This is a CPU raster workload, not a frame-rate claim; retained screens only repaint on change.
The editor benchmark's twelve pixel and input-geometry fingerprints are unchanged.

## Removed or merged

- `UiContext`: five hand-copied 29-argument child constructors became one `derive` helper;
  `button` was a verbatim copy of `drawDefaultButton`; button radius/fill/text colour switches
  are shared with `iconButton`; unused `addInput` overloads and `clampIndex` removed.
- `UiCanvas`: clip and transform scopes share one implementation each; a single-use image
  overload removed.
- `UiScreen`: page, popup, toast and tooltip surfaces each repeated texture + image + pixel array
  allocation, upload and release code, and the tooltip kept its own duplicate sampler. They share
  a small `RetainedTexture` now.
- Unused `UiTextInputState.hasSelection`, `UiPopupLayout.ROW_HEIGHT`, unused demo icons/data,
  an unused demo card painter and a write-only HUD scale field.

## Fixes found on the way

- `ellipsize` could cut a surrogate pair or combining sequence in half; it now steps by grapheme.
- `trackedText` drew each UTF-16 unit separately (broken emoji/accents), and `trackedTextWidth`
  measured the string differently from how it was drawn, misaligning right-aligned labels. Both
  now use the same per-grapheme measurement.
- Moving keyboard focus between two virtual-list rows repainted only the new row, leaving the
  previous row's focus ring on screen. Both rows repaint now.
- `ellipsizeFormatted` re-measured the ellipsis for every code point.

## Verification

1,282 Java tests pass. On macOS, sixteen Minecraft client fixtures pass on the final code: API,
review, popup, animation, editing, resource, editor, dialog, adoption, idle/toast performance,
studio, design lab (including GPU surface readback), castle, wayfarer, discord and gmail.
Windows remains untested. A focus-change partial repaint was tried and rejected: painters do
react to focus outside the focused control (the showcase's search suggestions).

Not changed: the HUD's full-opacity layer was measured and is faster than painting directly,
because Java2D blends faster into its premultiplied scratch image.
