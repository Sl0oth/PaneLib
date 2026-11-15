package dev.pane.ui;

import java.awt.Font;
import java.util.List;

/**
 * Labelled settings rows laid out top to bottom by {@link UiContext#form}: a label on the left and
 * its control on the right. Each control's id is its state key, so {@link UiScreen#settings} saves a
 * whole form by naming the same keys. Use {@link #row} for anything the form has no row for.
 */
public final class UiForm {
    private static final float ROW = 40, CONTROL = 28;
    private final UiContext ui;
    private final boolean paint;
    private final float x, top, width;
    private float y;

    UiForm(UiContext ui, boolean paint, float x, float y, float width) {
        this.ui = ui;
        this.paint = paint;
        this.x = x;
        this.top = y;
        this.y = y;
        this.width = Math.max(0, width);
    }

    /** Height of the rows so far. */
    public float height() { return y - top; }

    private float controlWidth() { return Math.max(Math.min(width, 96), Math.min(260, width * .46F)); }

    private UiRect control(float rowHeight, float controlHeight) {
        float w = controlWidth();
        return new UiRect(x + width - w, y + (rowHeight - controlHeight) / 2, w, controlHeight);
    }

    private void label(String label, float rowHeight) {
        if (!paint || label == null || label.isEmpty()) return;
        UiCanvas canvas = ui.canvas();
        String family = ui.theme().fontFamily();
        String shown = canvas.ellipsize(label, Math.max(0, width - controlWidth() - 12), 11, Font.PLAIN, family);
        canvas.text(shown, x, y + Math.min(rowHeight, ROW) / 2 - canvas.textHeight(11, Font.PLAIN, family) / 2, 11,
            ui.theme().text(), Font.PLAIN, family);
    }

    /** A heading above the rows that follow. */
    public UiForm section(String title) {
        if (height() > 0) y += 10;
        if (paint) {
            ui.canvas().text(title == null ? "" : title, x, y + 6, 12, ui.theme().text(), Font.BOLD, ui.theme().fontFamily());
            ui.canvas().fill(ui.theme().border(), x, y + 29, width, 1);
        }
        y += 34;
        return this;
    }

    /** Muted explanatory text across the form's width, wrapped onto as many lines as it needs. */
    public UiForm note(String text) {
        String family = ui.theme().fontFamily();
        List<String> lines = ui.canvas().wrapText(text == null ? "" : text, width, 10, Font.PLAIN, family);
        float line = 15;
        if (paint) for (int i = 0; i < lines.size(); i++)
            ui.canvas().text(lines.get(i), x, y + 2 + i * line, 10, ui.theme().mutedText(), Font.PLAIN, family);
        y += lines.size() * line + 8;
        return this;
    }

    /** An on/off switch stored as a boolean under {@code key}. */
    public UiForm toggle(String label, String key, boolean fallback) {
        label(label, ROW);
        if (paint) ui.toggle(key, x + width - 31, y + (ROW - 18) / 2, fallback);
        y += ROW;
        return this;
    }

    /** A slider from {@code minimum} to {@code maximum} stored as a number under {@code key}, with its value shown. */
    public UiForm slider(String label, String key, double minimum, double maximum, double fallback) {
        label(label, ROW);
        if (paint) {
            UiRect area = control(ROW, 16);
            float number = 40;
            double value = ui.slider(key, area.x() + 8, area.y(), area.width() - number - 16, minimum, maximum, fallback);
            boolean whole = minimum == Math.rint(minimum) && maximum == Math.rint(maximum) && maximum - minimum >= 2;
            String shown = whole ? Long.toString(Math.round(value)) : UiContext.formatNumber(Math.round(value * 100) / 100D, .01);
            String family = ui.theme().fontFamily();
            ui.canvas().text(shown, area.right() - ui.canvas().textWidth(shown, 10, Font.PLAIN, family),
                y + ROW / 2 - ui.canvas().textHeight(10, Font.PLAIN, family) / 2, 10, ui.theme().mutedText(), Font.PLAIN, family);
        }
        y += ROW;
        return this;
    }

    /** A single-line text field stored as a string under {@code key}. */
    public UiForm text(String label, String key, String placeholder, String fallback) {
        label(label, ROW);
        if (paint) ui.textField(key, placeholder, control(ROW, CONTROL), fallback);
        y += ROW;
        return this;
    }

    /** A dropdown stored as the chosen option's value under {@code key}. */
    public UiForm dropdown(String label, String key, String fallback, List<UiSelectOption> options) {
        label(label, ROW);
        if (paint) ui.dropdown(key, control(ROW, CONTROL), fallback, options);
        y += ROW;
        return this;
    }

    /** A number with minus and plus buttons, stored under {@code key}. */
    public UiForm stepper(String label, String key, double minimum, double maximum, double step, double fallback) {
        label(label, ROW);
        if (paint) {
            UiRect area = control(ROW, CONTROL);
            float w = Math.min(area.width(), 132);
            ui.stepper(key, area.right() - w, area.y(), w, minimum, maximum, step, fallback);
        }
        y += ROW;
        return this;
    }

    /** A key binding stored as a GLFW key code under {@code key}. */
    public UiForm keybind(String label, String key, int fallbackKey) {
        return keybind(label, key, fallbackKey, false);
    }

    /** A key binding that, with {@code mouseButtons}, also takes a mouse button; see {@link UiKeyNames#mouse}. */
    public UiForm keybind(String label, String key, int fallbackKey, boolean mouseButtons) {
        label(label, ROW);
        if (paint) {
            UiRect area = control(ROW, CONTROL);
            float w = Math.min(area.width(), mouseButtons ? 150 : 132);
            ui.keybind(key, area.right() - w, area.y(), w, area.height(), fallbackKey, mouseButtons);
        }
        y += ROW;
        return this;
    }

    /** A colour picker stored as hex text under {@code key}; read it with {@code colorValue}. */
    public UiForm color(String label, String key, int fallback, boolean opacity) {
        float picker = UiContext.colorPickerHeight(opacity), rowHeight = picker + 12;
        label(label, ROW);
        if (paint) {
            UiRect area = control(rowHeight, picker);
            ui.colorPicker(key, area.x(), area.y(), area.width(), fallback, opacity);
        }
        y += rowHeight;
        return this;
    }

    /** A button in the control column that runs {@code action}; its id is the action. */
    public UiForm button(String label, String action) {
        if (paint) {
            UiRect area = control(ROW, 30);
            float w = Math.min(area.width(), 132);
            ui.button(action, label, action, new UiRect(area.right() - w, area.y(), w, area.height()));
        }
        y += ROW;
        return this;
    }

    /**
     * Reserves a full-width row {@code height} tall and returns it, for content the form has no row for.
     * Draw into it only when {@link #painting()} is true; the form also runs once just to measure.
     */
    public UiRect row(float height) {
        UiRect row = new UiRect(x, y, width, Math.max(0, height));
        y += row.height();
        return row;
    }

    /** False during the measuring pass, when nothing should be drawn or registered. */
    public boolean painting() { return paint; }

    /** The context rows draw with, for use with {@link #row}. */
    public UiContext ui() { return ui; }
}
