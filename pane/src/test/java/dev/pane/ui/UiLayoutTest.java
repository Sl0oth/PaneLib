package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

final class UiLayoutTest {
    @Test
    void columnTracksNaturalContentHeight() {
        UiLayout layout = UiLayout.column(new UiRect(10, 20, 200, 500)).gap(8);
        assertEquals(new UiRect(10, 20, 200, 40), layout.next(40));
        assertEquals(new UiRect(10, 68, 200, 60), layout.next(60));
        layout.spacer(12);
        assertEquals(120, layout.contentHeight());
        assertEquals(new UiRect(10, 140, 200, 380), layout.remaining());
    }

    @Test
    void weightedDistributionUsesRemainingRoomAndGaps() {
        UiLayout row = UiLayout.row(new UiRect(0, 0, 410, 80)).gap(10);
        List<UiRect> cells = row.distribute(1, 2, 1);
        assertEquals(3, cells.size());
        assertEquals(97.5F, cells.get(0).width(), .001F);
        assertEquals(195F, cells.get(1).width(), .001F);
        assertEquals(410F, row.used(), .001F);
    }
}
