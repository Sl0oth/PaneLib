package dev.pane.ui;

import java.util.Objects;

/**
 * Single-line native Minecraft text. The slot height is one font line (normally 9 units);
 * increasing it scales glyphs uniformly. Colors are ARGB. Component styles may override RGB
 * and select Minecraft resource fonts; the supplied alpha and HUD fade still apply.
 */
public record UiMinecraftTextStyle(int color, boolean shadow, Alignment alignment, Overflow overflow) {
    /** Horizontal alignment within a text slot. */
    public enum Alignment {
        /** Against the left edge. */
        LEFT,
        /** Centred. */
        CENTER,
        /** Against the right edge. */
        RIGHT
    }
    /** What happens to text wider than its slot: cut with an ellipsis, or clipped. */
    public enum Overflow {
        /** Cut short with an ellipsis. */
        ELLIPSIS,
        /** Cut at the slot's edge. */
        CLIP
    }
    private static final UiMinecraftTextStyle HUD = new UiMinecraftTextStyle(0xFFFFFFFF, true, Alignment.LEFT, Overflow.ELLIPSIS);
    private static final UiMinecraftTextStyle LABEL = HUD.color(0xFF404040).shadow(false);

    /** Requires an alignment and an overflow mode. */
    public UiMinecraftTextStyle {
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(overflow, "overflow");
    }

    /** White, shadowed HUD text with bounded ellipsis. */
    public static UiMinecraftTextStyle hud() {
        return HUD;
    }

    /** Dark, unshadowed text for light vanilla container panels. */
    public static UiMinecraftTextStyle label() { return LABEL; }
    /** A copy with this colour, as ARGB. */
    public UiMinecraftTextStyle color(int value) { return new UiMinecraftTextStyle(value, shadow, alignment, overflow); }
    /** A copy with or without Minecraft's text shadow. */
    public UiMinecraftTextStyle shadow(boolean value) { return new UiMinecraftTextStyle(color, value, alignment, overflow); }
    /** A copy with this alignment. */
    public UiMinecraftTextStyle alignment(Alignment value) { return new UiMinecraftTextStyle(color, shadow, value, overflow); }
    /** A copy with this overflow mode. */
    public UiMinecraftTextStyle overflow(Overflow value) { return new UiMinecraftTextStyle(color, shadow, alignment, value); }
}
