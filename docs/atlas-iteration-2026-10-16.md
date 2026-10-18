# Atlas / composable motion / HUD iteration — 2026-10-16

A usable iteration is complete: Atlas is a native motion workbench and example launcher, with a
live editable field HUD. This work extends the previous three-design iteration, preserves its
fixes and other working-tree changes, and does not touch Peripheral. Claude was not dispatched.
No paid generation/services, publishing, official launcher, or personal worlds were used.

## Try it

```sh
./gradlew :pane-demo:runClient -Pstudio
```

This loads the reserved disposable field world and opens Atlas. Click the instrument to expand
it, retarget its spring while it is moving, select the sequence track and replay its choreography.
Field HUD edits visibility, scale, anchors, and reduced motion. Return to world resumes ordinary
Minecraft input. **H** reopens the console; **J** toggles the HUD. Examples opens the three prior
design references and the existing core/gallery screens.

The studio worlds are fixtures, not personal saves: `pane_studio_scratch` for hands-on use and
`pane_studio_autotest_20261016` for automation. The launcher prepares a small field area there;
do not use these worlds for builds you want to keep. All verification clients were shut down.

## Visual evidence

- [UI motion preview](previews/atlas-motion.gif): 91 captured game frames, 5.15 seconds including
  the final hold, reduced to 960 pixels wide. Expanding/reversing cards, spring retargeting,
  selection motion, and a sequential pulse are visible.
- [Live HUD close-up](previews/atlas-hud.gif): 38 actual game frames, 2.5 seconds including the
  final hold, cropped to the vitals panel while it reacts to damage/recovery.
- Full screenshots: `pane-demo/run/screenshots/pane-studio-console.png`,
  `pane-studio-in-motion.png`, `pane-studio-examples.png`, `pane-hud-editor.png`,
  `pane-hud-live.png`, `pane-hud-resized.png`, and `pane-hud-reloaded.png`.

These are actual client frames, not generated artwork or an animation simulation. Setup command
chat was cleared before the final motion capture. The Mac was locked, so native window recording
was unavailable. The previews instead sample the Minecraft framebuffer at the test's **20 Hz**
cadence, with no interpolated frames, then encode a looping GIF. Capture adds overhead and is not
used as proof of game FPS. `tools/export-studio-previews.py` reproduces the GIFs with existing Pillow;
no new capture stack was installed. The short last-frame hold is deliberate.

Wide UI, example launcher, HUD editor, gameplay HUD, and resized captures were inspected for text,
clipping, contrast, and collisions. The editor preview was moved out of the header/control area.
Gameplay preserves the crosshair, hotbar, normal movement, and attack path.

## Reusable library changes

- **UiTransform:** translation, positive X/Y scale, pivot rotation, and opacity for drawing/button
  groups. Hit regions retain exact affine polygons, including virtual-row translation; empty
  corners of an axis-aligned bounding rectangle cannot activate a rotated button.
- **UiTimeline:** finite scalar keyframes with holds, easing per segment, deterministic sampling,
  and revision-triggered replay. Combine tracks for position/scale/rotation/tint rather than using
  hardcoded demo-only motion. Replay and retarget start at the currently sampled value.
- **animateFrom:** initial values and delayed/staggered entrances; interruptions do not repeat
  the entrance delay. Existing animate semantics remain intact.
- **SPRING:** a bounded-duration damped curve with controlled overshoot and an exact settled
  endpoint. Reduced motion snaps running/delayed tracks to their final value.
- **cachedLayer:** a caller-owned revision cache for text/shadow/shape drawing under changing
  transforms. Input targets remain registered every paint. The Atlas instrument demonstrates
  stretching a cached panel independently from text and rotating the complete group.
- **prewarmFonts:** optional CPU-only startup warmup, used on a demo worker while assets load,
  to avoid doing the first font initialization in a visible HUD frame.
- **UiHud / UiHudPlacement:** input-free retained gameplay widgets; equal-value coalescing,
  data-driven content animation, visibility transitions, spring placement, anchors/viewport fitting,
  logical-point sizing across GUI scales, F1 inheritance, screen suppression/optional editor preview,
  world-value/texture cleanup, explicit close, and shutdown cleanup. No gameplay input hooks or
  Screen are installed by the HUD API.

The Field HUD consumer samples actual health, food, level, position, dimension, and heading.
Health/food meters and altitude counters interpolate; heading uses a shortest-path unwrapped
rotation. The two translucent components occupy separate corners. Host mods own sampling, bindings,
configuration, and the editor; the library owns retained drawing and lifecycle.

See [HUDs and composable motion](huds-and-composable-motion.md) for signatures, examples, and limits.

## Measured active cost

Local direct development client, Minecraft 26.2, Java 25, macOS OpenGL-on-Metal; surface size
1,640,640 pixels. Timings below are CPU raster/extraction/upload-submission costs, not GPU fences.
They are individual runs, and scene preparation/JVM warmup evolved between them. Treat the table
as observed local evidence, not a controlled hardware benchmark or a universal smoothness claim.

| Same scripted active UI interval | Extraction p95 | Extraction p99 | Frame interval p95 |
| --- | ---: | ---: | ---: |
| Initial uncached instrument | 15.437 ms | 17.101 ms | 25.240 ms |
| Cached text | 13.447 ms | 16.768 ms | 27.077 ms |
| Cached text/panel + startup font warmup | 10.090 ms | 14.338 ms | 22.324 ms |

The last row was measured without frame recording. This is about 35% lower observed p95 extraction
than the first run, but active CPU-transformed UI still costs milliseconds. A GPU-only retained
scene graph is not implemented. Capture-mode timing is kept separate in its raw log.

In that optimized non-recording run, the live **vitals widget** completed 53 paints in the sampled
interval, averaging **0.978 ms raster + 0.153 ms upload submission per paint**. The observed lifetime
maximum HUD raster dropped from a cold 354–422 ms in earlier runs to about 28 ms after prewarming.
This moves cold font work earlier/off the visible render path; it does not eliminate initialization
or certify hitch-free startup. The navigation widget has separate counters and was not folded
into the vitals-only average.

The stationary HUD recorded **zero new paints over two seconds**; hidden HUDs also recorded zero
new paints. Changed data and finite animation produce small retained-widget uploads, rather than
screen-sized uploads. Anchor motion composites unchanged content without forcing a body repaint.

## Verification

- Final `./gradlew build`: **46 tests**, zero failures/errors/skips; both modules and library JAR
  boundary verification passed. New tests cover delayed retargeting, sequential holds/replay,
  spring endpoints, reduced motion, polygon hit tests, unsupported input rejection, cached drawing
  reuse/revision/opacity, raster quality under vertical scale, and GUI-scale-independent HUD bounds.
- Full Atlas world run: **29 PASS lines**, no FAIL/INCONCLUSIVE markers. This covers card reversal,
  action clicks while transformed, spring retargeting, focus, menus, launcher/back navigation,
  HUD editing, movement/attack input through the HUD, actual health changes, registered H/J paths,
  toggles, F1, resize/GUI scale, coexistence with screens, idle behavior, unload, and reload.
- Existing broad client regression: **49 PASS lines**, no failures, including core controls,
  scrolling, virtual-row input/recycling, clipboard editing, overlays, resize, native preview paths,
  five-second UI idle behavior, and its GPU/native-backing byte comparison.
- The final UI-only recapture cleared setup chat and ran just the motion segment. It did not repeat
  the completed HUD/world gates. The exported GIFs were checked for dimensions, frame count,
  timing, and distinct frame content.

The new renderer was exercised in an actual integrated client/server disposable world. Input
checks use Minecraft's key mapping/input processing and actual player movement/swing state; they
are not a human playtest or proof of physical keyboard/mouse feel while the Mac is locked.

Raw logs and pre-edit shared source copies are under `build/hud-baseline/` (ignored):
`studio-client-second.log` (initial baseline), `studio-client-cached.log`,
`studio-client-optimized.log` (unrecorded optimized measurement), `studio-verified-recording.log`
(full passing workflow), `clean-motion-capture.log`, `core-client-regression.log`, `build-final.log`.
Earlier failed runs exposed bootstrap thread detection and fixture timing/hotkey ordering; those
were corrected before the passing gates above. Do not count a Gradle exit alone as a client verdict.

## Remaining limits

1. Rotated/scaled text editors, sliders, scroll viewports, and native item/entity overlays are not
   supported inside affine groups. Keep them outside or use translation. Native slots are not
   rendered by UiHud. Hidden transformed groups do not register hit targets.
2. Spring retargeting preserves position, not physical velocity. Timelines are finite scalar
   choreography, not skeletal animation or a physics engine.
3. Transformed cached drawing and group fades still use CPU raster/composition. Large active scenes
   need further profiling, especially high-refresh displays and Windows/Linux. The sampled HUD
   cold peak still exceeds a single 60 Hz frame budget.
4. Menus/dialogs retain their existing page-based path and suspend virtual-list atlas composition.
   The motion workbench's menu was checked during motion; this pass did not rewrite popup rendering.
5. HUD settings are session-local demo configuration. There is no drag-to-edit layout designer,
   automatic collision avoidance with other mods, or automatic OS reduced-motion detection.
   The host editor reserves preview space and restores ordinary HUD visibility on close.
6. IME/narrator support and public Maven/1.0 compatibility guarantees remain outstanding.

## Exact files changed in this pass

Library:
- `pane/src/main/java/dev/pane/ui/UiAnimationClock.java`
- `pane/src/main/java/dev/pane/ui/UiEasing.java`
- `pane/src/main/java/dev/pane/ui/UiTimeline.java` (new)
- `pane/src/main/java/dev/pane/ui/UiTransform.java` (new)
- `pane/src/main/java/dev/pane/ui/UiCanvas.java`
- `pane/src/main/java/dev/pane/ui/UiContext.java`
- `pane/src/main/java/dev/pane/ui/UiHitRegion.java`
- `pane/src/main/java/dev/pane/ui/UiScreen.java` (preserve transformed virtual-row hit shapes)
- `pane/src/main/java/dev/pane/ui/UiLayer.java`
- `pane/src/main/java/dev/pane/ui/UiHud.java` (new)
- `pane/src/main/java/dev/pane/ui/UiHudPlacement.java` (new)

Tests:
- `pane/src/test/java/dev/pane/ui/UiMotionTest.java` (new)
- `pane/src/test/java/dev/pane/ui/UiHudPlacementTest.java` (new)
- `pane/src/test/java/dev/pane/ui/UiLayerTest.java`

Demo/tooling:
- `pane-demo/src/main/java/dev/pane/demo/FieldHudDemo.java` (new)
- `pane-demo/src/main/java/dev/pane/demo/ShowcaseHub.java` (new)
- `pane-demo/src/main/java/dev/pane/demo/StudioAutoTest.java` (new)
- `pane-demo/src/main/java/dev/pane/demo/PaneDemoClient.java`
- `pane-demo/build.gradle`
- `pane-demo/src/main/resources/assets/pane_demo/lang/en_us.json` (new key-binding labels)
- `tools/export-studio-previews.py` (new)

Documentation/artifacts:
- `README.md`, `TODO.md`, `docs/index.md`, `docs/status.md`, `docs/performance.md`
- `docs/claude-design-handoff.md`, `docs/layered-iteration-2026-10-16.md` (current-pass pointers)
- `docs/huds-and-composable-motion.md` (new)
- `docs/atlas-iteration-2026-10-16.md` (this report)
- `docs/previews/atlas-motion.gif`, `docs/previews/atlas-hud.gif` (new)

Other Git differences predate this pass. Nothing was committed or published.
