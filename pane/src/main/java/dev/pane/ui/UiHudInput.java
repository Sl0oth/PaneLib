package dev.pane.ui;

import java.util.*;

/**
 * Pointer handling for an interactive HUD, in the HUD's own points. It knows the controls a HUD can
 * hold: buttons and other actions, toggles, checkboxes, segmented choices, steppers and sliders.
 */
final class UiHudInput {
    /** What a press, move or release did. */
    record Result(boolean consumed, boolean repaint, String action, String changedKey) {
        static final Result NONE = new Result(false, false, null, null);
    }

    private List<UiHitRegion> hits = List.of();
    private String hovered, pressed;
    private UiHitRegion dragging;

    String hovered() { return hovered; }
    String pressed() { return pressed; }
    boolean active() { return hovered != null || pressed != null; }

    /** The controls the latest paint registered; a drag follows the control's new region. */
    void painted(List<UiHitRegion> painted) {
        hits = List.copyOf(painted);
        if (dragging == null) return;
        UiHitRegion current = null;
        for (UiHitRegion hit : hits) if (hit.id().equals(dragging.id()) && hit.action().equals(dragging.action())) current = hit;
        dragging = current;
        if (current == null) pressed = null;
    }

    /** Forgets the pointer, as when the HUD stops taking input. Returns whether anything was shown. */
    boolean clear() {
        boolean shown = active();
        hovered = pressed = null; dragging = null;
        return shown;
    }

    private UiHitRegion at(float x, float y) {
        for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).contains(x, y)) return hits.get(i);
        return null;
    }

    Result move(float x, float y, Map<String, Object> values) {
        if (dragging != null) {
            String key = slide(dragging, x, y, values);
            return new Result(true, key != null, null, key);
        }
        UiHitRegion hit = at(x, y);
        String next = hit == null ? null : hit.id();
        if (Objects.equals(next, hovered)) return Result.NONE;
        hovered = next;
        return new Result(false, true, null, null);
    }

    Result press(float x, float y, Map<String, Object> values) {
        UiHitRegion hit = at(x, y);
        if (hit == null) return Result.NONE;
        hovered = pressed = hit.id();
        String changed = null;
        if (body(hit.action()).startsWith("slider:")) {
            dragging = hit;
            changed = slide(hit, x, y, values);
        }
        return new Result(true, true, null, changed);
    }

    Result release(float x, float y, Map<String, Object> values) {
        if (pressed == null) return Result.NONE;
        String id = pressed;
        boolean slid = dragging != null;
        pressed = null; dragging = null;
        UiHitRegion hit = at(x, y);
        if (slid || hit == null || !hit.id().equals(id)) return new Result(true, true, null, null);
        if (!hit.action().startsWith(UiContext.INTERNAL_ACTION_PREFIX)) return new Result(true, true, hit.action(), null);
        return new Result(true, true, null, apply(body(hit.action()), values));
    }

    private static String body(String action) {
        return action.startsWith(UiContext.INTERNAL_ACTION_PREFIX) ? action.substring(UiContext.INTERNAL_ACTION_PREFIX.length()) : "";
    }

    private static String slide(UiHitRegion slider, float x, float y, Map<String, Object> values) {
        String descriptor = body(slider.action()).substring("slider:".length());
        int end = descriptor.lastIndexOf(':'), start = end < 0 ? -1 : descriptor.lastIndexOf(':', end - 1);
        String key = start < 0 ? descriptor : descriptor.substring(0, start);
        double minimum = start < 0 ? 0 : Double.parseDouble(descriptor.substring(start + 1, end));
        double maximum = end < 0 ? 1 : Double.parseDouble(descriptor.substring(end + 1));
        double value = minimum + slider.fractionAt(x, y) * (maximum - minimum);
        Object previous = values.put(key, value);
        return previous instanceof Number number && number.doubleValue() == value ? null : key;
    }

    /** Runs a built-in control's action on the HUD's values; returns the key it changed, or null. */
    static String apply(String action, Map<String, Object> values) {
        if (action.startsWith("toggle:")) {
            String key = action.substring("toggle:".length());
            values.put(key, !(values.get(key) instanceof Boolean on && on));
            return key;
        }
        if (action.startsWith("step:")) {
            String[] parts = action.substring("step:".length()).split(":", 4);
            double delta = Double.parseDouble(parts[0]), minimum = Double.parseDouble(parts[1]), maximum = Double.parseDouble(parts[2]);
            String key = parts[3];
            double current = values.get(key) instanceof Number n && Double.isFinite(n.doubleValue()) ? n.doubleValue() : minimum;
            double step = Math.abs(delta);
            double next = Math.clamp(minimum + Math.rint((current + delta - minimum) / step) * step, minimum, maximum);
            return Objects.equals(values.put(key, next), next) ? null : key;
        }
        if (action.startsWith("select:")) {
            String body = action.substring("select:".length());
            int split = body.indexOf('=');
            if (split < 0) return null;
            String key = UiValueKey.decode(body.substring(0, split)), value = body.substring(split + 1);
            return Objects.equals(values.put(key, value), value) ? null : key;
        }
        // Text fields, dropdowns, key bindings and dialogs need a screen's keyboard and popups.
        return null;
    }
}
