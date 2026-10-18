# PaneLib engineering pass — 2026-10-16

Historical first pass. The subsequent [layered UI iteration](layered-iteration-2026-10-16.md)
updates the upload, overlay, animation, and visual-design work below.

## Scope and fixes

This pass preserves the pre-existing working tree. No Peripheral edits, publishing, personal
worlds, external API spending, or Claude collaboration occurred. LM Studio's local endpoint was
unavailable, so no Qwen work was delegated.

1. Stationary toasts no longer schedule full-screen repaints. Painting and scheduling share the
   same opacity calculation, including the last frame after a skipped fade boundary. Toast fades
   now respect the theme's motion scale, including zero.
2. Performance callbacks are limited to one per 250 ms even while repainting. Previously the
   repaint exception bypassed the rate limit and could amplify stutters through consumer logging.
3. Backdrop blur respects transformed shape clips and returns without allocating blur images
   when entirely clipped out. Both new clip tests failed against the original implementation.
4. Full texture upload no longer swaps red/blue in the retained AWT backing array. That array is
   read by later partial paints/blur, so it must remain ARGB. Conversion writes directly to the
   already allocated native image, without adding another full-surface pixel buffer.
5. Added a repeatable real-client benchmark and optional automatic exit for the existing harness.
   Corrected the scrolling gate to distinguish bounded rasterization from full-texture upload.

## Measurements

Direct Fabric development client, macOS OpenGL-on-Metal, Minecraft 26.2 / Java 25. The benchmark
uses the actual Discover showcase document, with native entity/item rendering disabled and no
world loaded. It fixes pointer input outside controls. Retained surface: 1,640,640 pixels;
approximately 180 frames per three-second sample, VSync enabled. These are one-run samples,
not a cross-device benchmark or a general smoothness guarantee.

| Three-second phase | Before | Final implementation |
| --- | ---: | ---: |
| Plain idle repaints | 0 | 0 |
| Stationary toast repaints | 136 | 0 |
| Stationary toast uploaded pixels | 223,127,040 | 0 |
| Stationary toast cumulative raster CPU | 837.254 ms | 0 ms |
| Stationary toast cumulative upload CPU | 252.672 ms | 0 ms |
| Stationary toast extraction p95 | 8.285 ms | 0.026 ms |
| Stationary toast extraction p99 | 8.608 ms | 0.038 ms |
| Forced repaint callbacks | 59 | 12 |
| Post-toast idle repaints | 0 | 0 |

Frame interval p95 for stationary toast was 17.551 ms before and 17.861 ms after; the test
remained refresh-limited and does **not** show improved overall FPS. The demonstrated improvement
is eliminated render-thread raster/upload work. Upload times are CPU submission, not GPU fence
times. Capture and JVM warmup add variability; cold-open performance was not measured here.

The final forced-redraw extraction p95/p99 was 9.828/10.281 ms versus 9.149/10.068 ms
before. Full redraw cost remains a limitation.

Raw logs and pre-edit copies are under `build/engineering-baseline/` (ignored local artifacts).
The benchmark screenshot is `pane-demo/run/screenshots/pane-perf-toast.png`.

## Repeat

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home ./gradlew build
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home ./gradlew :pane-demo:runClient -Pperftest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home ./gradlew :pane-demo:runClient -Pautotest -PautotestExit -PlevelId=pane_engineering_fresh
```

Choose a new disposable `levelId` for a genuinely fresh-world run. The harness reopens an existing
world with that ID. `-Pperftest` opens no world, prints measurements and PASS/FAIL checks, and quits.
Both harnesses report checks in their logs: Gradle exit status alone does not certify them.

## Remaining work, in order

1. **Ordinary scroll/hover uploads:** existing `upload(PixelRect)` deliberately uploads the entire
   DynamicTexture to avoid an earlier macOS right/bottom seam. The first full run exposed the
   stale test promising reduced upload. Preserve visual correctness, then profile and validate a
   seam-free bounded upload path on macOS, Windows, and Linux. Do not simply restore the old encoder.
2. **Overlay cost:** toast/menu/dialog visibility disables virtual atlas composition; fades and
   dialogs still repaint the whole page. Profile scrolling while an overlay is visible, then
   consider a separate retained overlay surface. Blur still allocates three images per visible call.
3. **Broader lag reproduction:** cold/warm opens, dense gallery pages, native entities, resize,
   high DPI, and different backends need frame-time/JFR captures. The benchmark isolates one
   reproducible waste source; it does not explain every report of lag.
4. **Demo readiness:** validate narrow layouts and disabled/error states, add visual regression
   baselines, and resolve keyboard/narrator/IME gaps. Existing manual TODO notes still apply.
5. **Integration surface:** persistent HUD lifecycle and shaped image-button API are still missing.
   Start the future Peripheral proof with a normal screen and settle the Minecraft version first.
6. **Release:** pin reproducible build tooling (Loom is currently a moving snapshot), run a clean
   downstream consumer build, and choose a Maven host before promising 1.0 stability.

See `claude-design-handoff.md` for the small proposed design deliverable and integration boundary.

## Validation and exact files changed in this pass

`./gradlew build`: 37 tests passed, no failures/errors/skips; both modules built and the library
artifact-boundary check passed. New tests cover toast timing, transformed blur clipping, and
full/partial pixel transfer. The final client performance harness reports `complete PASS`.
The final full client harness completed with 48 PASS lines and no FAIL/INCONCLUSIVE checks.
It reopened only the disposable world created by this pass. It exercised controls, clipboard,
menus, dialogs, toast placement/dismissal/expiry, async invalidation, virtual-list scrolling and
recycled-row input, gallery interactions, four resizes, and five seconds with zero idle repaints.
Native player/items were visually checked in the captured Loadout screenshot. This was not a
Windows/Linux run or an exhaustive visual review of all screenshots.

Ordinary scrolling recorded 52 partial CPU paints while uploading 1,640,640/1,640,640 pixels.
The virtual-list run recorded 99 GPU-composited frames, zero page repaints, 28 row rasters,
2,432,640 uploaded row pixels (1.5% of hypothetical full-frame traffic), and zero new retained
texture/atlas/row-buffer allocations. Last-row raster/upload cost was 0.47 ms.
Full client evidence: `build/engineering-baseline/client-final-regression.log`.

The native-image preservation fix adds a small cost to full conversion: the forced-redraw sample
spent 129.078 ms submitting 59 uploads versus 112.260 ms before (about 0.29 ms extra per upload).
That is a single-run observation, not a controlled microbenchmark; preserving the backing colors
is required for correctness. It adds no full-surface array allocation.

Library:
- `pane/src/main/java/dev/pane/ui/UiScreen.java`
- `pane/src/main/java/dev/pane/ui/UiCanvas.java`
- `pane/src/main/java/dev/pane/ui/UiPerformanceDiagnostics.java`
- `pane/src/main/java/dev/pane/ui/UiToastTiming.java` (new)
- `pane/src/main/java/dev/pane/ui/UiPixelTransfer.java` (new)

Tests and demo:
- `pane/src/test/java/dev/pane/ui/UiCanvasBlurTest.java` (new)
- `pane/src/test/java/dev/pane/ui/UiToastTimingTest.java` (new)
- `pane/src/test/java/dev/pane/ui/UiPixelTransferTest.java` (new)
- `pane-demo/src/main/java/dev/pane/demo/DemoPerformanceTest.java` (new)
- `pane-demo/src/main/java/dev/pane/demo/PaneDemoClient.java`
- `pane-demo/src/main/java/dev/pane/demo/DemoAutoTest.java`
- `pane-demo/build.gradle`

Documentation:
- `README.md`
- `docs/status.md`
- `docs/performance.md`
- `docs/engineering-audit-2026-10-16.md` (new)
- `docs/claude-design-handoff.md` (new)

Other differences reported by Git predate this pass. Pre-edit copies of the main edited source
files are in `build/engineering-baseline/`; do not reset the working tree to reproduce this work.
