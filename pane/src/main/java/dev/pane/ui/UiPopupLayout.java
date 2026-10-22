package dev.pane.ui;

/** Bounded popup geometry, independent of pointer/keyboard state. */
record UiPopupLayout(UiRect frame, UiRect viewport, float contentHeight, float rowHeight) {
    static UiPopupLayout menu(UiRect anchor, float desiredWidth, int itemCount, float screenWidth, float screenHeight) {
        return menu(anchor, desiredWidth, itemCount, screenWidth, screenHeight, UiMenuStyle.defaults());
    }
    static UiPopupLayout menu(UiRect anchor, float desiredWidth, int itemCount, float screenWidth, float screenHeight, UiMenuStyle style) {
        float rowHeight = style.rowHeight();
        float marginX = Math.min(6, Math.max(0, screenWidth / 4));
        float marginY = Math.min(6, Math.max(0, screenHeight / 4));
        float width = Math.max(.01F, Math.min(desiredWidth, screenWidth - marginX * 2));
        float available = Math.max(.01F, screenHeight - marginY * 2);
        float content = itemCount * rowHeight;
        float desiredHeight = content + style.paddingY() * 2;
        float below = Math.clamp(anchor.bottom() + 5, marginY, screenHeight - marginY);
        float above = Math.clamp(anchor.y() - 5, marginY, screenHeight - marginY);
        float belowSpace = screenHeight - marginY - below, aboveSpace = above - marginY;
        float height, y;
        if (desiredHeight <= belowSpace) { height = desiredHeight; y = below; }
        else if (desiredHeight <= aboveSpace) { height = desiredHeight; y = above - height; }
        else if (Math.max(belowSpace, aboveSpace) >= Math.min(desiredHeight, rowHeight * 3 + style.paddingY() * 2)) {
            boolean useBelow = belowSpace >= aboveSpace;
            height = useBelow ? belowSpace : aboveSpace;
            y = useBelow ? below : above - height;
        } else { height = Math.min(available, desiredHeight); y = marginY; }
        float pad = Math.min(style.paddingY(), height / 4);
        float x = Math.clamp(anchor.x(), marginX, Math.max(marginX, screenWidth - width - marginX));
        UiRect frame = new UiRect(x, y, width, height);
        return new UiPopupLayout(frame, new UiRect(x, y + pad, width, height - pad * 2), content, rowHeight);
    }
    float maxOffset() { return Math.max(0, contentHeight - viewport.height()); }
    float clamp(float offset) { return Math.clamp(offset, 0, maxOffset()); }
    float reveal(float offset, int row) {
        float top = row * rowHeight, bottom = top + rowHeight;
        if (top < offset) offset = top;
        else if (bottom > offset + viewport.height()) offset = bottom - viewport.height();
        return clamp(offset);
    }
    UiRect row(int index, float offset) {
        float top = viewport.y() + index * rowHeight - offset;
        float clippedTop = Math.max(top, viewport.y());
        float bottom = Math.min(top + rowHeight, viewport.bottom());
        return new UiRect(viewport.x(), clippedTop, viewport.width(), Math.max(0, bottom - clippedTop));
    }
}
