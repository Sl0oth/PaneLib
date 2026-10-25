# Native Minecraft text

Use `NativeOverlayContext.text` for vanilla font labels on `UiHud` or `UiScreen`. Minecraft draws
the glyphs directly, including resource-pack fonts, translations, colors, bold/italic and shadow.
PaneLib owns slot conversion, scaling, rectangular clipping, HUD fade and overlay ordering.
No raw `graphics()` calls, font copying or GUI-scale arithmetic are needed.

## Smallest example

```java
UiHud hud = new UiHud(180, 32, UiTheme.dark(), ui -> {
    ui.canvas().fill(0xFF202020, 0, 0, 180, 32);
    ui.nativeSlot("title", 8, 8, 164, 12);
}, overlay -> overlay.text("title", "Hello, Minecraft!"))
    .attach(Identifier.fromNamespaceAndPath("your_mod", "greeting"));
```

The same callback works on a `UiScreen`:

```java
new UiScreen(document, overlay -> overlay.text("title", "Hello, Minecraft!"), UiEventListener.NONE);
```

The document painter must reserve the matching `title` slot. A missing slot submits nothing.
Close the HUD when its owning feature is removed; `setEnabled(false)` deactivates it reversibly.

## Size and appearance

Slot height is **one line height**, not a font-size parameter plus a container height. Minecraft's
normal line is 9 font units; a slot 18 layout units tall scales it to twice that height. Width limits
the line without stretching glyphs horizontally. Slot coordinates follow the same logical layout
rules as native items and player previews, including placement magnification and responsive fitting.
Integer physical glyph scaling gives the crispest pixel-art result; fractional scaling follows
Minecraft's renderer and is not a promise of identical pixels at every display scale.

```java
var style = UiMinecraftTextStyle.label() // dark text, no shadow, for light vanilla panels
    .color(0xFF256B13)                  // explicit ARGB
    .alignment(UiMinecraftTextStyle.Alignment.RIGHT);

overlay.text("level", "Level " + level, style);
```

`hud()` defaults to opaque white, shadow, left alignment and ellipsis. `label()` defaults to dark
gray without shadow. Both return immutable shared presets; fluent methods return new values.
Alignments are LEFT/CENTER/RIGHT. `Overflow.ELLIPSIS` measures/truncates with Minecraft's own glyph
metrics and preserves component styles; very narrow slots may show fewer dots or nothing.
`Overflow.CLIP` preserves the full line and clips it to the slot instead. Shadow width is included
in the fit. For an unbounded title, reserve sufficient width explicitly.

Text is a **single-line native label**, not an editor or a wrapping paragraph widget. Supply one
line per slot. Existing `UiCanvas.text` and editor/dialog typography continue to use Java2D/TTF;
this API does not silently change their measurement, selection or rendering behavior. There is
still no complete vanilla widget theme; the example below is a supported composition recipe.

## Translations, styled components and resource fonts

```java
Component title = Component.translatable("container.inventory");
overlay.text("title", title, UiMinecraftTextStyle.label());

Component runes = Component.literal("RESOURCE FONT").withStyle(style -> style.withFont(
    new FontDescription.Resource(Identifier.withDefaultNamespace("alt"))));
overlay.text("runes", runes);
```

Imports for the second example are `net.minecraft.network.chat.Component`,
`net.minecraft.network.chat.FontDescription` and `net.minecraft.resources.Identifier` on 26.2.
A mod's font JSON uses its usual `assets/<namespace>/font/<name>.json` resource and matching
identifier. Minecraft owns provider loading, fallback glyphs, language order and glyph textures.
No separate font registry or automatic downloads are introduced.

Component RGB styles override the base RGB as in vanilla; the style's ARGB alpha and inherited
HUD opacity still apply. Minecraft's shadow style is preserved too. Reuse components for static
labels. Changed values, including in-place child/style edits and component translation arguments,
are snapshotted so vanilla's old visual-order cache cannot leave a stale label. Resource/language
reloads remeasure through the current providers; consumers do not need to clear a second cache.

## Composition and efficiency

Native text draws after the retained canvas, alongside native items/previews. Menus, dialogs and
registered `nativeOverlayLayer` covers remain above it. Native slots are rectangular: rounded
Java2D clips do not clip native glyphs, and partially scrolled native slots are suppressed under
the existing slot rules. Reserve padding where necessary. Drawing a transparent label submits
nothing; hiding/F1/screen suppression/disable/close follow the owner's normal lifecycle.

Lines are cached per owner and slot, with an upper bound of 256 entries. Stable strings/components
reuse measurement, translation order and ellipsis; Minecraft still submits glyphs each frame.
Changing native text alone does not require repainting/uploading the CPU panel. If that change
also alters the panel or slot geometry, update/invalidate the retained UI normally.

`nativeTextCount()` on `UiHud`/`UiScreen` reports the latest frame's text submissions, not visible
glyph count. `nativeTextLayoutCount()` is cumulative and exposes remeasurement, including reloads.
Caches are released with the owner; there is no global growing text cache or extra raster surface.

## Tested example

[`VanillaHudExample`](../pane-demo/src/main/java/dev/pane/demo/VanillaHudExample.java) combines a
vanilla bevel, heart textures, four native item slots and three native text labels. It supplements
the vanilla HUD and never modifies the player. The factory returns an unattached HUD; callers own
attachment, placement and close. It replaces the old font-less restoration example.

Build and run the isolated fixture:

```sh
./gradlew :pane:build :pane-demo:classes :pane-demo:configureClientLaunch --no-daemon --max-workers=2
python3 examples/wayfarer-hud/launch.py --font-test
```

This copies the existing private fixture world to `pane-demo/run-native-font`, creates a temporary
font pack there, tests the HUD, then disconnects and tests a world-free screen. It exits afterward.
It never selects a personal save. The existing `--restoration-test` also exercises the migrated
example.
