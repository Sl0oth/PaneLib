# W07 — HUD anchors and responsive placement

Local implementation completed across October 20–21, 2026. Review stayed local. No release,
version bump, or upload. Preserve the submitted 0.9.8 snapshot.

## Problem

Wayfarer used left anchors and `(windowWidth - widgetWidth) / 2` every tick to approximate centered
placement. That math ignores placement scale and the logical-point/GUI-coordinate conversion.
Independently fitting seven widgets also left the bottom row overlapping at compact sizes.

## Supported API and migration

`UiHudPlacement.Anchor` adds `TOP_CENTER`, `BOTTOM_CENTER`, `CENTER_LEFT`, `CENTER_RIGHT` after
existing constants. Centering uses the final scaled/fitted size; margins apply only on the
edge-aligned axis. Old corner/CENTER behavior and .5–2x placement scale bounds remain unchanged.

`UiHud.setPlacementPolicy(UiHudPlacementPolicy)` receives a `UiHudViewport` in logical UI points.
It resolves on the next presented frame, then only when logical viewport dimensions change.
Reinstalling a policy invalidates this placement cache for changed consumer settings. Stable
frames do not invoke the callback or allocate a viewport. Fixed `setPlacement` removes the
policy; `placement()` reports the last resolved placement, or the previous value before first
resolution. Callbacks are pure client-render-thread calculations; null results/invalid viewport
values fail without replacing the previous result. This is not a layout solver or error-swallowing
fallback. Hidden/disabled/closed widgets do not resolve policies once they stop presenting.

Placement changes do not mark retained pixels dirty. Existing native-slot mapping and visibility,
vanilla replacement and world lifecycle paths remain shared. GUI rounding can change the logical
available viewport slightly; calculations use the actual drawable GUI area converted to points.

Wayfarer installs seven policies at initialization and when its requested HUD scale changes.
`WayfarerHudLayout` is a small authored recipe: roomy windows use the corner/edge-center arrangement;
compact windows put the compass above the two upper panels, and the equipment belt below the two
lower panels. A common fit scale keeps those rows clear and leaves the crosshair centered. There
is no window polling or centering arithmetic in its tick method. The older Bramble example was
not migrated by this Wayfarer-scoped task.

## Evidence

- Full build, Javadoc and artifact verification pass: **144 unit tests, zero failures/errors**.
  Four added tests exercise all anchors, multiple GUI/placement scales, tiny-view fitting,
  10,000 unchanged cache resolutions, policy replacement/removal and rejected values/results.
- Focused isolated Minecraft fixture: **55 checks pass**, including all seven widgets bounded
  and non-overlapping at 640×440, 1440×900 and 1600×500, requested 1x/1.5x layout, Minecraft GUI
  scale changes, .5x/2x probe sizes, all four new anchors and all nine native equipment items.
- Stable probe frames have zero extra policy calls, paints, uploads or texture allocations.
  Viewport/anchor/placement-scale changes reuse pixels. Fixed placement removes the policy;
  disable/hide/show/close and world unload preserve expected lifecycle behavior.
- Existing native HUD/compass regression: **18 checks pass**. This includes native item/player
  rendering, fades, F1/screens, scale changes, north crossing, continuous turning and world unload.
- Existing vanilla-restoration regression: **33 checks pass**, including J toggle, fades, F1/screens,
  overlapping owners, earlier/later wrappers, two disconnect/rejoin cycles, close and ID reuse.
- Actual screenshots visually inspected in `pane-demo/run-hud-placement/screenshots/`:
  `hud-placement-compact.png`, `hud-placement-reference-large.png`, `hud-placement-ultrawide.png`
  and `wayfarer-hud-responsive-final.png`. The first three contain a deliberate gold probe rectangle
  used to check independent anchors; the final reference capture has the probe removed.
- Runtime SHA-256: `d921c852b7fa9367507269ba60ab4f3864432628adff580f018ec3ea6bb15eee`.
  ZIP integrity passes, all **144 compiled library classes** match the runtime artifact, and
  all **12 submitted snapshot files** remain unchanged. Version is still 0.9.8.

`build/audit/hud-placement/` contains pre-edit hashes/backups, `build.log`, `client.log`,
`native-regression.log`, `restoration-regression.log` and `verification.json`.
The first native regression tried the existing showcase directory and encountered its world lock.
Only that new blocked test process was stopped; the older game was left alone. Its diagnostic log
is retained as `native-regression-world-lock.log`. Test modes now use separate directories and
copies of the fixture world, rather than sharing the interactive showcase directory.

## Reproduce

```sh
./gradlew :pane:build :pane-demo:classes :pane-demo:configureClientLaunch --no-daemon --max-workers=2
python3 examples/wayfarer-hud/launch.py --placement-test
python3 examples/wayfarer-hud/launch.py --test
python3 examples/wayfarer-hud/launch.py --restoration-test
```

Each launcher freezes the runtime JAR/classes/resources. Placement uses `run-hud-placement`, native
regression uses `run-hud-native-regression`, and restoration uses `run-hud-restoration-regression`.
They modify only their copied showcase world and exit after validation. This is real local
Minecraft evidence on macOS, not a multiplayer/modpack/cross-platform guarantee.

## Boundaries and next issue

The authored seven-widget arrangement targets logical viewports at least 640×440. Smaller windows
still get individual widget clamping, but do not have a promised readable, collision-free layout.
Existing spring position transitions can cross during reflow; the tests establish settled layout.
Consumers may set motion scale to zero for immediate movement. Other mods' HUDs are not considered.

W08 is next: HUD raster quality still follows display DPI, not placement magnification, with a 2x
cap. This change does not certify enlarged text sharpness or solve W09 sampling, W11 bitmap fonts,
or D01 unnecessary updates caused by the showcase's shared live-state record.
