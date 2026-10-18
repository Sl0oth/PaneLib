package dev.pane.ui;

/** Responsive equal-column grid for card galleries, settings, and inventory-like layouts. */
public final class UiGrid {
    private final UiRect bounds;
    private final int columns;
    private final float rowHeight;
    private final float columnGap;
    private final float rowGap;

    private UiGrid(UiRect bounds, int columns, float rowHeight, float columnGap, float rowGap) {
        this.bounds = bounds == null ? new UiRect(0, 0, 0, 0) : bounds;
        this.columns = Math.max(1, columns);
        this.rowHeight = Math.max(0, rowHeight);
        this.columnGap = Math.max(0, columnGap);
        this.rowGap = Math.max(0, rowGap);
    }

    /** Fixed column count. */
    public static UiGrid columns(UiRect bounds, int columns, float rowHeight, float gap) {
        return new UiGrid(bounds, columns, rowHeight, gap, gap);
    }

    /** Chooses as many columns as fit without making a cell narrower than {@code minimumWidth}. */
    public static UiGrid responsive(UiRect bounds, float minimumWidth, float rowHeight, float gap) {
        float available = bounds == null ? 0 : bounds.width();
        float minimum = Math.max(1, minimumWidth);
        int columns = Math.max(1, (int) Math.floor((available + Math.max(0, gap)) / (minimum + Math.max(0, gap))));
        return new UiGrid(bounds, columns, rowHeight, gap, gap);
    }

    public UiGrid gaps(float columns, float rows) {
        return new UiGrid(bounds, this.columns, rowHeight, columns, rows);
    }

    public int columns() { return columns; }

    public float cellWidth() {
        return Math.max(0, (bounds.width() - (columns - 1) * columnGap) / columns);
    }

    public UiRect cell(int index) {
        int safe = Math.max(0, index);
        int column = safe % columns;
        int row = safe / columns;
        float width = cellWidth();
        return new UiRect(bounds.x() + column * (width + columnGap),
            bounds.y() + row * (rowHeight + rowGap), width, rowHeight);
    }

    public float contentHeight(int itemCount) {
        int rows = Math.max(0, (Math.max(0, itemCount) + columns - 1) / columns);
        return rows == 0 ? 0 : rows * rowHeight + (rows - 1) * rowGap;
    }
}
