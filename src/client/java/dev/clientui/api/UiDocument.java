package dev.clientui.api;

import java.util.ArrayList;
import java.util.List;

/** Retained native UI model. It contains state and painters, not HTML or textures. */
public final class UiDocument {
    private final String title;
    private final String brand;
    private final UiTheme theme;
    private final UiShell shell;
    private final List<UiPage> pages;
    private final String initialPageId;
    private final boolean pageShortcuts;

    private UiDocument(Builder builder) {
        title = builder.title;
        brand = builder.brand;
        theme = builder.theme == null ? UiTheme.dark() : builder.theme;
        shell = builder.shell == null ? UiShell.NONE : builder.shell;
        pages = List.copyOf(builder.pages);
        pageShortcuts = builder.pageShortcuts;
        if (pages.isEmpty()) throw new IllegalStateException("A UI document needs at least one page");
        initialPageId = builder.initialPageId == null ? pages.getFirst().id() : builder.initialPageId;
        if (page(initialPageId) == null) throw new IllegalStateException("Unknown initial page: " + initialPageId);
    }

    public static Builder builder(String title) { return new Builder(title); }
    public String title() { return title; }
    public String brand() { return brand; }
    public UiTheme theme() { return theme; }
    public UiShell shell() { return shell; }
    public List<UiPage> pages() { return pages; }
    public String initialPageId() { return initialPageId; }

    /** Whether keys 1-9 select the first nine pages. */
    public boolean pageShortcuts() { return pageShortcuts; }

    public UiPage page(String id) {
        if (id == null) return null;
        for (UiPage page : pages) {
            if (page.id().equals(id)) return page;
        }
        return null;
    }

    public static final class Builder {
        private final String title;
        private String brand = "Client UI";
        private UiTheme theme = UiTheme.dark();
        private UiShell shell = UiShell.NONE;
        private final List<UiPage> pages = new ArrayList<>();
        private String initialPageId;
        private boolean pageShortcuts;

        private Builder(String title) { this.title = title == null ? "Client UI" : title; }

        public Builder brand(String brand) { this.brand = brand == null ? "" : brand; return this; }
        public Builder theme(UiTheme theme) { this.theme = theme; return this; }

        /** Chrome painted around the active page. Defaults to {@link UiShell#NONE}. */
        public Builder shell(UiShell shell) { this.shell = shell; return this; }

        public Builder page(UiPage page) { pages.add(page); return this; }
        public Builder initialPage(String pageId) { initialPageId = pageId; return this; }

        /** Opt in to keys 1-9 selecting pages. Off by default so consumers keep their own key handling. */
        public Builder pageShortcuts(boolean pageShortcuts) { this.pageShortcuts = pageShortcuts; return this; }

        public UiDocument build() { return new UiDocument(this); }
    }
}
