# Build the PaneLib paper-craft look natively

The PaneLib marketing design uses paper as its brand theme. It is an example of the library, not
a renderer limitation. Every part of that design maps to the public native API:

| Marketing treatment | Native PaneLib API |
|---|---|
| Cream table and cardstock sheets | `fill`, `roundRect`, `card` |
| Paper fibre | `grain` or `dots` inside a clip |
| Cut 0–3px corners | per-corner `roundRect` and `strokeRoundRect` |
| Zero-blur cut shadow | a translated dark shape before the card |
| Soft resting shadow | `shadow` |
| Fold crease | translucent `UiPath` triangle or `gradientRect` |
| Dashed cut line / focus guide | `dashedLine`, `dashedRoundRect` |
| Tilted clippings | `withRotation` |
| Masking tape | translucent rotated `roundRect` plus `grain` |
| Punched hole | `circle` plus `strokeCircle` |
| Display, body, mono, and handwritten type | register bundled fonts and call `text(..., family)` |
| Buttons, checkbox, radio, switch, tabs, select | built-in controls or `UiButtonStyle` |
| Fast paper movement | `ui.animate(..., UiEasing.PAPER)` |
| Blurred modal scrim | native `backdropBlur`; dialogs use it automatically |
| Player skin or block texture on a paper card | texture calls inside rounded/circle clips |

No bitmap paper texture, HTML, CSS runtime, browser, or generated image is needed.

## A cut-paper card

```java
static void paperCard(UiContext ui, String id, UiRect box, String title, String body) {
    UiCanvas g = ui.canvas();
    boolean hover = ui.hovered(id);
    boolean press = ui.pressed(id);
    float lift = ui.animate(id + ":lift", hover && !press, 150);
    float push = ui.animate(id + ":push", press, 90);
    float dx = -lift + push * 3;
    float dy = -lift + push * 4;

    g.withTranslation(dx, dy, () -> {
        // The signature stiff-paper shadow is geometry, not a blur.
        g.roundRect(0x6622201C, box.x() + 3, box.y() + 4,
            box.width(), box.height(), 3);
        g.roundRect(0xFFFBF7EF, box.x(), box.y(), box.width(), box.height(), 3);
        g.strokeRoundRect(0xFF22201C, 1.5F,
            box.x(), box.y(), box.width(), box.height(), 3);
        g.withRoundedClip(box.x(), box.y(), box.width(), box.height(), 3,
            () -> g.grain(0x10000000, .06F,
                box.x(), box.y(), box.width(), box.height()));
        g.text(title, box.x() + 16, box.y() + 15, 14,
            0xFF22201C, Font.BOLD, "paper-display");
        g.wrappedText(body, box.x() + 16, box.y() + 42,
            box.width() - 32, 9, 14, 0xFF615C53,
            Font.PLAIN, "paper-body");
    });

    ui.hit(id, "open-card", box.x(), box.y(), box.width(), box.height());
}
```

## One-flourish rule

The marketing system deliberately budgets one strong paper flourish per screen. PaneLib can draw
many at once, but restraint keeps the result from looking like a scrapbook template. Use texture
on broad surfaces, then choose one of tape, tilt, fold, punched holes, or a torn edge as the focal
detail.

The `Field Notes` gallery screen is the current native proof for warm stock, fibre, editorial
type, input, dropdown, scrolling, and restrained green ink. The clean-room example mod should
rebuild the complete marketing component sheet using only this documentation and the published
artifact; any missing capability is a PaneLib bug, not something the consumer should work around.
