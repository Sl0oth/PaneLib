package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class UiGridTest {
    @Test
    void responsiveGridNeverUndershootsMinimumCellWidth() {
        UiGrid grid = UiGrid.responsive(new UiRect(0, 0, 760, 500), 220, 120, 16);
        assertEquals(3, grid.columns());
        assertEquals(242.66667F, grid.cellWidth(), .001F);
        UiRect cell = grid.cell(4);
        assertEquals(258.66666F, cell.x(), .001F);
        assertEquals(136F, cell.y(), .001F);
        assertEquals(242.66667F, cell.width(), .001F);
        assertEquals(120F, cell.height(), .001F);
        assertEquals(256, grid.contentHeight(6), .001F);
    }
}
