# PaneLib 0.9.8 status

Current caveats and measured performance: [current Atlas/HUD iteration](atlas-iteration-2026-10-16.md).
The list below is a feature inventory, not a claim that all performance or integration work is complete.

## Ready now

- Input-free retained gameplay HUDs, world/session cleanup, anchors and viewport fitting, runtime
  visibility/reduced motion, and data-driven meter/counter animation.
- Delayed entrances, finite scalar timelines, bounded spring curves, and transformed drawing/button
  groups with polygon hit targets. Rotated/scaled editors, sliders, scroll containers, and native
  item/entity slots remain unsupported; use translation for those.
- Atlas, a motion studio, live HUD editor, and entry point to the existing design/control examples.


- Native retained rendering with a cached GPU texture.
- Texture-safe grayscale text smoothing, so PaneLib text stays neutral and crisp after Minecraft samples the cached surface.
- Custom Java drawing plus gradients, procedural texture, shadows, blur, clipping, and transforms.
- Default and per-instance restylable buttons, code-drawn icon buttons, toggles, ranged sliders,
  checkboxes, radio options, tabs, badges, removable tags, progress bars, and selectable regions.
- Responsive rows, columns, weighted splits, adaptive grids, stacks, gaps, and spacers.
- Smooth clipped wheel/trackpad scrolling with bounded CPU rasterization and aligned input targets.
  Ordinary scroll containers upload full-width scanline bands with unchanged guard rows.
- Independent toast overlays preserve virtual-list composition, plus reusable bounded group fades,
  multiplicative nested opacity, and runtime reduced-motion controls.
- Three translucent functional design references: inventory, playful/open, and tactical/compact.
- GPU-composited fixed-height lists with a bounded row atlas, partial row uploads, and zero page
  rerasterization while scrolling. Rows share one fractional transform and guarded atlas edges, so
  separators and baselines keep constant spacing during motion. A warmed reusable row-buffer pool
  keeps the steady scroll hot path allocation-free.
- Tab and Shift-Tab traversal, visible focus rings, keyboard activation, public focus control, and nested modal focus scopes.
- Single-line text, password fields, and soft-wrapped multiline textareas with keyboard editing,
  clipboard support, pointer caret placement, drag selection, double-click selection, configurable typography,
  bounded undo/redo, and codepoint-safe navigation/deletion. See [input and focus](input-and-focus.md).
- Delayed native tooltips for controls and custom painted rectangles, including independent
  pixel-stable overlays for virtual-list rows.
- Anchored action menus with item selection, click-away dismissal, and Escape dismissal.
- Single-value dropdowns backed by normal screen state.
- Fully customizable native confirmation dialogs with confirm, cancel, Escape, and backdrop dismissal.
- Fully customizable edge-anchored toasts: icon, colors, radius, placement, duration, close button, stack limit, spacing, and fade speed.
- Native item and entity overlays.
- Nine real, distinct showcase screens that demonstrate the same library can support very different UI systems.
- A native YouTube-style consumer reference page with responsive cards, typed search, filters, menus, toasts, and confirmation.
- Bundled custom fonts: a document theme can set one family for built-in controls and overlay chrome, and custom painters can use it too.
- Opt-in Minecraft component and legacy MOTD text with RGB colors, bold, italic, underline,
  strikethrough, stable obfuscation, newlines, Unicode, and style-preserving ellipsis.
- Minecraft texture cover fills and source-region crops, including a live player-skin face inside a circular clip.
- Isolated internal actions plus typed consumer events.
- Per-control retained animations that stop scheduling repaints when complete.
- Parent/title panorama, in-world, and full-parent backdrops beneath transparent retained UI.
- Raw keyboard and target-aware pointer callbacks, including true double-click handling.
- Parent restoration and consumer-controlled Escape/back handling.
- Thread-safe completion-driven invalidation for remote logos, pings, and other asynchronous data,
  including single-row `invalidateVirtualItem` updates.
- Render-viewport synchronization that reallocates stale surfaces during native window resizing.
- Locally publishable Maven metadata, sources JAR, Javadoc JAR, API unit tests, and artifact-boundary verification.

Every item above has been built and checked in the real Minecraft client through the showcase.

## Not ready to promise yet

- Hosting the artifact on a public Maven repository and final 1.0 compatibility guarantees.
- Automated visual regression checks.
- Windows and Linux GPU profiling.
- Minecraft narrator/accessibility semantics.
- IME text composition.

The active implementation order lives in the repository's `TODO.md`. This page records what is
safe to use today; it is not a release roadmap.
