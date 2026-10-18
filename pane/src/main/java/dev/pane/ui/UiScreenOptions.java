package dev.pane.ui;

import net.minecraft.client.gui.screens.Screen;

/** Optional Minecraft lifecycle and raw-input integration for a {@link UiScreen}. */
public final class UiScreenOptions {
    private final Screen parent;
    private final UiBackdrop backdrop;
    private final UiKeyHandler keyHandler;
    private final UiClickHandler clickHandler;
    private final UiCloseHandler closeHandler;
    private final UiPerformanceDiagnostics performanceDiagnostics;

    private UiScreenOptions(Builder builder) {
        parent = builder.parent;
        backdrop = builder.backdrop;
        keyHandler = builder.keyHandler;
        clickHandler = builder.clickHandler;
        closeHandler = builder.closeHandler;
        performanceDiagnostics = builder.performanceDiagnostics;
    }

    public static UiScreenOptions defaults() { return builder().build(); }
    public static Builder builder() { return new Builder(); }

    public Screen parent() { return parent; }
    public UiBackdrop backdrop() { return backdrop; }
    public UiKeyHandler keyHandler() { return keyHandler; }
    public UiClickHandler clickHandler() { return clickHandler; }
    public UiCloseHandler closeHandler() { return closeHandler; }
    /** Opt-in render-thread diagnostics; disabled by default. */
    public UiPerformanceDiagnostics performanceDiagnostics() { return performanceDiagnostics; }

    public static final class Builder {
        private Screen parent;
        private UiBackdrop backdrop = UiBackdrop.NONE;
        private boolean backdropChosen;
        private UiKeyHandler keyHandler = UiKeyHandler.NONE;
        private UiClickHandler clickHandler = UiClickHandler.NONE;
        private UiCloseHandler closeHandler = UiCloseHandler.NONE;
        private UiPerformanceDiagnostics performanceDiagnostics = UiPerformanceDiagnostics.NONE;

        /** Parent restored by the default Escape/back behavior. Also inherits its background. */
        public Builder parent(Screen parent) {
            this.parent = parent;
            if (!backdropChosen && parent != null) backdrop = UiBackdrop.PARENT_BACKGROUND;
            return this;
        }

        /** Chooses the Minecraft-native layer extracted below PaneLib's transparent surface. */
        public Builder backdrop(UiBackdrop backdrop) {
            this.backdrop = backdrop == null ? UiBackdrop.NONE : backdrop;
            backdropChosen = true;
            return this;
        }

        public Builder onKey(UiKeyHandler handler) {
            keyHandler = handler == null ? UiKeyHandler.NONE : handler;
            return this;
        }

        public Builder onClick(UiClickHandler handler) {
            clickHandler = handler == null ? UiClickHandler.NONE : handler;
            return this;
        }

        public Builder onClose(UiCloseHandler handler) {
            closeHandler = handler == null ? UiCloseHandler.NONE : handler;
            return this;
        }

        /**
         * Receives bounded-rate screen performance snapshots on the render thread. This is for
         * profiling sessions; leave unset in production to avoid diagnostics work entirely.
         */
        public Builder performanceDiagnostics(UiPerformanceDiagnostics diagnostics) {
            performanceDiagnostics = diagnostics == null ? UiPerformanceDiagnostics.NONE : diagnostics;
            return this;
        }

        public UiScreenOptions build() { return new UiScreenOptions(this); }
    }
}
