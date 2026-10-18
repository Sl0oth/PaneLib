# Changelog

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
