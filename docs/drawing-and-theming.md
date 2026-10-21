# Drawing, themes, animation, and performance

## Canvas

`UiCanvas` is the escape hatch and the foundation. It is not a fallback web renderer: each call
draws Java geometry into PaneLib's retained surface.

```java
UiCanvas g = ui.canvas();

g.roundRect(0xFFFBF7EF, x, y, w, h, 3);
g.strokeRoundRect(0xFF22201C, 1.5F, x, y, w, h, 3);
g.dashedRoundRect(0xFF777168, 1, 5, x + 8, y + 8, w - 16, h - 16, 2);
g.shadow(0x42000000, 7, 2, 3, x, y, w, h, 3);
g.grain(0x12000000, .08F, x, y, w, h);
```

The canvas supports:

- Flat rectangles, independent corner radii, circles, polygons, lines, and dashed lines.
- Free-form quadratic/cubic `UiPath` geometry with fill, gradient fill, and stroke.
- Linear and radial gradients, vignette, soft shadow, and glow.
- Procedural grain, dots, stripes, grids, and scanlines cached as reusable tiles.
- Rectangle, rounded rectangle, and circle clips.
- Translation, rotation, scaling, and grouped opacity.
- Font registration, tracked text, measurement, ellipsis, word wrapping, and wrapped drawing.
- Minecraft texture cover fills and exact source-region crops.
- Backdrop blur for modal scrims and small translucent overlays.

Backdrop blur reads pixels already painted in the current retained surface. Use it sparingly; it
has no idle cost, but it does add CPU work to the invalidation that paints it.

### Text measurement and alignment

Use `textWidth`, not a character-count estimate, for labels in proportional or custom fonts. It
returns the horizontal advance in the same logical canvas units as `text`, and uses the exact
same font size, `Font` style, and family. A null label measures as zero. The three-argument form
uses `UiCanvas.SANS`; pass the same family to the four-argument form when drawing with `text(...,
family)`. That family can be `SANS`, `SERIF`, `MONO`, a system family, or a name passed earlier to
`UiCanvas.registerFont` (an absent family falls back to `SANS`).

```java
import java.awt.Font;

String label = "Nearby trails";
float labelSize = 10;
float labelWidth = g.textWidth(label, labelSize, Font.BOLD, ui.theme().fontFamily());
float labelX = card.x() + (card.width() - labelWidth) / 2F;

g.text(label, labelX, card.y() + 18, labelSize, 0xFFF2F8FB,
    Font.BOLD, ui.theme().fontFamily());
```

For simple centre-on-both-axes placement, `g.centeredText(...)` is also available. Use
`textWidth` when the width feeds another layout calculation, such as centering, label+icon flow,
or a responsive card title.

## Themes

`UiTheme` supplies the defaults used by built-in controls. It does not restrict custom painting.
Create a complete theme with the record constructor or derive common changes:

```java
UiTheme theme = UiTheme.light()
    .withAccent(0xFF1BD96A)
    .withDensity(.95F)
    .withMotionScale(1)
    .withFontFamily("my-font");
```

For a one-off button, `UiButtonStyle` overrides every visual part without creating another theme:

```java
UiButtonStyle paperButton = UiButtonStyle.builder()
    .background(0xFFC94F3D)
    .hoverBackground(0xFFD9624E)
    .pressedBackground(0xFFAD3E30)
    .border(0xFF22201C)
    .text(0xFFFBF7EF)
    .focus(0xFF506B79)
    .radius(2)
    .borderWidth(1.5F)
    .shadow(0x55000000, 3, 2, 3)
    .build();

ui.button("save", "Save layout", "save-layout", x, y, 112, 36, paperButton);
```

## Finite animation

`ui.animate` retains a value by id. The first target is adopted immediately. When a later paint
changes the target, PaneLib interpolates from the current on-screen value and schedules only the
frames needed to finish it.

```java
UiRect animatedCard = new UiRect(x - 12, y - 16, width + 24, height + 32);
float hover = ui.animate("card-hover:" + card.id(), ui.hovered(card.id()), 150, animatedCard);
float lift = 2 * hover;

ui.withTranslation(-lift, -lift, moved -> {
    drawCard(moved.canvas(), card);
    moved.hit(card.id(), "open-card:" + card.id(), x, y, width, height);
});
```

Available curves are `LINEAR`, `EASE_IN`, `EASE_OUT`, `EASE_IN_OUT`, and `PAPER`. For an arbitrary
numeric target:

```java
float width = ui.animate("details-width", expanded ? 320 : 0, 220, UiEasing.EASE_IN_OUT);
```

All durations are multiplied by the document theme's `motionScale`; setting it to zero snaps
immediately and schedules no animation frames.

For a large custom page, give a custom animation its affected bounds. PaneLib clips repainting to that rectangle while it runs. The main surface uploads full-width
scanline bands spanning the affected rows, including guard rows; a narrow rectangle does not imply
a narrow GPU upload. The bound must include the control's shadow, glow, and
travel distance; the original overload remains safe when that is not practical.

```java
UiRect animatedCard = new UiRect(x - 12, y - 16, width + 24, height + 32);
float hover = ui.animate("project-lift", ui.hovered("project"), 140, animatedCard);
```

### Moving a custom group

Translate through `UiContext` rather than only `UiCanvas` when a moving custom group contains
click targets, tooltips, text inputs, or native Minecraft items/entities. This keeps the drawing,
input, and native overlay coordinates together.

```java
float lift = ui.animate("project-lift", ui.hovered("project"), 140) * 3;
ui.withTranslation(0, -lift, moved -> {
    moved.canvas().roundRect(card, x, y, width, height, 10);
    moved.nativeSlot("project-item", x + 24, y + 30, 64, 64);
    moved.hit("project", "open-project", x, y, width, height);
    moved.tooltip("project", "Open this project");
});
```

## Retained-surface quality

Choose the ceiling on the document:

```java
.renderSettings(UiRenderSettings.balanced())
// or highQuality(), lowMemory(), or new UiRenderSettings(maxWidth, maxHeight, maxScale)
```

This is a ceiling, not a forced allocation. PaneLib still targets the physical framebuffer and
uses the smallest surface that satisfies the current layout, display density, and configured cap.

Ordinary scroll animation does not upload that whole surface. PaneLib clips rasterization to the
affected viewport and uploads full-width scanline bands covering its affected rows. `virtualList` goes further for long,
fixed-height result sets: a bounded GPU row atlas moves independently from the page surface, and
only newly visible or explicitly invalidated rows are partially uploaded.

Call `screen.invalidate()` only when external mod data used by the page or many rows changes. For
one async list result, use `screen.invalidateVirtualItem(listId, index)`. Both are safe from an
asynchronous completion thread; `invalidateWhenComplete(stage)` wires a page request directly.
PaneLib already invalidates itself for its controls, navigation, overlays, and finite animations.
Do not call `invalidate()` every client tick. See [Minecraft integration](minecraft-integration.md)
for async server data and transparent panorama-backed screens.

### Animation state lifetime

Keep IDs stable while controls exist. PaneLib retains animation state for IDs used by the last
page paint and by cached virtual rows, including finished animations. Hidden/removed controls
and evicted rows release their entries; there is no idle cleanup timer. A reintroduced `animate`
ID adopts its first target immediately. `animateFrom` entrances and `sequence` tracks start
fresh after eviction, even if the sequence revision is unchanged. Keep business-level “played
once” flags in consumer state if an entrance must not replay after its control disappears.

Page changes and screen removal clear animation state. `screen.animationStateCount()` is a
client-thread diagnostic for the retained entry count, not a byte-memory measurement.

## Per-field appearance and custom controls

`UiInputStyle.appearance(...)` overrides individual paint values for text fields,
password fields and multiline editors. Unset values inherit the theme; typography,
caret placement and editing behavior stay shared. Existing six-argument styles and
existing field overloads remain available. Retain styles outside the paint loop.

```java
var whiteField = UiInputStyle.defaults().appearance(UiInputAppearance.defaults()
    .background(0xFFFFFFFF).text(0xFF202124).placeholder(0xFF5F6368)
    .radius(0).borderWidth(0).focus(0xFF0B57D0).focusWidth(1)
    .caret(0xFF0B57D0).selection(0x665588CC));
ui.textField("subject", "subject", "Subject", 20, 50, 300, 35, "", 256, whiteField);
```

`borderWidth(0)` removes the resting outline; `focusWidth(0)` separately removes
the focused outline. Keep a visible focus treatment for keyboard users. Custom
consumer dividers can be drawn after the field. Transparent backgrounds do not
paint an opaque surface over the content beneath them.

`UiIcon` now includes mail, inbox, archive, stars, send, clock, tag, file, pen,
settings and other commonly used toolbar symbols. `UiSymbol.vector` lets a
consumer author a reusable icon once in its own viewbox, with automatic aspect
ratio fitting and centering. It does not parse SVG files.

```java
UiSymbol mark = UiSymbol.vector(24, 24, (g, color, stroke) ->
    g.path().moveTo(4, 12).lineTo(10, 18).lineTo(20, 6).stroke(color, stroke));
mark.draw(ui.canvas(), 0xFF202124, 20, 20, 18, 1.5F);
UiIcon.MAIL.draw(ui.canvas(), 0xFF202124, 50, 20, 18, 1.5F);

ui.paintedButton("archive", "Archive selected messages", "archive",
    20, 90, 36, 36, 18, canArchive, (g, state) -> {
        if (state.hovered() || state.pressed()) g.circle(0xFFE8EAED, 38, 108, 18);
        UiIcon.ARCHIVE.draw(g, 0xFF202124, 28, 98, 20, 1.5F);
    });
```

The helper registers pointer/Tab/Enter/Space behavior, draws the focus ring after
custom painting, and dims disabled controls while excluding them from activation
and focus traversal. The callback receives label, enabled, hovered, pressed and
focused state. Use stable IDs and repaint after changing enabled state, as with
other controls. Painting uses the surrounding layout coordinates.

Labels become tooltips and are available through `UiScreen.buttonState(id)` as
semantic metadata from the last paint, including retained virtual rows. This is
not an automatic narrator or screen-reader bridge. Pointer shape remains the
registered rectangle, including for circular artwork.

## Popup menu styling

Both `menuButton` and `dropdown` accept an optional final `UiMenuStyle` argument.
Existing overloads use `UiMenuStyle.defaults()`. Popup styling is separate from the
trigger button or dropdown field styling.

```java
var menuStyle = UiMenuStyle.builder()
    .fontFamily("my-font").textSize(14).rowHeight(34).padding(12, 6)
    .background(0xFF18191C).text(0xFFDCDDDE).highlight(0xFF5865F2)
    .borderWidth(0).radius(6).rowRadius(4).rowInset(3).build();
ui.menuButton("server", "Server", 20, 20, 180, 32,
    UiContext.ButtonStyle.GHOST, UiContext.Shape.ROUNDED,
    List.of(new UiMenuItem("settings", "Server Settings", "settings")), menuStyle);
```

Metrics are layout units. `paddingX` is the label's horizontal inset; `paddingY`
is the popup's top/bottom padding. Text is vertically centered in each row and
clipped to it. Null colors/font family inherit the document theme. Font style,
border width/color and row highlight geometry are also configurable. Use a row
height large enough for the font. Numeric metrics reject non-finite/negative values;
font size and row height must be positive.

Styled row heights also drive wheel distance, PageUp/PageDown, keyboard reveal,
content height and clipped pointer targets. Styles survive transformed triggers
and retained virtual rows, and compare by value for retained popup caching.
