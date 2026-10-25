# Changelog

## 1.0.0-beta.3 (unreleased) — speed, the narrator and input methods

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
- New fixtures: `narrationTest`, `imeTest`; the perf fixture gained `-PperfLoop`, `-PperfCompare`,
  `-PperfVerbose` and per-phase upload and dirty-area figures.

### Fixed

- Hover repaints stay page-wide for painters that read the shared `UiContext.interactionMotion`.
- Hover follows the cursor in an unfocused window, as vanilla widgets do. Minecraft sends move
  events only to a focused window, so a control under the cursor stayed unhovered after another
  app or a fullscreen change took focus.

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
