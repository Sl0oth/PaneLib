package dev.pane.demo;

import dev.pane.ui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

/** Keyboard editing acceptance checks against actual painted fields. */
final class EditingAuditTest {
    private UiScreen screen;
    private int stage, checks, pointerProbe;
    private long deadline;
    private boolean failed, finished;
    private String originalClipboard;
    private volatile String logText = "";
    private static final int SHORTCUT = System.getProperty("os.name", "").startsWith("Mac")
        ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL;
    private static final String ACCENT = "e\u0301", FAMILY = "👩‍👩‍👧‍👦", FLAG = "🇺🇸";
    private static final String[] POINTER_PIECES = {"A", ACCENT, FAMILY, FLAG, "Z"};
    private static final String POINTER_TEXT = String.join("", POINTER_PIECES);
    private static final String ROW = "A" + ACCENT + FLAG + "Z";
    private static final int WORD = System.getProperty("os.name", "").startsWith("Mac")
        ? GLFW.GLFW_MOD_ALT : GLFW.GLFW_MOD_CONTROL;
    void tick(Minecraft client) {
        if (finished) return;
        try {
            if (screen == null) {
                if (!(client.gui.screen() instanceof TitleScreen)) return;
                originalClipboard = client.keyboardHandler.getClipboard();
                screen = new UiScreen(UiDocument.builder("Editing audit").scaling(UiScaling.fixed())
                    .page(new UiPage("main", "Main", "", 0xFF88AA55, ui -> {
                        ui.textField("field", "value", "", 30, 30, 300, "alpha beta", 1000);
                        ui.textArea("area", "lines", "", 30, 100, 300, 100, "abcdef\n".repeat(30), 1000);
                        ui.textField("limited", "limitedValue", "", 30, 220, 130, "", 4);
                        ui.passwordField("masked", "secret", "", 180, 220, 150, "", 1000);
                        ui.textView("view", "read only\nsecond line", 30, 260, 300, 50, UiInputStyle.defaults(), true);
                        ui.textView("log", logText, 360, 100, 260, 60, UiInputStyle.defaults(), false, UiScrollFollow.WHEN_AT_END);
                    })).build());
                client.gui.setScreen(screen); deadline=System.nanoTime()+600_000_000L; return;
            }
            if (screen.paintCount()==0 || System.nanoTime()<deadline) return;
            if (stage==0) {
                screen.focus("field"); key(GLFW.GLFW_KEY_BACKSPACE, WORD);
                check(screen.stringValue("value", "").equals("alpha "), "word Backspace removes the preceding word");
                screen.setValue("value", "alpha beta"); key(GLFW.GLFW_KEY_END, 0);
                key(GLFW.GLFW_KEY_LEFT, WORD|GLFW.GLFW_MOD_SHIFT); type('X');
                check(screen.stringValue("value", "").equals("alpha X"), "Shift-word movement selects a word for replacement");
                screen.setValue("value", "alpha beta"); key(GLFW.GLFW_KEY_HOME, 0);
                key(GLFW.GLFW_KEY_DELETE, WORD);
                check(screen.stringValue("value", "").equals(" beta"), "word Delete removes the following word");
                screen.focus("area"); key(GLFW.GLFW_KEY_HOME, GLFW.GLFW_MOD_CONTROL);
                stage++; deadline=System.nanoTime()+150_000_000L; return;
            }
            if (stage==1) {
                key(GLFW.GLFW_KEY_PAGE_DOWN, 0); stage++; deadline=System.nanoTime()+150_000_000L; return;
            }
            if (stage==2) {
                type('!'); int position=screen.stringValue("lines", "").indexOf('!');
                check(position>=21 && position<=42, "PageDown moves roughly one visible editor page");
                key(GLFW.GLFW_KEY_PAGE_UP, 0); stage++; deadline=System.nanoTime()+150_000_000L; return;
            }
            if (stage==3) {
                type('?');
                check(screen.stringValue("lines", "").indexOf('?')<7, "PageUp returns to the first visual line");
                screen.setValue("value", "alpha beta");screen.focus("field");
                stage++;deadline=System.nanoTime()+150_000_000L;return;
            }
            if (stage==4) {
                click(50,45,0,true);type('X');
                check(screen.stringValue("value", "").equals("X beta"),"double-click replaces only the clicked word");
                screen.setValue("value","alpha beta");key(GLFW.GLFW_KEY_END,0);
                stage++;deadline=System.nanoTime()+150_000_000L;return;
            }
            if (stage==5) {
                click(31,45,GLFW.GLFW_MOD_SHIFT,false);type('X');
                check(screen.stringValue("value", "").equals("X"),"Shift-click extends selection from the existing anchor");
                graphemeKeys();
                try { clipboardEdits(client); joinedEdits(client); lengthLimits(client); }
                finally { restoreClipboard(client); }
                maskedEdits();
                readOnlyView(client);
                screen.setValue("value","notes.py");screen.clearFocus();screen.requestFocus("field",0,5);type('x');
                check(screen.stringValue("value","").equals("x.py"),"requestFocus with a range selects it, so typing replaces the name");
                screen.setValue("lines", ROW+"\n"+ROW+"\n"+ROW); screen.focus("area");
                key(GLFW.GLFW_KEY_HOME, SHORTCUT); key(GLFW.GLFW_KEY_RIGHT,0); key(GLFW.GLFW_KEY_RIGHT,0);
                advance(); return;
            }
            if (stage==6) {
                key(GLFW.GLFW_KEY_DOWN,0); type('|');
                check(screen.stringValue("lines", "").equals(ROW+"\nA"+ACCENT+"|"+FLAG+"Z\n"+ROW),
                    "Down preserves the grapheme column in a painted multiline editor");
                key(GLFW.GLFW_KEY_BACKSPACE,0); advance(); return;
            }
            if (stage==7) {
                key(GLFW.GLFW_KEY_UP,0); type('|');
                check(screen.stringValue("lines", "").equals("A"+ACCENT+"|"+FLAG+"Z\n"+ROW+"\n"+ROW),
                    "Up returns to the matching grapheme boundary after repaint");
                field(POINTER_TEXT); advance(); return;
            }
            if (stage>=9) { followingLog(client); return; }
            click(34+pointerProbe*8,45,0,false); type('|');
            check(boundaryInsertion(screen.stringValue("value", "")),
                "painted pointer probe "+pointerProbe+" inserts only at a complete grapheme boundary");
            if (++pointerProbe<17) { field(POINTER_TEXT); deadline=System.nanoTime()+150_000_000L; return; }
            logText="line\n".repeat(30)+"end"; screen.invalidate(); screen.scrollToEnd("log"); advance();
        } catch (Throwable error) {
            finished=true; error.printStackTrace();
            try { restoreClipboard(client); } catch (Throwable restoreError) { restoreError.printStackTrace(); }
            System.out.println("[EditingTest] complete FAIL "+error); client.stop();
        }
    }

    /** A following text view stays on its last line as text arrives, scrolls by id, and opens links on click. */
    private void followingLog(Minecraft client) {
        try {
            var log=screen.scrollSnapshot("log").orElseThrow();
            double end=log.contentHeight()-log.viewport().height();
            if (stage==9) {
                check(end>0 && Math.abs(log.offset()-end)<1,"scrollToEnd reaches the end of a text view");
                logText=logText+"\nmore\nmore\nmore"; screen.invalidate(); advance(); return;
            }
            if (stage==10) {
                check(end>0 && Math.abs(log.offset()-end)<1,"a following text view keeps showing new lines");
                screen.scrollTo("log",0); advance(); return;
            }
            if (stage==11) {
                check(log.offset()==0,"scrollTo moves a text view");
                logText="https://example.com/x\nplain text after"; screen.invalidate(); advance(); return;
            }
            if (stage==12) {
                click(log.viewport().right()-8,log.viewport().y()+6,0,false);
                check(client.gui.screen()==screen,"a click past the end of a link's line does not open it");
                click(log.viewport().x()+30,log.viewport().y()+6,0,false);
                check(client.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmLinkScreen,
                    "clicking a link in a text view asks to open it");
                client.gui.setScreen(screen); advance(); return;
            }
            finished=true; restoreClipboard(client);
            System.out.println("[EditingTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks); client.stop();
        } catch (Throwable error) {
            finished=true; error.printStackTrace();
            try { restoreClipboard(client); } catch (Throwable restoreError) { restoreError.printStackTrace(); }
            System.out.println("[EditingTest] complete FAIL "+error); client.stop();
        }
    }
    private void advance() { stage++; deadline=System.nanoTime()+150_000_000L; }
    private void restoreClipboard(Minecraft client) {
        if (originalClipboard != null) { client.keyboardHandler.setClipboard(originalClipboard); originalClipboard=null; }
    }
    private void field(String value) { screen.setValue("value",value); screen.focus("field"); key(GLFW.GLFW_KEY_HOME,0); }
    private void paste(Minecraft client,String value) {
        client.keyboardHandler.setClipboard(value); key(GLFW.GLFW_KEY_V,SHORTCUT);
    }
    private void undo() { key(GLFW.GLFW_KEY_Z,SHORTCUT); }
    private void redo() { key(GLFW.GLFW_KEY_Z,SHORTCUT|GLFW.GLFW_MOD_SHIFT); }
    private void graphemeKeys() {
        String[] clusters={ACCENT,"😀",FAMILY,FLAG,"👍🏽","1\uFE0F\u20E3"};
        for(int i=0;i<clusters.length;i++) {
            String source="A"+clusters[i]+"Z";
            field(source); key(GLFW.GLFW_KEY_RIGHT,0); key(GLFW.GLFW_KEY_RIGHT,0); key(GLFW.GLFW_KEY_BACKSPACE,0);
            check(screen.stringValue("value","").equals("AZ"),"cluster "+i+" Right and Backspace remove one whole grapheme");
            undo(); check(screen.stringValue("value","").equals(source),"cluster "+i+" undo restores all UTF-16 units");
            redo(); check(screen.stringValue("value","").equals("AZ"),"cluster "+i+" redo repeats whole-grapheme deletion");
            field(source); key(GLFW.GLFW_KEY_END,0); key(GLFW.GLFW_KEY_LEFT,0); key(GLFW.GLFW_KEY_LEFT,0);
            key(GLFW.GLFW_KEY_DELETE,0);
            check(screen.stringValue("value","").equals("AZ"),"cluster "+i+" Left and Delete remove one whole grapheme");
            field(source); key(GLFW.GLFW_KEY_RIGHT,0); key(GLFW.GLFW_KEY_RIGHT,GLFW.GLFW_MOD_SHIFT); type('X');
            check(screen.stringValue("value","").equals("AXZ"),"cluster "+i+" Shift-Right selects the complete grapheme");
            field(source); key(GLFW.GLFW_KEY_END,0); key(GLFW.GLFW_KEY_LEFT,0);
            key(GLFW.GLFW_KEY_LEFT,GLFW.GLFW_MOD_SHIFT); type('X');
            check(screen.stringValue("value","").equals("AXZ"),"cluster "+i+" Shift-Left selects the complete grapheme");
        }
        field("cafe\u0301 next"); key(GLFW.GLFW_KEY_END,0); key(GLFW.GLFW_KEY_BACKSPACE,WORD);
        check(screen.stringValue("value","").equals("cafe\u0301 "),"word deletion preserves an adjacent decomposed accent");
        key(GLFW.GLFW_KEY_BACKSPACE,WORD);
        check(screen.stringValue("value","").isEmpty(),"word deletion consumes the entire accented word and trailing space");
    }
    private void clipboardEdits(Minecraft client) {
        String selected=ACCENT+FLAG, source="A"+selected+"Z";
        field(source); key(GLFW.GLFW_KEY_RIGHT,0); key(GLFW.GLFW_KEY_RIGHT,GLFW.GLFW_MOD_SHIFT);
        key(GLFW.GLFW_KEY_RIGHT,GLFW.GLFW_MOD_SHIFT); key(GLFW.GLFW_KEY_X,SHORTCUT);
        check(screen.stringValue("value","").equals("AZ")&&client.keyboardHandler.getClipboard().equals(selected),
            "Cut copies exact complete clusters and removes the selected UTF-16 range");
        undo(); check(screen.stringValue("value","").equals(source),"undo Cut restores both clusters");
        redo(); check(screen.stringValue("value","").equals("AZ"),"redo Cut restores the deletion and insertion caret");
        key(GLFW.GLFW_KEY_V,SHORTCUT);
        check(screen.stringValue("value","").equals(source),"Paste restores exact decomposed and supplementary text");
        undo(); check(screen.stringValue("value","").equals("AZ"),"undo Paste removes its complete insertion");
        redo(); check(screen.stringValue("value","").equals(source),"redo Paste restores its complete insertion");
    }
    private void joinedEdits(Minecraft client) {
        String woman="👩", laptop="💻", joined=woman+"\u200D"+laptop;
        field(woman+laptop); key(GLFW.GLFW_KEY_RIGHT,0); paste(client,"\u200D"); type('!');
        check(screen.stringValue("value","").equals(joined+"!"),"ZWJ insertion moves the caret beyond the newly joined neighbors");
        undo(); undo();
        check(screen.stringValue("value","").equals(woman+laptop),"undo joined edit restores the separate emoji");
        redo(); key(GLFW.GLFW_KEY_BACKSPACE,0);
        check(screen.stringValue("value","").isEmpty(),"redo joined edit restores a caret that deletes the complete new cluster");
        String a="🇦", b="🇧", c="🇨", separated=a+"-"+b+c;
        field(separated); key(GLFW.GLFW_KEY_RIGHT,0); key(GLFW.GLFW_KEY_DELETE,0); type('|');
        check(screen.stringValue("value","").equals(a+b+"|"+c),"separator deletion reparses regional-indicator pairing and advances the caret");
        undo(); undo();
        check(screen.stringValue("value","").equals(separated),"undo regional-indicator join restores the separator");
        redo(); key(GLFW.GLFW_KEY_BACKSPACE,0);
        check(screen.stringValue("value","").equals(c),"redo regional-indicator join deletes the newly paired flag atomically");
    }
    private void lengthLimits(Minecraft client) {
        screen.setValue("limitedValue",""); screen.focus("limited"); key(GLFW.GLFW_KEY_HOME,0);
        paste(client,ACCENT+"😀Z");
        check(screen.stringValue("limitedValue","").equals(ACCENT+"😀"),"four-unit limit preserves a complete accent and supplementary emoji");
        screen.setValue("limitedValue",""); key(GLFW.GLFW_KEY_HOME,0); paste(client,FAMILY);
        check(screen.stringValue("limitedValue","").isEmpty(),"four-unit limit refuses a partial family cluster");
        screen.setValue("limitedValue",""); key(GLFW.GLFW_KEY_HOME,0); paste(client,FLAG+"x");
        check(screen.stringValue("limitedValue","").equals(FLAG),"four-unit limit accepts an exact flag without its overflow suffix");
        screen.setValue("limitedValue","abcd"); key(GLFW.GLFW_KEY_HOME,0);
        key(GLFW.GLFW_KEY_RIGHT,GLFW.GLFW_MOD_SHIFT); key(GLFW.GLFW_KEY_RIGHT,GLFW.GLFW_MOD_SHIFT);
        paste(client,ACCENT+"Z");
        check(screen.stringValue("limitedValue","").equals(ACCENT+"cd"),"replacement capacity accounts for the selected UTF-16 range before cluster truncation");
        undo(); check(screen.stringValue("limitedValue","").equals("abcd"),"undo restores a limit-constrained replacement");
        redo(); check(screen.stringValue("limitedValue","").equals(ACCENT+"cd"),"redo restores the complete limit-constrained cluster");
    }
    private void maskedEdits() {
        screen.setValue("secret","A"+FAMILY+FLAG+"Z"); screen.focus("masked"); key(GLFW.GLFW_KEY_HOME,0);
        key(GLFW.GLFW_KEY_RIGHT,0); key(GLFW.GLFW_KEY_RIGHT,0); key(GLFW.GLFW_KEY_BACKSPACE,0);
        check(screen.stringValue("secret","").equals("A"+FLAG+"Z"),"masked navigation and Backspace use original graphemes rather than display bullets");
        key(GLFW.GLFW_KEY_RIGHT,GLFW.GLFW_MOD_SHIFT); type('X');
        check(screen.stringValue("secret","").equals("AXZ"),"masked Shift selection replaces the complete flag");
    }
    private boolean boundaryInsertion(String actual) {
        int offset=0;
        for(String piece:POINTER_PIECES) {
            if(actual.equals(POINTER_TEXT.substring(0,offset)+"|"+POINTER_TEXT.substring(offset)))return true;
            offset+=piece.length();
        }
        return actual.equals(POINTER_TEXT+"|");
    }
    private void click(float x,float y,int mods,boolean twice) {
        var event=new MouseButtonEvent(x/screen.layoutScale(),y/screen.layoutScale(),new MouseButtonInfo(0,mods));
        screen.mouseClicked(event,twice);screen.mouseReleased(event);
    }
    /** A text view selects and copies like a text area, but no key, paste or cut edits it. */
    private void readOnlyView(Minecraft client) {
        var controls=screen.controls();
        check(controls.stream().anyMatch(ctl->ctl.id().equals("field")&&ctl.narration()!=null&&ctl.narration().role()==UiNarration.Role.TEXT_FIELD&&ctl.bounds().width()==300),
            "controls() lists a painted field with its role and bounds");
        check(controls.stream().map(UiScreen.Control::id).distinct().count()==controls.size(),"controls() lists each control once");
        String text="read only\nsecond line";
        check(screen.focus("view"),"a text view takes keyboard focus");
        type('q'); key(GLFW.GLFW_KEY_BACKSPACE,0); key(GLFW.GLFW_KEY_ENTER,0);
        client.keyboardHandler.setClipboard("pasted"); key(GLFW.GLFW_KEY_V,SHORTCUT); key(GLFW.GLFW_KEY_X,SHORTCUT);
        check(screen.stringValue("pane:view:view","").equals(text),"typing, Backspace, Enter, paste and cut leave a text view unchanged");
        key(GLFW.GLFW_KEY_A,SHORTCUT); key(GLFW.GLFW_KEY_C,SHORTCUT);
        check(client.keyboardHandler.getClipboard().equals(text),"select all and copy work in a text view");
        screen.clearFocus();
    }

    private void key(int key,int mods) { screen.keyPressed(new KeyEvent(key,0,mods)); }
    private void type(char value) { screen.charTyped(new CharacterEvent(value)); }
    private void check(boolean passed,String message) { checks++; failed|=!passed; System.out.println("[EditingTest] "+message+": "+(passed?"PASS":"FAIL")); }
}
