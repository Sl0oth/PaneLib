package dev.pane.ui;

import dev.pane.ui.internal.NativeOpacity;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;

/** Bounded, owner-local line layout cache. Minecraft continues drawing the glyphs every frame. */
final class NativeTextRenderer {
    private static final int MAX_LINES = 256;
    private final LinkedHashMap<String, Line> lines = new LinkedHashMap<>(16, .75F, true);
    private long layouts;

    record Frame(float x, float y, float width, float height, float scale, int available) {}
    record Line(String literal, Component component, FormattedCharSequence source, Font font,
                        Language language, long revision, int available, boolean shadow, UiMinecraftTextStyle.Overflow overflow,
                        FormattedCharSequence text, int width) {}

    static Frame frame(NativeBounds box, double guiScale, int lineHeight, boolean shadow) {
        if (box == null || !Double.isFinite(guiScale) || guiScale <= 0 || lineHeight <= 0
            || !Double.isFinite(box.x()) || !Double.isFinite(box.y())
            || !Double.isFinite(box.width()) || !Double.isFinite(box.height())
            || box.width() < 1 || box.height() < 1) return null;
        float x = (float) (box.x() / guiScale), y = (float) (box.y() / guiScale);
        float width = (float) (box.width() / guiScale), height = (float) (box.height() / guiScale);
        float scale = height / lineHeight;
        if (!Float.isFinite(x + width) || !Float.isFinite(y + height) || !Float.isFinite(scale) || scale <= 0) return null;
        int available = Math.max(0, (int) Math.floor(box.width() / box.height() * lineHeight) - (shadow ? 1 : 0));
        return new Frame(x, y, width, height, scale, available);
    }

    static int fadedColor(int color, float opacity) {
        return Math.round((color >>> 24) * Math.clamp(opacity, 0, 1)) << 24 | color & 0xFFFFFF;
    }

    boolean render(GuiGraphicsExtractor graphics, String id, NativeBounds box, double guiScale,
                   Object input, UiMinecraftTextStyle style) {
        // Check eligibility before looking up Minecraft, also making absent slots safe headlessly.
        Frame frame = frame(box, guiScale, 9, style.shadow());
        int color = fadedColor(style.color(), NativeOpacity.current());
        if (frame == null || frame.available() == 0 || color >>> 24 == 0) return false;
        Font font = Minecraft.getInstance().font;
        Line line = resolve(id, input, font, frame.available(), style);
        if (line.width() == 0) return false;
        float spare = frame.width() / frame.scale() - line.width() - (style.shadow() ? 1 : 0);
        float offset = switch (style.alignment()) { case LEFT -> 0; case CENTER -> spare / 2; case RIGHT -> spare; };
        // Establish scissor before the glyph pose; Minecraft intersects it with the parent's clip.
        graphics.enableScissor((int) Math.floor(frame.x()), (int) Math.floor(frame.y()),
            (int) Math.ceil(frame.x() + frame.width()), (int) Math.ceil(frame.y() + frame.height()));
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(frame.x() + offset * frame.scale(), frame.y());
            graphics.pose().scale(frame.scale(), frame.scale());
            graphics.text(font, line.text(), 0, 0, color, style.shadow());
        } finally {
            graphics.pose().popMatrix();
            graphics.disableScissor();
        }
        return true;
    }

    Line resolve(String id, Object input, Font font, int available, UiMinecraftTextStyle style) {
        Line old = lines.get(id);
        String literal = input instanceof String s ? s : null;
        Language language = Language.getInstance();
        long revision = UiCanvas.minecraftTextureRevision();
        boolean sameValue = old != null && (literal != null ? literal.equals(old.literal())
            : old.literal() == null && input.equals(old.component()));
        if (sameValue && old.font() == font && old.language() == language && old.revision() == revision
            && old.available() == available && old.shadow() == style.shadow() && old.overflow() == style.overflow()) return old;
        // MutableComponent caches visual order independently of later edits. Snapshot changed
        // values (including children/translation arguments) instead of inheriting stale output.
        Component component = literal == null ? snapshot((Component) input) : Component.literal(literal);
        FormattedCharSequence source = component.getVisualOrderText();
        FormattedCharSequence text = source;
        int width = font.width(source);
        if (style.overflow() == UiMinecraftTextStyle.Overflow.ELLIPSIS && width > available) {
            FormattedText dots = FormattedText.of("...", component.getStyle());
            int dotsWidth = font.width(dots);
            FormattedText fitted = dotsWidth > available ? font.substrByWidth(dots, available)
                : FormattedText.composite(font.substrByWidth(component, available - dotsWidth), dots);
            text = Language.getInstance().getVisualOrder(fitted);
            width = font.width(text);
        }
        Line line = new Line(literal, component, source, font, language, revision, available, style.shadow(), style.overflow(), text, width);
        lines.put(id, line);
        if (lines.size() > MAX_LINES) lines.remove(lines.keySet().iterator().next());
        layouts++;
        return line;
    }

    private static Component snapshot(Component input) {
        var result = input.plainCopy();
        if (input.getContents() instanceof TranslatableContents translated) {
            Object[] args = translated.getArgs().clone();
            for (int i = 0; i < args.length; i++) if (args[i] instanceof Component child) args[i] = snapshot(child);
            result = Component.translatableWithFallback(translated.getKey(), translated.getFallback(), args);
        }
        result.setStyle(input.getStyle());
        for (Component child : input.getSiblings()) result.append(snapshot(child));
        return result;
    }

    void clear() { lines.clear(); }
    long layouts() { return layouts; }
    int cachedLines() { return lines.size(); }
}
