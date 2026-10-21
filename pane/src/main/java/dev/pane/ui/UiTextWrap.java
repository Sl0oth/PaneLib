package dev.pane.ui;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Word-aware visual lines, retaining exact UTF-16 offsets and all original whitespace. */
final class UiTextWrap {
    record Line(int start, int end) { }
    static List<Line> lines(String text, float width, float[] advances) {
        double[] sums = new double[text.length() + 1];
        for (int i = 0; i < text.length(); i++) sums[i + 1] = sums[i] + advances[i];
        return lines(text, width, (start, end) -> sums[end] - sums[start]);
    }
    static List<Line> lines(String text, float width, java.util.function.ToDoubleBiFunction<Integer, Integer> measure) {
        var result = new ArrayList<Line>();
        var breaks = BreakIterator.getLineInstance(Locale.ROOT);
        breaks.setText(text);
        var boundaries=new java.util.BitSet(text.length()+1);
        for(int boundary=breaks.first();boundary!=BreakIterator.DONE;boundary=breaks.next())boundaries.set(boundary);
        int start = 0, index = 0, lastBreak = -1;
        while (index < text.length()) {
            int cp = text.codePointAt(index);
            if (cp == '\n') {
                result.add(new Line(start, index));
                start = ++index; lastBreak = -1; continue;
            }
            int next = UiTextBoundary.next(text, index);
            // A trailing space belongs to its preceding line even when its advance exceeds the edge.
            if (index > start && measure.applyAsDouble(start, next) > width && !Character.isWhitespace(cp)) {
                int end = lastBreak > start ? lastBreak : index;
                result.add(new Line(start, end));
                start = index = end; lastBreak = -1;
                continue;
            }
            index = next;
            if (boundaries.get(index)) lastBreak = index;
        }
        result.add(new Line(start, text.length()));
        return List.copyOf(result);
    }
}
