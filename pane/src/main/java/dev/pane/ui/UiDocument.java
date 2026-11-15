package dev.pane.ui;

import java.util.ArrayList;
import java.util.List;

/** Retained native UI model. It contains state and painters, not HTML or textures. */
public final class UiDocument {
    private final String title;
    private final String brand;
    private final UiTheme theme;
    private final UiShell shell;
    private final UiScaling scaling;
    private final List<UiPage> pages;
    private final String initialPageId;
    private final boolean pageShortcuts;
    private final int tooltipDelayMillis;
    private final UiTooltipStyle tooltipStyle;
    private final boolean localFocusRepaint;
    private final UiToastSettings toastSettings;
    private final UiRenderSettings renderSettings;
    private final UiScrollSettings scrollSettings;
    private final UiButtonStyle buttonStyle;
    private final boolean labelTooltips;
    private final UiInputStyle inputStyle;
    private final UiMenuStyle menuStyle;

    private UiDocument(Builder builder) {
        title = builder.title;
        brand = builder.brand;
        theme = builder.theme == null ? UiTheme.dark() : builder.theme;
        shell = builder.shell == null ? UiShell.NONE : builder.shell;
        scaling = builder.scaling == null ? UiScaling.standard() : builder.scaling;
        pages = List.copyOf(builder.pages);
        pageShortcuts = builder.pageShortcuts;
        tooltipDelayMillis = builder.tooltipDelayMillis;
        tooltipStyle = builder.tooltipStyle;
        localFocusRepaint = builder.localFocusRepaint;
        toastSettings = builder.toastSettings == null ? UiToastSettings.defaults() : builder.toastSettings;
        renderSettings = builder.renderSettings == null ? UiRenderSettings.balanced() : builder.renderSettings;
        scrollSettings = builder.scrollSettings == null ? UiScrollSettings.smooth() : builder.scrollSettings;
        buttonStyle = builder.buttonStyle;
        labelTooltips = builder.labelTooltips;
        inputStyle = builder.inputStyle == null ? UiInputStyle.defaults() : builder.inputStyle;
        menuStyle = builder.menuStyle == null ? UiMenuStyle.defaults() : builder.menuStyle;
        if (pages.isEmpty()) throw new IllegalStateException("A UI document needs at least one page");
        var pageIds = new java.util.HashSet<String>();
        for (UiPage page : pages) {
            if (!pageIds.add(page.id())) throw new IllegalArgumentException("Duplicate page ID: " + page.id());
        }
        initialPageId = builder.initialPageId == null ? pages.getFirst().id() : builder.initialPageId;
        if (page(initialPageId) == null) throw new IllegalStateException("Unknown initial page: " + initialPageId);
    }

    /** Starts a document titled {@code title}, the screen title and the narrator's name for it. */
    public static Builder builder(String title) { return new Builder(title); }
    /** The document title. */
    public String title() { return title; }
    /** Brand text the default shell shows beside its navigation. */
    public String brand() { return brand; }
    /** The theme every page paints with. */
    public UiTheme theme() { return theme; }
    /** The shell that paints navigation around each page. */
    public UiShell shell() { return shell; }

    /** How the layout responds to window size. */
    public UiScaling scaling() { return scaling; }
    /** Pages in navigation order. */
    public List<UiPage> pages() { return pages; }
    /** Id of the page shown first. */
    public String initialPageId() { return initialPageId; }

    /** Whether keys 1-9 select the first nine pages. */
    public boolean pageShortcuts() { return pageShortcuts; }

    /** Delay before a hovered native tooltip appears, in milliseconds. Zero shows it immediately. */
    public int tooltipDelayMillis() { return tooltipDelayMillis; }

    /** How native tooltips look. */
    public UiTooltipStyle tooltipStyle() { return tooltipStyle; }

    /** Whether a focus change repaints only the controls losing and gaining focus. */
    public boolean localFocusRepaint() { return localFocusRepaint; }

    /** Document-wide defaults for toast placement, spacing, animation, and style. */
    public UiToastSettings toastSettings() { return toastSettings; }

    /** Retained surface quality and memory limits. */
    public UiRenderSettings renderSettings() { return renderSettings; }

    /** Wheel/trackpad distance and finite smoothing duration. */
    public UiScrollSettings scrollSettings() { return scrollSettings; }

    /** Whether painted, shaped, menu and dialog buttons show their label as a hover tooltip. */
    public boolean labelTooltips() { return labelTooltips; }

    /** The style of buttons and dropdown triggers drawn without one, when the document sets it. */
    public java.util.Optional<UiButtonStyle> buttonStyle() { return java.util.Optional.ofNullable(buttonStyle); }

    /** The style of text fields, text areas and text views drawn without one. */
    public UiInputStyle inputStyle() { return inputStyle; }

    /** The style of menus and dropdown lists opened without one. */
    public UiMenuStyle menuStyle() { return menuStyle; }

    /** The page with id {@code id}, or null. */
    public UiPage page(String id) {
        if (id == null) return null;
        for (UiPage page : pages) {
            if (page.id().equals(id)) return page;
        }
        return null;
    }

    /** Collects a document's pages and settings; {@link #build()} validates them. */
    public static final class Builder {
        private final String title;
        private String brand = "Client UI";
        private UiTheme theme = UiTheme.dark();
        private UiShell shell = UiShell.NONE;
        private UiScaling scaling = UiScaling.standard();
        private final List<UiPage> pages = new ArrayList<>();
        private String initialPageId;
        private boolean pageShortcuts;
        private int tooltipDelayMillis = 450;
        private UiTooltipStyle tooltipStyle = UiTooltipStyle.defaults();
        private boolean localFocusRepaint;
        private UiToastSettings toastSettings = UiToastSettings.defaults();
        private UiRenderSettings renderSettings = UiRenderSettings.balanced();
        private UiScrollSettings scrollSettings = UiScrollSettings.smooth();
        private UiButtonStyle buttonStyle;
        private boolean labelTooltips = true;
        private UiInputStyle inputStyle;
        private UiMenuStyle menuStyle;

        private Builder(String title) { this.title = title == null ? "Client UI" : title; }

        /** Brand text for the default shell; null means none. */
        public Builder brand(String brand) { this.brand = brand == null ? "" : brand; return this; }
        /** The theme every page paints with. */
        public Builder theme(UiTheme theme) { this.theme = theme; return this; }

        /** Chrome painted around the active page. Defaults to {@link UiShell#NONE}. */
        public Builder shell(UiShell shell) { this.shell = shell; return this; }

        /** How the layout responds to window size. Defaults to {@link UiScaling#standard()}. */
        public Builder scaling(UiScaling scaling) { this.scaling = scaling; return this; }

        /** Adds a page; pages appear in the order added. */
        public Builder page(UiPage page) { pages.add(page); return this; }
        /** The page shown first; defaults to the first page added. */
        public Builder initialPage(String pageId) { initialPageId = pageId; return this; }

        /** Opt in to keys 1-9 selecting pages. Off by default so consumers keep their own key handling. */
        public Builder pageShortcuts(boolean pageShortcuts) { this.pageShortcuts = pageShortcuts; return this; }

        /** Sets the 0..10000ms delay before a native tooltip appears. Defaults to 450ms. */
        public Builder tooltipDelayMillis(int tooltipDelayMillis) {
            this.tooltipDelayMillis = Math.max(0, Math.min(10_000, tooltipDelayMillis));
            return this;
        }

        /** Sets how native tooltips look. Defaults to {@link UiTooltipStyle#defaults()}. */
        public Builder tooltipStyle(UiTooltipStyle tooltipStyle) {
            this.tooltipStyle = tooltipStyle == null ? UiTooltipStyle.defaults() : tooltipStyle;
            return this;
        }

        /**
         * Makes focus changes, including the focus every click moves, repaint only the controls
         * losing and gaining focus instead of the page. Enable it when no painter reads
         * {@link UiContext#focused} to draw outside the focused control's own bounds. Off by default.
         */
        public Builder localFocusRepaint(boolean local) { localFocusRepaint = local; return this; }

        /** Sets document-wide defaults for native toast notifications. */
        public Builder toastSettings(UiToastSettings toastSettings) {
            this.toastSettings = toastSettings == null ? UiToastSettings.defaults() : toastSettings;
            return this;
        }

        /** Chooses the retained surface's quality and memory ceiling. */
        public Builder renderSettings(UiRenderSettings renderSettings) {
            this.renderSettings = renderSettings == null ? UiRenderSettings.balanced() : renderSettings;
            return this;
        }

        /** Tunes document-wide scrolling without changing individual scroll containers. */
        public Builder scrollSettings(UiScrollSettings scrollSettings) {
            this.scrollSettings = scrollSettings == null ? UiScrollSettings.smooth() : scrollSettings;
            return this;
        }

        /**
         * Styles every button and dropdown trigger that is drawn without a style of its own, so a
         * design system is set once here instead of passed to each control. Null restores Pane's look.
         */
        public Builder buttonStyle(UiButtonStyle buttonStyle) { this.buttonStyle = buttonStyle; return this; }

        /**
         * Whether painted, shaped, menu and dialog buttons show their label as a hover tooltip; on by
         * default. Turn it off for a menu whose buttons already draw their label, so hovering PLAY does
         * not raise a tip that says PLAY. Icon buttons keep theirs, and {@link UiContext#tooltip} still
         * adds any tip you ask for. The label stays the button's name for the narrator.
         */
        public Builder labelTooltips(boolean show) { labelTooltips = show; return this; }

        /** Styles every text field, text area and text view drawn without a style of its own. */
        public Builder inputStyle(UiInputStyle inputStyle) { this.inputStyle = inputStyle; return this; }

        /** Styles every menu and dropdown list opened without a style of its own. */
        public Builder menuStyle(UiMenuStyle menuStyle) { this.menuStyle = menuStyle; return this; }

        /** The finished document. */
        public UiDocument build() { return new UiDocument(this); }
    }
}
