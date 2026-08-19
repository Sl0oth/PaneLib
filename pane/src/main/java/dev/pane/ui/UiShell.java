package dev.pane.ui;

/**
 * Chrome painted around a document's active page: window frame, navigation, status.
 * <p>
 * A shell receives a {@link UiContext} whose content rectangle is the whole surface and
 * returns the rectangle the page painter should own. Implementations may register hit
 * regions and native slots exactly like a page painter does.
 * <p>
 * The library ships {@link #NONE} for full-bleed screens that own every pixel, and
 * {@link UiSidebarShell} for the reference navigation layout. Consuming mods are expected
 * to supply their own shell when they want a different structure.
 */
@FunctionalInterface
public interface UiShell {

    /** No chrome at all. The page painter owns the entire surface. */
    UiShell NONE = (context, document, activePage) ->
        new UiRect(0, 0, context.width(), context.height());

    /**
     * Paints the chrome and returns the content rectangle, in layout units, for the page.
     *
     * @param context    a context whose content rectangle spans the full surface
     * @param document   the document being rendered
     * @param activePage the page that is about to paint
     */
    UiRect paint(UiContext context, UiDocument document, UiPage activePage);
}
