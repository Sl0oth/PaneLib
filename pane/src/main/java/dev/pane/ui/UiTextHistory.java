package dev.pane.ui;

import java.util.ArrayDeque;
import java.util.Optional;

/** Bounded whole-edit undo/redo for plain text. Selection indices use UTF-16 offsets at grapheme boundaries. */
public final class UiTextHistory {
    public record Snapshot(String value, int cursor, int anchor) {
        public Snapshot {
            value = java.util.Objects.requireNonNull(value);
            UiTextBoundary.Index boundaries = UiTextBoundary.index(value);
            cursor = boundaries.floor(cursor); anchor = boundaries.floor(anchor);
        }
    }
    private final int maximumEdits, maximumCharacters;
    private final ArrayDeque<Snapshot> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private String expected;
    public UiTextHistory() { this(32, 65_536); }
    /** Character budget applies separately to each stack; oversized edits are not retained. */
    public UiTextHistory(int maximumEdits, int maximumCharacters) {
        if (maximumEdits < 1 || maximumCharacters < 1) throw new IllegalArgumentException("History limits must be positive");
        this.maximumEdits = maximumEdits; this.maximumCharacters = maximumCharacters;
    }
    public void record(Snapshot before, Snapshot after) {
        if (before.value().equals(after.value())) return;
        if (expected != null && !expected.equals(before.value())) clear();
        redo.clear(); push(undo, before); expected = after.value();
    }
    public Optional<Snapshot> undo(Snapshot current) { return move(current, undo, redo); }
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
    public void clear() { undo.clear(); redo.clear(); expected = null; }
}
