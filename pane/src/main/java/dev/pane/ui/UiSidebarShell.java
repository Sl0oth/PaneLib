package dev.pane.ui;

import java.awt.Font;

/**
 * The reference chrome: a rounded window with a left navigation rail.
 * <p>
 * Every label is supplied by the consuming mod, so nothing about this library's own demo
 * leaks into another mod's screens. Use {@link UiShell#NONE} or a custom {@link UiShell}
 * for a completely different structure.
 */
public final class UiSidebarShell implements UiShell {
    private final String caption;
    private final String sectionLabel;
    private final String footnote;
    private final boolean statusIndicator;

    private UiSidebarShell(String caption, String sectionLabel, String footnote, boolean statusIndicator) {
        this.caption = caption == null ? "" : caption;
        this.sectionLabel = sectionLabel == null ? "" : sectionLabel;
        this.footnote = footnote == null ? "" : footnote;
        this.statusIndicator = statusIndicator;
    }

    /** A plain navigation rail showing only the document brand and its pages. */
    public static UiSidebarShell create() {
        return new UiSidebarShell("", "", "", false);
    }

    /** Sub-title rendered under the brand name. */
    public UiSidebarShell caption(String caption) {
        return new UiSidebarShell(caption, sectionLabel, footnote, statusIndicator);
    }

    /** Small heading above the navigation list. Blank removes it and closes the gap. */
    public UiSidebarShell sectionLabel(String sectionLabel) {
        return new UiSidebarShell(caption, sectionLabel, footnote, statusIndicator);
    }

    /** Hint pinned to the bottom of the rail, for example a key binding. */
    public UiSidebarShell footnote(String footnote) {
        return new UiSidebarShell(caption, sectionLabel, footnote, statusIndicator);
    }

    /** Shows a repaint-state dot in the rail footer. Useful while developing a screen. */
    public UiSidebarShell statusIndicator(boolean statusIndicator) {
        return new UiSidebarShell(caption, sectionLabel, footnote, statusIndicator);
    }

    @Override
    public UiRect paint(UiContext context, UiDocument document, UiPage page) {
        UiCanvas canvas = context.canvas();
        UiTheme theme = context.theme();
        int layoutWidth = context.width();
        int layoutHeight = context.height();
        float margin = layoutWidth < 700 ? 7 : 12;
        float sidebar = Math.min(layoutWidth < 760 ? 154 : 184, Math.max(0, layoutWidth - margin * 2));

        canvas.fill(theme.background(), 0, 0, layoutWidth, layoutHeight);
        float frameWidth = Math.max(0, layoutWidth - margin * 2);
        float frameHeight = Math.max(0, layoutHeight - margin * 2);
        canvas.roundRect(theme.surface(), margin, margin, frameWidth, frameHeight, theme.largeRadius());
        canvas.strokeRoundRect(theme.border(), .8F, margin, margin, frameWidth, frameHeight, theme.largeRadius());
        canvas.fill(theme.surface(), margin, margin + theme.largeRadius(), sidebar,
            Math.max(0, frameHeight - theme.largeRadius() * 2));
        canvas.line(theme.border(), .8F, margin + sidebar, margin, margin + sidebar, layoutHeight - margin);

        float brandX = margin + 17;
        float brandY = margin + 17;
        canvas.roundRect(theme.raisedSurface(), brandX, brandY, 30, 30, theme.smallRadius());
        canvas.strokeRoundRect(theme.border(), .8F, brandX, brandY, 30, 30, theme.smallRadius());
        canvas.polygon(page.accent(), brandX + 15, brandY + 7, brandX + 23, brandY + 15,
            brandX + 15, brandY + 23, brandX + 7, brandY + 15);
        canvas.textMedium(document.brand(), brandX + 39, brandY + 1, 13, theme.text());
        canvas.text(caption, brandX + 39, brandY + 18, 7, theme.mutedText());

        float navY = brandY + 54;
        if (!sectionLabel.isEmpty()) {
            canvas.textMedium(sectionLabel, brandX + 3, brandY + 48, 8, theme.mutedText());
            navY = brandY + 68;
        }
        for (int i = 0; i < document.pages().size(); i++) {
            UiPage candidate = document.pages().get(i);
            float y = navY + i * 34;
            boolean active = candidate.id().equals(page.id());
            boolean hover = context.hovered("nav-" + candidate.id());
            if (active || hover) {
                canvas.roundRect(active ? theme.raisedSurface()
                        : UiCanvas.mix(theme.surface(), theme.raisedSurface(), .55F),
                    margin + 10, y, sidebar - 20, 30, theme.smallRadius());
                if (active) {
                    canvas.strokeRoundRect(theme.border(), .7F, margin + 10, y, sidebar - 20, 30, theme.smallRadius());
                    canvas.roundRect(page.accent(), margin, y + 6, 3, 18, 2);
                }
            }
            canvas.centeredText(candidate.navigationIcon(), margin + 29, y + 15, 10,
                active ? theme.text() : theme.mutedText(), Font.PLAIN);
            canvas.textMedium(candidate.navigationLabel(), margin + 48, y + 9, 10,
                active ? theme.text() : theme.mutedText());
            context.hit("nav-" + candidate.id(), "page:" + candidate.id(), margin + 10, y, sidebar - 20, 30);
        }

        if (!footnote.isEmpty()) {
            canvas.text(footnote, margin + 17, layoutHeight - margin - 22, 7, theme.mutedText());
        }
        if (statusIndicator) {
            boolean animating = context.animating();
            canvas.circle(animating ? page.accent() : 0xFF48D994, margin + sidebar - 53, layoutHeight - margin - 18, 2.5F);
            canvas.text(animating ? "painting" : "cached", margin + sidebar - 45,
                layoutHeight - margin - 23, 7, theme.mutedText());
        }

        float contentX = margin + sidebar;
        return new UiRect(contentX, margin, layoutWidth - margin - contentX, layoutHeight - margin * 2);
    }
}
