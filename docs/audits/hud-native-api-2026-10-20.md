# W02 native HUD overlays and scrolling compass — October 20, 2026

Completed locally. User authorized the next audit repair and explicitly requested a scrolling,
animated compass. W03 scoped vanilla HUD replacement remains open. Review stayed local, as
requested; no source was sent to an external reviewer.

## Plan and implementation

1. Retain HUD-local native slots and add an optional `NativeOverlayRenderer` constructor argument.
2. Apply the existing HUD placement, fitting, DPI, clip and lifecycle to native submissions.
3. Carry fade opacity through Minecraft's deferred item/entity rendering inside the library.
4. Migrate both real showcase consumers and remove their private Fabric anchor/transform code.
5. Fix the compass with angular interpolation and a moving tick strip; test in an isolated client.

`UiHud` now exposes native item/entity submission counts. Its callback uses the screen API's
`nativeSlot`, `item`, `entity` and `playerPreview` contracts. Slots are retained with the painted
surface, converted to the current presented bounds each visible frame, and cleared on release.
The callback still runs when the canvas is unchanged, keeping native inventory/entity content live.

Minecraft 26.2's deferred native GUI states do not expose the needed opacity. Three internal
mixins tag item/entity states with extraction-time alpha and tint their final atlas/PiP blits.
The extraction scope is restored in `finally`; no external state cache or resource is retained.
Outside a PaneLib HUD fade, alpha is 1 and original colors are returned unchanged. RGB and alpha
are both attenuated for premultiplied composition. Mixin injection is required, not a silent
fallback to opaque items. This backend is version-specific, matching the existing 26.2 target.

Wayfarer and Bramble declare nine slot rectangles and call `overlay.item` by ID. Neither has the
extra Fabric native-item callback, private `panelib:retained_huds` anchor, snapshot transform,
or custom draw counter anymore. Their vanilla-component suppression is still the W03 shortcut.

The previous Wayfarer compass sampled rounded headings every five ticks and kept ticks fixed.
It now samples fractional heading every tick into its own HUD state and uses `animateAngle`
with a 90 ms ease-out. Fixed 5° bearings and 30° labels move beneath a stationary needle.
The library handles north wrapping, bounded track coordinates, interruption, settling and reduced
motion. Other panels are no longer invalidated just by yaw; the broader D01 state split remains open.

## Verification

- `:pane:build :pane-demo:classes`: successful; 113 unit tests, zero failures/errors/skips.
- `python3 examples/wayfarer-hud/launch.py --test`: 18 actual Minecraft checks passed.
  Nine inventory slots; item/avatar helpers; absent slots; intermediate fade; hide/reappear;
  F1; ordinary screens; show-on-screens; GUI scale 3 and probe placement scale 1.4; close;
  stationary compass zero repaints; native draws while canvas idle; north interpolation and
  short path; intermediate headings while turning; reduced motion; world-unload cleanup.
- `python3 examples/wayfarer/launch.py --player-preview-test`: all 18 regression checks passed,
  including world-free title-screen use, sizing, pointer tracking and resource reload.
- Full/fade/scaled HUD screenshots inspected. Native icons and avatar fade with the panel.
- `examples/wayfarer-hud/screenshots/compass-scroll.gif`: 40 actual Minecraft captures, cropped
  to the compass and played at 100 ms per frame. No generated/interpolated recording frames.
  Pixel comparison confirms the needle stays fixed and the bearing strip changes. This recording
  is not an FPS benchmark. Full updated frame: `wayfarer-native-hud.jpg` in the same directory.
- Both clients loaded SHA-256 `9bf4da2915cf8ea9e7efac233a5114dbe6c58bf972bd9a9d4e8ae08b8b3de81b`.
  All 135 compiled library classes match that JAR; ZIP integrity and mixin metadata pass.
  All 12 protected submitted-snapshot files remain unchanged.

Logs, source baselines, recording hashes and integrity results: `build/audit/hud-native/`.
Raw probe and compass captures: `pane-demo/run-wayfarerHud/screenshots/`.
Tests ran only in the copied fixture save; no ordinary player save or production server involved.

## Boundaries

Native content draws above the HUD canvas with rectangular clipping. This does not add arbitrary
native shape masks, partial-slot rendering, or an arbitrary merged offscreen opacity group.
Raw `graphics()` text/fill escapes do not inherit this item/entity alpha contract. Oversized item
PiP alpha is wired through the owning item state and source-reviewed; a custom oversized model
was not separately exercised in this client run. Cross-platform/modpack rendering is untested.

The core repair does not solve W03 restoration, W07 edge-center anchors or W08 raster quality
under enlarged placement. Version is still the development 0.9.8; no release staging or upload.
