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

For a large custom page, give a custom animation its affected bounds. PaneLib will then repaint and
upload just that rectangle while it runs. The bound must include the control's shadow, glow, and
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
affected viewport and submits only that rectangle to the GPU. `virtualList` goes further for long,
fixed-height result sets: a bounded GPU row atlas moves independently from the page surface, and
only newly visible or explicitly invalidated rows are partially uploaded.

Call `screen.invalidate()` only when external mod data used by the page or many rows changes. For
one async list result, use `screen.invalidateVirtualItem(listId, index)`. Both are safe from an
asynchronous completion thread; `invalidateWhenComplete(stage)` wires a page request directly.
PaneLib already invalidates itself for its controls, navigation, overlays, and finite animations.
Do not call `invalidate()` every client tick. See [Minecraft integration](minecraft-integration.md)
for async server data and transparent panorama-backed screens.
