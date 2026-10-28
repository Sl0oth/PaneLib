package dev.pane.ui;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

/**
 * A ready-made screen where players drag HUDs to new places. Every attached HUD that called
 * {@link UiHud#setEditable} shows with an outline; dragging one moves it live, snapping to a small
 * grid, and dropping it picks the nearest corner, edge or centre as its anchor so it stays put when
 * the window changes size. Arrow keys nudge the last one picked. Reset puts every HUD back at its
 * defaults; Done or Escape keeps the changes and hands each moved HUD's placement to its
 * {@code onMoved} callback, then returns to {@code parent}.
 *
 * <pre>{@code
 * hud.setEditable("Health", defaultPlacement, placement -> config.save("health", placement));
 * Minecraft.getInstance().gui.setScreen(new UiHudEditor(parentScreen));
 * }</pre>
 */
public final class UiHudEditor extends UiScreen {
    private static final float BAR = 30;
    private static final int GRID = 4;

    private static final class Entry {
        final UiHud hud;
        final boolean showOnScreens;
        final float motionScale;
        boolean moved;
        Entry(UiHud hud) { this.hud = hud; showOnScreens = hud.showsOnScreens(); motionScale = hud.motionScale(); }
    }

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private Entry dragging, selected;
    private float grabX, grabY;
    private String boundsSignature = "";
    private boolean finished;

    /** An editor in PaneLib's dark theme that returns to {@code parent}, which may be null. */
    public UiHudEditor(Screen parent) { this(parent, UiTheme.dark()); }

    /** An editor in {@code theme}, so its toolbar matches the mod that opened it. */
    public UiHudEditor(Screen parent, UiTheme theme) {
        this(parent, theme, new UiHudEditor[1]);
    }

    private UiHudEditor(Screen parent, UiTheme theme, UiHudEditor[] self) {
        super(UiDocument.builder("Move HUD").shell(UiShell.NONE).theme(theme == null ? UiTheme.dark() : theme)
                .page(new UiPage("hud-editor", "Move HUD", "", (theme == null ? UiTheme.dark() : theme).accent(), ui -> self[0].paint(ui)))
                .build(),
            NativeOverlayRenderer.NONE, (UiEventHandler) event -> self[0].onEvent(event),
            UiScreenOptions.builder().backdrop(UiBackdrop.NONE).build());
        self[0] = this;
        this.parent = parent;
        for (UiHud hud : UiHud.attached()) {
            if (hud.editableName() == null || !hud.enabled()) continue;
            Entry entry = new Entry(hud);
            // Shown under this screen and moved without springing, so the drag follows the pointer.
            hud.setShowOnScreens(true);
            hud.setMotionScale(0);
            entries.add(entry);
        }
    }

    /** The names of the HUDs this editor can move, in the order they were attached. */
    public List<String> names() { return entries.stream().map(e -> e.hud.editableName()).toList(); }

    @Override public boolean isPauseScreen() { return false; }

    // ── Painting ────────────────────────────────────────────────────────────

    private void paint(UiContext ui) {
        var c = ui.canvas();
        float w = ui.width(), h = ui.height(), s = layoutScale();
        var theme = ui.theme();
        c.fill(0x30000000, 0, BAR, w, h - BAR);
        if (dragging != null) {
            int guide = UiCanvas.fade(ui.accent(), .35F);
            c.fill(guide, w / 2, BAR, 1, h - BAR);
            c.fill(guide, 0, BAR + (h - BAR) / 2, w, 1);
        }
        for (Entry entry : entries) {
            UiRect r = bounds(entry.hud);
            if (r == null) continue;
            UiRect box = new UiRect(r.x() * s, r.y() * s, r.width() * s, r.height() * s);
            boolean on = entry == selected;
            c.strokeRoundRect(UiCanvas.fade(ui.accent(), on ? 1 : .55F), on ? 1.5F : 1, box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2, 1);
            if (on) {
                String name = entry.hud.editableName();
                float tw = c.textWidth(name, 8, Font.BOLD, theme.fontFamily()) + 10;
                float ty = box.y() - 15 >= BAR ? box.y() - 15 : box.bottom() + 3;
                c.roundRect(ui.accent(), box.x() - 1, ty, tw, 12, 2);
                c.text(name, box.x() + 4, ty + 2, 8, theme.accentText(), Font.BOLD, theme.fontFamily());
            }
        }

        c.fill(UiCanvas.fade(theme.surface(), .94F), 0, 0, w, BAR);
        c.fill(theme.border(), 0, BAR - 1, w, 1);
        float pad = 10, doneW = 56, resetW = 52;
        ui.button("hud-editor-done", "Done", "hud-editor-done", w - pad - doneW, 5, doneW, BAR - 10,
            UiContext.ButtonStyle.PRIMARY, UiContext.Shape.ROUNDED);
        boolean anyMovedFromDefault = entries.stream().anyMatch(e -> !e.hud.placement().equals(e.hud.editableDefaults()));
        if (anyMovedFromDefault) ui.button("hud-editor-reset", "Reset", "hud-editor-reset", w - pad - doneW - 6 - resetW, 5, resetW, BAR - 10,
            UiContext.ButtonStyle.SECONDARY, UiContext.Shape.ROUNDED);
        c.text("Move HUD", pad, 8, 12, theme.text(), Font.BOLD, theme.fontFamily());
        float hx = pad + c.textWidth("Move HUD", 12, Font.BOLD, theme.fontFamily()) + 12;
        float room = w - pad - doneW - resetW - 18 - hx;
        String hint = entries.isEmpty() ? "No HUD on screen can be moved right now."
            : "Drag a HUD to move it. Arrow keys nudge the one you picked.";
        if (room > 60) c.text(c.ellipsize(hint, room, 8, Font.PLAIN, theme.fontFamily()), hx, 11, 8, theme.mutedText(), Font.PLAIN, theme.fontFamily());
    }

    /** Where {@code hud} is drawn, in GUI pixels, or null while it isn't on screen. */
    private UiRect bounds(UiHud hud) {
        if (!hud.presented()) return null;
        Minecraft client = Minecraft.getInstance();
        int gw = client.getWindow().getGuiScaledWidth(), gh = client.getWindow().getGuiScaledHeight();
        return hud.placement().bounds(gw, gh, hud.width(), hud.height(), UiHud.unitsPerGui(client, hud.scalesWithGui()));
    }

    @Override
    public void tick() {
        super.tick();
        // A HUD can resize or appear while the editor is open; keep the outlines on it.
        StringBuilder sig = new StringBuilder();
        for (Entry entry : entries) sig.append(bounds(entry.hud)).append(';');
        if (!sig.toString().equals(boundsSignature)) { boundsSignature = sig.toString(); invalidate(); }
    }

    // ── Input ───────────────────────────────────────────────────────────────

    private void onEvent(UiEvent event) {
        if (event.type() != UiEventType.ACTION) return;
        switch (event.key()) {
            case "hud-editor-done" -> onClose();
            case "hud-editor-reset" -> {
                for (Entry entry : entries) { entry.hud.setPlacement(entry.hud.editableDefaults()); entry.moved = true; }
                invalidate();
            }
            default -> {}
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || event.y() * layoutScale() < BAR) return false;
        // Topmost first: later HUDs draw above earlier ones.
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            UiRect r = bounds(entry.hud);
            if (r == null || !r.inset(-2).contains((float) event.x(), (float) event.y())) continue;
            dragging = selected = entry;
            grabX = (float) event.x() - r.x(); grabY = (float) event.y() - r.y();
            invalidate();
            return true;
        }
        if (selected != null) { selected = null; invalidate(); }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging == null || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseDragged(event, dragX, dragY);
        moveTo(dragging, (float) event.x() - grabX, (float) event.y() - grabY);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) { dragging = null; invalidate(); return true; }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (selected != null) {
            int dx = switch (event.key()) { case GLFW.GLFW_KEY_LEFT -> -1; case GLFW.GLFW_KEY_RIGHT -> 1; default -> 0; };
            int dy = switch (event.key()) { case GLFW.GLFW_KEY_UP -> -1; case GLFW.GLFW_KEY_DOWN -> 1; default -> 0; };
            UiRect r = bounds(selected.hud);
            if ((dx != 0 || dy != 0) && r != null) {
                float step = grid(selected.hud);
                moveTo(selected, r.x() + dx * step, r.y() + dy * step);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    /** One grid step in GUI pixels: four of the HUD's own units. */
    private static float grid(UiHud hud) {
        return GRID / UiHud.unitsPerGui(Minecraft.getInstance(), hud.scalesWithGui());
    }

    private void moveTo(Entry entry, float x, float y) {
        UiRect r = bounds(entry.hud);
        if (r == null) return;
        Minecraft client = Minecraft.getInstance();
        float gw = client.getWindow().getGuiScaledWidth(), gh = client.getWindow().getGuiScaledHeight();
        float step = grid(entry.hud);
        x = Math.round(x / step) * step; y = Math.round(y / step) * step;
        x = Math.max(0, Math.min(gw - r.width(), x)); y = Math.max(0, Math.min(gh - r.height(), y));
        entry.hud.setPlacement(placementFor(new UiRect(x, y, r.width(), r.height()), gw, gh,
            UiHud.unitsPerGui(client, entry.hud.scalesWithGui()), entry.hud.placement().scale()));
        entry.moved = true;
        invalidate();
    }

    /**
     * The placement that draws a HUD at {@code rect} (GUI pixels) on a {@code width} by
     * {@code height} GUI viewport: anchored to whichever third of the screen its centre is in, with
     * margins from the near edges and offsets from the centre lines.
     */
    static UiHudPlacement placementFor(UiRect rect, float width, float height, float unitsPerGui, float scale) {
        float cx = rect.x() + rect.width() / 2, cy = rect.y() + rect.height() / 2;
        int col = cx < width / 3 ? 0 : cx > width * 2 / 3 ? 2 : 1;
        int row = cy < height / 3 ? 0 : cy > height * 2 / 3 ? 2 : 1;
        UiHudPlacement.Anchor anchor = switch (row * 3 + col) {
            case 0 -> UiHudPlacement.Anchor.TOP_LEFT;
            case 1 -> UiHudPlacement.Anchor.TOP_CENTER;
            case 2 -> UiHudPlacement.Anchor.TOP_RIGHT;
            case 3 -> UiHudPlacement.Anchor.CENTER_LEFT;
            case 4 -> UiHudPlacement.Anchor.CENTER;
            case 5 -> UiHudPlacement.Anchor.CENTER_RIGHT;
            case 6 -> UiHudPlacement.Anchor.BOTTOM_LEFT;
            case 7 -> UiHudPlacement.Anchor.BOTTOM_CENTER;
            default -> UiHudPlacement.Anchor.BOTTOM_RIGHT;
        };
        float marginX = col == 0 ? rect.x() : col == 2 ? width - rect.right() : 0;
        float marginY = row == 0 ? rect.y() : row == 2 ? height - rect.bottom() : 0;
        float offsetX = col == 1 ? cx - width / 2 : 0, offsetY = row == 1 ? cy - height / 2 : 0;
        float u = unitsPerGui;
        return new UiHudPlacement(anchor, marginX * u, marginY * u, scale, offsetX * u, offsetY * u);
    }

    // ── Closing ─────────────────────────────────────────────────────────────

    @Override
    public void onClose() {
        finish();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void removed() {
        finish();
        super.removed();
    }

    /** Hands moved placements to their owners once, and puts each HUD's screen and motion settings back. */
    private void finish() {
        if (finished) return;
        finished = true;
        for (Entry entry : entries) {
            entry.hud.setShowOnScreens(entry.showOnScreens);
            entry.hud.setMotionScale(entry.motionScale);
            if (entry.moved && entry.hud.editableName() != null) entry.hud.moved(entry.hud.placement());
        }
    }
}
