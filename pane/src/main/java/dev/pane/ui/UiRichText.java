package dev.pane.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;

/**
 * Immutable, prepared Minecraft-style text for Pane's retained rasterizer.
 * <p>
 * Plain UI labels remain plain. Consumers opt into this type for data such as server MOTDs that
 * can carry Minecraft colors and decorations. Prepare it when the data set changes and reuse it
 * from virtual-row painters instead of reparsing formatting during hover or scroll invalidations.
 */
public final class UiRichText {
    /** One contiguous run with one visual style. Color is nullable and falls back at draw time. */
    public record Span(String text, Integer color, boolean bold, boolean italic,
                       boolean underlined, boolean strikethrough, boolean obfuscated) {
        public Span {
            text = Objects.requireNonNullElse(text, "");
        }
    }

    private static final UiRichText EMPTY = new UiRichText(List.of(), "");
    private static final String OBFUSCATED_GLYPHS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&?";

    private final List<Span> spans;
    private final String plainText;

    private UiRichText(List<Span> spans, String plainText) {
        this.spans = List.copyOf(spans);
        this.plainText = Objects.requireNonNullElse(plainText, "");
    }

    /** Creates unformatted text. Formatting-marker characters remain visible. */
    public static UiRichText plain(String value) {
        if (value == null || value.isEmpty()) return EMPTY;
        return new UiRichText(List.of(new Span(value, null, false, false, false, false, false)), value);
    }

    /**
     * Parses Minecraft's native section-sign formatting codes ({@code \u00a7a}, {@code \u00a7l},
     * {@code \u00a7o}, and so on). Unknown markers remain harmless and broken surrogate pairs are
     * replaced the same way Minecraft replaces them.
     */
    public static UiRichText legacy(String value) {
        return parse(value, false);
    }

    /**
     * Parses both native section-sign codes and the common ampersand form used by server-list
     * APIs. Ampersands are treated as formatting only when followed by a valid Minecraft code.
     */
    public static UiRichText legacyAmpersand(String value) {
        return parse(value, true);
    }

    /** Converts a styled Minecraft component, including arbitrary RGB colors, into reusable runs. */
    public static UiRichText component(Component component) {
        if (component == null) return EMPTY;
        return formatted(component);
    }

    /** Converts any Minecraft formatted text into reusable runs. */
    public static UiRichText formatted(FormattedText value) {
        if (value == null) return EMPTY;
        Builder builder = new Builder();
        StringDecomposer.iterateFormatted(value, Style.EMPTY, builder::accept);
        return builder.build();
    }

    /** Creates rich text from explicit runs for non-Minecraft data sources. */
    public static UiRichText of(List<Span> spans) {
        if (spans == null || spans.isEmpty()) return EMPTY;
        Builder builder = new Builder();
        for (Span span : spans) {
            if (span == null || span.text().isEmpty()) continue;
            builder.append(span.text(), span);
        }
        return builder.build();
    }

    public List<Span> spans() {
        return spans;
    }

    /** Text without formatting markers; obfuscated runs return their original unmasked content. */
    public String plainText() {
        return plainText;
    }

    public boolean isEmpty() {
        return spans.isEmpty();
    }

    private static UiRichText parse(String value, boolean ampersand) {
        if (value == null || value.isEmpty()) return EMPTY;
        String source = ampersand ? normalizeAmpersandCodes(value) : value;
        Builder builder = new Builder();
        StringDecomposer.iterateFormatted(source, Style.EMPTY, builder::accept);
        return builder.build();
    }

    private static String normalizeAmpersandCodes(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '&' && index + 1 < value.length()
                    && ChatFormatting.getByCode(value.charAt(index + 1)) != null) {
                normalized.append(ChatFormatting.PREFIX_CODE).append(value.charAt(++index));
            } else {
                normalized.append(current);
            }
        }
        return normalized.toString();
    }

    private static final class Builder {
        private final List<Span> spans = new ArrayList<>();
        private final StringBuilder plain = new StringBuilder();
        private StringBuilder currentText;
        private StyleKey currentStyle;
        private int glyphIndex;

        private boolean accept(int sourceIndex, Style style, int codePoint) {
            StyleKey key = StyleKey.from(style);
            if (!key.equals(currentStyle)) {
                flush();
                currentStyle = key;
                currentText = new StringBuilder();
            }
            plain.appendCodePoint(codePoint);
            if (key.obfuscated() && !Character.isWhitespace(codePoint)) {
                currentText.append(OBFUSCATED_GLYPHS.charAt(
                    Math.floorMod(codePoint * 31 + sourceIndex * 17 + glyphIndex++, OBFUSCATED_GLYPHS.length())));
            } else {
                currentText.appendCodePoint(codePoint);
            }
            return true;
        }

        private void append(String text, Span style) {
            StyleKey key = new StyleKey(style.color(), style.bold(), style.italic(),
                style.underlined(), style.strikethrough(), style.obfuscated());
            if (!key.equals(currentStyle)) {
                flush();
                currentStyle = key;
                currentText = new StringBuilder();
            }
            plain.append(text);
            currentText.append(text);
        }

        private void flush() {
            if (currentText == null || currentText.isEmpty() || currentStyle == null) return;
            spans.add(currentStyle.span(currentText.toString()));
        }

        private UiRichText build() {
            flush();
            return spans.isEmpty() ? EMPTY : new UiRichText(spans, plain.toString());
        }
    }

    private record StyleKey(Integer color, boolean bold, boolean italic, boolean underlined,
                            boolean strikethrough, boolean obfuscated) {
        private static StyleKey from(Style style) {
            Integer color = style.getColor() == null ? null : style.getColor().getValue();
            return new StyleKey(color, style.isBold(), style.isItalic(), style.isUnderlined(),
                style.isStrikethrough(), style.isObfuscated());
        }

        private Span span(String text) {
            return new Span(text, color, bold, italic, underlined, strikethrough, obfuscated);
        }
    }
}
