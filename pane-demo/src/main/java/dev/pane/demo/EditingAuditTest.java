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
    private int stage, checks;
    private long deadline;
    private boolean failed, finished;
    private static final int WORD = System.getProperty("os.name", "").startsWith("Mac")
        ? GLFW.GLFW_MOD_ALT : GLFW.GLFW_MOD_CONTROL;
    void tick(Minecraft client) {
        if (finished) return;
        try {
            if (screen == null) {
                if (!(client.gui.screen() instanceof TitleScreen)) return;
                screen = new UiScreen(UiDocument.builder("Editing audit").scaling(UiScaling.fixed())
                    .page(new UiPage("main", "Main", "", 0xFF88AA55, ui -> {
                        ui.textField("field", "value", "", 30, 30, 300, "alpha beta", 1000);
                        ui.textArea("area", "lines", "", 30, 100, 300, 100, "abcdef\n".repeat(30), 1000);
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
            click(31,45,GLFW.GLFW_MOD_SHIFT,false);type('X');
            check(screen.stringValue("value", "").equals("X"),"Shift-click extends selection from the existing anchor");
            finished=true; System.out.println("[EditingTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks); client.stop();
        } catch (Throwable error) { finished=true; error.printStackTrace(); System.out.println("[EditingTest] complete FAIL "+error); client.stop(); }
    }
    private void click(float x,float y,int mods,boolean twice) {
        var event=new MouseButtonEvent(x/screen.layoutScale(),y/screen.layoutScale(),new MouseButtonInfo(0,mods));
        screen.mouseClicked(event,twice);screen.mouseReleased(event);
    }
    private void key(int key,int mods) { screen.keyPressed(new KeyEvent(key,0,mods)); }
    private void type(char value) { screen.charTyped(new CharacterEvent(value)); }
    private void check(boolean passed,String message) { checks++; failed|=!passed; System.out.println("[EditingTest] "+message+": "+(passed?"PASS":"FAIL")); }
}
