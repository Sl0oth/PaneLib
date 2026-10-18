package dev.pane.ui;

/** A short, non-blocking acknowledgement shown at a screen edge. */
public final class UiToast {
    private final String id, title, message, icon;
    private final int durationMillis;
    private final UiToastPlacement placement;
    private final UiToastStyle style;
    private final boolean dismissible;

    /** Creates a default themed toast that remains visible for three seconds. */
    public UiToast(String id, String title, String message) {
        this(builder(id, title).message(message));
    }

    /** Creates a default themed toast with an explicit duration. */
    public UiToast(String id, String title, String message, int durationMillis) {
        this(builder(id, title).message(message).durationMillis(durationMillis));
    }

    private UiToast(Builder builder) {
        if (builder.id == null || builder.id.isBlank()) throw new IllegalArgumentException("A toast needs a stable id");
        id = builder.id;
        title = builder.title == null || builder.title.isBlank() ? "Done" : builder.title;
        message = builder.message == null ? "" : builder.message;
        icon = builder.icon == null ? "" : builder.icon;
        durationMillis = Math.max(250, Math.min(30_000, builder.durationMillis));
        placement = builder.placement;
        style = builder.style;
        dismissible = builder.dismissible;
    }

    public static Builder builder(String id, String title) { return new Builder(id, title); }
    public String id() { return id; }
    public String title() { return title; }
    public String message() { return message; }
    public String icon() { return icon; }
    public int durationMillis() { return durationMillis; }
    /** Null means use the document's default placement. */
    public UiToastPlacement placement() { return placement; }
    /** Null means use the document's default style or the document theme. */
    public UiToastStyle style() { return style; }
    public boolean dismissible() { return dismissible; }

    public static final class Builder {
        private final String id, title;
        private String message = "", icon = "";
        private int durationMillis = 3_000;
        private UiToastPlacement placement;
        private UiToastStyle style;
        private boolean dismissible = true;

        private Builder(String id, String title) { this.id = id; this.title = title; }
        public Builder message(String value) { message = value; return this; }
        /** A short text glyph, such as "✓", "!", or "i". */
        public Builder icon(String value) { icon = value; return this; }
        public Builder durationMillis(int value) { durationMillis = value; return this; }
        public Builder placement(UiToastPlacement value) { placement = value; return this; }
        public Builder style(UiToastStyle value) { style = value; return this; }
        public Builder dismissible(boolean value) { dismissible = value; return this; }
        public UiToast build() { return new UiToast(this); }
    }
}
