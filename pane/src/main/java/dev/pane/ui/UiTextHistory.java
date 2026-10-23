package dev.pane.ui;

import java.util.ArrayDeque;
import java.util.Optional;

/** Bounded whole-edit undo/redo for plain text. Selection indices use UTF-16 offsets at grapheme boundaries. */
public final class UiTextHistory {
    /** A text value with its caret and selection anchor. */
    public record Snapshot(String value, int cursor, int anchor) {
        /** Requires a value and moves the caret and anchor to the nearest grapheme boundary at or before them. */
        public Snapshot {
            value = java.util.Objects.requireNonNull(value);
            UiTextBoundary.Index boundaries = UiTextBoundary.index(value);
            cursor = boundaries.floor(cursor); anchor = boundaries.floor(anchor);
        }
    }
    private final int maximumEdits, maximumCharacters;
    private final ArrayDeque<Snapshot> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private String expected;
    /** A history of up to 32 edits and 65,536 characters on each of undo and redo. */
    public UiTextHistory() { this(32, 65_536); }
    /** Character budget applies separately to each stack; oversized edits are not retained. */
    public UiTextHistory(int maximumEdits, int maximumCharacters) {
        if (maximumEdits < 1 || maximumCharacters < 1) throw new IllegalArgumentException("History limits must be positive");
        this.maximumEdits = maximumEdits; this.maximumCharacters = maximumCharacters;
    }
    /** Records one edit as its own undo step; an unchanged value records nothing. */
    public void record(Snapshot before, Snapshot after) {
        if (before.value().equals(after.value())) return;
        if (expected != null && !expected.equals(before.value())) clear();
        redo.clear(); push(undo, before); expected = after.value();
    }
    /**
     * Records an edit that continues the one just recorded, such as the rest of a word an input
     * method commits character by character, as part of that single undo step.
     */
    public void extend(Snapshot before, Snapshot after) {
        if (expected == null || undo.isEmpty() || !expected.equals(before.value())) { record(before, after); return; }
        if (before.value().equals(after.value())) return;
        redo.clear(); expected = after.value();
    }
    /** The state before the latest edit, if the text still matches what history expects. */
    public Optional<Snapshot> undo(Snapshot current) { return move(current, undo, redo); }
    /** The state an undo went back from, if the text still matches. */
    public Optional<Snapshot> redo(Snapshot current) { return move(current, redo, undo); }
    private Optional<Snapshot> move(Snapshot current, ArrayDeque<Snapshot> source, ArrayDeque<Snapshot> target) {
        if (expected != null && !expected.equals(current.value())) { clear(); return Optional.empty(); }
        if (source.isEmpty()) return Optional.empty();
        Snapshot next = source.removeLast(); push(target, current); expected = next.value(); return Optional.of(next);
    }
    private void push(ArrayDeque<Snapshot> stack, Snapshot snapshot) {
        if (snapshot.value().length() > maximumCharacters) { stack.clear(); return; }
        stack.addLast(snapshot);
        long characters = 0; for (Snapshot entry : stack) characters += entry.value().length();
        while (stack.size() > maximumEdits || characters > maximumCharacters)
            characters -= stack.removeFirst().value().length();
    }
    /** Forgets every edit. */
    public void clear() { undo.clear(); redo.clear(); expected = null; }
}
