package dev.pane.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * What Minecraft's narrator says about one control: what kind of control it is, its label and its
 * current state. Built-in controls describe themselves from their visible label and state; give a
 * control without a visible label, or one painted by hand, its own with {@link UiContext#narration}.
 *
 * @param label what the control is called; null or empty when it has no words of its own
 * @param state its current value or state, such as "On", "40%" or a field's text; may be null
 * @param position its place among its siblings, 1-based, or 0 when it has none
 * @param count how many siblings share that position count, or 0
 */
public record UiNarration(Role role, String label, String state, int position, int count) {
    /** Kinds of control, each narrated the way Minecraft narrates its own. */
    public enum Role { BUTTON, LINK, CHECKBOX, TOGGLE, RADIO, TAB, SLIDER, TEXT_FIELD, PASSWORD, MENU, LIST_ROW, TEXT }

    public UiNarration {
        java.util.Objects.requireNonNull(role, "role");
        label = label == null ? "" : label;
        position = Math.max(0, position);
        count = Math.max(0, count);
    }

    public static UiNarration of(Role role, String label) { return new UiNarration(role, label, null, 0, 0); }

    public static UiNarration of(Role role, String label, String state) { return new UiNarration(role, label, state, 0, 0); }

    /** The same description with a new state, as a control changes. */
    public UiNarration withState(String state) { return new UiNarration(role, label, state, position, count); }

    /** The same description placed {@code position} of {@code count} among its siblings. */
    public UiNarration at(int position, int count) { return new UiNarration(role, label, state, position, count); }

    /** One spoken part: the narration element type Minecraft files it under and its words. */
    record Part(NarratedElementType type, Component text) { }

    /**
     * The parts Minecraft's narrator speaks for this control, in its own words: a title naming the
     * control and its state, its position, and how to use it from the keyboard or with the mouse.
     */
    List<Part> parts(boolean focused) {
        var parts = new ArrayList<Part>();
        Component name = Component.literal(label);
        Component on = Component.translatable("options.on"), off = Component.translatable("options.off");
        boolean checked = "true".equals(state) || "on".equalsIgnoreCase(state);
        Component title = switch (role) {
            case BUTTON, MENU -> state == null || state.isEmpty() ? Component.translatable("gui.narrate.button", name)
                : Component.translatable("gui.narrate.button", CommonComponents.joinForNarration(name, Component.literal(state)));
            case LINK -> Component.translatable("gui.narrate.button", name);
            case CHECKBOX -> CommonComponents.joinForNarration(Component.translatable("narration.checkbox", name), checked ? on : off);
            case TOGGLE -> CommonComponents.joinForNarration(label.isEmpty() ? Component.empty() : name, checked ? on : off);
            case RADIO, TAB -> checked ? CommonComponents.joinForNarration(Component.translatable("gui.narrate.tab", name), Component.translatable("gui.yes"))
                : Component.translatable("gui.narrate.tab", name);
            // Minecraft's own sliders read "Volume: 50% slider".
            case SLIDER -> Component.translatable("gui.narrate.slider", state == null ? name
                : Component.literal(label.isEmpty() ? state : label + ": " + state));
            case TEXT_FIELD -> Component.translatable("gui.narrate.editBox", name, state == null ? "" : state);
            // A password's characters are never spoken.
            case PASSWORD -> Component.translatable("gui.narrate.editBox", name, "");
            case LIST_ROW -> state == null ? name : CommonComponents.joinForNarration(name, Component.literal(state));
            case TEXT -> name;
        };
        parts.add(new Part(NarratedElementType.TITLE, title));
        if (position > 0 && count > 0) {
            String key = switch (role) {
                case TAB, RADIO -> "narrator.position.tab";
                case LIST_ROW -> "narrator.position.list";
                default -> "narrator.position.screen";
            };
            parts.add(new Part(NarratedElementType.POSITION, Component.translatable(key, position, count)));
        }
        String usage = switch (role) {
            case BUTTON, MENU -> focused ? "narration.button.usage.focused" : "narration.button.usage.hovered";
            case LINK -> focused ? "narration.link.usage.focused" : "narration.link.usage.hovered";
            case CHECKBOX, TOGGLE -> focused ? (checked ? "narration.checkbox.usage.focused.uncheck" : "narration.checkbox.usage.focused.check")
                : checked ? "narration.checkbox.usage.hovered.uncheck" : "narration.checkbox.usage.hovered.check";
            case SLIDER -> focused ? "narration.slider.usage.focused" : "narration.slider.usage.hovered";
            case LIST_ROW -> focused ? "narration.selection.usage" : null;
            default -> null;
        };
        if (usage != null) parts.add(new Part(NarratedElementType.USAGE, Component.translatable(usage)));
        return parts;
    }
}
