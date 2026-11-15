# Changelog

## 1.0.0-beta.4 — settings forms, new controls, clickable HUDs and high refresh rates

### Faster and lighter

- Animation follows the display's refresh rate. Repaints used to wait a fixed 16 ms, which on a
  144 Hz monitor let through only every third frame, about 48 frames a second. Hover fades,
  `animate`, smooth scrolling, toasts and HUD content now repaint once per displayed frame.
  `UiRenderSettings.withMaximumFrameRate(60)` sets a ceiling, kept to a whole number of display
  frames so capped motion stays even; `lowMemory()` caps at 60.
- Opening a screen or switching page paints once instead of on every frame of the 180–210 ms
  transition, unless the page reads `ui.motion()` or `ui.animating()`.
- Showing and hiding a HUD fades its finished texture on the GPU. The 220 ms fade used to paint and
  upload the whole HUD every frame.
- A surface no longer keeps a native copy of its pixels between its raster and its GPU texture.
  Uploads go through one shared 4 MB buffer in bands, which saves the surface's size again in memory:
  about 15 MB for a 2560×1440 screen, and the same share for every popup, toast, tooltip, HUD and
  GPU layer surface.

### Easier

- `UiScreen.open("My mod", ui -> { ... })` builds and shows a one-page screen in one call;
  `UiScreen.of(title, painter)` builds it without showing it, and `screen.open()` shows any screen.
- `screen.onAction("save", owner -> ...)` runs a handler for one action, in place of matching
  strings in an event handler. `UiScreen.create(document, options)` makes a screen that only uses these.
- `screen.onChange("volume", owner -> ...)` runs a handler when a control changes that value.
- `screen.settings(file, Map.of("name", "Player", "hints", true, "volume", 50))` loads saved values
  from a properties file, and `screen.saveSettings()` writes them back.
- Shorter control calls: `ui.button(id, label, action, x, y, width, height)` needs no style,
  `button`, `textField` and `dropdown` take a `UiRect` such as a layout cell, and `toggle`,
  `checkbox`, `slider`, `textField` and `dropdown` have forms where one string is both id and state key.
- `UiPage.of(id, label, painter)` for pages without an icon or their own accent.

### New controls and layouts

- `ui.form(id, bounds, form -> ...)` lays out labelled settings rows and scrolls when they do not fit:
  `form.section`, `note`, `toggle`, `slider`, `text`, `dropdown`, `stepper`, `keybind`, `color`,
  `button`, and `row(height)` for anything else. Each control's id is its state key, so
  `screen.settings` saves a whole form.
- `ui.keybind` takes the next key pressed as its value (Escape unbinds, as in Minecraft's controls),
  and with `mouseButtons` a clicked mouse button too. `UiKeyNames.name(code)` names it as Minecraft's
  controls do, in the player's language and keyboard layout, and `UiKeyNames.isDown(code)` tells
  whether it is held.
- `ui.colorPicker` has hue, saturation, brightness and optional opacity sliders with a hex field. The
  colour is stored as hex text; read it with `colorValue`, or hear it change with
  `screen.onChange(key, ...)`. `UiColor` converts hex text and HSV.
- `ui.stepper` is a number with minus and plus buttons.
- `ui.scrollHorizontal` scrolls sideways, for carousels, wide tables and timelines. Keyboard focus
  reveals controls scrolled off either side, and it nests with vertical scrolling. Shift with the
  wheel scrolls sideways on every platform.
- `ui.variableList` is a scrolling list whose rows have different heights; only rows in view paint,
  and Tab walks through every row.
- `hud.setInteractive(true)` lets a HUD's buttons, toggles, checkboxes, steppers and sliders take the
  mouse while chat is open, with `hud.onAction(action, handler)`, `hud.onChange(listener)` and
  `hud.value(key)`. HUDs take no input unless this is on.
- Sliders, text fields and text areas work inside `withTransform` groups: a tilted or scaled card
  can hold them, and drags and clicks are mapped into the group. They used to throw there.

### For game-style menus

Two new demo screens, a battle-royale lobby and a car-sports main menu, were built to find what
still took workarounds. Each gap they found is now one call:

- `canvas.slantRect` and `slantPath` draw leaning panels and buttons; `chamferRect` and
  `chamferPath` cut corners off, each corner by its own amount. The paths work with
  `ui.shapedButton`, so the pointer target follows the slanted or cut edge.
- `canvas.withSkew(degrees, pivotX, pivotY, painter)` leans anything drawn inside it.
- `UiTextEffect` gives display text an outline, a hard drop shadow, a top-to-bottom colour ramp and
  a forward lean, in any combination: `canvas.text(..., UiTextEffect.outline(ink, 3).withShadow(...))`.
- `canvas.rays` draws a sunburst that can turn, and `canvas.fitTextSize` shrinks a long name to fit.
- `ui.loop(period, bounds)` and `ui.pulse(period, bounds)` are animations that never end, for a
  breathing Play button or a turning sunburst. They repaint only their bounds, once per displayed
  frame, and hold still when motion is off.
- `UiDocument.Builder.labelTooltips(false)` stops painted, shaped, menu and dialog buttons raising a
  tooltip that repeats the label they already draw.
- `numberValue` reads a number stored as text, such as the "3" a `selectable` choice writes. It used
  to return the fallback, so the choice never showed.
- `UiKeyNames.name` no longer asks GLFW about a code that is not a key, such as a raw mouse button
  number passed where `UiKeyNames.mouse(button)` was meant.

### More to customise

- `UiTheme` has a `with...` method for every token, and `withRadii`, so a theme is the dark or light
  one with a few changes. Themes gain `danger`, `success` and `warning` colours for painters; older
  constructors still work.
- `UiDocument.Builder.buttonStyle`, `inputStyle` and `menuStyle` style every control drawn without a
  style of its own, so a design system is set once.
- Danger buttons take their colours from the theme's `danger` once a theme sets it.

## 1.0.0-beta.3 — speed, the narrator, input methods and text views

### Faster

- Large fills, strokes and images draw on several cores. Each core repeats the whole Java2D draw
  clipped to its own band of rows, with band edges kept where an outline is straight and on the
  rasterizer's tile edges for textures, so every pixel is exactly what one draw gives. Images split
  into row strips, which is exact under any clip.
- Opaque rectangle interiors fill as one plain rectangle, and opaque cached layers copy instead of
  blending.
- Finished liquid glass is copied back when nothing beneath it changed, instead of blending the lens
  and stroking its edges again. Backdrops hash in place and are copied only to compute a new lens.
- A hover or press repaints only controls whose painting reads it (`UiContext.hovered`, `pressed`);
  a background hit area that only raises a window repaints nothing.
- A partial repaint narrower than three quarters of the surface uploads only its rectangle.
- Unfocused text fields and text areas keep their layouts between paints.
- Typing repaints through the value's declared repaint scope instead of always the whole page.
- Parallel work runs on PaneLib's own daemon threads ("PaneLib raster"), which keep Java2D's
  per-thread rasterizer state; on Windows this cut allocation during clicks from about 1 GB/s to
  under 40 MB/s.
- Backdrop and shadow scratch, cached-layer scans and frost copies no longer allocate or run on one
  core each paint.
- PaneOS (standard perf fixture, Windows 11, RTX 2080 SUPER): full repaint 33.6 → 17.9 ms, note click
  25.8 → 13.4 ms, window drag 28.1 → 19.1 ms, Apps grid 46.4 → 29.1 ms. Mac (3024×1890 surface):
  full repaint 33.2 → 16.8 ms, click 22.2 → 10.0 ms. Warmed up, full repaints take 13–15 ms and clicks
  about 8 ms on both. PaneOS keeps its wallpaper painting in its own cache, so a theme change only
  re-tints it.
- Every shortcut leaves exactly the pixels of the plain path: `-Dpane.ui.plainRaster=true` turns them
  off, and the `-PperfCompare` fixture compares six PaneOS scenes both ways and reads the GPU texture
  back.

### Narrator

- Built-in controls describe themselves to Minecraft's narrator in Minecraft's own words: buttons,
  checkboxes, switches, radios, tabs with their position, sliders, text fields and text areas,
  menus, dropdowns and dialog buttons. Password fields never speak their characters.
- `UiContext.narration` sets or replaces a control's description (`UiNarration`, experimental); a
  tooltip names a control with no words of its own.
- The focused control, or the hovered one, is narrated under the screen title, which includes the
  page title in a multi-page document. Dialogs speak their title, message and focused button,
  toasts are spoken as they appear, and virtual-list rows say their place in the whole list.
- A focused text field or text area is read with the text it had when focus reached it. Minecraft
  speaks whatever changed after each key, so the live text was read back on every keystroke.

### Input methods

- Focusing a text input turns Minecraft's input method on, and leaving it turns it off.
- A composition is drawn at the caret in the input's own type, underlined, with the clause being
  converted marked; it never enters the value, so cancelling leaves the input and surface
  unchanged. The caret area is reported so the candidate window opens beside it.
- The characters one composition commits undo as one step (`UiTextHistory.extend`).

### Documentation and tooling

- Every public type, member and enum constant has Javadoc; mixins and `dev.pane.ui.internal` are
  marked internal. `-Pdoclint=all,-missing` reports nothing.
- `docs/compatibility.md` drafts what 1.x will promise and lists experimental APIs.
- The demo's gallery opens a **Field kit** in a world: a PaneLib panel beside the running world
  with the live player model, real item icons, tabs, a dropdown and tooltips.
- PaneOS opened in a world starts on an **Overworld** wallpaper: the world you are standing in,
  which its glass dock, widgets and lock-screen blur bend and soften. Settings › Wallpaper offers it
  beside the generated ones.
- `-PscreenshotTour` captures gallery screenshots in a fresh survival world, PaneOS included.
- New fixtures: `narrationTest`, `imeTest`; the perf fixture gained `-PperfLoop`, `-PperfCompare`,
  `-PperfVerbose` and per-phase upload and dirty-area figures.

### Fixed

- The `SETTINGS` gear icon drew its teeth outside its square.
- Hover repaints stay page-wide for painters that read the shared `UiContext.interactionMotion`.
- Hover follows the cursor in an unfocused window, as vanilla widgets do. Minecraft sends move
  events only to a focused window, so a control under the cursor stayed unhovered after another
  app or a fullscreen change took focus.
- The Wayfarer HUD's paper panels drew a parchment texture the demo no longer ships, leaving dark
  text on dark cloth. The paper is now drawn in code.
- A dropdown's own tooltip no longer shows over the menu it opened while the pointer rests on the
  trigger.
- A GPU-animated group that finished while only another part of the page was repainting, such as
  the second of two PaneOS windows opened together while music played, never reached the screen:
  its animation ended and the page beneath had not been uploaded there. The screen now repaints
  where each finished group was. The PaneOS fixture compares the game frame with a full repaint.

### Added

- `UiScreenOptions.Builder.worldSnapshot(true)` and `UiContext.worldSnapshot()`: a screen captures
  the game frame beneath it, without GUI, when it opens and after each resize, so a page can paint
  the world and have liquid glass and blur act on it (`UiWorldSnapshot`).
- Styled buttons with an icon: `button(id, label, icon, action, …, style, enabled)`, measured with
  `buttonWidth(label, icon, style)`. Before, an icon meant painting over a button with no label.
- Styled menu triggers with an icon and a down arrow: `menuButton(id, label, icon, …, style,
  enabled, items)`, measured with `menuButtonWidth`.
- `iconButton(id, label, icon, action, x, y, size, style, enabled)`: a restylable icon-only button
  whose label is its tooltip and accessible name.
- Icons `STOP`, `RECORD`, `FOLDER`, `TERMINAL`, `ARROW_LEFT`, `ARROW_DOWN` and `SHIELD`.
- `UiScreen.showDialog(id, dialog)`: opens the native confirmation dialog from code, for prompts no
  button opens (a delete from a menu, a request that arrives while the screen is open).
- `UiScreen.requestFocus(id, anchor, cursor)`: focuses a text field with part of its text
  selected, such as a file name being renamed, so typing replaces it.
- `UiContext.textView(id, text, …, style, lineNumbers)`: read-only text that wraps, scrolls,
  selects and copies like a text area, with optional line numbers. Before, read-only text meant
  painting lines and writing your own selection.
  - Takes `UiRichText` for coloured, underlined or struck-through runs, such as red error lines in
    a log.
  - Takes `UiScrollFollow.WHEN_AT_END`, so a log stays on its newest line while the reader is at
    the bottom.
  - Underlines web links in the accent colour and opens them on click through Minecraft's link
    prompt, or straight away when the player turned that prompt off.
- `scrollTo`, `scrollToEnd`, `scrollSnapshot`, `scrollOffset` and `scrollAtEnd` work on text areas
  and text views by id, not only on scroll regions.
- `UiHudEditor`: a ready-made screen where players drag HUDs to new places. Mark a HUD with
  `UiHud.setEditable(name, defaults, onMoved)`; the editor outlines it, snaps drags to a small grid,
  re-anchors a dropped HUD to the nearest corner, edge or centre, and hands the new placement to
  `onMoved` when it closes. Reset goes back to the defaults. Before, every mod wrote its own editor.
- `UiHudPlacement` offsets (`offsetX`, `offsetY`, `offset(x, y)`) move a HUD from its anchor in
  either direction, such as 20 below the centre. The four-argument constructor still works.
- `UiHud.setSize(width, height)` resizes a HUD after it's made.
- `UiHud.setScalesWithGui(true)` measures a HUD in Minecraft GUI pixels, so it scales with the GUI
  Scale option like vanilla's HUD.
- `UiScreen.controls()` lists every control the last paint drew, with its id, how the narrator
  describes it and its bounds, so tests and automation can find "the Save button" by its label.
- `UiContext.scrollOffset(id)` and `scrollAtEnd(id)`, for painting only the visible rows and for a
  "Jump to latest" control.
- `UiScreen.settled()`: whether the screen has finished drawing, for tests that measure idle
  repainting. A cold first frame can outlast the opening transition, which then paints once more.

## 1.0.0-beta.2 — readable tooltips, the demo mod and Windows verification

- Fixed default tooltips on light themes: the near-black plate drew the theme's dark text, so the
  tip showed as an empty pill. Unset tooltip text now keeps the theme colour only when it reads on
  the plate, otherwise near-white or near-black (`UiTooltipStyle.plate()`, `textOn(themeText)`).
- Releases now include `panelib-demo-<version>.jar`, the examples as their own mod: install it
  next to PaneLib and Fabric API; it opens on the title screen and H reopens it. `prepare_release.py --upload` collects the files to
  attach, and `:pane-demo:releaseClient` plays them in a clean production client.
- Packaging checks the README's coordinate instead of a line the README no longer has.
- Windows: checkouts keep LF line endings so builds match macOS and Linux byte for byte; release
  candidates are ordinary folders; symlink tests skip where the account may not create symlinks;
  consumer verification finds the runtime through the manifest now that the demo shares its prefix.
- Fixture runs start past vanilla's first-launch accessibility screen.
- Added the `windowHudTest` fixture: maximize, restore, fullscreen and GUI scale with real clicks,
  then vanilla HUD hiding and restoration in a fresh survival world.

## 1.0.0-beta.1 — liquid glass, GPU motion and PaneOS

- Added `UiCanvas.liquidGlass` with `UiGlass`: rim refraction along the edge normal, colour
  dispersion, frosted and tinted body, specular edge and an outside-only shadow. Lenses and
  `backdropBlur` results are reused between paints while their backdrop pixels are unchanged, and
  frost is blurred at half resolution.
- Partial repaints now grow to cover everything glass or backdrop blur reads, so a lens never
  samples its own stale output; glass inside `withLayer` groups sees what lies behind the group.
- Added `UiContext.pointer` (bounded pointer-driven painting), `refreshAfter` (timed repaints),
  `dragArea` with moved bounds (drag steps repaint only old and new positions), `withPresentation`
  (transitions for groups that contain editors, sliders and scrolls) and `paintedSlider`.
- Added `UiScreen.requestFocus` for controls that appear in a later paint, `UiDocument` tooltip
  styling through `UiTooltipStyle`, and glass reuse diagnostics.
- Added menu row shortcut hints, disabled rows and group dividers (`UiMenuItem.shortcut`,
  `enabled`, `startsGroup`) and right-click context menus (`UiContext.contextMenu`).
- Added `gpuGroup`, `gpuTransition` and `dragLayer` for GPU-animated groups of controls,
  `repaintScope` for region-only state repaints, opt-in `localFocusRepaint`, and `shadowOutside`.
- Blur now costs the same at any radius; large lenses, blurs, shadows and cache hashes split across
  CPU cores; outside shadows are reused between paints. Interactions on controls that are not on the
  page no longer fall back to repainting the page.
- Replaced the Beacon example with PaneOS, a Mac-like desktop showcase with a real-client fixture.

## 0.9.8 — opt-in Minecraft-formatted text

- Added immutable `UiRichText` preparation from styled Minecraft components, native section-sign
  codes, opt-in ampersand codes, literal strings, or explicit spans.
- Added retained formatted-text drawing and measurement with RGB colors, bold, italic, underline,
  strikethrough, stable obfuscation, explicit newlines, and Unicode symbols.
- Added style-preserving ellipsis for fixed-width rows without changing the behavior of ordinary
  literal UI labels.
- Added parser, component-style, rendering, width, and ellipsis regression coverage plus a live
  formatted row in the 10,000-item demo.

## 0.9.7 — allocation-free virtual-list hot path

- Recycled bounded virtual-row image, pixel, and direct upload buffers as rows leave overscan,
  eliminating per-row storage allocation during steady scrolling.
- Warmed the full two-sided overscan pool before motion begins and added explicit retained-texture,
  atlas, and row-buffer allocation diagnostics.
- Added `UiScreen.scrollVirtualItemIntoView(listId, index)` for consumer-owned keyboard selection.
- Expanded the real-client matrix with virtual-row double-clicks during motion and after reversal,
  arrow reveal, Tab/Shift-Tab traversal, row-raster ratio, and strict zero hot-path allocations.
- Added a simulated 30-second continuous fast-input regression that requires visible progress
  throughout the stream, no post-release jump, finite settling, and the exact final target.

## 0.9.6 — coherent subpixel list motion

- Replaced per-row integer Y rounding with one shared floating-point GPU transform for the whole
  virtual list, eliminating alternating row spacing and the visible scrolling wave.
- Preserved fractional row bounds and dimensions through Minecraft's GUI pose matrix while keeping
  hit targets and native overlays on the same layout-space offset.
- Added duplicated one-pixel gutters around atlas rows so linear filtering cannot sample a
  neighboring row during subpixel motion.
- Added regression coverage that reproduces the former alternating spacing and verifies constant
  spacing throughout fractional scroll phases.

## 0.9.5 — pixel-stable virtual-list tooltips

- Moved delayed tooltips into a small independent retained GPU texture instead of baking them
  into the full page surface.
- Kept virtualized rows in the same GPU atlas when a tooltip appears or disappears, eliminating
  the one-to-two-pixel text shift caused by switching raster paths.
- Kept tooltip anchor bounds synchronized with moving and recycled virtual rows without forcing
  full-page repaints.
- Added a real-client regression proving that a virtual-row tooltip becomes visible with the row
  atlas still active and the page paint count unchanged.

## 0.9.4 — large-window GPU list composition

- Moved virtual-list motion out of the full retained page texture and into one bounded GPU row
  atlas per list.
- Scroll frames now move cached row quads without rerasterizing or reuploading page chrome.
- Newly exposed and explicitly invalidated rows use partial atlas uploads; off-screen rows stay
  unpainted and the cache remains bounded by the viewport plus overscan.
- Added thread-safe `UiScreen.invalidateVirtualItem(listId, index)` for async favicons, pings, and
  other row-local data.
- Suppressed transient hover churn during kinetic motion while keeping moving hit targets live,
  then restored the correct hover after settling.
- Avoided scheduling tooltip frames for controls that do not own a tooltip.
- Expanded the live 10,000-row regression with GPU-frame, page-paint, row-raster, upload-volume,
  moving-click, direction-reversal, resize, and idle checks.

## 0.9.3 — continuous trackpad response

- Replaced restart-on-event scroll easing with a frame-rate-independent moving-target follower.
- Continuous trackpad deltas now move the visible viewport while input is still arriving instead
  of accumulating a delayed jump after release.
- Added regression coverage for continuous input, direction reversal, finite settling, and long
  frame stalls.

## 0.9.2 — smooth virtual scrolling

- Replaced fullscreen scroll repaints with clipped viewport rasterization and partial GPU uploads.
- Added short finite wheel/trackpad interpolation that returns to zero idle repaints.
- Added document-level `UiScrollSettings` for scroll distance and smoothing duration.
- Added `virtualList` so data-heavy screens paint only visible rows plus one-row overscan.
- Added scroll paint-time, partial-paint, and uploaded-pixel diagnostics to the live harness.

## 0.9.1 — Minecraft integration hardening

- Added parent-screen, title-panorama, in-world, and full-parent backdrop modes beneath transparent
  retained surfaces.
- Added documented raw key and pointer callbacks, including target-aware true double-click events.
- Added parent restoration and a consumer close/back hook for correct Escape behavior.
- Made `invalidate`, `setValue`, and `toast` safe to call from asynchronous completion threads and
  added `invalidateWhenComplete` for external requests.
- Synchronized retained texture dimensions against the active render viewport to prevent stale,
  partially widened surfaces during macOS native-window resizing.
- Added live regression coverage for raw input, double-click, async invalidation, and post-resize
  surface dimensions.

## 0.9.0 — clean-room candidate

- Renamed the native library artifact and Fabric mod to PaneLib (`dev.pane:panelib`).
- Added responsive weighted layouts and adaptive grids.
- Added checkboxes, radio options, segmented tabs, badges, progress bars, icon buttons, toggle
  buttons, password fields, and fully restylable buttons.
- Added code-drawn vector icons, dashed rounded outlines, wrapped text, backdrop blur, and
  configurable retained-surface quality.
- Added finite per-control animation with selectable easing and reduced-motion scaling.
- Added pointer caret placement, drag selection, and double-click selection for text fields.
- Isolated PaneLib's internal control actions from consumer action names.
- Added typed events while keeping the original listener API source-compatible.
- Added unit tests, CI, Javadocs, Maven publication metadata, and artifact-boundary verification.
- Added consumer documentation and a native mapping for the paper-craft marketing system.
- Retained the optional MCEF prototype in source while keeping it out of the native artifact.

The 1.0.0 gate is a separate Fabric mod built by a clean-room consumer using only the published
artifact and documentation.
