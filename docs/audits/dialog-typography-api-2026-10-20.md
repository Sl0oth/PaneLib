# W06 — Dialog typography and bundled fonts

Completed locally October 20, 2026. Review stayed local. No release, version bump or upload.

## Problem and API

Dialog title/message/actions were fixed at 12/8/8 even on screens with larger custom typography.
Wayfarer accepted that mismatch. `UiCanvas.registerFont(alias, InputStream)` already supported
bundled TrueType/OpenType fonts; a second font loader was unnecessary.

`UiDialogStyle` now provides a common `fontFamily`, optional `titleFontFamily`,
`messageFontFamily`, `buttonFontFamily`, independent title/message/button sizes and font styles,
and extra title/message line spacing. Per-part families override the common family, which
overrides the document theme. Sizes accept finite 1–128 layout units; extra spacing accepts
finite 0–128. Plain, bold, italic and bold-italic flags are supported. Legacy defaults remain.

`UiDialogLayout.Typography` carries the resolved font metrics into wrapping, line positions,
action sizing/ellipsis and drawing. Buttons grow with their font, stack when needed and remain
bounded. Wheel/arrow/page scrolling uses measured line heights. Hit regions and action painting
use the same button rectangles. Text stays clipped in extremely small windows.

The immutable style includes every typography value in equality/hashCode. Dialog measurement
caches include the existing font registry revision. Register fonts at client setup; if changed
later, register on the client thread and invalidate affected screens/HUDs. Successful registration
then causes one dialog remeasurement and popup redraw even when its bounds stay the same. Failed
registration preserves the old alias and cache revision. No per-frame font polling or global
repaint listener was added. `UiScreen.dialogLayoutCount()` exposes measurement count.

Wayfarer uses its existing bundled `wayfarer-roboto` alias in the real confirmation style, with
scaled 20/12/11 title/message/button sizes and 3/4 extra title/message line spacing. No substitute
modal or text overpainting is needed.

## Verification

- Full Gradle build, Javadoc and artifact validation: **140 unit tests, zero failures/errors**.
  Seven new tests cover defaults, family precedence, wrapping/spacing, long labels, tiny viewports,
  value equality (including signed zero), and validation.
- Focused real Minecraft typography fixture: **45 checks pass**. Bundled TTF registration changes
  fallback metrics/pixels; late registration remeasures once; equal rebuilt styles and failed
  registration reuse caches. Large type, independent families, resize, long/localized labels,
  scroll/Home/End/PageDown, Tab/Enter/Escape, pointer confirmation, focus restoration, reopening
  and popup release pass.
- Existing dialog regression: **24 checks pass**. Existing Wayfarer regression: **28 checks pass**.
- Focused fixture measures zero extra settled page paints, dialog layouts, popup uploads or
  retained texture allocations. Scrolling/focus reuse line wrapping and do not repaint the page.
  This is bounded behavior evidence, not an FPS or cross-platform performance guarantee.
- Actual captures inspected: `pane-demo/run-dialog-typography/screenshots/dialog-font-registered.png`,
  `dialog-font-overrides.png`, `dialog-typography-compact.png`, and
  `pane-demo/run-wayfarer/screenshots/wayfarer-confirmation.png`. The compact capture deliberately
  uses oversized fonts and shows ellipsized actions and scrollable copy.
- All three final client logs used runtime SHA-256
  `b692b4536296a2db403ee587fd41e24f7ef70656b78b26129321ff8dd1072bb9`.
  ZIP integrity passed; all **141 compiled library classes** match the runtime JAR byte-for-byte.
  All **12 submitted snapshot files** are unchanged. Version remains 0.9.8.

Evidence: `build/audit/dialog-typography/`. `before.json` preserves pre-edit hashes;
`verification.json` records artifact/unit/snapshot checks. `build-final.log`, `client.log`,
`dialog-regression.log`, and `wayfarer-regression.log` record final validation.

Initial fixture issues are retained: a unit assertion expected truncation from a label that fit
at width 330; the test was narrowed to 220 to actually exercise ellipsis. The first client launch
enabled both Wayfarer and typography flags, so the earlier Wayfarer branch ran. The launcher now
selects one fixture flag. Its old log is `client-launcher-routing-before.log`; that run is not
counted as typography evidence. Neither required a product workaround.

## Reproduce

```sh
./gradlew :pane:build :pane-demo:classes :pane-demo:configureClientLaunch --no-daemon --max-workers=2
python3 examples/wayfarer/launch.py --dialog-typography-test
python3 examples/wayfarer/launch.py --dialog-test
python3 examples/wayfarer/launch.py --test
```

Launchers freeze runtime files. Fixtures use isolated clients, no world, and exit normally.
The earlier interactive client was left alone. No external source review was requested.

## Remaining boundaries

This exposes existing AWT-compatible TTF/OTF rendering; the live custom asset test is a bundled
Roboto TTF, not a certification of every OTF or font's glyph coverage. There is no variable-font
axis API or Minecraft bitmap-font integration in this change. Registration does not automatically
invalidate all UI. Extreme font/window combinations prioritize bounded keyboard-reachable actions
over fully visible labels. W07 responsive HUD anchors/layout is next; W08/W09 raster/sampling,
W11 vanilla-font support and other queue items remain separate.
