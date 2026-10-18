package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class NativeOverlayContextTest {
    @Test
    void entityPreviewIsSafeWhenThePageDoesNotReserveItsSlot() {
        NativeOverlayContext overlays = new NativeOverlayContext(null, "previews", Map.of(), 1, 0, 0);

        assertEquals("previews", overlays.pageId());
        assertTrue(overlays.bounds("iron-golem").isEmpty());
        assertDoesNotThrow(() -> overlays.entity("iron-golem", null));
        assertDoesNotThrow(() -> overlays.player("villager", null));
    }
}
