# Pane — one thing at a time

## Latest usable iteration: Atlas / HUD / composable motion

The current implementation adds finite timelines, staggered entrances, bounded spring curves,
affine drawing/button groups, cached transformed drawing, and a retained input-free HUD lifecycle.
Atlas demonstrates these with real game telemetry in a dedicated disposable world. Measurements,
verification scope, and remaining limits: `docs/atlas-iteration-2026-10-16.md`.

Next focused work: independent menu/dialog composition, broader backend/high-refresh profiling,
and extending affine input beyond buttons without compromising editing or scroll behavior.
This is a usable iteration, not a completed 1.0 certification.


## Done: scrolling + layout

**Goal:** a developer can build a screen taller than the window without hand-calculating every position.

Done means:

- Rows, columns, stacks, gaps, and spacers reserve responsive rectangles.
- A scroll container handles wheel input and keeps controls clickable.
- The long Settings page proves it in the real client.
- In-game verification passes: scrolling, controls, resizing, and idle repainting.

## Done: keyboard focus + text input

**Goal:** a developer can build an accessible config or search form without inventing their own input system.

Done means:

- Tab and Shift-Tab move a visible focus ring between controls.
- Enter/Space activate focused buttons, toggles, and selection controls.
- A text field supports typing, selection, backspace/delete, and clipboard paste.
- The client was launched in the scratch world with a real form open for review.

## Done: native tooltips

**Goal:** a developer can explain an unfamiliar control without building their own hover system.

Done means:

- A tooltip anchors to any painted control or a custom rectangle.
- It stays inside the screen and never blocks the control being explained.
- It has a short hover delay, so it does not flicker while moving the mouse.
- The client opened in the scratch world with tooltips ready to inspect.

## Done: native menus

**Goal:** a developer can offer a compact list of actions without writing their own popover and dismissal logic.

Done means:

- A menu opens from a painted control and anchors to it.
- Selecting an item emits a normal action event and closes the menu.
- Clicking away or pressing Escape closes it safely.
- The client opened in the scratch world with a real menu ready to inspect.

## Done: native dropdowns

**Goal:** a developer can let a player choose one value from a compact list without writing their own selector.

Done means:

- A dropdown opens from a painted control and anchors to it.
- Choosing an option updates a string state value and closes the list.
- Clicking away or pressing Escape closes it safely.
- The client opened in the scratch world with a real dropdown ready to inspect.

## Done: native dialogs

**Goal:** a developer can ask for an important confirmation without building their own modal layer and input blocking.

Done means:

- A dialog sits above the page and receives interaction first.
- Confirm and cancel actions close it cleanly.
- Escape and clicking the backdrop cancel it safely.
- In-game verification passes for confirm, cancel, Escape, and backdrop dismissal.

## Done: toasts

**Goal:** a developer can acknowledge a completed action without inventing temporary UI, timers, or safe screen-edge placement.

Done means:

- A toast can show an icon-free title and short message without covering the active control.
- It can dismiss itself after a configurable duration and can be manually dismissed.
- Several toasts stack predictably instead of covering each other.
- In-game verification passes for showing, stacking, manual dismissal, and automatic expiry.

## Done: full toast customization

**Goal:** a developer can make toasts match their own UI rather than Pane's default look.

Done means:

- A toast can choose its icon, colors, corner, duration, radius, and whether it has an × close button.
- A document can choose default placement, stack limit, margin, gap, and fade duration.
- The default still follows the document theme with no extra setup.
- The Waypoints reference screen proves the options in the real client before the other examples use them.

## Done: four additional functional UI examples

**Goal:** prove Pane's customization through four new working screens with styles unlike the existing gallery.

Done means:

- Each screen uses real controls and state, not a static picture.
- Each intentionally shows a different component combination and visual direction.
- New features are added to the reference screen first, then only copied after their real-client test passes.
- All four new screens have a real interaction check and scratch-world capture.

## Done: YouTube-style native consumer test

**Goal:** prove Pane can build a dense, familiar consumer website layout without HTML, Chromium, or a web view.

Done means:

- A real-client page has a desktop header, search, navigation rail, topic chips, and responsive video grid.
- Search, topic filtering, navigation, video menus, toasts, and a confirmation dialog all do real work.
- It uses native drawing and local sample data only, so it is a layout/interaction reference rather than a YouTube client.
- The scratch-world launcher opens this screen directly for hands-on testing.

## Done: Minecraft textures and skins as clipped fills

**Goal:** a developer can use Minecraft-provided textures or a player skin inside a shaped Pane surface without content escaping its bounds.

Done means:

- A texture can fill a rounded or circular Pane shape and stay correctly clipped.
- A player skin can be shown as a clipped avatar without an external image system.
- It scales cleanly with the UI and does not turn the renderer into a web view.
- The client opens in the scratch world with texture and skin fills ready to inspect.

## Done: release-candidate library gate

**Goal:** another mod can use PaneLib from its documentation and artifact without reading the
implementation or finding private workarounds.

Done means:

- The library publishes as `dev.pane:panelib:0.9.8` with sources and Javadocs.
- A standalone clean-room Fabric mod builds against that Maven artifact.
- Every API/docs mismatch found by the clean-room mod is fixed and rechecked.
- Unit tests, artifact-boundary checks, the full in-game harness, CI configuration, and zero idle
  repaints all pass.

## Next

1. Choose a public Maven host and run Windows/Linux GPU profiling before calling the API 1.0.
2. Take real screenshots for the README and the Modrinth gallery: PaneLib screens as a mod would
   ship them in a normal game, not the demo showcase pages.

## Parking lot

- Multi-stop gradients, textured strokes, and blend modes.
- More showcase pages.

## Working rule

We finish **Now** before taking on a larger new idea. Small fixes that unblock it are fine.
If something changes the goal, it goes in the parking lot first and I will bring us back here.

## Reference-screen rule

Every new feature starts on one strong reference screen and is tested in the real client there.
Only after that check passes do we add it to the other showcase screens.


My ideas (manual notes — not immediate)

1. **Partly done:** Pane already supports fully custom buttons and full-screen, parent-aware Minecraft screens. Still missing: a deliberate vanilla-screen integration layer for title, pause, inventory, and HUD replacements.
2. **Partly done:** Pane can paint PNG/Minecraft textures and provide selectable regions, but it needs a first-class `imageButton` API with transparent pixels, a supplied hit shape, focus, tooltip, and keyboard activation.
3. Pulling from APIs or websites belongs in the consumer mod, not PaneLib's rendering core. Pane should keep providing safe async invalidation for data that a mod fetches.
4. YouTube/video playback should stay out of core PaneLib. It needs a browser/video runtime and would break the native-only, low-idle-rendering direction; only consider it later as a separate optional addon.
5. ~~Eventually add page-loading and control animations.~~ **Done:** `ui.animate(...)` provides finite animations that stop repainting when complete. Deliberate continuous idle animation remains out of scope so idle repainting stays at zero.
6. **Partly done:** Pane can place native item/entity overlays inside Pane screens. Now available: the documented `UiHud` retained gameplay layer with lifecycle, anchors, reduced motion, data invalidation, and no gameplay input capture. Native item/entity HUD slots remain future work.
