package dev.pane.ui;

import net.minecraft.client.gui.screens.Screen;

/** Optional Minecraft lifecycle and raw-input integration for a {@link UiScreen}. */
public final class UiScreenOptions {
    private final Screen parent;
    private final boolean pauseGame;
    private final UiBackdrop backdrop;
    private final UiKeyHandler keyHandler;
    private final UiClickHandler clickHandler;
    private final UiCloseHandler closeHandler;
    private final UiPerformanceDiagnostics performanceDiagnostics;

    private UiScreenOptions(Builder builder) {
        parent = builder.parent;
        pauseGame = builder.pauseGame;
        backdrop = builder.backdrop;
        keyHandler = builder.keyHandler;
        clickHandler = builder.clickHandler;
        closeHandler = builder.closeHandler;
        performanceDiagnostics = builder.performanceDiagnostics;
    }

    /** No parent screen, the default backdrop and no extra handlers. */
    public static UiScreenOptions defaults() { return builder().build(); }
    /** Starts a style with every value unset, so each uses the default. */
    public static Builder builder() { return new Builder(); }

    /** Requests pausing an eligible local singleplayer world; multiplayer keeps running. */
    public boolean pauseGame() { return pauseGame; }
    /** The screen Escape returns to, or null. */
    public Screen parent() { return parent; }
    /** What is drawn behind the page. */
    public UiBackdrop backdrop() { return backdrop; }
    /** Raw key handling that runs before the screen's own. */
    public UiKeyHandler keyHandler() { return keyHandler; }
    /** Raw click handling that runs before the screen's own. */
    public UiClickHandler clickHandler() { return clickHandler; }
    /** Asked before Escape closes the screen. */
    public UiCloseHandler closeHandler() { return closeHandler; }
    /** Opt-in render-thread diagnostics; disabled by default. */
    public UiPerformanceDiagnostics performanceDiagnostics() { return performanceDiagnostics; }

    /** Collects values for one style; any left unset uses the default. */
    public static final class Builder {
        /** A builder with every value unset; {@link #build()} fills in the defaults. Usually reached through {@code builder()}. */
        public Builder() { }

        private Screen parent;
        private boolean pauseGame;
        private UiBackdrop backdrop = UiBackdrop.NONE;
        private boolean backdropChosen;
        private UiKeyHandler keyHandler = UiKeyHandler.NONE;
        private UiClickHandler clickHandler = UiClickHandler.NONE;
        private UiCloseHandler closeHandler = UiCloseHandler.NONE;
        private UiPerformanceDiagnostics performanceDiagnostics = UiPerformanceDiagnostics.NONE;

        /** Parent restored by the default Escape/back behavior. Also inherits its background. */
        public Builder parent(Screen parent) {
            this.parent = parent;
            if (!backdropChosen) backdrop = parent == null ? UiBackdrop.NONE : UiBackdrop.PARENT_BACKGROUND;
            return this;
        }

        /** Chooses the Minecraft-native layer extracted below PaneLib's transparent surface. */
        public Builder backdrop(UiBackdrop backdrop) {
            this.backdrop = backdrop == null ? UiBackdrop.NONE : backdrop;
            backdropChosen = true;
            return this;
        }

        /** Requests Minecraft singleplayer pause behavior. Defaults to false. */
        public Builder pauseGame(boolean pause) { pauseGame = pause; return this; }

        /** Sets the on key. */
        public Builder onKey(UiKeyHandler handler) {
            keyHandler = handler == null ? UiKeyHandler.NONE : handler;
            return this;
        }

        /** Sets the on click. */
        public Builder onClick(UiClickHandler handler) {
            clickHandler = handler == null ? UiClickHandler.NONE : handler;
            return this;
        }

        /** Sets the on close. */
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

        /** The finished, immutable style. */
        public UiScreenOptions build() { return new UiScreenOptions(this); }
    }
}
