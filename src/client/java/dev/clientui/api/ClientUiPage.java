package dev.clientui.api;

/** One route in a Client UI document. Markup and CSS are intentionally open-ended. */
public record ClientUiPage(
    String id,
    String navigationLabel,
    String navigationIcon,
    String accent,
    String markup,
    String scopedCss
) {
    public ClientUiPage {
        id = safeId(id);
        navigationLabel = html(navigationLabel);
        navigationIcon = html(navigationIcon);
        accent = safeCss(accent);
        markup = markup == null ? "" : markup;
        scopedCss = scopedCss == null ? "" : scopedCss;
    }

    static String html(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String safeId(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException("Page ids must be lowercase CSS identifiers");
        }
        return value;
    }

    private static String safeCss(String value) {
        if (value == null || value.isBlank() || value.matches(".*[;{}<>].*")) {
            throw new IllegalArgumentException("Unsafe page accent");
        }
        return value;
    }
}
