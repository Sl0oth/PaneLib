package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Danger buttons follow the theme, and a colour picker announces the colour its sliders made. */
class UiControlPolishTest {
    private final Map<String, Object> state = new HashMap<>();
    private final UiInteractionState interactions = new UiInteractionState();
    private final BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);

    private void paint(UiTheme theme, java.util.function.Consumer<UiContext> painter) {
        var g = image.createGraphics();
        try {
            interactions.beginPaint();
            painter.accept(new UiContext(new UiCanvas(g, image), theme, 300, 200, theme.accent(), new UiRect(0, 0, 300, 200), null, null,
                1, 1, false, 0, state, new ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new UiTextInputState(null, 0, 0), new ArrayList<>(), new ArrayList<>(), null, 1, null)
                .withInteractions(interactions));
        } finally { g.dispose(); }
    }

    @Test void aDangerButtonTakesItsColourFromTheTheme() {
        java.util.function.Consumer<UiContext> button = ui ->
            ui.button("delete", "", "delete", 10, 10, 100, 40, UiContext.ButtonStyle.DANGER, UiContext.Shape.SQUARE);
        paint(UiTheme.dark(), button);
        assertEquals(0xFF3A1D20, image.getRGB(60, 20), "a theme that never set danger keeps the stock red");
        paint(UiTheme.dark().withDanger(0xFF00C853), button);
        int fill = image.getRGB(60, 20);
        int red = fill >> 16 & 0xFF, green = fill >> 8 & 0xFF;
        assertTrue(green > red + 15, "a green danger colour tints the button green: " + Integer.toHexString(fill));
    }

    @Test void aColourPickerAnnouncesTheColourItsSlidersMade() {
        java.util.function.Consumer<UiContext> picker = ui -> ui.colorPicker("tint", 10, 10, 200, 0xFF58A6FF, false);
        paint(UiTheme.dark(), picker);
        assertTrue(interactions.live.valueChanges.isEmpty(), "the first paint only places the sliders");
        state.put("tint:hue", 0D);
        paint(UiTheme.dark(), picker);
        assertEquals(List.of("tint"), interactions.live.valueChanges);
        assertNotEquals("#58A6FF", state.get("tint"));
        paint(UiTheme.dark(), picker);
        assertTrue(interactions.live.valueChanges.isEmpty(), "and only once");
        state.put("tint", "#00FF00");
        paint(UiTheme.dark(), picker);
        assertTrue(interactions.live.valueChanges.isEmpty(), "typed or set colours are already announced by whoever set them");
    }
}
