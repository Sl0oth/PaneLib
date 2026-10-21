# Move an existing mod to PaneLib

You do not need to rewrite the mod. Keep your configuration, packets, game logic, and data model;
replace one client screen first. PaneLib targets Fabric/Minecraft 26.2 and Java 25. It is not an
adapter for older Minecraft mappings, another loader, HTML/CSS, or existing vanilla widget trees.

Use the [installation guide](installation.md) to add the library JAR and declare the runtime
requirement. The library installs no opening key, menu button, command, or replacement HUD on its own.
The [independent starter](../examples/first-screen/README.md) is the smallest runnable reference.

## What changes

| Your current screen | PaneLib equivalent | What stays in your mod |
| --- | --- | --- |
| A `Screen` subclass that draws each frame | `UiDocument`, `UiPage`, and `UiScreen` | The action that opens it |
| Button widgets and click handlers | Stable control ids and typed `UiEvent` actions | Save, join, delete, or network operations |
| Widget-local draft values | `screen.setValue` / `stringValue` / `booleanValue` / `numberValue` | Saved configuration and validation |
| A `render` method reading mutable game data | A painter reading a snapshot, invalidated when it changes | Sampling that data on the client thread |
| Hand-scaled coordinates and mouse conversion | Document `UiScaling` plus layout coordinates | Your responsive design and minimum usable size |
| An item, player, or native font drawn directly | `nativeSlot` plus the native overlay callback | Choosing the item, appearance, or text |
| A Fabric HUD render callback | Attached `UiHud`, values, placement policy, and visibility | Data sampling and the toggle setting/key |
| Restoring the previous screen in Escape handling | `UiScreenOptions.builder().parent(previousScreen)` | Any custom unsaved-change policy |

## Migrate one settings screen

1. Keep the existing opening key/menu action. Capture the current Minecraft screen as the parent,
   create the PaneLib screen, seed its values from saved configuration, then open it. For a settings
   screen that should pause ordinary single-player play, opt into `UiScreenOptions.builder()
   .parent(parent).pauseGame(true).build()`. The default is non-pausing. Multiplayer/LAN worlds
   continue running, following Minecraft's own pause rules.
2. Use `UiScreen.create(document, overlays, (screen, event) -> { ... }, options)` for an event
   handler that receives its owner. No array holder or screen subclass is needed. Declare controls in a page painter. Use stable ids such as `volume-slider`; do not use the current
   list index as an id when rows can reorder. Give editable values their own stable state keys.
3. Handle `VALUE` events only for immediate preview behavior. On `ACTION` Save, read the screen's
   draft, validate it, and persist through your existing config code. Reconstructing the screen
   from saved configuration makes Cancel/Escape discard unsaved edits.
4. Let PaneLib repaint after controls/state changes. If outside data changes, invalidate its view
   explicitly. A painter may run more than once, so it must not send packets or write configuration.
5. Check keyboard Tab/Shift-Tab, Enter, Escape, a small window, GUI scale changes, and reopen after
   saving. Run these with the packaged JAR, not only a dependency on the library source project.

A `UiDocument` is a recipe, not your persistent model. A new `UiScreen` has new screen state unless
you seed it. Keep saved settings outside the screen. Treat complex state as immutable snapshots; create a new
record or `List.copyOf(...)` after a change. Repeated equal immutable scalar values do not repaint
a `UiScreen`; sending a mutable object with `UiScreen.setValue` still explicitly signals an update.
`UiHud` retains equality-based state comparisons, so use new snapshots or explicit invalidation there.
If you mutate an external model already read by a painter, call `invalidate()` on its owner. If a worker completes a request,
`screen.setValue` and `invalidate` queue their library updates safely; mutations to your own mutable
model still need client-thread coordination. See [async integration](minecraft-integration.md#asynchronous-server-data).

## Preserve your visual style

You do not have to use the default theme. `UiCanvas` draws shapes, images, text, borders, and texture;
control styles customize geometry, colors, and typography. Use `paintedButton`, `paintedMenuButton`,
and the other public painter variants for authored controls while retaining their hit targets and
focus semantics. Do not draw a blank invisible button and manually duplicate its interaction state.
See [control styling](ui-basics.md) and [drawing and theming](drawing-and-theming.md).

Register bundled fonts once during client initialization. Retained Java2D text supports custom
TTF/compatible OTF; Minecraft bitmap/resource-pack text uses the native text helper. These are
separate text paths. Native text currently handles single-line labels, not editable text areas or
wrapped paragraphs. [Native Minecraft text](native-minecraft-text.md) explains the supported styles.

For a title-screen player, use the public player-preview appearance and framing APIs rather than
creating a fake world/entity. For a HUD, attach one owner per widget, sample only its data, and
close it when permanently removing it. `setEnabled(false)` releases vanilla HUD replacement claims;
re-enabling lets the owner claim them again. Use `replaceVanilla` for the elements you actually
replace. See [HUD ownership and lifecycle](huds-and-composable-motion.md).

## Where adoption still needs care

- Vanilla widgets do not automatically convert to PaneLib controls. Move a screen's layout and
  event handling deliberately; keep unrelated vanilla screens as they are.
- HUD widgets are input-free. Use a `UiScreen` for clickable inventory/configuration interfaces.
- Native overlays are composited with reserved slots; they are not arbitrary Java2D layers.
  Use the public helpers for clipping, opacity, and pixel/GUI conversions.
- Minecraft narrator semantics and IME text composition are not complete. Do not promise those
  features to your players on the strength of a visual demo.
- Real-client validation so far is on macOS ARM64. Test Windows/Linux and your modpack before
  claiming support or performance there.
- A public Maven repository is not configured. The local candidate's repository ZIP supports an
  exact-module dry run without Maven Local; it does not establish a hosted service. Pin the matching
  version/checksum. `implementation` alone does not bundle the dependency for players.
- The current candidate has a new version, separate from the preserved submitted 0.9.8 snapshot.
  Root `gradle.properties` is authoritative in the checkout; a standalone starter's descriptor carries
  that value. Use a matched runtime/starter and retain the release manifest; do not mix old bundled
  JARs with current API examples.

Keep the first migration small. A successful one-screen integration should establish your opening
flow, saved/draft state, style, resize behavior, packaging, and client testing before replacing a HUD
or a large multi-page interface.
