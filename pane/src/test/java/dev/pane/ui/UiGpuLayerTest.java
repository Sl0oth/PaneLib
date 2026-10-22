package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class UiGpuLayerTest {
    private static final UiTransform MOVED = new UiTransform(30, 10, 1.25F, 1.25F, 12, .5F);

    @Test
    void screenPaintsHandGroupsToTheCompositorWithScreenBounds() {
        var image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.scale(2, 2);
            var canvas = new UiCanvas(graphics, image);
            var state = new UiInteractionState();
            var collector = new UiGpuLayers.Collector(canvas);
            state.gpuLayers = collector;
            var ui = context(canvas, state);
            ui.gpuLayer("plain", new UiRect(10, 10, 40, 20), 1, MOVED, 200, UiEasing.EASE_OUT, this::card);
            ui.withTranslation(15, 25, moved -> moved.gpuLayer("translated", new UiRect(0, 0, 30, 30), 1,
                UiTransform.identity(), 200, UiEasing.EASE_OUT, this::card));
            ui.scroll("list", new UiRect(100, 20, 80, 60), 200, list -> list.gpuLayer("scrolled", new UiRect(100, 30, 50, 20),
                1, UiTransform.identity(), 200, UiEasing.EASE_OUT, this::card));
            canvas.withTranslation(5, 5, () -> ui.gpuLayer("manual", new UiRect(0, 0, 10, 10), 1,
                UiTransform.identity(), 200, UiEasing.EASE_OUT, this::card));
            ui.withLayer(new UiLayer(), .5F, new UiRect(0, 0, 200, 150), group -> group.gpuLayer("faded",
                new UiRect(0, 0, 10, 10), 1, UiTransform.identity(), 200, UiEasing.EASE_OUT, this::card));

            var ids = collector.requests.stream().map(UiGpuLayers.Request::id).toList();
            assertEquals(java.util.List.of("plain", "translated", "scrolled"), ids,
                "manual canvas transforms and group layers draw in place instead");
            assertEquals(new UiRect(15, 25, 30, 30), collector.requests.get(1).screen());
            assertEquals(new UiRect(0, 0, 30, 30), collector.requests.get(1).local());
            assertEquals(new UiRect(100, 20, 80, 60), collector.requests.get(2).clip(), "scroll viewport clips the layer");
        } finally { graphics.dispose(); }
    }

    @Test
    void withoutACompositorTheGroupIsDrawnInPlaceAtItsTarget() {
        int[] inline = paint(canvas -> context(canvas, new UiInteractionState())
            .gpuLayer("card", new UiRect(20, 20, 60, 40), 1, MOVED, 200, UiEasing.EASE_OUT, this::card));
        int[] reference = paint(canvas -> canvas.withTransform(MOVED, 50, 40, () -> card(canvas)));
        assertArrayEquals(reference, inline);
        boolean painted = false;
        for (int pixel : inline) painted |= pixel != 0;
        assertTrue(painted);
    }

    private void card(UiCanvas canvas) {
        canvas.roundRect(0xFF5B9BE3, 20, 20, 60, 40, 8);
        canvas.text("GPU", 30, 28, 10, 0xFFFFFFFF);
    }

    private static int[] paint(Consumer<UiCanvas> painter) {
        var image = new BufferedImage(200, 150, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { painter.accept(new UiCanvas(graphics, image)); } finally { graphics.dispose(); }
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    private static UiContext context(UiCanvas canvas, UiInteractionState state) {
        return new UiContext(canvas, UiTheme.dark(), 200, 150, 0xFF5B9BE3, new UiRect(0, 0, 200, 150), null, null,
            1, 1, false, 0, new HashMap<>(), new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
            new HashMap<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new UiTextInputState(null, 0, 0),
            new ArrayList<>(), new ArrayList<>(), null, 1, null).withInteractions(state);
    }
}
