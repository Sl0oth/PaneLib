package dev.pane.demo;

import dev.pane.ui.UiRect;
import dev.pane.ui.UiScreen;
import dev.pane.ui.UiPerformanceSnapshot;
import java.io.File;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

/**
 * Development-only harness. Enabled with {@code -Dpane.demo.autotest=true}, it loads a
 * throwaway singleplayer world, opens the showcase, and captures each page so the native
 * player and ItemStack overlays can be verified where they actually run: inside a world.
 * <p>
 * Nothing in this class is referenced unless the system property is set, and it lives in the
 * demo source set rather than the API, so consuming mods never see it.
 */
final class DemoAutoTest {
    private static final String LEVEL_ID = "pane_autotest";
    private static final String ENTITY_LEVEL_ID = "pane_entity_autotest";
    private static final String[] CAPTURE_PAGES = {"loadout", "discover", "waypoints", "performance", "settings"};

    private enum Stage { WAIT_FOR_TITLE, TITLE_BACKDROP, TITLE_BACK, LOADING_WORLD, SETTLING, ENTITY_CAPTURE, ENTITY_HOVER_COLD, ENTITY_HOVER_COLD_RESULT, ENTITY_HOVER_WARM_OPEN, ENTITY_HOVER_WARM, ENTITY_HOVER_WARM_RESULT, SHADOW_CAPTURE, SHADOW_HOVER, CAPTURING, INTERACTION, SCROLL, ASYNC_INVALIDATION, DROPDOWN_OPEN, DROPDOWN_SELECT, DROPDOWN_ESCAPE, TEXT_INPUT, SEARCH_SUGGESTIONS, MENU_OPEN, MENU_ESCAPE, MENU_REOPEN, MENU_ITEM, TOAST_VISIBLE, TOAST_CUSTOM, TOAST_CUSTOM_EXPIRY, TOAST_STACK_VISIBLE, TOAST_EXPIRY, MENU_CLICK_AWAY, DIALOG_OPEN, DIALOG_BACKDROP, DIALOG_REOPEN, DIALOG_ESCAPE, DIALOG_CONFIRM_OPEN, DIALOG_CONFIRM, TOOLTIP, HOVER_JUMP, LONG_LIST_SCROLL, LONG_LIST_REVERSE, LONG_LIST_CLICK, LONG_LIST_IDLE, GALLERY, ALT_SHELL, RESIZE, IDLE_CHECK, VIDEO_SEARCH, VIDEO_TOPIC, VIDEO_CARD, VIDEO_MENU_OPEN, VIDEO_MENU_ITEM, VIDEO_HISTORY, VIDEO_HISTORY_DIALOG, VIDEO_HISTORY_CONFIRM, DONE }

    private Stage stage = Stage.WAIT_FOR_TITLE;
    private int ticks;
    private int captureIndex;
    private int idlePaintCount = -1;
    private double idleMouseX;
    private double idleMouseY;
    private int resizeStep;
    private boolean resizePageRequested;

    private static final int[][] RESIZE_SIZES = {{900, 560}, {430, 300}, {1024, 640}, {1600, 1000}};
    private int galleryIndex;
    private static final String[] GALLERY_PAGES =
        {"terminal", "editorial", "aurora", "blueprint", "brutalist", "harbor", "pixelport", "fieldnotes", "orbit"};
    private int hoverStep;
    private int beforeHoverPaints;
    private float hoverBaseline;
    private int tooltipBeforeRevealPagePaints = -1;
    private int tooltipStep;
    private int entityColdHoverBasePaints = -1;
    private long entityColdHoverBasePaintNanos = -1;
    private int entityColdHoverPostPaints = -1;
    private double entityColdHoverX = -1;
    private double entityColdHoverY = -1;
    private int entityWarmHoverBasePaints = -1;
    private long entityWarmHoverBasePaintNanos = -1;
    private int entityWarmHoverPostPaints = -1;
    private double entityWarmHoverX = -1;
    private double entityWarmHoverY = -1;
    private int beforeAsyncPaints;
    private static final int ENTITY_HOVER_MAX_WAIT_TICKS = 60;
    private int beforeScrollPartialPaints;
    private double scrollMouseX;
    private double scrollMouseY;
    private double scrollMidOffset;
    private double scrollReleaseOffset;
    private double scrollAfterReleaseOffset;
    private int longListMidFirst;
    private int longListReleaseFirst;
    private int longListFarFirst;
    private int longListIdlePaints = -1;
    private int longListStableTicks;
    private int longListLastPaintCount;
    private double longListLastOffset;
    private long longListBeforeGpuFrames;
    private long longListBeforeRowRasters;
    private long longListBeforeRowPixels;
    private long longListBeforeRetainedAllocations;
    private long longListBeforeAtlasAllocations;
    private long longListBeforeRowBufferAllocations;
    private int longListBeforePagePaints;
    private boolean longListBaselineCaptured;
    private int longListCompositeStableTicks;
    private int longListScrollInputTicks;
    private double longListMouseX;
    private double longListMouseY;
    private Screen titleParent;

    /** Automated verification run. */
    static boolean enabled() {
        return Boolean.getBoolean("pane.demo.autotest") || playMode();
    }

    /** Hands-on mode: load a world, open the showcase, then get out of the way. */
    static boolean playMode() {
        return Boolean.getBoolean("pane.demo.play");
    }

    private static boolean galleryMode() {
        return Boolean.getBoolean("pane.demo.gallery");
    }

    private static boolean youtubeMode() {
        return Boolean.getBoolean("pane.demo.youtube");
    }

    private static boolean texturesMode() {
        return Boolean.getBoolean("pane.demo.textures");
    }

    private static boolean entitiesMode() {
        return Boolean.getBoolean("pane.demo.entities");
    }

    private static boolean shadowsMode() {
        return Boolean.getBoolean("pane.demo.shadows");
    }

    void tick(Minecraft client) {
        ticks++;
        switch (stage) {
            case WAIT_FOR_TITLE -> {
                if (ticks > 60 && client.gui.screen() instanceof TitleScreen) {
                    titleParent = client.gui.screen();
                    client.gui.setScreen(BackdropShowcase.createScreen(titleParent));
                    stage = Stage.TITLE_BACKDROP;
                    ticks = 0;
                }
            }
            case TITLE_BACKDROP -> {
                if (ticks < 30) return;
                capture(client, "pane-title-backdrop");
                if (client.gui.screen() instanceof UiScreen screen) {
                    screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                }
                stage = Stage.TITLE_BACK;
                ticks = 0;
            }
            case TITLE_BACK -> {
                if (ticks < 8) return;
                boolean restored = client.gui.screen() == titleParent;
                log("title panorama composition and exact parent Escape: " + (restored ? "PASS" : "FAIL"));
                log("loading test world");
                loadWorld(client);
                stage = Stage.LOADING_WORLD;
                ticks = 0;
            }
            case LOADING_WORLD -> {
                if (client.player != null && client.level != null) {
                    log("world ready, settling");
                    stage = Stage.SETTLING;
                    ticks = 0;
                } else if (ticks > 20 * 90) {
                    log("FAILED: world did not load within 90s");
                    stage = Stage.DONE;
                }
            }
            case SETTLING -> {
                if (ticks > 60) {
                    client.gui.setScreen(shadowsMode() ? ShadowTabShowcase.createScreen() : entitiesMode() ? EntityPreviewShowcase.createScreen() : texturesMode() ? TextureSkinShowcase.createScreen() : youtubeMode() ? VideoSiteShowcase.createScreen()
                        : galleryMode() ? StyleGallery.createScreen() : NativeDemoShowcase.createScreen());
                    ticks = 0;
                    if (playMode()) {
                        log(entitiesMode() ? "ready - native iron-golem and villager entity previews open"
                            : texturesMode() ? "ready - Minecraft texture and skin fill reference open; click the material chips"
                            : youtubeMode() ? "ready - native YouTube-style test open; type, filter, and click cards"
                            : galleryMode() ? "ready - gallery open; use 1-9 or the bottom bar"
                            : "ready - press Esc to play, H to reopen, 1-5 for pages");
                        stage = Stage.DONE;
                        return;
                    }
                    stage = shadowsMode() ? Stage.SHADOW_CAPTURE : entitiesMode() ? Stage.ENTITY_CAPTURE : youtubeMode() ? Stage.VIDEO_SEARCH : Stage.CAPTURING;
                    captureIndex = 0;
                }
            }
            case SHADOW_CAPTURE -> {
                if (ticks < 30 || !(client.gui.screen() instanceof UiScreen screen)) return;
                capture(client, "pane-shadow-tabs-full");
                Optional<UiRect> tab = screen.regionBounds("paper-tab-1");
                if (tab.isEmpty()) {
                    log("FAILED: shadow tab bounds missing");
                    stage = Stage.DONE;
                    return;
                }
                UiRect box = tab.get();
                screen.mouseMoved((box.centerX()) / screen.layoutScale(), (box.centerY()) / screen.layoutScale());
                beforeHoverPaints = screen.paintCount();
                stage = Stage.SHADOW_HOVER;
                ticks = 0;
            }
            case SHADOW_HOVER -> {
                if (!(client.gui.screen() instanceof UiScreen screen)) {
                    log("FAILED: shadow tab screen was replaced");
                    stage = Stage.DONE;
                    return;
                }
                if (screen.paintCount() <= beforeHoverPaints) return;
                capture(client, "pane-shadow-tabs-partial-hover");
                log("shadow tabs partial hover: paints=" + (screen.paintCount() - beforeHoverPaints)
                    + " dirty=" + screen.performanceSnapshot().lastDirtyPixels());
                stage = Stage.DONE;
            }
            case ENTITY_CAPTURE -> {
                if (ticks < 30) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    capture(client, "pane-entity-previews");
                    log("native iron-golem and villager entity previews: CAPTURED");
                    log("entity cold open: paints=" + screen.paintCount() + " lastUploadPixels="
                        + screen.lastUploadPixelCount() + " lastPaintMs="
                        + String.format(Locale.ROOT, "%.2f", screen.lastPaintDurationNanos() / 1_000_000D)
                        + " snapshot=" + screen.performanceSnapshot().summary());
                    entityColdHoverBasePaints = -1;
                    entityColdHoverBasePaintNanos = -1;
                    entityColdHoverPostPaints = -1;
                    entityColdHoverX = -1;
                    entityColdHoverY = -1;
                    stage = Stage.ENTITY_HOVER_COLD;
                    ticks = 0;
                    return;
                }
                log("FAILED: entity preview screen was replaced");
                stage = Stage.DONE;
            }
            case ENTITY_HOVER_COLD -> {
                if (!(client.gui.screen() instanceof UiScreen screen)) {
                    log("FAILED: entity preview screen was replaced before cold hover");
                    stage = Stage.DONE;
                    return;
                }
                if (ticks < 12) return;
                Optional<UiRect> anchor = screen.regionBounds("iron-golem");
                if (anchor.isEmpty()) {
                    if (ticks < ENTITY_HOVER_MAX_WAIT_TICKS) {
                        return;
                    }
                    log("FAILED: iron-golem region was not painted for cold hover");
                    stage = Stage.DONE;
                    return;
                }
                UiRect box = anchor.get();
                float s = screen.layoutScale();
                entityColdHoverX = (box.x() + box.width() / 2D) / s;
                entityColdHoverY = (box.y() + box.height() / 2D) / s;
                entityColdHoverBasePaints = screen.paintCount();
                entityColdHoverBasePaintNanos = System.nanoTime();
                screen.mouseMoved(entityColdHoverX, entityColdHoverY);
                stage = Stage.ENTITY_HOVER_COLD_RESULT;
                ticks = 0;
            }
            case ENTITY_HOVER_COLD_RESULT -> {
                if (!(client.gui.screen() instanceof UiScreen screen)) {
                    log("FAILED: entity preview screen was replaced during cold hover");
                    stage = Stage.DONE;
                    return;
                }
                // A passive native-preview tooltip must not invalidate the retained card surface.
                // Give the delayed tooltip a few frames to appear, then assert zero page paints.
                if (ticks < 20) return;
                entityColdHoverPostPaints = screen.paintCount();
                int paints = Math.max(0, entityColdHoverPostPaints - Math.max(0, entityColdHoverBasePaints));
                if (paints != 0) {
                    log("FAILED: passive cold entity hover repainted retained surface: paints=" + paints);
                    stage = Stage.DONE;
                    return;
                }
                long elapsed = Math.max(0, System.nanoTime() - entityColdHoverBasePaintNanos);
                log("entity cold hover: paints=" + paints + " elapsedMs="
                    + String.format(Locale.ROOT, "%.2f", elapsed / 1_000_000D)
                    + " uploadPixels=0 dirty=0 (passive tooltip; no retained repaint)");
                screen.mouseMoved(1, 1);
                client.gui.setScreen(EntityPreviewShowcase.createScreen());
                stage = Stage.ENTITY_HOVER_WARM_OPEN;
                ticks = 0;
            }
            case ENTITY_HOVER_WARM_OPEN -> {
                if (client.gui.screen() instanceof UiScreen screen) {
                    stage = Stage.ENTITY_HOVER_WARM;
                    ticks = 0;
                }
                return;
            }
            case ENTITY_HOVER_WARM -> {
                if (!(client.gui.screen() instanceof UiScreen screen)) {
                    log("FAILED: entity preview screen was replaced before warm hover");
                    stage = Stage.DONE;
                    return;
                }
                if (ticks == 0) {
                    log("entity warm open: paints=" + screen.paintCount() + " lastUploadPixels="
                        + screen.lastUploadPixelCount() + " lastPaintMs="
                        + String.format(Locale.ROOT, "%.2f", screen.lastPaintDurationNanos() / 1_000_000D));
                    entityWarmHoverBasePaints = -1;
                    entityWarmHoverBasePaintNanos = -1;
                    entityWarmHoverPostPaints = -1;
                }
                if (ticks < 12) return;
                Optional<UiRect> anchor = screen.regionBounds("iron-golem");
                if (anchor.isEmpty()) {
                    if (ticks < ENTITY_HOVER_MAX_WAIT_TICKS) {
                        return;
                    }
                    log("FAILED: iron-golem region was not painted for warm hover");
                    stage = Stage.DONE;
                    return;
                }
                if (entityWarmHoverX < 0 || entityWarmHoverY < 0) {
                    UiRect box = anchor.get();
                    float s = screen.layoutScale();
                    entityWarmHoverX = (box.x() + box.width() / 2D) / s;
                    entityWarmHoverY = (box.y() + box.height() / 2D) / s;
                }
                entityWarmHoverBasePaints = screen.paintCount();
                entityWarmHoverBasePaintNanos = System.nanoTime();
                screen.mouseMoved(entityWarmHoverX, entityWarmHoverY);
                stage = Stage.ENTITY_HOVER_WARM_RESULT;
                ticks = 0;
            }
            case ENTITY_HOVER_WARM_RESULT -> {
                if (!(client.gui.screen() instanceof UiScreen screen)) {
                    log("FAILED: entity preview screen was replaced during warm hover");
                    stage = Stage.DONE;
                    return;
                }
                if (ticks < 20) return;
                entityWarmHoverPostPaints = screen.paintCount();
                int paints = Math.max(0, entityWarmHoverPostPaints - Math.max(0, entityWarmHoverBasePaints));
                if (paints != 0) {
                    log("FAILED: passive warm entity hover repainted retained surface: paints=" + paints);
                    stage = Stage.DONE;
                    return;
                }
                long elapsed = Math.max(0, System.nanoTime() - entityWarmHoverBasePaintNanos);
                log("entity warm hover: paints=" + paints + " elapsedMs="
                    + String.format(Locale.ROOT, "%.2f", elapsed / 1_000_000D)
                    + " uploadPixels=0 dirty=0 (passive tooltip; no retained repaint)");
                stage = Stage.DONE;
            }
            // A dedicated consumer-layout check. Run it with -Pautotest -Pyoutube; it keeps the
            // reference page separate from the base showcase regression sequence.
            case VIDEO_SEARCH -> {
                if (ticks < 24) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean focused = clickAndExpect(screen, "video-search",
                        () -> "video-search".equals(screen.focusedId().orElse("")));
                    for (char letter : "coding".toCharArray()) screen.charTyped(new CharacterEvent(letter));
                    boolean filtered = "coding".equals(screen.stringValue("video-search", ""));
                    log("video page typed search: " + (focused && filtered ? "PASS" : "FAIL"));
                } else log("FAILED: video page was replaced before typed search");
                stage = Stage.VIDEO_TOPIC;
                ticks = 0;
                return;
            }
            case VIDEO_TOPIC -> {
                if (ticks < 12) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean changed = clickAndExpect(screen, "video-topic-3",
                        () -> "Coding".equals(screen.stringValue("video-topic", "")));
                    log("video page topic chip filters: " + (changed ? "PASS" : "FAIL"));
                } else log("FAILED: video page was replaced before topic check");
                stage = Stage.VIDEO_CARD;
                ticks = 0;
                return;
            }
            case VIDEO_CARD -> {
                if (ticks < 12) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean opened = clickAndExpect(screen, "video-card-building-a-tiny-ui-library-from-scratch",
                        () -> screen.activeToastCount() == 1);
                    log("video page card action and toast: " + (opened ? "PASS" : "FAIL"));
                } else log("FAILED: video page was replaced before card check");
                stage = Stage.VIDEO_MENU_OPEN;
                ticks = 0;
                return;
            }
            case VIDEO_MENU_OPEN -> {
                if (ticks < 12) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    String menuId = "video-more-building-a-tiny-ui-library-from-scratch";
                    boolean opened = clickAndExpect(screen, menuId, () -> menuId.equals(screen.openMenuId().orElse(null)));
                    log("video page card menu opens: " + (opened ? "PASS" : "FAIL"));
                } else log("FAILED: video page was replaced before menu check");
                stage = Stage.VIDEO_MENU_ITEM;
                ticks = 0;
                return;
            }
            case VIDEO_MENU_ITEM -> {
                if (ticks < 8) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    String itemId = "menu:video-more-building-a-tiny-ui-library-from-scratch:queue";
                    boolean selected = clickAndExpect(screen, itemId,
                        () -> screen.openMenuId().isEmpty() && screen.activeToastCount() == 1);
                    log("video page menu action and toast: " + (selected ? "PASS" : "FAIL"));
                } else log("FAILED: video page was replaced before menu item check");
                stage = Stage.VIDEO_HISTORY;
                ticks = 0;
                return;
            }
            case VIDEO_HISTORY -> {
                if (ticks < 8) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean selected = clickAndExpect(screen, "video-nav-4",
                        () -> "History".equals(screen.stringValue("video-nav", "")));
                    log("video page navigation changes content: " + (selected ? "PASS" : "FAIL"));
                } else log("FAILED: video page was replaced before history check");
                stage = Stage.VIDEO_HISTORY_DIALOG;
                ticks = 0;
                return;
            }
            case VIDEO_HISTORY_DIALOG -> {
                if (ticks < 12) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean opened = clickAndExpect(screen, "video-clear-history",
                        () -> "video-clear-history".equals(screen.openDialogId().orElse(null)));
                    log("video page confirmation opens: " + (opened ? "PASS" : "FAIL"));
                } else log("FAILED: video page was replaced before confirmation check");
                stage = Stage.VIDEO_HISTORY_CONFIRM;
                ticks = 0;
                return;
            }
            case VIDEO_HISTORY_CONFIRM -> {
                if (ticks < 8) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean cleared = clickAndExpect(screen, "dialog-confirm",
                        () -> screen.booleanValue("history-cleared", false) && screen.openDialogId().isEmpty());
                    capture(client, "pane-video-site");
                    log("video page confirmation writes state: " + (cleared ? "PASS" : "FAIL"));
                    log("video consumer test complete");
                } else log("FAILED: video page was replaced before confirmation");
                stage = Stage.DONE;
                return;
            }
            case CAPTURING -> {
                if (ticks < 25) return;
                ticks = 0;
                if (client.gui.screen() instanceof UiScreen screen) {
                    if (captureIndex > 0) {
                        capture(client, "pane-live-" + CAPTURE_PAGES[captureIndex - 1]);
                        log("captured " + CAPTURE_PAGES[captureIndex - 1] + " paints=" + screen.paintCount());
                    }
                    if (captureIndex >= CAPTURE_PAGES.length) {
                        screen.showPage("settings");
                        stage = Stage.INTERACTION;
                        return;
                    }
                    screen.showPage(CAPTURE_PAGES[captureIndex]);
                    captureIndex++;
                } else {
                    log("FAILED: showcase screen was replaced");
                    stage = Stage.DONE;
                }
            }
            case INTERACTION -> {
                if (ticks < 30) return;
                ticks = 0;
                if (client.gui.screen() instanceof UiScreen screen) {
                    checkSlider(screen);
                    checkToggle(screen);
                    checkSelection(screen);
                    checkScrollStart(screen);
                }
                stage = Stage.SCROLL;
                ticks = 0;
                return;
            }
            case SCROLL -> {
                if (client.gui.screen() instanceof UiScreen screen) {
                    if (ticks <= 20) {
                        screen.mouseScrolled(scrollMouseX, scrollMouseY, 0, -.1);
                        if (ticks == 10) scrollMidOffset = screen.numberValue("scroll:settings-content", 0);
                        if (ticks == 20) scrollReleaseOffset = screen.numberValue("scroll:settings-content", 0);
                        return;
                    }
                    if (ticks == 21) {
                        scrollAfterReleaseOffset = screen.numberValue("scroll:settings-content", 0);
                        return;
                    }
                    if (ticks == 22) {
                        double releaseAdvance = scrollAfterReleaseOffset - scrollReleaseOffset;
                        boolean advancedLive = scrollMidOffset > 1 && scrollReleaseOffset > scrollMidOffset;
                        boolean noReleaseJump = releaseAdvance >= 0
                            && releaseAdvance < screen.document().scrollSettings().step() * .75;
                        log("continuous trackpad target following: "
                            + (advancedLive && noReleaseJump ? "PASS" : "FAIL")
                            + " mid=" + Math.round(scrollMidOffset)
                            + " release=" + Math.round(scrollReleaseOffset)
                            + " next=" + Math.round(scrollAfterReleaseOffset));
                        // Finish at the lower controls so the existing alignment checks still run.
                        screen.mouseScrolled(scrollMouseX, scrollMouseY, 0, -20);
                        return;
                    }
                    if (ticks < 34) return;
                    boolean moved = screen.numberValue("scroll:settings-content", 0) > 0;
                    boolean partial = screen.partialPaintCount() > beforeScrollPartialPaints;
                    long fullPixels = (long) screen.surfacePixelWidth() * screen.surfacePixelHeight();
                    // The macOS seam workaround deliberately uploads the complete DynamicTexture.
                    // Gate bounded CPU painting here; report GPU traffic separately, without
                    // claiming that partial rasterization guarantees a partial upload.
                    log("scroll movement and bounded rasterization: "
                        + (moved && partial ? "PASS" : "FAIL")
                        + " partialPaints=" + (screen.partialPaintCount() - beforeScrollPartialPaints)
                        + " upload=" + screen.lastUploadPixelCount() + "/" + fullPixels
                        + " paint=" + String.format(java.util.Locale.ROOT, "%.2fms",
                            screen.lastPaintDurationNanos() / 1_000_000D));
                    boolean visible = screen.regionBounds("scroll-speed").isPresent();
                    log("scroll reveals lower control: " + (visible ? "PASS" : "FAIL"));
                    SurfaceGpuCheck.verify(screen, DemoAutoTest::log);
                    checkTextAreaAndTag(screen);
                    beforeAsyncPaints = screen.paintCount();
                    screen.invalidateWhenComplete(java.util.concurrent.CompletableFuture.runAsync(() -> {}));
                } else {
                    log("FAILED: settings screen was replaced while scrolling");
                }
                stage = Stage.ASYNC_INVALIDATION;
                ticks = 0;
                return;
            }
            case ASYNC_INVALIDATION -> {
                if (ticks < 12) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean repainted = screen.paintCount() > beforeAsyncPaints;
                    log("off-thread completion invalidation: " + (repainted ? "PASS" : "FAIL"));
                    screen.showPage("settings");
                } else log("FAILED: settings screen was replaced before async invalidation check");
                stage = Stage.DROPDOWN_OPEN;
                ticks = 0;
                return;
            }
            case DROPDOWN_OPEN -> {
                if (ticks < 12) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean opened = clickAndExpect(screen, "accent-preset",
                        () -> "accent-preset".equals(screen.openMenuId().orElse(null)));
                    log("native dropdown opens: " + (opened ? "PASS" : "FAIL"));
                } else log("FAILED: settings screen was replaced before dropdown open");
                stage = Stage.DROPDOWN_SELECT;
                ticks = 0;
                return;
            }
            case DROPDOWN_SELECT -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean selected = clickAndExpect(screen, "menu:accent-preset:option-1",
                        () -> screen.openMenuId().isEmpty()
                            && "ocean".equals(screen.stringValue("accent-preset", "")));
                    log("dropdown writes selection and closes: " + (selected ? "PASS" : "FAIL"));
                    clickAndExpect(screen, "accent-preset",
                        () -> "accent-preset".equals(screen.openMenuId().orElse(null)));
                } else log("FAILED: settings screen was replaced before dropdown selection");
                stage = Stage.DROPDOWN_ESCAPE;
                ticks = 0;
                return;
            }
            case DROPDOWN_ESCAPE -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                    boolean closed = screen.openMenuId().isEmpty() && screen.focusedId().isEmpty();
                    log("dropdown Escape dismissal and focus cleanup: " + (closed ? "PASS" : "FAIL"));
                    screen.showPage("discover");
                } else log("FAILED: settings screen was replaced before dropdown Escape check");
                stage = Stage.TEXT_INPUT;
                ticks = 0;
                return;
            }
            case TEXT_INPUT -> {
                if (ticks < 12) return;
                if (client.gui.screen() instanceof UiScreen screen) checkTextInput(screen);
                else log("FAILED: discover screen was replaced before text input");
                stage = Stage.SEARCH_SUGGESTIONS;
                ticks = 0;
                return;
            }
            case SEARCH_SUGGESTIONS -> {
                if (ticks < 12) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    checkSearchSuggestion(screen);
                    screen.showPage("waypoints");
                }
                else log("FAILED: discover screen was replaced before search suggestions");
                stage = Stage.MENU_OPEN;
                ticks = 0;
                return;
            }
            case MENU_OPEN -> {
                if (ticks < 12) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean opened = clickAndExpect(screen, "waypoint-actions",
                        () -> "waypoint-actions".equals(screen.openMenuId().orElse(null)));
                    log("native menu opens from anchor: " + (opened ? "PASS" : "FAIL"));
                } else log("FAILED: waypoints screen was replaced before menu open");
                stage = Stage.MENU_ESCAPE;
                ticks = 0;
                return;
            }
            case MENU_ESCAPE -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean painted = screen.regionBounds("menu:waypoint-actions:copy").isPresent();
                    screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                    boolean closed = screen.openMenuId().isEmpty() && screen.focusedId().isEmpty();
                    log("menu popup, Escape, and focus cleanup: " + (painted && closed ? "PASS" : "FAIL"));
                } else log("FAILED: waypoints screen was replaced before menu Escape check");
                stage = Stage.MENU_REOPEN;
                ticks = 0;
                return;
            }
            case MENU_REOPEN -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean opened = clickAndExpect(screen, "waypoint-actions",
                        () -> "waypoint-actions".equals(screen.openMenuId().orElse(null)));
                    if (!opened) log("FAILED: menu did not reopen for item check");
                } else log("FAILED: waypoints screen was replaced before menu item check");
                stage = Stage.MENU_ITEM;
                ticks = 0;
                return;
            }
            case MENU_ITEM -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean selected = clickAndExpect(screen, "menu:waypoint-actions:copy",
                        () -> screen.openMenuId().isEmpty()
                            && "Coordinates copied to clipboard".equals(screen.stringValue("waypoint-menu-result", ""))
                            && screen.activeToastCount() == 1);
                    log("menu item emits action, closes, and shows toast: " + (selected ? "PASS" : "FAIL"));
                } else log("FAILED: waypoints screen was replaced before menu selection");
                stage = Stage.TOAST_VISIBLE;
                ticks = 0;
                return;
            }
            case TOAST_VISIBLE -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean dismissed = clickAndExpect(screen, "toast-dismiss:coordinates-copied",
                        () -> screen.activeToastCount() == 0);
                    log("toast paints and manual dismissal works: " + (dismissed ? "PASS" : "FAIL"));
                    screen.toast(dev.pane.ui.UiToast.builder("toast-no-close", "Pinned notice")
                        .message("Top-left custom toast").icon("i").durationMillis(300)
                        .placement(dev.pane.ui.UiToastPlacement.TOP_LEFT).dismissible(false)
                        .style(dev.pane.ui.UiToastStyle.builder().background(0xFF1B2133).radius(3).build()).build());
                } else log("FAILED: waypoints screen was replaced before toast dismissal");
                stage = Stage.TOAST_CUSTOM;
                ticks = 0;
                return;
            }
            case TOAST_CUSTOM -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    Optional<UiRect> bounds = screen.toastBounds("toast-no-close");
                    boolean custom = bounds.isPresent() && bounds.get().x() < 80 && bounds.get().y() < 80
                        && screen.regionBounds("toast-dismiss:toast-no-close").isEmpty();
                    log("toast placement and optional close button: " + (custom ? "PASS" : "FAIL"));
                } else log("FAILED: waypoints screen was replaced before custom toast check");
                stage = Stage.TOAST_CUSTOM_EXPIRY;
                ticks = 0;
                return;
            }
            case TOAST_CUSTOM_EXPIRY -> {
                if (ticks < 12) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean expired = screen.activeToastCount() == 0;
                    log("custom toast expiry works: " + (expired ? "PASS" : "FAIL"));
                    screen.toast(new dev.pane.ui.UiToast("toast-stack-first", "First task", "Queued first", 3_000));
                    screen.toast(new dev.pane.ui.UiToast("toast-stack-second", "Second task", "Queued second", 3_000));
                } else log("FAILED: waypoints screen was replaced before custom toast expiry");
                stage = Stage.TOAST_STACK_VISIBLE;
                ticks = 0;
                return;
            }
            case TOAST_STACK_VISIBLE -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    Optional<UiRect> first = screen.regionBounds("toast-dismiss:toast-stack-first");
                    Optional<UiRect> second = screen.regionBounds("toast-dismiss:toast-stack-second");
                    boolean stacked = screen.activeToastCount() == 2 && first.isPresent() && second.isPresent()
                        && Math.abs(first.get().y() - second.get().y()) > 20;
                    log("toasts stack predictably: " + (stacked ? "PASS" : "FAIL"));
                    clickAndExpect(screen, "toast-dismiss:toast-stack-first", () -> screen.activeToastCount() == 1);
                    clickAndExpect(screen, "toast-dismiss:toast-stack-second", () -> screen.activeToastCount() == 0);
                    screen.toast(new dev.pane.ui.UiToast("toast-expiry", "Temporary", "This fades out", 300));
                } else log("FAILED: waypoints screen was replaced before toast stack check");
                stage = Stage.TOAST_EXPIRY;
                ticks = 0;
                return;
            }
            case TOAST_EXPIRY -> {
                if (ticks < 12) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    log("toast automatic expiry works: " + (screen.activeToastCount() == 0 ? "PASS" : "FAIL"));
                } else log("FAILED: waypoints screen was replaced before toast expiry check");
                stage = Stage.MENU_CLICK_AWAY;
                ticks = 0;
                return;
            }
            case MENU_CLICK_AWAY -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    screen.mouseClicked(at(1, 1), false);
                    boolean closed = screen.openMenuId().isEmpty() && screen.focusedId().isEmpty();
                    log("menu click-away dismissal and focus cleanup: " + (closed ? "PASS" : "FAIL"));
                } else log("FAILED: waypoints screen was replaced before menu click-away check");
                stage = Stage.DIALOG_OPEN;
                ticks = 0;
                return;
            }
            case DIALOG_OPEN -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean opened = clickAndExpect(screen, "reset-routes",
                        () -> "reset-routes".equals(screen.openDialogId().orElse(null)));
                    log("native dialog opens above page: " + (opened ? "PASS" : "FAIL"));
                } else log("FAILED: waypoints screen was replaced before dialog open");
                stage = Stage.DIALOG_BACKDROP;
                ticks = 0;
                return;
            }
            case DIALOG_BACKDROP -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean painted = screen.regionBounds("dialog-confirm").isPresent();
                    press(screen, 1, 1);
                    release(screen, 1, 1);
                    boolean cancelled = painted && screen.openDialogId().isEmpty()
                        && "Reset cancelled".equals(screen.stringValue("waypoint-menu-result", ""));
                    log("dialog backdrop cancels and blocks page: " + (cancelled ? "PASS" : "FAIL"));
                } else log("FAILED: waypoints screen was replaced before dialog backdrop check");
                stage = Stage.DIALOG_REOPEN;
                ticks = 0;
                return;
            }
            case DIALOG_REOPEN -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean opened = clickAndExpect(screen, "reset-routes",
                        () -> "reset-routes".equals(screen.openDialogId().orElse(null)));
                    if (!opened) log("FAILED: dialog did not reopen for Escape check");
                } else log("FAILED: waypoints screen was replaced before dialog Escape check");
                stage = Stage.DIALOG_ESCAPE;
                ticks = 0;
                return;
            }
            case DIALOG_ESCAPE -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                    boolean cancelled = screen.openDialogId().isEmpty()
                        && "reset-routes".equals(screen.focusedId().orElse(null))
                        && "Reset cancelled".equals(screen.stringValue("waypoint-menu-result", ""));
                    log("dialog Escape dismissal and trigger focus restoration: " + (cancelled ? "PASS" : "FAIL"));
                } else log("FAILED: waypoints screen was replaced before dialog Escape check");
                stage = Stage.DIALOG_CONFIRM_OPEN;
                ticks = 0;
                return;
            }
            case DIALOG_CONFIRM_OPEN -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean opened = clickAndExpect(screen, "reset-routes",
                        () -> "reset-routes".equals(screen.openDialogId().orElse(null)));
                    if (!opened) log("FAILED: dialog did not reopen for confirm check");
                } else log("FAILED: waypoints screen was replaced before dialog confirm check");
                stage = Stage.DIALOG_CONFIRM;
                ticks = 0;
                return;
            }
            case DIALOG_CONFIRM -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean confirmed = clickAndExpect(screen, "dialog-confirm",
                        () -> screen.openDialogId().isEmpty()
                            && "Routes reset (demo only)".equals(screen.stringValue("waypoint-menu-result", "")));
                    log("dialog confirm emits action and closes: " + (confirmed ? "PASS" : "FAIL"));
                    screen.showPage("discover");
                    tooltipStep = 0;
                    tooltipBeforeRevealPagePaints = -1;
                } else log("FAILED: waypoints screen was replaced before dialog confirm");
                stage = Stage.TOOLTIP;
                ticks = 0;
                return;
            }
            case TOOLTIP -> {
                if (!(client.gui.screen() instanceof UiScreen screen)) {
                    log("FAILED: discover screen was replaced before tooltip check");
                    stage = Stage.HOVER_JUMP;
                    return;
                }
                // A prior dialog test intentionally creates a toast. Wait for it to expire so
                // this stage isolates the virtual-row tooltip render path.
                if (screen.activeToastCount() > 0) return;
                Optional<UiRect> anchor = screen.regionBounds("directory-row-0");
                if (anchor.isEmpty()) {
                    if (ticks < 8) return;
                    log("FAILED: virtual row tooltip anchor was not painted after waiting");
                    stage = Stage.HOVER_JUMP;
                    return;
                }
                if (tooltipStep == 0) {
                    UiRect box = anchor.get();
                    float scale = screen.layoutScale();
                    screen.mouseMoved((box.x() + box.width() / 2) / scale, (box.y() + box.height() / 2) / scale);
                    tooltipStep = 1;
                    ticks = 0;
                    return;
                }
                // Capture after the finite row-hover animation but before the delayed tooltip.
                // Revealing the tooltip must not repaint the page or disable row compositing.
                if (ticks == 10) tooltipBeforeRevealPagePaints = screen.paintCount();
                // The showcase intentionally uses a 650ms tooltip delay. Leave headroom for a
                // busy real client instead of treating an almost-on-time repaint as a failure.
                if (ticks < 30) return;
                boolean visible = "directory-row-0".equals(screen.visibleTooltipId().orElse(null));
                boolean atlasStayedActive = screen.virtualCompositionActive();
                boolean pageStayedStill = tooltipBeforeRevealPagePaints >= 0
                    && screen.paintCount() == tooltipBeforeRevealPagePaints;
                log("virtual tooltip overlay stays pixel-stable: "
                    + (visible && atlasStayedActive && pageStayedStill ? "PASS" : "FAIL")
                    + " (visible=" + visible + ", atlas=" + atlasStayedActive
                    + ", page paints=" + tooltipBeforeRevealPagePaints + "->" + screen.paintCount() + ")");
                screen.mouseMoved(0, 0);
                hoverStep = 0;
                stage = Stage.HOVER_JUMP;
                return;
            }
            // Hovering a control must not move page content. This is the regression guard for
            // the shared animation clock, where every hover replayed the page entrance and the
            // whole layout visibly jumped.
            case HOVER_JUMP -> {
                if (!(client.gui.screen() instanceof UiScreen screen)) {
                    stage = Stage.DONE;
                    return;
                }
                if (hoverStep == 0) {
                    if (ticks < 30) return;
                    Optional<UiRect> anchor = screen.regionBounds("install-sodium");
                    if (anchor.isEmpty()) {
                        log("FAILED: install-sodium was not painted");
                        stage = Stage.ALT_SHELL;
                        return;
                    }
                    hoverBaseline = anchor.get().y();
                    screen.mouseMoved(0, 0);
                    hoverStep = 1;
                    ticks = 0;
                    return;
                }
                if (hoverStep == 1) {
                    if (ticks < 12) return;
                    Optional<UiRect> nav = screen.regionBounds("nav-loadout");
                    if (nav.isEmpty()) {
                        log("FAILED: nav-loadout was not painted");
                        stage = Stage.ALT_SHELL;
                        return;
                    }
                    UiRect box = nav.get();
                    float s = screen.layoutScale();
                    beforeHoverPaints = screen.paintCount();
                    screen.mouseMoved((box.x() + box.width() / 2) / s, (box.y() + box.height() / 2) / s);
                    hoverStep = 2;
                    ticks = 0;
                    return;
                }
                if (ticks < 1) return;
                float after = screen.regionBounds("install-sodium").map(UiRect::y).orElse(Float.NaN);
                boolean repainted = screen.paintCount() > beforeHoverPaints;
                float drift = Math.abs(after - hoverBaseline);
                log("hover page drift: " + drift + " layout units"
                    + (!repainted ? " (INCONCLUSIVE: hover did not repaint)"
                       : drift < 0.01F ? " (PASS)" : " (FAIL)"));
                Optional<UiRect> firstRow = screen.regionBounds("directory-row-0");
                if (firstRow.isEmpty()) {
                    log("FAILED: first directory row was not painted");
                    client.gui.setScreen(StyleGallery.createScreen());
                    galleryIndex = 0;
                    stage = Stage.GALLERY;
                    ticks = 0;
                    return;
                }
                UiRect row = firstRow.get();
                float scale = screen.layoutScale();
                // Keep the pointer inside the viewport but outside moving row hit targets. That
                // isolates scroll invalidation from legitimate hover-transition repaints.
                longListMouseX = (row.x() + row.width() - 2) / scale;
                longListMouseY = (row.y() + row.height() / 2) / scale;
                screen.mouseMoved(longListMouseX, longListMouseY);
                longListMidFirst = 0;
                longListReleaseFirst = 0;
                longListFarFirst = 0;
                longListBaselineCaptured = false;
                longListCompositeStableTicks = 0;
                longListScrollInputTicks = 0;
                stage = Stage.LONG_LIST_SCROLL;
                ticks = 0;
            }
            case LONG_LIST_SCROLL -> {
                if (!(client.gui.screen() instanceof UiScreen screen)) {
                    log("FAILED: directory screen was replaced during long-list scroll");
                    stage = Stage.DONE;
                    return;
                }
                if (!screen.virtualCompositionActive()
                    || screen.virtualCachedRows("discover-directory") == 0) {
                    longListCompositeStableTicks = 0;
                    if (ticks > 80) {
                        log("FAILED: virtual-list composition did not become ready");
                        stage = Stage.GALLERY;
                    }
                    return;
                }
                longListCompositeStableTicks++;
                if (longListCompositeStableTicks < 10) return;
                if (!longListBaselineCaptured) {
                    int cachedRows = screen.virtualCachedRows("discover-directory");
                    log("10,000-project virtual list caches " + cachedRows + " rows: "
                        + (cachedRows > 0 && cachedRows < 32 ? "PASS" : "FAIL"));
                    log("last virtual row non-transparent pixels: "
                        + screen.lastVirtualRowNonTransparentPixels());
                    longListBeforeGpuFrames = screen.gpuScrollFrameCount();
                    longListBeforeRowRasters = screen.virtualRowRasterCount();
                    longListBeforeRowPixels = screen.virtualRowUploadPixels();
                    longListBeforeRetainedAllocations = screen.retainedTextureAllocationCount();
                    longListBeforeAtlasAllocations = screen.virtualAtlasAllocationCount();
                    longListBeforeRowBufferAllocations = screen.virtualRowBufferAllocationCount();
                    longListBeforePagePaints = screen.paintCount();
                    longListBaselineCaptured = true;
                    return;
                }
                longListScrollInputTicks++;
                if (longListScrollInputTicks <= 30) {
                    screen.mouseScrolled(longListMouseX, longListMouseY, 0, -.7);
                    if (longListScrollInputTicks == 12) longListMidFirst = screen.virtualFirstVisible("discover-directory");
                    if (longListScrollInputTicks == 30) longListReleaseFirst = screen.virtualFirstVisible("discover-directory");
                    return;
                }
                if (longListScrollInputTicks < 48) return;
                longListFarFirst = screen.virtualFirstVisible("discover-directory");
                int paintedRows = screen.virtualCachedRows("discover-directory");
                boolean live = longListMidFirst > 0 && longListReleaseFirst > longListMidFirst;
                boolean far = longListFarFirst >= longListReleaseFirst;
                boolean bounded = paintedRows > 0 && paintedRows < 32;
                long gpuFrames = screen.gpuScrollFrameCount() - longListBeforeGpuFrames;
                boolean gpuMoved = gpuFrames > 0;
                boolean pageStayedCached = screen.paintCount() == longListBeforePagePaints;
                long fullPixels = (long) screen.surfacePixelWidth() * screen.surfacePixelHeight();
                long rowRasters = screen.virtualRowRasterCount() - longListBeforeRowRasters;
                long rowPixels = screen.virtualRowUploadPixels() - longListBeforeRowPixels;
                long oldFramePixels = fullPixels * Math.max(1, gpuFrames);
                double uploadPercent = rowPixels * 100D / oldFramePixels;
                double rowRasterRatio = rowRasters * 100D
                    / Math.max(1, (long) paintedRows * Math.max(1, gpuFrames));
                long retainedAllocations = screen.retainedTextureAllocationCount()
                    - longListBeforeRetainedAllocations;
                long atlasAllocations = screen.virtualAtlasAllocationCount()
                    - longListBeforeAtlasAllocations;
                long rowBufferAllocations = screen.virtualRowBufferAllocationCount()
                    - longListBeforeRowBufferAllocations;
                boolean allocationStable = retainedAllocations == 0 && atlasAllocations == 0
                    && rowBufferAllocations == 0;
                log("long-list continuous scroll: mid=" + longListMidFirst + " release="
                    + longListReleaseFirst + " settled=" + longListFarFirst + " "
                    + (live && far ? "PASS" : "FAIL"));
                log("long-list GPU composition: cachedRows=" + paintedRows + " gpuFrames="
                    + gpuFrames + " pagePaints="
                    + (screen.paintCount() - longListBeforePagePaints) + " rowRasters=" + rowRasters
                    + " rowUpload=" + rowPixels + " vsOldFrames=" + oldFramePixels + " ("
                    + String.format(java.util.Locale.ROOT, "%.1f%%", uploadPercent) + ") lastRowPaint="
                    + String.format(java.util.Locale.ROOT, "%.2fms", screen.lastVirtualRowPaintNanos() / 1_000_000D)
                    + " " + (bounded && gpuMoved && pageStayedCached
                        && rowPixels * 8 < oldFramePixels ? "PASS" : "FAIL"));
                log("long-list hot-path allocations: retained=" + retainedAllocations + " atlas="
                    + atlasAllocations + " rowBuffers=" + rowBufferAllocations + " rowRasterRatio="
                    + String.format(java.util.Locale.ROOT, "%.1f%%", rowRasterRatio) + " "
                    + (allocationStable ? "PASS" : "FAIL"));
                stage = Stage.LONG_LIST_REVERSE;
                ticks = 0;
            }
            case LONG_LIST_REVERSE -> {
                if (!(client.gui.screen() instanceof UiScreen screen)) {
                    stage = Stage.DONE;
                    return;
                }
                if (ticks <= 12) {
                    screen.mouseScrolled(longListMouseX, longListMouseY, 0, .7);
                    if (ticks == 6) {
                        int movingTarget = Math.max(0,
                            screen.virtualFirstVisible("discover-directory") + 2);
                        boolean movingClick = clickAndExpect(screen,
                            "directory-row-" + movingTarget,
                            () -> screen.numberValue("directory-opened-index", -1) == movingTarget);
                        boolean movingDoubleClick = doubleClickAndExpect(screen,
                            "directory-row-" + movingTarget,
                            () -> screen.numberValue("directory-double-clicked-index", -1) == movingTarget);
                        log("virtual row click during motion index " + movingTarget + ": "
                            + (movingClick && movingDoubleClick ? "PASS" : "FAIL"));
                    }
                    return;
                }
                if (ticks == 13) {
                    int reversedTarget = Math.max(0,
                        screen.virtualFirstVisible("discover-directory") + 2);
                    boolean postReverseDoubleClick = doubleClickAndExpect(screen,
                        "directory-row-" + reversedTarget,
                        () -> screen.numberValue("directory-double-clicked-index", -1) == reversedTarget);
                    log("virtual row double-click immediately after reversal index " + reversedTarget
                        + ": " + (postReverseDoubleClick ? "PASS" : "FAIL"));
                    return;
                }
                if (ticks < 28) return;
                int reversedFirst = screen.virtualFirstVisible("discover-directory");
                log("long-list direction reversal: " + longListFarFirst + " → " + reversedFirst
                    + (reversedFirst < longListFarFirst ? " (PASS)" : " (FAIL)"));
                stage = Stage.LONG_LIST_CLICK;
                ticks = 0;
            }
            case LONG_LIST_CLICK -> {
                if (ticks < 4) return;
                if (client.gui.screen() instanceof UiScreen screen) {
                    screen.setValue("directory-key-selected", 249D);
                    screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_DOWN, 0, 0));
                    boolean arrowSelected = screen.numberValue("directory-key-selected", -1) == 250
                        && screen.virtualFirstVisible("discover-directory") <= 250
                        && screen.regionBounds("directory-row-250").isPresent();
                    log("arrow selection reveals virtual row 250: " + (arrowSelected ? "PASS" : "FAIL"));
                    int first = screen.virtualFirstVisible("discover-directory");
                    int target = Math.max(0, first + 2);
                    boolean clicked = clickAndExpect(screen, "directory-row-" + target,
                        () -> screen.numberValue("directory-opened-index", -1) == target);
                    log("recycled virtual row click index " + target + ": " + (clicked ? "PASS" : "FAIL"));
                    String originalFocus = screen.focusedId().orElse(null);
                    boolean tabMoved = screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, 0))
                        && screen.focusedId().isPresent()
                        && !screen.focusedId().get().equals(originalFocus);
                    boolean shiftTabReturned = screen.keyPressed(
                        new KeyEvent(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_SHIFT))
                        && java.util.Objects.equals(originalFocus, screen.focusedId().orElse(null));
                    log("virtual row Tab/Shift-Tab traversal: "
                        + (tabMoved && shiftTabReturned ? "PASS" : "FAIL"));
                    log("virtual composition at capture: " + screen.virtualCompositionActive());
                    capture(client, "pane-live-long-directory");
                    longListIdlePaints = -1;
                    longListStableTicks = 0;
                    longListLastOffset = screen.numberValue("scroll:discover-directory", 0);
                    longListLastPaintCount = screen.paintCount();
                } else log("FAILED: directory screen was replaced before row click");
                stage = Stage.LONG_LIST_IDLE;
                ticks = 0;
            }
            case LONG_LIST_IDLE -> {
                if (!(client.gui.screen() instanceof UiScreen screen)) {
                    stage = Stage.DONE;
                    return;
                }
                if (longListIdlePaints < 0) {
                    double offset = screen.numberValue("scroll:discover-directory", 0);
                    int paints = screen.paintCount();
                    if (Math.abs(offset - longListLastOffset) < .01 && paints == longListLastPaintCount) {
                        longListStableTicks++;
                    }
                    else longListStableTicks = 0;
                    longListLastOffset = offset;
                    longListLastPaintCount = paints;
                    if (longListStableTicks < 12) return;
                    longListIdlePaints = screen.paintCount();
                    ticks = 0;
                    return;
                }
                if (ticks < 60) return;
                int idleDelta = screen.paintCount() - longListIdlePaints;
                log("long-list idle repaints over 3s: " + idleDelta
                    + (idleDelta == 0 ? " (PASS)" : " (FAIL)"));
                client.gui.setScreen(StyleGallery.createScreen());
                galleryIndex = 0;
                stage = Stage.GALLERY;
                ticks = 0;
            }
            case GALLERY -> {
                if (ticks < 22) return;
                ticks = 0;
                if (!(client.gui.screen() instanceof UiScreen screen)) {
                    stage = Stage.ALT_SHELL;
                    return;
                }
                if (galleryIndex > 0) {
                    checkGalleryInteraction(screen, GALLERY_PAGES[galleryIndex - 1]);
                    capture(client, "pane-style-" + GALLERY_PAGES[galleryIndex - 1]);
                    log("captured style " + GALLERY_PAGES[galleryIndex - 1]);
                }
                if (galleryIndex >= GALLERY_PAGES.length) {
                    client.gui.setScreen(AltShellShowcase.createScreen());
                    stage = Stage.ALT_SHELL;
                    return;
                }
                screen.showPage(GALLERY_PAGES[galleryIndex]);
                galleryIndex++;
            }
            case ALT_SHELL -> {
                if (ticks < 30) return;
                ticks = 0;
                capture(client, "pane-live-altshell");
                log("captured alt shell consumer");
                // This part of the harness intentionally has no parent. Parent restoration is
                // already verified against the exact title screen above, and retaining the
                // temporary alternate shell here would make Escape semantics irrelevant noise.
                client.gui.setScreen(NativeDemoShowcase.createScreen(null));
                resizeStep = 0;
                resizePageRequested = false;
                stage = Stage.RESIZE;
            }
            // Resizing frees and reallocates the surface texture and its backing image. Cycle
            // through several sizes, including a very small one, to shake out lifetime bugs.
            case RESIZE -> {
                if (ticks < 12) return;
                ticks = 0;
                if (resizeStep < RESIZE_SIZES.length) {
                    int[] size = RESIZE_SIZES[resizeStep++];
                    client.getWindow().setWindowed(size[0], size[1]);
                    return;
                }
                if (resizeStep == RESIZE_SIZES.length && client.gui.screen() instanceof UiScreen s) {
                    log("scale curve: points=" + client.getWindow().getScreenWidth()
                        + "x" + client.getWindow().getScreenHeight()
                        + " guiScale=" + client.getWindow().getGuiScale()
                        + " layoutScale=" + s.layoutScale()
                        + " layout=" + Math.round(client.getWindow().getGuiScaledWidth() * s.layoutScale())
                        + "x" + Math.round(client.getWindow().getGuiScaledHeight() * s.layoutScale()));
                }
                if (client.gui.screen() instanceof UiScreen screen) {
                    boolean viewportSynced = screen.width == client.getWindow().getGuiScaledWidth()
                        && screen.height == client.getWindow().getGuiScaledHeight()
                        && screen.surfacePixelWidth() > 0 && screen.surfacePixelHeight() > 0;
                    log("retained surface matches active resize viewport: " + (viewportSynced ? "PASS" : "FAIL"));
                    log("survived " + RESIZE_SIZES.length + " resizes, paints=" + screen.paintCount()
                        + " window=" + client.getWindow().getGuiScaledWidth()
                        + "x" + client.getWindow().getGuiScaledHeight()
                        + " layoutScale=" + screen.layoutScale());
                    // Native slots are placed in layout units and consumed in GUI units, so a
                    // resize that changes the scale is where that conversion would break.
                    if (!resizePageRequested) {
                        resizePageRequested = true;
                        screen.showPage("loadout");
                        ticks = 0;
                        return;
                    }
                    log("post-resize page transition: "
                        + ("loadout".equals(screen.pageId()) ? "PASS" : "FAIL (" + screen.pageId() + ")"));
                    capture(client, "pane-live-resized");
                } else {
                    log("FAILED: screen lost during resize");
                }
                idlePaintCount = -1;
                stage = Stage.IDLE_CHECK;
            }
            case IDLE_CHECK -> {
                if (!(client.gui.screen() instanceof UiScreen screen)) {
                    stage = Stage.DONE;
                    return;
                }
                if (idlePaintCount < 0) {
                    if (ticks < 40) return;
                    idlePaintCount = screen.paintCount();
                    idleMouseX = client.mouseHandler.xpos();
                    idleMouseY = client.mouseHandler.ypos();
                    ticks = 0;
                    return;
                }
                if (ticks < 100) return;
                int delta = screen.paintCount() - idlePaintCount;
                // Hover changes legitimately repaint, so a moving cursor is not a failure. Report
                // it separately instead of letting real input masquerade as a stuck repaint loop.
                boolean mouseMoved = client.mouseHandler.xpos() != idleMouseX
                    || client.mouseHandler.ypos() != idleMouseY;
                String verdict = delta == 0 ? " (PASS)"
                    : mouseMoved ? " (INCONCLUSIVE: cursor moved during the check)" : " (FAIL)";
                log("idle repaints over 5s: " + delta + verdict);
                capture(client, "pane-live-idle");
                log("autotest complete");
                stage = Stage.DONE;
            }
            case DONE -> {
                if (Boolean.getBoolean("pane.demo.autotestExit") && !playMode()) client.stop();
            }
        }
    }

    /**
     * Drags the motion slider to known points on its visible track and checks the value that
     * comes back. This is the regression guard for the grab-padding offset: the hit rectangle is
     * wider than the rail, so mapping the value against the rectangle used to make the ends
     * unreachable and left the knob trailing the cursor.
     */
    private void checkSlider(UiScreen screen) {
        Optional<UiRect> track = screen.trackBounds("motion-slider");
        if (track.isEmpty()) {
            log("FAILED: motion-slider was not painted");
            return;
        }
        UiRect rail = track.get();
        float scale = screen.layoutScale();
        double grabX = (rail.x() + rail.width() / 2) / scale;
        double grabY = (rail.y() + rail.height() / 2) / scale;
        press(screen, grabX, grabY);
        boolean ok = true;
        ok &= expectSlider(screen, rail, scale, 0.0);
        ok &= expectSlider(screen, rail, scale, 1.0);
        ok &= expectSlider(screen, rail, scale, 0.25);
        ok &= expectSlider(screen, rail, scale, 0.5);
        release(screen, grabX, grabY);
        log("slider track mapping: " + (ok ? "PASS" : "FAIL"));
    }

    private void checkScrollStart(UiScreen screen) {
        Optional<UiRect> anchor = screen.regionBounds("motion-slider");
        if (anchor.isEmpty()) {
            log("FAILED: scroll viewport was not painted");
            return;
        }
        UiRect box = anchor.get();
        float scale = screen.layoutScale();
        beforeScrollPartialPaints = screen.partialPaintCount();
        scrollMouseX = (box.x() + box.width() / 2) / scale;
        scrollMouseY = (box.y() + box.height() / 2) / scale;
        scrollMidOffset = 0;
        scrollReleaseOffset = 0;
        scrollAfterReleaseOffset = 0;
    }

    private void checkTextAreaAndTag(UiScreen screen) {
        Optional<UiRect> field = screen.regionBounds("project-notes");
        if (field.isEmpty()) {
            log("FAILED: scroll did not reveal multiline editor");
            return;
        }
        UiRect box = field.get();
        float scale = screen.layoutScale();
        double x = (box.x() + 14) / scale;
        double y = (box.y() + 14) / scale;
        press(screen, x, y);
        release(screen, x, y);
        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL));
        Minecraft.getInstance().keyboardHandler.setClipboard("first line\nsecond line");
        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL));
        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
        screen.charTyped(new CharacterEvent('z'));
        boolean editor = "first line\nsecond line\nz".equals(screen.stringValue("project-notes", ""));
        boolean tag = clickAndExpect(screen, "tag-client",
            () -> screen.booleanValue("client-tag-removed", false));
        log("multiline editor and removable tag: " + (editor && tag ? "PASS" : "FAIL"));
    }

    private void checkTextInput(UiScreen screen) {
        Optional<UiRect> field = screen.regionBounds("project-search");
        if (field.isEmpty()) {
            log("FAILED: project search field was not painted");
            return;
        }
        UiRect box = field.get();
        float scale = screen.layoutScale();
        double x = (box.x() + box.width() / 2) / scale;
        double y = (box.y() + box.height() / 2) / scale;
        press(screen, x, y);
        release(screen, x, y);
        boolean focus = "project-search".equals(screen.focusedId().orElse(""));
        screen.charTyped(new CharacterEvent('x'));
        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL));
        Minecraft.getInstance().keyboardHandler.setClipboard("soduim");
        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL));
        boolean editing = "soduim".equals(screen.stringValue("project-search", ""));
        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, 0));
        boolean tab = !"project-search".equals(screen.focusedId().orElse(""));
        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, GLFW.GLFW_MOD_SHIFT));
        log("text input, clipboard and tab focus: " + (focus && editing && tab ? "PASS" : "FAIL"));
    }

    private void checkSearchSuggestion(UiScreen screen) {
        boolean shown = screen.regionBounds("search-suggestion-sodium").isPresent();
        boolean chosen = shown && clickAndExpect(screen, "search-suggestion-sodium",
            () -> "Sodium".equals(screen.stringValue("project-search", "")));
        log("fuzzy search correction: " + (chosen ? "PASS" : "FAIL"));
    }

    /** The newer gallery pages are examples, not static art: each gets a live interaction check. */
    private void checkGalleryInteraction(UiScreen screen, String page) {
        boolean ok = switch (page) {
            case "harbor" -> clickAndExpect(screen, "harbor-nav-1",
                () -> "Projects".equals(screen.stringValue("harbor-nav", "")));
            case "pixelport" -> clickAndExpect(screen, "pixel-mode-1",
                () -> "SOLO".equals(screen.stringValue("pixel-mode", "")));
            case "fieldnotes" -> clickAndExpect(screen, "field-save", () -> screen.activeToastCount() > 0);
            case "orbit" -> {
                boolean selected = clickAndExpect(screen, "orbit-system-1",
                    () -> "COMMS".equals(screen.stringValue("orbit-system", "")));
                boolean before = screen.booleanValue("orbit-uplink", true);
                yield selected && clickAndExpect(screen, "orbit-uplink", () -> screen.booleanValue("orbit-uplink", true) != before);
            }
            default -> true;
        };
        if (page.equals("harbor") || page.equals("pixelport") || page.equals("fieldnotes") || page.equals("orbit")) {
            log("gallery " + page + " interaction: " + (ok ? "PASS" : "FAIL"));
        }
    }

    private boolean expectSlider(UiScreen screen, UiRect rail, float scale, double fraction) {
        double layoutX = rail.x() + rail.width() * fraction;
        drag(screen, layoutX / scale, (rail.y() + rail.height() / 2) / scale);
        double expected = fraction * 100;
        double actual = screen.numberValue("motion-intensity", -1);
        // One layout pixel of tolerance, since the drag goes through GUI-space coordinates.
        double tolerance = Math.max(1, 100.0 / Math.max(1, rail.width()));
        boolean ok = Math.abs(actual - expected) <= tolerance;
        if (!ok) log("  expected " + expected + " got " + actual);
        return ok;
    }

    /** Radio groups, filter chips and install buttons must actually change state. */
    private void checkSelection(UiScreen screen) {
        boolean ok = clickAndExpect(screen, "shape-pill", () -> "pill".equals(screen.stringValue("shape", "rounded")));
        ok &= clickAndExpect(screen, "section-input", () -> "input".equals(screen.stringValue("settings-section", "appearance")));
        ok &= clickAndExpect(screen, "preview-primary", () -> screen.booleanValue("reserved-action-received", false));
        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_F6, 0, 0));
        ok &= screen.booleanValue("raw-key-received", false);
        ok &= doubleClickAndExpect(screen, "preview-secondary",
            () -> screen.booleanValue("double-click-received", false));
        log("selection controls: " + (ok ? "PASS" : "FAIL"));
    }

    private boolean doubleClickAndExpect(UiScreen screen, String id, java.util.function.BooleanSupplier check) {
        Optional<UiRect> box = screen.regionBounds(id);
        if (box.isEmpty()) return false;
        UiRect r = box.get();
        float scale = screen.layoutScale();
        screen.mouseClicked(at((r.x() + r.width() / 2) / scale, (r.y() + r.height() / 2) / scale), true);
        return check.getAsBoolean();
    }

    private boolean clickAndExpect(UiScreen screen, String id, java.util.function.BooleanSupplier check) {
        Optional<UiRect> box = screen.regionBounds(id);
        if (box.isEmpty()) {
            log("  " + id + " was not painted");
            return false;
        }
        UiRect r = box.get();
        float s = screen.layoutScale();
        double x = (r.x() + r.width() / 2) / s;
        double y = (r.y() + r.height() / 2) / s;
        press(screen, x, y);
        release(screen, x, y);
        boolean ok = check.getAsBoolean();
        if (!ok) log("  " + id + " click did not change state");
        return ok;
    }

    private void checkToggle(UiScreen screen) {
        Optional<UiRect> bounds = screen.regionBounds("reduce-toggle");
        if (bounds.isEmpty()) {
            log("FAILED: reduce-toggle was not painted");
            return;
        }
        UiRect box = bounds.get();
        float scale = screen.layoutScale();
        double x = (box.x() + box.width() / 2) / scale;
        double y = (box.y() + box.height() / 2) / scale;
        boolean before = screen.booleanValue("reduced-motion", false);
        press(screen, x, y);
        release(screen, x, y);
        boolean after = screen.booleanValue("reduced-motion", false);
        log("toggle click: " + (after != before ? "PASS" : "FAIL"));
    }

    private static MouseButtonEvent at(double x, double y) {
        return new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
    }

    private static void press(UiScreen screen, double x, double y) {
        screen.mouseMoved(x, y);
        screen.mouseClicked(at(x, y), false);
    }

    private static void drag(UiScreen screen, double x, double y) {
        screen.mouseDragged(at(x, y), 0, 0);
    }

    private static void release(UiScreen screen, double x, double y) {
        screen.mouseReleased(at(x, y));
    }

    private void loadWorld(Minecraft client) {
        try {
            String levelId = levelId();
            if (client.getLevelSource().levelExists(levelId)) {
                client.createWorldOpenFlows().openWorld(levelId, () -> log("world open cancelled"));
                return;
            }
            LevelSettings settings = new LevelSettings(
                "Pane Autotest",
                GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                true,
                WorldDataConfiguration.DEFAULT
            );
            client.createWorldOpenFlows().createFreshLevel(
                levelId, settings, new WorldOptions(1234L, false, false),
                WorldPresets::createNormalWorldDimensions, null
            );
        } catch (RuntimeException e) {
            log("FAILED: could not load world: " + e);
            stage = Stage.DONE;
        }
    }

    private static String levelId() {
        String override = System.getProperty("pane.demo.levelId");
        if (override != null && !override.isBlank()) return override;
        return entitiesMode() ? ENTITY_LEVEL_ID : LEVEL_ID;
    }

    private static void capture(Minecraft client, String name) {
        // Screenshot.grab only creates one directory level under workDir, so anchor at the game
        // directory (run/screenshots) rather than a nested folder that does not exist yet.
        Screenshot.grab(client.gameDirectory, name + ".png",
            client.gameRenderer.mainRenderTarget(), 1, message -> { });
    }

    private static void log(String message) {
        System.out.println("[pane-autotest] " + message);
    }
}
