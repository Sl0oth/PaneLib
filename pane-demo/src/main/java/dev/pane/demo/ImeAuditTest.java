package dev.pane.demo;

import dev.pane.ui.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.PreeditEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Input-method composition in a PaneLib text field and text area, driven with the same events
 * Minecraft delivers from the operating system: focus turns text input on, a composition draws at
 * the caret without entering the value, a commit inserts its characters as one undo step, and
 * cancelling leaves the field exactly as it was. A real Japanese or Chinese keyboard still needs a
 * hands-on check; this covers what PaneLib does with what the keyboard sends.
 */
final class ImeAuditTest {
    private static final int SHORTCUT = System.getProperty("os.name", "").startsWith("Mac") ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL;
    private UiScreen screen;
    private int stage, failures, checks, paintsBefore;
    private long deadline;
    private int[] plain;
    private boolean finished;

    void tick(Minecraft client) {
        if (finished) return;
        try {
            if (screen == null) {
                if (!(client.gui.screen() instanceof TitleScreen) || client.gui.overlay() != null) return;
                screen = new UiScreen(UiDocument.builder("IME audit").page(new UiPage("main", "Main", "M", 0xFF5B9BE3, ui -> {
                    ui.textField("name", "name", "Name", 40, 40, 260, "Sl0oth");
                    ui.textArea("notes", "notes", "Notes", 40, 100, 320, 120, "First line");
                })).build());
                client.gui.setScreen(screen);
                deadline = System.nanoTime() + 600_000_000L;
                return;
            }
            if (screen.paintCount() == 0 || System.nanoTime() < deadline) return;
            step(client);
        } catch (Throwable error) {
            error.printStackTrace();
            System.out.println("[ImeTest] complete FAIL " + error);
            finished = true;
            client.stop();
        }
    }

    private void step(Minecraft client) {
        switch (stage++) {
            case 0 -> {
                screen.focus("name");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_END, 0, 0));
                deadline = System.nanoTime() + 200_000_000L;
            }
            case 1 -> {
                check(textInputEnabled(client), "focusing a field turns Minecraft's text input on");
                plain = SurfacePixels.copy(screen);
                paintsBefore = screen.paintCount();
                check(screen.preeditUpdated(new PreeditEvent("にほんご", 2, List.of("にほん", "ご"), 0)), "the focused field accepts a composition");
                deadline = System.nanoTime() + 200_000_000L;
            }
            case 2 -> {
                check(screen.compositionText().equals("にほんご"), "the composition is held while it is being typed");
                check(screen.stringValue("name", "").equals("Sl0oth"), "the composition does not enter the value");
                check(screen.paintCount() > paintsBefore && !java.util.Arrays.equals(plain, SurfacePixels.copy(screen)), "the composition is drawn on the surface");
                SurfacePixels.save(screen, "ime-composition.png");
                // The input method commits: composition ends and its characters arrive as typed text.
                screen.preeditUpdated(null);
                for (int codepoint : "日本語".codePoints().toArray()) screen.charTyped(new CharacterEvent(codepoint));
                deadline = System.nanoTime() + 200_000_000L;
            }
            case 3 -> {
                check(screen.stringValue("name", "").equals("Sl0oth日本語"), "a commit inserts its characters at the caret");
                check(screen.compositionText().isEmpty(), "no composition remains after a commit");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_Z, 0, SHORTCUT));
                check(screen.stringValue("name", "").equals("Sl0oth"), "one undo removes the whole committed word");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_Z, 0, SHORTCUT | GLFW.GLFW_MOD_SHIFT));
                check(screen.stringValue("name", "").equals("Sl0oth日本語"), "one redo restores it");
                deadline = System.nanoTime() + 200_000_000L;
            }
            case 4 -> {
                plain = SurfacePixels.copy(screen);
                screen.preeditUpdated(new PreeditEvent("zhong", 5, List.of("zhong"), 0));
                deadline = System.nanoTime() + 200_000_000L;
            }
            case 5 -> {
                // Cancelling ends the composition with nothing typed; Minecraft then sends no event text.
                screen.preeditUpdated(null);
                deadline = System.nanoTime() + 200_000_000L;
            }
            case 6 -> {
                check(screen.stringValue("name", "").equals("Sl0oth日本語"), "cancelling leaves the value unchanged");
                check(java.util.Arrays.equals(plain, SurfacePixels.copy(screen)), "cancelling leaves the surface exactly as it was");
                screen.focus("notes");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_END, 0, 0));
                screen.preeditUpdated(new PreeditEvent("한국어", 3, List.of("한국어"), 0));
                deadline = System.nanoTime() + 200_000_000L;
            }
            case 7 -> {
                check(screen.compositionText().equals("한국어"), "a text area accepts a composition too");
                screen.preeditUpdated(null);
                for (int codepoint : "한국어".codePoints().toArray()) screen.charTyped(new CharacterEvent(codepoint));
                check(screen.stringValue("notes", "").equals("First line한국어"), "a text area commit inserts at its caret");
                screen.focus(null);
                deadline = System.nanoTime() + 200_000_000L;
            }
            case 8 -> {
                check(!textInputEnabled(client), "leaving the inputs turns text input off");
                check(!screen.preeditUpdated(new PreeditEvent("x", 1, List.of("x"), 0)), "with nothing focused a composition is not taken");
                System.out.println("[ImeTest] complete " + (failures == 0 ? "PASS" : "FAIL") + " checks=" + checks);
                finished = true;
                client.stop();
            }
            default -> { }
        }
    }

    /** Minecraft keeps whether text input is on privately; read it the way the window reads it. */
    private static boolean textInputEnabled(Minecraft client) {
        try {
            var field = client.textInputManager().getClass().getDeclaredField("textInputEnabled");
            field.setAccessible(true);
            return field.getBoolean(client.textInputManager());
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private void check(boolean ok, String name) {
        checks++;
        if (!ok) failures++;
        System.out.println("[ImeTest] " + name + ": " + (ok ? "PASS" : "FAIL"));
    }

    /** A copy of the screen's retained surface pixels; reflection keeps them out of PaneLib's public API. */
    static final class SurfacePixels {
        /** Writes the retained surface to the game directory, for looking at the composition. */
        static void save(UiScreen screen, String name) {
            try {
                var field = UiScreen.class.getDeclaredField("surface");
                field.setAccessible(true);
                Object surface = field.get(screen);
                var image = surface.getClass().getDeclaredField("image");
                image.setAccessible(true);
                javax.imageio.ImageIO.write((java.awt.image.BufferedImage) image.get(surface), "png", new java.io.File(name));
            } catch (ReflectiveOperationException | java.io.IOException error) { throw new IllegalStateException(error); }
        }

        static int[] copy(UiScreen screen) {
            try {
                var field = UiScreen.class.getDeclaredField("surface");
                field.setAccessible(true);
                Object surface = field.get(screen);
                var pixels = surface.getClass().getDeclaredField("pixels");
                pixels.setAccessible(true);
                return ((int[]) pixels.get(surface)).clone();
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
    }
}
