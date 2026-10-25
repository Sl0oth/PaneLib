# Performance diagnostics

PaneLib keeps static content in one retained texture. Native Minecraft items and living entities
are composed after that texture and do not themselves request a repaint. Diagnostics are off by
default. Enable them only while profiling a specific screen:

```java
UiScreenOptions options = UiScreenOptions.builder()
    .performanceDiagnostics(sample -> LOGGER.info(sample.summary()))
    .build();
```

The listener runs on the render thread at a bounded rate. A `UiPerformanceSnapshot` reports frame
and paint counts, idle repaints, dirty area and surface area, retained raster/upload/composite
submission timings, native item/entity work, active animations, and retained/virtual allocation
counters. GPU values are CPU submission durations, not a GPU fence or total frame time; use JFR
and a client frame-time capture when a stall needs deeper attribution.

For custom hover motion, pass bounds that include its lift, shadow, glow, and both end positions:

```java
UiRect repaint = new UiRect(x - 14, y - 16, width + 28, height + 32);
float hover = ui.animate("card:" + id, ui.hovered(id), 140, repaint);
```

This bounds CPU repainting to that card region. The main texture uploads full-width scanline bands with guard rows; a small rectangle can still
produce a wide transfer. Virtual-row atlases have their own bounded upload path. The standard Pane controls supply their
own bounded repaint regions. An unbounded `animate` overload remains available when a change
really affects the full page.

## Measurement notes

- Compare cold and warm opens separately. Record open-stage times and p95/p99 frame time, not
  only average FPS.
- An idle page should keep its paint count and upload count unchanged. A live entity preview may
  render every frame, but it must not force retained rasterization or texture upload.
- Treat window size, Minecraft GUI scale, screen DPI, resource pack, and GPU driver as part of a
  result. These change retained-surface and native-preview cost.

## Primary references

- [Fabric rendering concepts](https://docs.fabricmc.net/develop/rendering/basic-concepts) for the
  extraction/draw pipeline and render-thread constraints.
- [Fabric GUI rendering](https://docs.fabricmc.net/develop/rendering/gui-graphics) for GUI-space
  drawing and clipping behavior.
- [OpenJDK Flight Recorder](https://docs.oracle.com/en/java/javase/26/docs/specs/man/jfr.html)
  and the [JFR user guide](https://docs.oracle.com/en/java/java-components/jdk-mission-control/9/user-guide/using-jdk-flight-recorder.html)
  for low-overhead recording and analysis.

## Repeatable client check

Run `./gradlew :pane-demo:runClient -Pperftest` for isolated Discover-page idle, toast, expiry,
and forced-redraw measurements. It opens no world and quits after printing results. Inspect the
`[PanePerf] complete PASS` marker; Gradle success alone does not certify performance checks.
The listener runs at most once per 250 ms even during repainting. Static toasts no longer schedule
frames; only their changing fade opacity and removal invalidate the retained surface.

Snapshot timings are latest values, not retained maxima; short spikes can occur
between callback samples. Use a frame capture or JFR when measuring those spikes.

The subsequent layered UI pass moves toast fades/expiry to an independent cropped texture.
See [layered UI and motion](layered-ui-and-motion.md) for the current upload and composition paths.

## Active motion and live HUDs

The Atlas pass adds `cachedLayer` to reuse expensive drawing under changing affine transforms,
and optional worker-side `prewarmFonts` to move cold font work out of a first visible frame.
`UiHud.setValue` coalesces equal values; its retained content repaints only on changed data,
finite content animation, or visibility transitions. Anchor motion composites the cached texture.
Run `-Pbenchmark -Pprofile` for the isolated Atlas benchmark; it keeps the interactive client's
run directory separate.

Shadows draw in one blend, and caret and selection updates repaint only their editor.
`ui.gpuLayer` animates a cached group's transform and opacity on the GPU with zero page repaints
per frame; toasts, tooltips, popups and virtual lists draw in their own layers.
