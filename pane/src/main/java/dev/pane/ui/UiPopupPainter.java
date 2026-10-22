package dev.pane.ui;

import static java.util.Objects.requireNonNullElse;

import java.util.List;

/** Paints PaneLib's built-in menu and confirmation dialog. Open state and input stay with the screen. */
final class UiPopupPainter {
    static final String MENU_ITEM_PREFIX = "menu-item:";
    private static final UiDialogStyle NO_STYLE = UiDialogStyle.builder().build();

    private UiPopupPainter() { }

    /** Card, scrolled copy and both actions; unset style values inherit the document theme. */
    static void dialog(UiCanvas canvas, UiDialogLayout layout, UiDialogStyle requested, UiTheme theme, float offset,
                       String hovered, String pressed, String focused) {
        UiDialogStyle style = requireNonNullElse(requested, NO_STYLE);
        int shadow = requireNonNullElse(style.shadow(), 0x88000000);
        int surface = requireNonNullElse(style.surface(), theme.raisedSurface());
        int border = requireNonNullElse(style.border(), theme.border());
        int title = requireNonNullElse(style.titleColor(), theme.text());
        int message = requireNonNullElse(style.messageColor(), theme.mutedText());
        float radius = requireNonNullElse(style.radius(), 12F);
        UiRect card = layout.frame(), content = layout.content();
        canvas.shadow(shadow, 12, 0, 4, card.x(), card.y(), card.width(), card.height(), radius);
        canvas.roundRect(surface, card.x(), card.y(), card.width(), card.height(), radius);
        canvas.strokeRoundRect(border, .9F, card.x(), card.y(), card.width(), card.height(), radius);
        canvas.withClip(content.x(), content.y(), content.width() - Math.min(6, content.width() / 4), content.height(), () -> {
            for (int i = layout.firstVisible(offset); i < layout.lines().size(); i++) {
                var line = layout.lines().get(i);
                if (line.top() >= offset + content.height()) break;
                var typography = line.title() ? layout.titleText() : layout.messageText();
                canvas.text(line.text(), content.x(), content.y() + line.top() - offset, typography.size(),
                    line.title() ? title : message, typography.fontStyle(), typography.family());
            }
        });
        if (layout.maxOffset() > 0) {
            float thumb = Math.min(content.height(), Math.max(8, content.height() * content.height() / layout.contentHeight()));
            float y = content.y() + (content.height() - thumb) * offset / layout.maxOffset();
            canvas.roundRect(theme.mutedText(), content.right() - 2, y, 2, thumb, 1);
        }
        int cancelText = requireNonNullElse(style.cancelText(), theme.text());
        int confirmBackground = requireNonNullElse(style.confirmBackground(), theme.accent());
        action(canvas, layout, "dialog-cancel", layout.cancelLabel(), layout.cancel(),
            requireNonNullElse(style.cancelBackground(), theme.surface()),
            requireNonNullElse(style.cancelBorder(), theme.border()),
            cancelText, radius, hovered, pressed, focused);
        action(canvas, layout, "dialog-confirm", layout.confirmLabel(), layout.confirm(), confirmBackground, confirmBackground,
            requireNonNullElse(style.confirmText(), theme.accentText()),
            radius, hovered, pressed, focused);
    }

    private static void action(UiCanvas canvas, UiDialogLayout layout, String id, String label, UiRect box, int background,
                               int border, int text, float radius, String hovered, String pressed, String focused) {
        float curve = Math.min(Math.min(8, radius), Math.min(box.width(), box.height()) / 2);
        canvas.roundRect(background, box.x(), box.y(), box.width(), box.height(), curve);
        if (id.equals(hovered) || id.equals(pressed))
            canvas.roundRect(id.equals(pressed) ? 0x20000000 : 0x18FFFFFF, box.x(), box.y(), box.width(), box.height(), curve);
        canvas.strokeRoundRect(border, .8F, box.x() + 1, box.y() + 1, Math.max(0, box.width() - 2), Math.max(0, box.height() - 2), curve);
        if (id.equals(focused) && box.width() > 6 && box.height() > 6)
            canvas.strokeRoundRect(text, 1.2F, box.x() + 3, box.y() + 3, box.width() - 6, box.height() - 6, Math.max(0, curve - 2));
        var typography = layout.buttonText();
        canvas.withClip(box.x(), box.y(), box.width(), box.height(), () ->
            canvas.centeredText(label, box.centerX(), box.centerY(), typography.size(), text, typography.fontStyle(), typography.family()));
    }

    /** Menu frame and visible rows. Adds the body and one hit per visible row to {@code hits}. */
    static void menu(UiCanvas canvas, List<UiHitRegion> hits, UiMenu menu, UiPopupLayout layout, float offset,
                     String hovered, String focused, UiTheme theme) {
        UiRect frame = layout.frame(), viewport = layout.viewport();
        UiMenuStyle style = menu.style();
        String family = style.fontFamily() == null ? theme.fontFamily() : style.fontFamily();
        canvas.shadow(0x72000000, 7, 0, 3, frame.x(), frame.y(), frame.width(), frame.height(), style.radius());
        canvas.roundRect(requireNonNullElse(style.background(), theme.raisedSurface()), frame.x(), frame.y(), frame.width(), frame.height(), style.radius());
        if (style.borderWidth() > 0) canvas.strokeRoundRect(requireNonNullElse(style.border(), theme.border()), style.borderWidth(),
            frame.x(), frame.y(), frame.width(), frame.height(), style.radius());
        hits.add(new UiHitRegion("menu-body:" + menu.id(), UiContext.internalAction("menu-body"), frame.x(), frame.y(), frame.width(), frame.height()));
        float gutter = layout.maxOffset() > 0 ? Math.min(8, viewport.width()) : 0;
        canvas.withClip(viewport.x(), viewport.y(), viewport.width() - gutter, viewport.height(), () -> {
            for (int i = 0; i < menu.items().size(); i++) {
                UiRect hit = layout.row(i, offset);
                if (hit.height() <= 0) continue;
                UiMenuItem item = menu.items().get(i);
                float rowY = viewport.y() + i * layout.rowHeight() - offset;
                String itemId = "menu:" + menu.id() + ":" + item.id();
                boolean lit = item.enabled() && (itemId.equals(hovered) || itemId.equals(focused));
                if (item.separated() && i > 0) canvas.fill(UiCanvas.fade(requireNonNullElse(style.text(), theme.text()), .14F),
                    frame.x() + style.paddingX() - 2, rowY, Math.max(0, frame.width() - style.paddingX() * 2 + 4 - gutter), .8F);
                float inset = item.separated() && i > 0 ? 2 : 0;
                if (lit) canvas.roundRect(requireNonNullElse(style.highlight(), UiCanvas.fade(theme.accent(), .18F)),
                    frame.x() + style.rowInset(), rowY + inset, Math.max(0, frame.width() - style.rowInset() * 2 - gutter), layout.rowHeight() - inset, style.rowRadius());
                float textY = rowY + inset / 2 + Math.max(0, (layout.rowHeight() - canvas.textHeight(style.textSize(), style.fontStyle(), family)) / 2);
                int ink = requireNonNullElse(style.text(), theme.text());
                // A highlighted row on an accent fill reads in white; disabled rows are dimmed.
                int color = !item.enabled() ? UiCanvas.fade(ink, .38F) : lit && style.highlight() != null && (style.highlight() >>> 24) > 200 ? 0xFFFFFFFF : ink;
                canvas.withClip(frame.x(), rowY, Math.max(0, frame.width() - gutter), layout.rowHeight(), () -> {
                    canvas.text(item.label(), frame.x() + style.paddingX(), textY, style.textSize(), color, style.fontStyle(), family);
                    if (!item.shortcut().isEmpty()) {
                        float width = canvas.textWidth(item.shortcut(), style.textSize(), style.fontStyle(), family);
                        canvas.text(item.shortcut(), frame.right() - gutter - style.paddingX() - width, textY, style.textSize(),
                            lit ? color : UiCanvas.fade(color, .55F), style.fontStyle(), family);
                    }
                });
                if (item.enabled()) hits.add(new UiHitRegion(itemId, UiContext.internalAction(MENU_ITEM_PREFIX + item.action()),
                    hit.x(), hit.y(), Math.max(0, hit.width() - gutter), hit.height()));
            }
        });
        if (layout.maxOffset() > 0 && viewport.width() > 8) {
            float thumb = Math.min(viewport.height(), Math.max(10, viewport.height() * viewport.height() / layout.contentHeight()));
            float y = viewport.y() + (viewport.height() - thumb) * offset / layout.maxOffset();
            canvas.roundRect(theme.mutedText(), viewport.right() - 4, y, 2, thumb, 1);
        }
    }
}
