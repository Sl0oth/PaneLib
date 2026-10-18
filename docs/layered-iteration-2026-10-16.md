# PaneLib layered UI iteration — 2026-10-16

The subsequent [Atlas/HUD iteration](atlas-iteration-2026-10-16.md) adds composable motion and
a persistent HUD API; the measurements below record this earlier pass.

This is a completed, usable iteration of the library and three functional visual references.
It does not certify the library as finished or uniformly smooth on every machine. The current
focus is PaneLib. Peripheral work is deferred and that project was not modified. Claude was not
dispatched; the visuals here were implemented directly with original code-drawn geometry.

## What changed

- Ordinary partial repaints upload full-width scanline bands, plus unchanged guard rows, instead
  of the complete texture. This avoids arbitrary-width subrectangle row packing. Hover resolution
  after scrolling also requests a bounded repaint when bounds are known.
- Toasts have a cropped independent retained texture. Their fades, expiry, and placement no longer
  repaint the page or disable virtual-list atlas composition. Separate hit regions preserve
  dismissal and keyboard traversal. Expired/dismissed targets resolve hover against the page again.
- `UiLayer` and `UiCanvas`/`UiContext.withLayer` provide reusable bounded group opacity, with
  clipping, translated input targets, and no targets at opacity zero. Nested `withOpacity` now
  multiplies its parent opacity; it remains a per-drawing-operation scope.
- Existing time-based animation is reused rather than duplicated. Retargeting preserves the
  current interpolated value, and reduced motion can interrupt an animation already in progress.
  `UiScreen.setMotionScale` adds runtime control for animations, scrolling, and toast fades.
- Three new designs share route selection, scrolling, briefing, ready state, toasts, and reduced
  motion. Their structures differ: Trailcraft inventory/journal, Skybound open hero/floating cards,
  and Blackline command rail/compact rows. Transparent surfaces leave the native panorama visible.
- Optional retained-panel blur is off by default. Text is painted afterward and stays crisp.

## Before/after evidence

All values below are local direct-dev-client measurements on macOS OpenGL-on-Metal / Minecraft
26.2 / Java 25. Retained surface: 1,640,640 pixels. These are individual samples, not averages across
machines. Upload timings are CPU submission, not GPU fence timings.

The matching Discover benchmark compares the end of the previous engineering pass with this pass:

| Three-second phase | Previous pass | This pass |
| --- | ---: | ---: |
| Stationary toast page repaints / uploads | 0 / 0 | 0 / 0 |
| Toast fade/expiry page repaints | 13 | 0 |
| Toast fade/expiry uploaded pixels | 21,328,320 main pixels | 1,129,304 toast pixels; 0 main pixels |
| Toast fade/expiry extraction p95 | 8.873 ms | 2.448 ms |
| Toast fade/expiry extraction p99 | 10.864 ms | 3.064 ms |
| Post-toast idle page repaints | 0 | 0 |
| Forced full repaint extraction p95 | 9.828 ms | 10.044 ms |

The fade/expiry sample transferred **94.7% fewer pixels**. The stationary-toast optimization from
the first pass remains intact. Full-page redraw cost is essentially unchanged; this iteration
removes unnecessary page work rather than claiming to speed up every painter.

In the existing ordinary-scroll harness, the last upload changed from 1,640,640 pixels to
1,244,152 pixels (24.2% less). This is one sample's last dirty region, not a promised reduction for
all scrolling. A full-height dirty region still uploads the full texture.

New designs, measured while scrolling under stationary toasts:

| Design | Page repaints | New row pixels | New toast pixels | Extraction p95 / p99 | Frame interval p95 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Trailcraft | 0 | 347,776 | 0 | 0.558 / 1.875 ms | 19.616 ms |
| Skybound | 0 | 539,091 | 0 | 0.749 / 0.864 ms | 17.763 ms |
| Blackline | 0 | 645,624 | 0 | 1.375 / 1.855 ms | 20.262 ms |

The frame intervals include Minecraft's native panorama, refresh scheduling, and other client
work. They do not establish a universal FPS improvement or human-perceived smoothness.

Optional Soft glass's latest main raster samples were 3.197 / 3.914 / 4.119 ms respectively,
with upload submissions around 0.19–0.20 ms. These are panel-repaint samples, not sustained blur
benchmarks or a claim that blur is free. Blur sees retained UI decoration, not the Minecraft world.

## Verification

- `./gradlew build`: both modules and artifact-boundary verification passed; **41 tests**, zero
  failures/errors/skips. New unit coverage checks nested alpha, group overlap alpha, reuse, clip
  preservation, translated/hidden input targets, animation reversal, and mid-animation reduced motion.
- Full disposable-world harness: **49 PASS lines**, no FAIL/INCONCLUSIVE results, including ordinary
  scrolling, focus, clipboard editing, menus/dialogs, independent toast placement/dismissal/expiry,
  async invalidation, virtual rows, four resizes, and zero idle repaints over five seconds.
- Final Design Lab harness: **52 PASS lines**, no failures. It checks actual route actions,
  keyboard focus, reversed fade/slide targets, a button clicked while moving, scrolling with
  toasts, dismissal, runtime reduced motion, hidden input, resize, optional blur, and settled idle.
- GPU readback compared **6,562,560 bytes** of the main texture with its native backing after
  ordinary scrolling, and separately in all three designs after blur/resize. Every comparison
  returned **zero differences**. This validates the local upload path, including retained pixels
  around band boundaries; it is not a Windows/Linux driver certification.
- The final matching performance harness reports `complete PASS`.
- Wide, compact, overlay, and glass screenshots were captured. Wide styles, compact Skybound,
  and Trailcraft glass were visually inspected; other captures are available for further review.

The 49-check full harness ran before the final narrow toast-hover/focus cleanup; the subsequent
52-check Design Lab run, performance run, and build passed with that cleanup. Do not treat the
older broad run as evidence for code added afterward.

Raw logs and pre-edit source copies are in `build/layered-baseline/` (ignored local artifacts):
`build-verified.log`, `regression-gpu-verified.log`, `design-final.log`, `performance-final.log`.
The first failed Design Lab run exposed startup timing in the harness; it now waits for a real
first paint before checking targets. Its final run completed successfully.

## View and run

Screenshots under `pane-demo/run/screenshots/`:

- `pane-design-voxel-wide.png`, `pane-design-sky-wide.png`, `pane-design-tactical-wide.png`
- Matching `-compact.png`, `-overlay.png`, and `-glass.png` variants.

```sh
./gradlew :pane-demo:runClient -PdesignLab
./gradlew :pane-demo:runClient -PdesignTest
./gradlew :pane-demo:runClient -Pperftest
```

`-PdesignLab` is interactive; use 1/2/3 or the header buttons. Tests open no world for the new
Design Lab/performance runs. The broad regression run reused only the disposable world created
in the first pass. All clients were launched directly through Gradle and shut down after tests.

## Remaining limits and next step

1. Menus and dialogs still use page rasterization and suspend atlas composition. Toasts and
   tooltips are independent. Extracting popup/modal composition is the next focused performance task.
2. Full-width upload bands retain some excess horizontal traffic. Keep the locally validated path
   until another backend has equivalent visual/readback evidence; do not restore the old encoder.
3. CPU blur still allocates convolution images and cannot blur a world/panorama or virtual atlas.
   Group layers use scratch raster storage, not GPU-only transform animation. They are not modal
   input barriers and native item/entity overlays do not inherit their opacity.
4. Very small layouts, native entity stress, cold opens, 120/144Hz displays, Windows/Linux, and JFR
   stall attribution need broader profiling. IME/narrator semantics and automatic OS reduced-motion
   detection remain incomplete.

See `layered-ui-and-motion.md` for current API examples and `claude-design-handoff.md` for the
updated future visual handoff. Continue from these three references rather than adding more
showcase pages before the remaining popup path and backend behavior are understood.

## Exact files changed in this iteration

Library:
- `pane/src/main/java/dev/pane/ui/UiScreen.java`
- `pane/src/main/java/dev/pane/ui/UiCanvas.java`
- `pane/src/main/java/dev/pane/ui/UiContext.java`
- `pane/src/main/java/dev/pane/ui/UiAnimationClock.java`
- `pane/src/main/java/dev/pane/ui/UiLayer.java` (new)

Tests:
- `pane/src/test/java/dev/pane/ui/UiLayerTest.java` (new)
- `pane/src/test/java/dev/pane/ui/UiAnimationClockTest.java`
- `pane/src/test/java/dev/pane/ui/UiContextTransformTest.java`

Demo and client gates:
- `pane-demo/build.gradle`
- `pane-demo/src/main/java/dev/pane/demo/PaneDemoClient.java`
- `pane-demo/src/main/java/dev/pane/demo/DemoAutoTest.java`
- `pane-demo/src/main/java/dev/pane/demo/DemoPerformanceTest.java`
- `pane-demo/src/main/java/dev/pane/demo/DesignLabShowcase.java` (new)
- `pane-demo/src/main/java/dev/pane/demo/DesignLabTest.java` (new)
- `pane-demo/src/main/java/dev/pane/demo/SurfaceGpuCheck.java` (new, dev-only GPU readback)

Documentation:
- `README.md`
- `docs/index.md`
- `docs/status.md`
- `docs/performance.md`
- `docs/engineering-audit-2026-10-16.md` (historical-pass link)
- `docs/claude-design-handoff.md`
- `docs/layered-ui-and-motion.md` (new)
- `docs/layered-iteration-2026-10-16.md` (this report)

Other working-tree changes predate this iteration. Nothing was committed, published, or sent to
an external collaborator; no external assets/APIs were purchased.
