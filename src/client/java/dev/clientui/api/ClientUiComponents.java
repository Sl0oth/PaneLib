package dev.clientui.api;

import java.util.Map;

/** Small optional HTML DSL. Mods may mix these primitives with arbitrary HTML and CSS. */
public final class ClientUiComponents {
    public enum ButtonStyle { PRIMARY, SECONDARY, GHOST, DANGER }
    public enum Shape { SQUARE, ROUNDED, SOFT, PILL }

    private ClientUiComponents() {}

    public static String button(String label, String action, ButtonStyle style, Shape shape) {
        return "<button class=\"ui-button " + styleClass(style) + " " + shapeClass(shape)
            + "\" data-action=\"" + attr(action) + "\">" + ClientUiPage.html(label) + "</button>";
    }

    public static String badge(String text, String tone) {
        return "<span class=\"ui-badge tone-" + attr(tone) + "\">" + ClientUiPage.html(text) + "</span>";
    }

    public static String toggle(String label, String key, boolean enabled) {
        return "<label class=\"ui-toggle-row\"><span>" + ClientUiPage.html(label)
            + "</span><button class=\"ui-toggle" + (enabled ? " on" : "")
            + "\" role=\"switch\" aria-checked=\"" + enabled + "\" data-toggle=\""
            + attr(key) + "\"><i></i></button></label>";
    }

    public static String textField(String placeholder, String key, String value) {
        return "<input class=\"ui-field\" type=\"text\" placeholder=\"" + attr(placeholder)
            + "\" value=\"" + attr(value) + "\" data-value=\"" + attr(key) + "\">";
    }

    public static String slider(String key, int min, int max, int value) {
        int safeMax = Math.max(min + 1, max);
        int safeValue = Math.max(min, Math.min(safeMax, value));
        return "<input class=\"ui-slider\" type=\"range\" min=\"" + min + "\" max=\""
            + safeMax + "\" value=\"" + safeValue + "\" data-value=\"" + attr(key) + "\">";
    }

    public static String select(String key, Map<String, String> options, String selectedValue) {
        StringBuilder html = new StringBuilder("<select class=\"ui-select\" data-value=\"")
            .append(attr(key)).append("\">");
        options.forEach((value, label) -> html.append("<option value=\"").append(attr(value)).append("\"")
            .append(value.equals(selectedValue) ? " selected" : "").append(">")
            .append(ClientUiPage.html(label)).append("</option>"));
        return html.append("</select>").toString();
    }

    /** Creates a DOM anchor that can receive a native ItemStack or entity render. */
    public static String nativeSlot(String id, String cssClass, String fallback) {
        return "<div class=\"" + attr(cssClass) + "\" data-native=\"" + attr(id) + "\">"
            + ClientUiPage.html(fallback) + "</div>";
    }

    private static String styleClass(ButtonStyle style) {
        return "button-" + style.name().toLowerCase();
    }

    private static String shapeClass(Shape shape) {
        return "shape-" + shape.name().toLowerCase();
    }

    private static String attr(String value) {
        return ClientUiPage.html(value == null ? "" : value).replace("'", "&#39;");
    }
}
