package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** An interactive HUD's controls, driven the way the HUD drives them: paint, then pointer steps. */
class UiHudInputTest {
    private final Map<String, Object> values = new HashMap<>();
    private final UiHudInput input = new UiHudInput();

    /** Paints a 200 by 120 HUD the way {@link UiHud} does and hands its hit regions to the input. */
    private void paint() {
        var image = new BufferedImage(200, 120, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        var hits = new ArrayList<UiHitRegion>();
        try {
            var ui = new UiContext(new UiCanvas(g, image), UiTheme.dark(), 200, 120, 0xFFFFAA00, new UiRect(0, 0, 200, 120),
                input.hovered(), input.pressed(), 1, 1, false, 0, values, hits, new LinkedHashMap<>(), new LinkedHashMap<>(),
                new HashMap<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new UiTextInputState(null, 0, 0),
                new ArrayList<>(), new ArrayList<>(), null, 1, null);
            ui.button("start", "Start", "start", 10, 10, 60, 20);
            ui.toggle("sound", 100, 10, false);
            ui.slider("volume", 10, 50, 100, 0, 10, 0);
            ui.stepper("count", 10, 80, 100, 0, 5, 1, 2);
        } finally { g.dispose(); }
        input.painted(hits);
    }

    @Test void aButtonReportsItsActionOnReleaseOverIt() {
        paint();
        assertFalse(input.press(190, 110, values).consumed(), "empty HUD area lets the click through");
        assertFalse(input.release(190, 110, values).consumed());
        var down = input.press(20, 20, values);
        assertTrue(down.consumed());
        assertNull(down.action(), "nothing runs until release");
        assertEquals("start", input.pressed());
        assertEquals("start", input.release(20, 20, values).action());
        assertNull(input.pressed());

        input.press(20, 20, values);
        var away = input.release(190, 110, values);
        assertTrue(away.consumed(), "the release that ends a press is the HUD's");
        assertNull(away.action(), "releasing elsewhere cancels");
    }

    @Test void hoverRepaintsOnlyWhenTheControlChanges() {
        paint();
        assertTrue(input.move(20, 20, values).repaint());
        assertEquals("start", input.hovered());
        assertFalse(input.move(22, 21, values).repaint(), "same control");
        assertTrue(input.move(190, 110, values).repaint());
        assertNull(input.hovered());
        assertFalse(input.clear());
    }

    @Test void aToggleFlipsItsValue() {
        paint();
        input.press(110, 15, values);
        assertEquals("sound", input.release(110, 15, values).changedKey());
        assertEquals(true, values.get("sound"));
        paint();
        input.press(110, 15, values);
        input.release(110, 15, values);
        assertEquals(false, values.get("sound"));
    }

    @Test void aSliderFollowsADragAndRunsNoAction() {
        paint();
        var down = input.press(60, 55, values);
        assertTrue(down.consumed());
        assertEquals("volume", down.changedKey());
        double pressed = ((Number) values.get("volume")).doubleValue();
        assertTrue(pressed > 3 && pressed < 7, "near the middle: " + pressed);
        paint();
        assertEquals("volume", input.move(500, 55, values).changedKey(), "the drag continues outside the HUD");
        assertEquals(10D, values.get("volume"));
        assertNull(input.move(600, 55, values).changedKey(), "no change, no event");
        input.move(-50, 0, values);
        assertEquals(0D, values.get("volume"));
        var up = input.release(-50, 0, values);
        assertTrue(up.consumed());
        assertNull(up.action());
        assertNull(input.pressed());
    }

    @Test void aStepperStepsWithinItsRange() {
        paint();
        assertEquals(2D, ((Number) values.get("count")).doubleValue());
        assertEquals("count", UiHudInput.apply("step:1.0:0.0:5.0:count", values));
        assertEquals(3D, values.get("count"));
        values.put("count", 5D);
        assertNull(UiHudInput.apply("step:1.0:0.0:5.0:count", values), "already at the top");
        assertNull(UiHudInput.apply("menu:anything", values), "popups belong to screens");
    }

    @Test void aDragEndsWhenItsSliderIsNoLongerPainted() {
        paint();
        input.press(60, 55, values);
        input.painted(List.of());
        assertNull(input.pressed());
        assertFalse(input.move(80, 55, values).consumed());
    }

    @Test void shiftTurnsOnlyAPlainWheelStepSideways() {
        assertTrue(UiScreen.shiftTurnsWheel(0, 1, true));
        assertFalse(UiScreen.shiftTurnsWheel(0, 1, false));
        assertFalse(UiScreen.shiftTurnsWheel(1, 0, true), "already sideways, as macOS reports it");
        assertFalse(UiScreen.shiftTurnsWheel(0, 0, true));
    }

    @Test void mouseButtonsHaveTheirOwnCodesAndNames() {
        int right = UiKeyNames.mouse(1);
        assertTrue(UiKeyNames.isMouse(right));
        assertFalse(UiKeyNames.isMouse(UiKeyNames.UNBOUND));
        assertFalse(UiKeyNames.isMouse(org.lwjgl.glfw.GLFW.GLFW_KEY_H));
        assertEquals(1, UiKeyNames.mouseButton(right));
        assertEquals("Right Button", UiKeyNames.english(right));
        assertEquals("Mouse 5", UiKeyNames.english(UiKeyNames.mouse(4)));
        assertEquals("H", UiKeyNames.english(org.lwjgl.glfw.GLFW.GLFW_KEY_H));
        assertFalse(UiKeyNames.isDown(UiKeyNames.UNBOUND));
    }
}
