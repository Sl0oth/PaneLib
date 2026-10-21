package dev.pane.ui;

import java.util.Objects;

/**
 * Single-line native Minecraft text. The slot height is one font line (normally 9 units);
 * increasing it scales glyphs uniformly. Colors are ARGB. Component styles may override RGB
 * and select Minecraft resource fonts; the supplied alpha and HUD fade still apply.
 */
public record UiMinecraftTextStyle(int color, boolean shadow, Alignment alignment, Overflow overflow) {
    public enum Alignment { LEFT, CENTER, RIGHT }
    public enum Overflow { ELLIPSIS, CLIP }
    private static final UiMinecraftTextStyle HUD = new UiMinecraftTextStyle(0xFFFFFFFF, true, Alignment.LEFT, Overflow.ELLIPSIS);
    private static final UiMinecraftTextStyle LABEL = HUD.color(0xFF404040).shadow(false);

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
    public UiMinecraftTextStyle color(int value) { return new UiMinecraftTextStyle(value, shadow, alignment, overflow); }
    public UiMinecraftTextStyle shadow(boolean value) { return new UiMinecraftTextStyle(color, value, alignment, overflow); }
    public UiMinecraftTextStyle alignment(Alignment value) { return new UiMinecraftTextStyle(color, shadow, value, overflow); }
    public UiMinecraftTextStyle overflow(Overflow value) { return new UiMinecraftTextStyle(color, shadow, alignment, value); }
}
