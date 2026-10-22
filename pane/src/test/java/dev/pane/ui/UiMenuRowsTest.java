package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Shortcut hints, disabled rows, group dividers and context menus. */
class UiMenuRowsTest {
    @Test
    void itemsCarryShortcutsDisabledStateAndGroups() {
        var item = new UiMenuItem("close", "Close Window", "close");
        assertEquals("", item.shortcut());
        assertTrue(item.enabled());
        assertFalse(item.separated());
        var styled = item.shortcut("⌘W").enabled(false).startsGroup();
        assertEquals("⌘W", styled.shortcut());
        assertFalse(styled.enabled());
        assertTrue(styled.separated());
        assertEquals("", new UiMenuItem("a", "A", "a", null, true, false).shortcut());
        assertThrows(IllegalArgumentException.class, () -> new UiMenuItem("a", " ", "a"));
    }

    @Test
    void disabledRowsTakeNoPointerAndShortcutsPaintAtTheRight() {
        var image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        var hits = new ArrayList<UiHitRegion>();
        var style = UiMenuStyle.builder().rowHeight(24).padding(12, 4).background(0xFF202020).text(0xFFFFFFFF).build();
        var menu = new UiMenu("m", new UiRect(10, 0, 0, 0), List.of(
            new UiMenuItem("open", "Open", "open").shortcut("⌘O"),
            new UiMenuItem("rename", "Rename", "rename").enabled(false),
            new UiMenuItem("trash", "Trash", "trash").startsGroup()), null, style);
        var layout = UiPopupLayout.menu(menu.anchor(), 240, 3, 300, 200, style);
        try {
            UiPopupPainter.menu(new UiCanvas(graphics, image), hits, menu, layout, 0, null, null, UiTheme.dark());
        } finally { graphics.dispose(); }
        var ids = hits.stream().map(UiHitRegion::id).toList();
        assertTrue(ids.contains("menu:m:open"));
        assertTrue(ids.contains("menu:m:trash"));
        assertFalse(ids.contains("menu:m:rename"), "a disabled row is not a target");
        UiRect first = layout.row(0, 0);
        int lit = 0;
        for (int x = (int) (layout.frame().right() - 40); x < layout.frame().right() - 8; x++)
            for (int y = (int) first.y(); y < first.bottom(); y++) if ((image.getRGB(x, y) & 0xFF) > 0x60) lit++;
        assertTrue(lit > 10, "shortcut text is drawn at the right edge");
    }

    @Test
    void contextMenusAttachToTheirControlAndOpenAtThePointer() {
        var image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        var menus = new ArrayList<UiMenu>();
        try {
            var ui = new UiContext(new UiCanvas(graphics, image), UiTheme.dark(), 400, 300, 0xFF5B9BE3, new UiRect(0, 0, 400, 300),
                null, null, 1, 1, false, 0, new HashMap<>(), new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new UiTextInputState(null, 0, 0), menus, new ArrayList<>(),
                null, 1, null).withInteractions(new UiInteractionState());
            ui.hit("desk", "none", 0, 0, 400, 300);
            ui.contextMenu("desk", List.of(new UiMenuItem("new", "New Folder", "new")), UiMenuStyle.defaults());
            ui.contextMenu("missing", List.of(new UiMenuItem("x", "X", "x")), UiMenuStyle.defaults());
        } finally { graphics.dispose(); }
        assertEquals(1, menus.size(), "only a painted control gets a context menu");
        assertEquals(UiMenu.CONTEXT_PREFIX + "desk", menus.getFirst().id());
        assertEquals(new UiRect(0, 0, 400, 300), menus.getFirst().anchor());
    }
}
