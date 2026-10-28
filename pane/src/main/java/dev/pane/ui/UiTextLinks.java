package dev.pane.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Web links found in read-only text, so text views can underline them and open them on click. */
final class UiTextLinks {
    record Link(int start, int end, String url) {}

    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"'`]+", Pattern.CASE_INSENSITIVE);
    private static final String TRAILING = ".,;:!?)]}'\"";

    private UiTextLinks() {}

    /** Every http or https link in {@code text}, without trailing sentence punctuation. */
    static List<Link> find(String text) {
        if (text == null || text.indexOf("://") < 0) return List.of();
        List<Link> links = new ArrayList<>();
        Matcher matcher = URL.matcher(text);
        while (matcher.find()) {
            int end = matcher.end();
            while (end > matcher.start() && TRAILING.indexOf(text.charAt(end - 1)) >= 0) {
                // Keep a closing bracket that belongs to the link, as in a wiki URL with "(disambiguation)".
                char last = text.charAt(end - 1);
                if (last == ')' && count(text, matcher.start(), end, '(') >= count(text, matcher.start(), end, ')')) break;
                end--;
            }
            if (end > text.indexOf("://", matcher.start()) + 3) links.add(new Link(matcher.start(), end, text.substring(matcher.start(), end)));
        }
        return links;
    }

    /** The link covering character {@code index}, or null. */
    static Link at(String text, int index) {
        for (Link link : find(text)) if (index >= link.start() && index < link.end()) return link;
        return null;
    }

    private static int count(String text, int from, int to, char c) {
        int n = 0;
        for (int i = from; i < to; i++) if (text.charAt(i) == c) n++;
        return n;
    }
}
