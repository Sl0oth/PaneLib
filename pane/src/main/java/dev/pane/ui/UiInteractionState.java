package dev.pane.ui;

import java.util.*;

/** Internal scroll state and keyboard geometry, deliberately separate from consumer values and pointer hits. */
final class UiInteractionState {
    final Map<String, UiButtonState> buttons = new HashMap<>();
    final UiTextLayoutCache textLayouts;
    final Map<String,UiEditorViewport> editors;
    final Map<String, Double> offsets;
    final UiScrollEndState scrollEnds;
    final LinkedHashMap<String, Target> targets = new LinkedHashMap<>();
    final Map<String, UiInputRegion> inputs = new HashMap<>();
    final Deque<UiScrollRegion> ancestors = new ArrayDeque<>();
    /** Present only while a screen's page paint can hand groups to the GPU compositor. */
    UiGpuLayers.Collector gpuLayers;
    /** Show/hide state of each {@link UiContext#gpuTransition}, kept between paints. */
    final Map<String, Transition> transitions = new HashMap<>();

    static final class Transition {
        boolean shown;
        long changedAt;
        Transition(boolean shown) { this.shown = shown; }
    }

    /** Pointer, drag and timer requests; affine child contexts share the parent's instance. */
    Live live = new Live();

    /** Everything here is in document layout units. */
    record Refresh(long atNanos, UiRect bounds) { }

    static final class Live {
        /** Pointer position, NaN when unknown, and the areas painted from it. */
        float pointerX = Float.NaN, pointerY = Float.NaN;
        final List<UiRect> pointerAreas = new ArrayList<>();
        /** What each drag area moves, so a drag step repaints only that. */
        final Map<String, UiRect> dragBounds = new HashMap<>();
        /** The drag area key being dragged, set by the screen before each paint, and whether it has moved. */
        String draggingKey;
        boolean dragMoved;
        /** State key prefixes that only affect a region of the page, with that region. */
        final Map<String, UiRect> scopes = new HashMap<>();
        /** Timed repaints painters asked for; null bounds means the page. */
        final List<Refresh> refreshes = new ArrayList<>();

        void requestRefresh(long atNanos, UiRect bounds) { refreshes.add(new Refresh(atNanos, bounds)); }

        void clear() {
            pointerAreas.clear(); dragBounds.clear(); refreshes.clear(); scopes.clear();
        }
    }

    UiInteractionState() { this(new HashMap<>()); }
    UiInteractionState(Map<String, Double> offsets) { this(offsets,new HashMap<>(),new UiTextLayoutCache()); }
    UiInteractionState(Map<String, Double> offsets,Map<String,UiEditorViewport> editors,UiTextLayoutCache layouts) {
        this(offsets, editors, layouts, new UiScrollEndState());
    }
    UiInteractionState(Map<String, Double> offsets,Map<String,UiEditorViewport> editors,UiTextLayoutCache layouts, UiScrollEndState scrollEnds) {
        this.offsets=offsets;this.editors=editors;this.textLayouts=layouts;this.scrollEnds=scrollEnds;
    }
    void beginPaint() {
        buttons.clear(); targets.clear(); inputs.clear(); ancestors.clear();
        live.clear();
    }
    void register(UiHitRegion hit) {
        List<Ancestor> chain = ancestors.isEmpty() ? List.of()
            : ancestors.stream().map(region -> new Ancestor(region, offsets.getOrDefault(region.id(), 0D))).toList();
        UiRect bounds = new UiRect(hit.x(), hit.y(), hit.width(), hit.height());
        // Inner content may extend beyond its parent's content, yet become visible by inner scrolling.
        // Validate reachability in reveal order, rather than clipping the raw target against every ancestor.
        UiRect reachable = bounds;
        for (int i = chain.size() - 1; i >= 0; i--) {
            var ancestor = chain.get(i);
            UiRect viewport = ancestor.region.viewport();
            float top = viewport.y() - (float) ancestor.offset;
            if (reachable.right() <= viewport.x() || reachable.x() >= viewport.right()
                    || reachable.y() < top || reachable.bottom() > top + ancestor.region.contentHeight()) return;
            double delta = reachable.y() < viewport.y() ? reachable.y() - viewport.y()
                : reachable.bottom() > viewport.bottom() ? reachable.bottom() - viewport.bottom() : 0;
            if (reachable.height() > viewport.height()) delta = reachable.y() - viewport.y();
            double next = Math.clamp(ancestor.offset + delta, 0, ancestor.region.maxOffset());
            reachable = reachable.translate(0, (float) (ancestor.offset - next));
        }
        targets.put(hit.id(), new Target(new UiHitRegion(hit.id(), hit.action(), bounds.x(), bounds.y(),
            bounds.width(), bounds.height(), hit.trackX(), hit.trackWidth()), chain));
    }
    void inScroll(UiScrollRegion region, Runnable painter) {
        ancestors.addLast(region);
        try { painter.run(); } finally { ancestors.removeLast(); }
    }
    record Ancestor(UiScrollRegion region, double offset) { }
    record Target(UiHitRegion hit, List<Ancestor> ancestors) { }

    /** Reveal the control from inner to outer scrolls; never change the consumer state map. */
    Set<String> reveal(String id) {
        Target target = targets.get(id);
        if (target == null) return Set.of();
        UiRect bounds = new UiRect(target.hit.x(), target.hit.y(), target.hit.width(), target.hit.height());
        float shifted = 0;
        for (var ancestor : target.ancestors) shifted += (float) (ancestor.offset - offsets.getOrDefault(ancestor.region.id(), 0D));
        bounds = bounds.translate(0, shifted);
        Set<String> changed = new HashSet<>();
        for (int i = target.ancestors.size() - 1; i >= 0; i--) {
            UiScrollRegion scroll = target.ancestors.get(i).region;
            float parentShift = 0;
            for (int j = 0; j < i; j++) {
                var ancestor = target.ancestors.get(j);
                parentShift += (float) (ancestor.offset - offsets.getOrDefault(ancestor.region.id(), 0D));
            }
            UiRect viewport = scroll.viewport().translate(0, parentShift);
            double old = offsets.getOrDefault(scroll.id(), 0D);
            // Include the outside focus stroke and antialiasing when the viewport has room.
            float padding = Math.min(4, Math.max(0, (viewport.height() - bounds.height()) / 2));
            double delta = bounds.y() - padding < viewport.y() ? bounds.y() - padding - viewport.y()
                : bounds.bottom() + padding > viewport.bottom() ? bounds.bottom() + padding - viewport.bottom() : 0;
            if (bounds.height() > viewport.height()) delta = bounds.y() - viewport.y();
            double next = Math.clamp(old + delta, 0, scroll.maxOffset());
            if (next != old) {
                scrollEnds.navigate(scroll.id(), next);
                offsets.put(scroll.id(), next); changed.add(scroll.id());
                bounds = bounds.translate(0, (float) (old - next));
            }
        }
        return changed;
    }
}
