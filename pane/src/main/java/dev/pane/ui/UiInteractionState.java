package dev.pane.ui;

import java.util.*;

/** Internal scroll state and keyboard geometry, deliberately separate from consumer values and pointer hits. */
final class UiInteractionState {
    final Map<String, UiButtonState> buttons = new HashMap<>();
    final UiTextLayoutCache textLayouts;
    /** Layouts of text inputs that are not focused. */
    final UiTextLayoutCache restingLayouts = new UiTextLayoutCache(32);
    final Map<String,UiEditorViewport> editors;
    final Map<String, Double> offsets;
    final UiScrollEndState scrollEnds;
    final LinkedHashMap<String, Target> targets = new LinkedHashMap<>();
    final Map<String, UiInputRegion> inputs = new HashMap<>();
    final Deque<UiScrollRegion> ancestors = new ArrayDeque<>();
    /** Present only while a screen's page paint can hand groups to the GPU compositor. */
    UiGpuLayers.Collector gpuLayers;
    /** The world frame captured beneath the screen, or null; set by the screen before each paint. */
    UiWorldSnapshot worldSnapshot;
    /** Show/hide state of each {@link UiContext#gpuTransition}, kept between paints. */
    final Map<String, Transition> transitions = new HashMap<>();

    static final class Transition {
        boolean shown;
        long changedAt;
        Transition(boolean shown) { this.shown = shown; }
    }

    /** The document's styles for controls drawn without one; null means Pane's own. */
    UiButtonStyle buttonStyle;
    UiInputStyle inputStyle;
    UiMenuStyle menuStyle;

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
        /** The key binding waiting for a key press, set by the screen before each paint. */
        String capturingKey;
        boolean dragMoved;
        /** State key prefixes that only affect a region of the page, with that region. */
        final Map<String, UiRect> scopes = new HashMap<>();
        /** Timed repaints painters asked for; null bounds means the page. */
        final List<Refresh> refreshes = new ArrayList<>();
        /** Controls whose hover or press state this paint read; only they change when it changes. */
        final Set<String> interactionReads = new HashSet<>();
        /** A painter read the shared interaction progress, so any control's hover or press may change pixels. */
        boolean interactionMotionRead;
        /** A painter read the page transition, so the page repaints for as long as it runs. */
        boolean motionRead;

        /** State keys a control changed from other values this paint, such as a colour from its sliders. */
        final List<String> valueChanges = new ArrayList<>();

        void requestRefresh(long atNanos, UiRect bounds) { refreshes.add(new Refresh(atNanos, bounds)); }

        /** What built-in controls said about themselves this paint, and the descriptions painters set, which win. */
        final Map<String, UiNarration> narrations = new HashMap<>(), namedNarrations = new HashMap<>();
        /** The type each text input painted with this paint. */
        final Map<String, UiInputTypography> typography = new HashMap<>();

        void clear() {
            pointerAreas.clear(); dragBounds.clear(); refreshes.clear(); scopes.clear(); interactionReads.clear(); interactionMotionRead = false; motionRead = false; valueChanges.clear();
            narrations.clear(); namedNarrations.clear(); typography.clear();
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
            boolean across = ancestor.region.horizontal();
            UiRect viewport = ancestor.region.viewport();
            float contentStart = start(viewport, across) - (float) ancestor.offset;
            boolean beside = across ? reachable.bottom() <= viewport.y() || reachable.y() >= viewport.bottom()
                : reachable.right() <= viewport.x() || reachable.x() >= viewport.right();
            if (beside || start(reachable, across) < contentStart
                    || end(reachable, across) > contentStart + ancestor.region.contentHeight()) return;
            double delta = start(reachable, across) < start(viewport, across) ? start(reachable, across) - start(viewport, across)
                : end(reachable, across) > end(viewport, across) ? end(reachable, across) - end(viewport, across) : 0;
            if (size(reachable, across) > size(viewport, across)) delta = start(reachable, across) - start(viewport, across);
            double next = Math.clamp(ancestor.offset + delta, 0, ancestor.region.maxOffset());
            reachable = move(reachable, across, (float) (ancestor.offset - next));
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
        for (var ancestor : target.ancestors)
            bounds = move(bounds, ancestor.region.horizontal(), (float) (ancestor.offset - offsets.getOrDefault(ancestor.region.id(), 0D)));
        Set<String> changed = new HashSet<>();
        for (int i = target.ancestors.size() - 1; i >= 0; i--) {
            UiScrollRegion scroll = target.ancestors.get(i).region;
            boolean across = scroll.horizontal();
            UiRect viewport = scroll.viewport();
            for (int j = 0; j < i; j++) {
                var ancestor = target.ancestors.get(j);
                viewport = move(viewport, ancestor.region.horizontal(), (float) (ancestor.offset - offsets.getOrDefault(ancestor.region.id(), 0D)));
            }
            double old = offsets.getOrDefault(scroll.id(), 0D);
            // Include the outside focus stroke and antialiasing when the viewport has room.
            float padding = Math.min(4, Math.max(0, (size(viewport, across) - size(bounds, across)) / 2));
            double delta = start(bounds, across) - padding < start(viewport, across) ? start(bounds, across) - padding - start(viewport, across)
                : end(bounds, across) + padding > end(viewport, across) ? end(bounds, across) + padding - end(viewport, across) : 0;
            if (size(bounds, across) > size(viewport, across)) delta = start(bounds, across) - start(viewport, across);
            double next = Math.clamp(old + delta, 0, scroll.maxOffset());
            if (next != old) {
                scrollEnds.navigate(scroll.id(), next);
                offsets.put(scroll.id(), next); changed.add(scroll.id());
                bounds = move(bounds, across, (float) (old - next));
            }
        }
        return changed;
    }

    /** A rectangle's start, end and size along a scroll axis, and the rectangle moved along it. */
    private static float start(UiRect rect, boolean across) { return across ? rect.x() : rect.y(); }
    private static float end(UiRect rect, boolean across) { return across ? rect.right() : rect.bottom(); }
    private static float size(UiRect rect, boolean across) { return across ? rect.width() : rect.height(); }
    private static UiRect move(UiRect rect, boolean across, float distance) {
        return across ? rect.translate(distance, 0) : rect.translate(0, distance);
    }
}
