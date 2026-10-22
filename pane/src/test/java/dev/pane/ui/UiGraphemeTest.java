package dev.pane.ui;

import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

final class UiGraphemeTest {
    static final List<String> CLUSTERS = List.of("e\u0301", "👍🏽", "🇺🇸", "👩‍🚀", "👨‍👩‍👧‍👦", "1️⃣", "\u0915\u094D\u0937", "\u1100\u1161\u11A8");

    @TestFactory Stream<DynamicTest> officialUnicode16Boundaries() throws Exception {
        String data;
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/unicode/GraphemeBreakTest-16.0.0.txt"))) {
            data = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var tests = new ArrayList<DynamicTest>();
        for (String line : data.lines().toList()) {
            String body = line.split("#", 2)[0].trim();
            if (body.isEmpty()) continue;
            var text = new StringBuilder(); var boundaries = new TreeSet<Integer>();
            for (String token : body.split("\\s+")) {
                if (token.equals("÷")) boundaries.add(text.length());
                else if (!token.equals("×")) text.appendCodePoint(Integer.parseInt(token, 16));
            }
            String value = text.toString();
            tests.add(DynamicTest.dynamicTest("Unicode 16 case " + (tests.size() + 1), () -> {
                for (int i = -1; i <= value.length() + 1; i++) {
                    int at = Math.clamp(i, 0, value.length());
                    int floor = boundaries.floor(at);
                    assertEquals(floor, UiTextBoundary.floor(value, i), body + " floor " + i);
                    assertEquals(boundaries.ceiling(at), UiTextBoundary.ceil(value, i), body + " ceil " + i);
                    assertEquals(floor == 0 ? 0 : boundaries.lower(floor), UiTextBoundary.previous(value, i), body + " previous " + i);
                    assertEquals(floor == value.length() ? floor : boundaries.higher(floor), UiTextBoundary.next(value, i), body + " next " + i);
                    assertEquals(value.substring(0, floor), UiTextBoundary.truncate(value, i), body + " truncate " + i);
                }
            }));
        }
        assertEquals(1093, tests.size(), "The complete pinned conformance corpus must run");
        return tests.stream();
    }

    @Test void wordSelectionAndMovementNeverSplitACluster() {
        for (String cluster : CLUSTERS) {
            String value = cluster + " abc " + cluster;
            var valid = Set.of(0, cluster.length(), cluster.length()+1, cluster.length()+2,
                cluster.length()+3, cluster.length()+4, cluster.length()+5, value.length());
            for (int i = 0; i <= value.length(); i++) {
                var selected = UiTextBoundary.wordAt(value, i);
                assertTrue(valid.contains(selected.start()), value + " word start " + i);
                assertTrue(valid.contains(selected.end()), value + " word end " + i);
                assertTrue(selected.end() > selected.start());
                assertTrue(valid.contains(UiTextBoundary.previousWord(value, i)));
                assertTrue(valid.contains(UiTextBoundary.nextWord(value, i)));
            }
        }
    }

    @Test void historySnapsExternalUtf16IndicesToWholeClusters() {
        var snapshot = new UiTextHistory.Snapshot("A👩‍🚀B", 5, 2);
        assertEquals(1, snapshot.cursor()); assertEquals(1, snapshot.anchor());
        var history = new UiTextHistory(); var after = new UiTextHistory.Snapshot("AB", 1, 1);
        history.record(snapshot, after);
        assertEquals(snapshot, history.undo(after).orElseThrow());
        assertEquals(after, history.redo(snapshot).orElseThrow());
    }

    @Test void narrowWrappingAndShapedCaretsRespectClusterEdges() {
        var image = new BufferedImage(300,100,BufferedImage.TYPE_INT_ARGB); var g = image.createGraphics();
        try {
            var canvas = new UiCanvas(g,image);
            for (String cluster : CLUSTERS) {
                String value = cluster + cluster;
                Set<Integer> valid = Set.of(0,cluster.length(),value.length());
                var shaped = canvas.shapeText(value,18,Font.PLAIN,UiCanvas.SANS);
                for (int i=0;i<=value.length();i++) assertEquals(valid.contains(i), Float.isFinite(shaped.x(i)), value + " caret " + i);
                for (float width : new float[]{.01F, 8, 50}) {
                    for (var line : canvas.wrapShapedText(value,width,18,Font.PLAIN,UiCanvas.SANS)) {
                        assertTrue(valid.contains(line.start())); assertTrue(valid.contains(line.end()));
                        assertTrue(line.end()>line.start());
                    }
                    for (var line : UiTextWrap.lines(value,width,(start,end)->end-start)) {
                        assertTrue(valid.contains(line.start())); assertTrue(valid.contains(line.end()));
                    }
                }
            }
        } finally { g.dispose(); }
    }

    @Test void fieldPasswordAndAreaPointerGeometryNeverEntersACluster() {
        for (int kind=0;kind<3;kind++) for (String cluster : CLUSTERS) {
            String value = "A" + cluster + "B";
            var valid = Set.of(0,1,1+cluster.length(),value.length());
            var image = new BufferedImage(300,150,BufferedImage.TYPE_INT_ARGB); var g=image.createGraphics();
            try {
                var inputs = new ArrayList<UiInputRegion>();
                var ui = new UiContext(new UiCanvas(g,image),UiTheme.dark(),300,150,0xFF55AAFF,
                    new UiRect(0,0,300,150),null,null,1,1,false,0,new HashMap<>(),new ArrayList<>(),
                    new LinkedHashMap<>(),new LinkedHashMap<>(),new HashMap<>(),new ArrayList<>(),inputs,
                    new ArrayList<>(),new UiTextInputState(null,0,0),new ArrayList<>(),new ArrayList<>(),null,1,null);
                if(kind==0) ui.textField("field","value","",10,10,260,40,value,100,UiInputStyle.defaults());
                else if(kind==1) ui.passwordField("field","value","",10,10,260,40,value,100,UiInputStyle.defaults());
                else ui.textArea("field","value","",10,10,80,100,value,100,UiInputStyle.defaults());
                var input=inputs.getFirst(); float[] xs=input.caretXs();
                for(int i=0;i<xs.length;i++) assertEquals(valid.contains(i),Float.isFinite(xs[i]),"kind="+kind+" index="+i);
                for(float y=12;y<100;y+=7) for(float x=12;x<270;x+=3)
                    assertTrue(valid.contains(input.caretPositionAt(x,y).index()));
            } finally {g.dispose();}
        }
    }
}
