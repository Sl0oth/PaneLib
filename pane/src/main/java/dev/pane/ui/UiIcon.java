package dev.pane.ui;

/** Small code-drawn icon set. Consumers can still draw any custom icon with {@link UiPath}. */
public enum UiIcon {
    CHECK, CLOSE, PLUS, MINUS, SEARCH, CHEVRON_DOWN, ARROW_RIGHT, MENU,
    HOME, INFO, WARNING, PLAY, PAUSE, MORE, COPY, BOOKMARK, COLUMNS, TRASH;

    /** Draws this icon inside the supplied square. */
    public void draw(UiCanvas canvas, int color, float x, float y, float size, float stroke) {
        if (canvas == null || size <= 0) return;
        float scale = size / 24F;
        canvas.withTranslation(x, y, () -> canvas.withScale(scale, scale, 0, 0,
            () -> draw24(canvas, color, Math.max(.75F, stroke / scale))));
    }

    private void draw24(UiCanvas g, int color, float s) {
        switch (this) {
            case CHECK -> g.path().moveTo(4, 12).lineTo(9, 17).lineTo(20, 6).stroke(color, s);
            case CLOSE -> {
                g.line(color, s, 5, 5, 19, 19);
                g.line(color, s, 19, 5, 5, 19);
            }
            case PLUS -> {
                g.line(color, s, 12, 4, 12, 20);
                g.line(color, s, 4, 12, 20, 12);
            }
            case MINUS -> g.line(color, s, 4, 12, 20, 12);
            case SEARCH -> {
                g.strokeCircle(color, s, 10, 10, 6);
                g.line(color, s, 14.5F, 14.5F, 20, 20);
            }
            case CHEVRON_DOWN -> g.path().moveTo(5, 9).lineTo(12, 16).lineTo(19, 9).stroke(color, s);
            case ARROW_RIGHT -> {
                g.line(color, s, 4, 12, 20, 12);
                g.path().moveTo(14, 6).lineTo(20, 12).lineTo(14, 18).stroke(color, s);
            }
            case MENU -> {
                g.line(color, s, 4, 6, 20, 6);
                g.line(color, s, 4, 12, 20, 12);
                g.line(color, s, 4, 18, 20, 18);
            }
            case HOME -> g.path().moveTo(3, 11).lineTo(12, 3).lineTo(21, 11)
                .moveTo(6, 9).lineTo(6, 21).lineTo(18, 21).lineTo(18, 9).stroke(color, s);
            case INFO -> {
                g.strokeCircle(color, s, 12, 12, 9);
                g.line(color, s, 12, 10, 12, 17);
                g.circle(color, 12, 7, s / 2);
            }
            case WARNING -> {
                g.path().moveTo(12, 3).lineTo(22, 21).lineTo(2, 21).close().stroke(color, s);
                g.line(color, s, 12, 9, 12, 15);
                g.circle(color, 12, 18, s / 2);
            }
            case PLAY -> g.path().moveTo(7, 4).lineTo(20, 12).lineTo(7, 20).close().fill(color);
            case PAUSE -> {
                g.roundRect(color, 6, 4, 4, 16, 1);
                g.roundRect(color, 14, 4, 4, 16, 1);
            }
            case MORE -> {
                g.circle(color, 5, 12, 1.5F);
                g.circle(color, 12, 12, 1.5F);
                g.circle(color, 19, 12, 1.5F);
            }
            case COPY -> {
                g.strokeRoundRect(color, s, 8, 8, 12, 12, 2);
                g.path().moveTo(5, 16).lineTo(4, 16).lineTo(4, 4).lineTo(16, 4).lineTo(16, 5).stroke(color, s);
            }
            case BOOKMARK -> g.path().moveTo(6, 3).lineTo(18, 3).lineTo(18, 21)
                .lineTo(12, 17).lineTo(6, 21).close().stroke(color, s);
            case COLUMNS -> {
                g.strokeRoundRect(color, s, 3, 4, 18, 16, 2);
                g.line(color, s, 11, 4, 11, 20);
                g.line(color, s, 16, 4, 16, 20);
            }
            case TRASH -> {
                g.path().moveTo(5, 7).lineTo(19, 7).moveTo(9, 7).lineTo(9, 4)
                    .lineTo(15, 4).lineTo(15, 7).moveTo(7, 7).lineTo(8, 21)
                    .lineTo(16, 21).lineTo(17, 7).stroke(color, s);
                g.line(color, s, 11, 11, 11, 17);
                g.line(color, s, 14, 11, 14, 17);
            }
        }
    }
}
