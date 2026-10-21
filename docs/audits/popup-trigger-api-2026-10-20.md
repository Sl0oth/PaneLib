# W05 — styled and custom popup triggers

Scope: finish the trigger styling surface, remove the actual consumer workarounds, and verify
input and retained rendering in Minecraft. Review remained local; no source was sent to an
external review service. W06 dialog body typography and other queued issues remain separate.

## Design and implementation

1. Reuse `UiButtonStyle` for menu, selector and confirmation triggers rather than add another
   competing style model. New enabled overloads retain the normal hover/press animation path.
2. Add `paintedMenuButton`, `paintedDropdown` and `paintedDialogButton` using the existing
   `UiButtonState`/painter contract. A shared helper records semantic state. Custom painters draw
   first; the library adds the focus ring afterward and handles disabled opacity and hit targets.
3. Keep popup content independent: `UiMenuStyle` still describes rows; `UiDialogStyle` describes
   the confirmation. Private internal action strings, popup composition and event routing stay
   inside the library. No consumer creates or parses them.
4. Extract shared menu-anchor registration and selection-model building. Translation, clipping,
   affine groups and retained virtual rows keep their existing input/composition paths.
5. Migrate and validate the real examples, not just a standalone fixture.

Existing overload signatures remain. Dropdowns now honor the theme's font family. Explicit
selected-value font family/weight/size are used for both measurement and painting, with vertical
centering and ellipsis/clipping before the arrow. Values and normal `value` events are unchanged.

New styled/custom menu and dialog triggers require descriptive labels. `buttonState(id)` exposes
semantic and interaction metadata, with normal tooltips; automatic narrator speech is not added.
For custom selectors, the painter state's label is the current option label or unmatched value;
an empty unmatched value uses the ID as a semantic fallback. Disabled controls retain metadata
but no hit/keyboard target or popup registration. Invalidating after disabling an open trigger
closes it during the next paint. Empty menus draw disabled; empty selectors retain their existing
no-control behavior. Trigger labels/painters remain the consumer's responsibility for localization.

## Removed workarounds

- Gather server heading: empty `menuButton` plus separate fill/text/icons replaced with one
  `paintedMenuButton`, meaningful label, and hover/pressed rendering inside its callback. Name
  measurement now reserves space for the chevron and people icon after server renaming.
- Gather role selector: `UiButtonStyle` explicitly matches selected-value typography to its
  14-unit `UiMenuStyle` rows. No selected-label overpaint or font transform needed.
- Wayfarer deploy: empty `dialogButton` plus separate yellow surface/text/icon replaced with
  `paintedDialogButton("deploy", "Ready to explore", ...)`. Existing bounded glow animation
  uses the supplied state; pressed color and automatic focus ring remain visible.
- Video example account/card menus: blank-label triggers replaced with descriptive painted menu
  buttons. Visual review found the font did not contain the old ellipsis glyph; the triggers now
  use the existing `UiIcon.MORE_VERTICAL` vector icon. Older sidebar glyph placeholders are a
  separate pre-existing example-art issue; this audit does not claim a complete video UI polish.

No blank-label menu/dialog trigger remains in the demo source. No broader ordinary-button
migration, branding redesign, new popup implementation, or release-version change was made.

## Efficiency boundary

All work occurs during an existing page/row paint. New methods create no timer, listener registry,
extra retained surface, or per-frame polling loop. Immutable styles can be reused by consumers.
Styled triggers share existing finite, bounded button animations; custom painters can opt into
`ui.animate`. Menu cache equality still depends on popup data/style, not painter callback identity.

The focused real-client fixture measures settled page/menu paints, popup uploads, retained
texture allocations, virtual-row rasterizations/uploads, and a 10,000-row end jump. Existing
popup regressions also cover end-stop wheel caching and cropped texture release. These are
bounded rendering checks, not an FPS benchmark or cross-platform performance guarantee.
Existing root-paint invalidation of virtual rows (N31) is not changed by this work.

## Validation

- `:pane:build :pane-demo:classes :pane-demo:configureClientLaunch` passes including Javadoc,
  runtime artifact verification, and **133 unit tests**, zero failures/errors/skips.
- Eight new unit tests exercise semantic/state parity, disabled/empty controls, native request
  registration, selected label/value/style separation, exact supplied-font pixels, clipping,
  custom selector binding, scroll/translation anchors, focus-ring pixels and invalid metadata.
- Focused Minecraft fixture: **40 checks pass**. Pointer hold/release, Space/Enter/menu navigation,
  selection and event routing, dialog confirm/Escape, focus restoration, disabled/disappearing
  triggers, affine/scrolled anchors, retained virtual row triggers, resize, native popup bounds,
  video example actions and idle rendering are exercised without loading a player world.
- Measured jump to row 9,999: **8 rows rasterized and 8 retained**. Settled triggers/open menu:
  **zero additional page paints, popup uploads, retained texture allocations or virtual-row work**.
  Popup wheel updates its cropped texture without repainting the page or virtual rows.
- Existing bounded-popup Minecraft regression: **34 checks pass**, covering legacy dropdowns,
  current selection reveal, Home/End/Page navigation, clipped targets, resize/stale clicks,
  pointer blocking, wheel boundaries, virtual popup style and idle/release behavior.
- Final Gather Minecraft regression: **34 checks pass**, including server menu, selected role,
  settings save/reset, Enter send, scroll following, compact layout and idle behavior.
- Final Wayfarer Minecraft regression: **28 checks pass**, including world-free player preview,
  migrated deploy confirmation/cancel/confirm, navigation, compact layout and settled rendering.
- Visually inspected final trigger fixture, video menu, Gather server heading/selected role, and
  Wayfarer lobby/confirmation screenshots. Focus rings and authored controls remain visible;
  final video dots render correctly and the renamed server heading no longer overlaps its chevron.

Initial build caught a missing demo `UiMenuStyle` import; it was added. The first focused fixture
had one incorrectly cumulative virtual-row-cost assertion, counting prior interactions across the
whole screen lifetime. The corrected fixture waits for earlier popup closure to settle and measures
only the end jump, with a bound on both new raster work and retained rows. The original log remains
`client-initial-cumulative-assertion.log`; no renderer change was made to hide that test failure.

## Reproduction and evidence

```sh
./gradlew :pane:build :pane-demo:classes :pane-demo:configureClientLaunch --no-daemon --max-workers=2
python3 examples/discord-inbox/launch.py --popup-trigger-test
python3 examples/discord-inbox/launch.py --popup-test
python3 examples/discord-inbox/launch.py --test
python3 examples/wayfarer/launch.py --test
```

Launchers freeze the runtime JAR, demo classes and resources before opening a separate fixture
client. The focused fixture uses `pane-demo/run-popup-triggers`; the legacy popup fixture uses
`pane-demo/run-popup`. Normal worlds and the earlier interactive Wayfarer client were left alone.
Only the test harness injects mouse callback/input events; consumers contain no test injection.

Logs, before hashes and artifact parity: `build/audit/popup-triggers/`. Actual captures:

- `pane-demo/run-popup-triggers/screenshots/popup-triggers-styled.png`
- `pane-demo/run-popup-triggers/screenshots/popup-custom-pressed.png`
- `pane-demo/run-popup-triggers/screenshots/video-custom-menu.png`
- `pane-demo/run-discord/screenshots/discord-server-menu.png`
- `pane-demo/run-discord/screenshots/discord-members.png`
- `pane-demo/run-wayfarer/screenshots/wayfarer-confirmation.png`

Runtime SHA-256: `ea7ace40b9874425a907c9a258434b27c4b9819942b1faf66e18658d7702d343`.
All **140 compiled library classes** match the tested JAR byte for byte; ZIP integrity passes.
All **12 submitted 0.9.8 snapshot files** are unchanged. Working version remains 0.9.8. Nothing
staged, uploaded, published or represented as release-ready by this change.
