package dev.pane.demo;

import dev.pane.ui.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.narration.ScreenNarrationCollector;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/**
 * What Minecraft's narrator would say on a PaneLib screen, read through Minecraft's own narration
 * collector: each built-in control's role, label, state and usage as keyboard focus moves, a
 * painter's own description, a dialog, and the page title. Runs without sound.
 */
final class NarrationAuditTest {
    private Probe screen;
    private int stage, failures, checks;
    private long deadline;
    private boolean finished;

    void tick(Minecraft client) {
        if (finished) return;
        try {
            if (screen == null) {
                if (!(client.gui.screen() instanceof TitleScreen) || client.gui.overlay() != null) return;
                screen = new Probe(UiDocument.builder("Narration audit")
                    .page(new UiPage("controls", "Controls", "C", 0xFF5B9BE3, NarrationAuditTest::controls))
                    .page(new UiPage("about", "About", "A", 0xFF5B9BE3, ui -> ui.button("back", "Back", "page:controls", 20, 20, 90, 30,
                        UiContext.ButtonStyle.PRIMARY, UiContext.Shape.ROUNDED)))
                    .build());
                client.gui.setScreen(screen);
                deadline = System.nanoTime() + 600_000_000L;
                return;
            }
            if (screen.paintCount() == 0 || System.nanoTime() < deadline) return;
            step();
        } catch (Throwable error) {
            error.printStackTrace();
            System.out.println("[NarrationTest] complete FAIL " + error);
            finished = true;
            client.stop();
        }
    }

    private static void controls(UiContext ui) {
        ui.button("save", "Save", "saved", 20, 20, 90, 30, UiContext.ButtonStyle.PRIMARY, UiContext.Shape.ROUNDED);
        ui.checkbox("snap", "snap", "Snap to grid", 20, 70, 140, false);
        ui.toggle("dark", "dark", 20, 110, false);
        ui.narration("dark", UiNarration.of(UiNarration.Role.TOGGLE, "Dark mode", ui.booleanValue("dark", false) ? "on" : "off"));
        ui.slider("volume", "volume", 20, 150, 160, .5);
        ui.tooltip("volume", "Volume");
        ui.textField("name", "name", "Name", 20, 190, 160, "Sl0oth");
        ui.passwordField("secret", "secret", "Password", 20, 230, 160, "hunter2");
        ui.tabs("view", "view", List.of(new UiSelectOption("general", "General"), new UiSelectOption("audio", "Audio"),
            new UiSelectOption("video", "Video")), 20, 270, 240, 28, "audio");
        ui.hit("plain", "noop", 300, 20, 60, 60);
        ui.dialogButton("ask", "Delete", 300, 100, 90, 30, UiContext.ButtonStyle.SECONDARY, UiContext.Shape.ROUNDED,
            new UiDialog("Delete note?", "This cannot be undone.", "Delete", "deleted", "Keep", "kept"));
    }

    private void step() {
        switch (stage++) {
            case 0 -> {
                expect("save", "Save button", "Press Enter to activate");
                expect("snap", "Checkbox: Snap to grid", "OFF", "Press Enter to check");
                expect("dark", "Dark mode", "OFF");
                expect("volume", "Volume: 50% slider", "Press the left or right keyboard buttons");
                expect("name", "Name edit box: Sl0oth");
                expect("secret", "Password edit box");
                refuse("secret", "hunter2");
                expect("view-1", "Audio tab", "Selected tab 2 out of 3");
                expect("view-0", "General tab", "Selected tab 1 out of 3");
                check(spoken("plain").equals("Narration audit. Controls"), "a bare hit area with no description adds nothing to the screen title (\"" + spoken("plain") + "\")");
                check(screen.getNarrationMessage().getString().contains("Controls"), "a multi-page document announces the page title");
                // Keyboard changes reach the narrator on the next paint.
                screen.focus("snap");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0));
                deadline = System.nanoTime() + 200_000_000L;
            }
            case 1 -> {
                expect("snap", "Checkbox: Snap to grid", "ON", "Press Enter to uncheck");
                // Only what changed is spoken again, as Minecraft does for its own screens.
                screen.focus("save");
                String first = screen.collect(false);
                screen.focus("name");
                String changed = screen.collect(true);
                check(first.contains("Save button") && changed.contains("Name edit box") && !changed.contains("Save"),
                    "moving focus speaks the newly focused control (\"" + changed + "\")");
                screen.focus("ask");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                deadline = System.nanoTime() + 300_000_000L;
            }
            case 2 -> {
                String dialog = screen.collect(false);
                check(dialog.contains("Delete note?") && dialog.contains("This cannot be undone.") && dialog.contains("Keep button"),
                    "an open dialog speaks its title, message and focused button (\"" + dialog + "\")");
                screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                screen.showPage("about");
                deadline = System.nanoTime() + 300_000_000L;
            }
            case 3 -> {
                check(screen.getNarrationMessage().getString().contains("About"), "switching pages changes the narrated title");
                System.out.println("[NarrationTest] complete " + (failures == 0 ? "PASS" : "FAIL") + " checks=" + checks);
                finished = true;
                Minecraft.getInstance().stop();
            }
            default -> { }
        }
    }

    private String spoken(String id) {
        screen.focus(id);
        return screen.collect(false);
    }

    private void expect(String id, String... words) {
        String text = spoken(id);
        boolean all = true;
        for (String word : words) all &= text.contains(word);
        check(all, id + " narrates " + String.join(" / ", words) + " (\"" + text + "\")");
    }

    private void refuse(String id, String word) {
        String text = spoken(id);
        check(!text.contains(word), id + " never speaks \"" + word + "\"");
    }

    private void check(boolean ok, String name) {
        checks++;
        if (!ok) failures++;
        System.out.println("[NarrationTest] " + name + ": " + (ok ? "PASS" : "FAIL"));
    }

    /** Reads narration the way Minecraft's screen does: through a narration collector over the screen's narration state. */
    static final class Probe extends UiScreen {
        private final ScreenNarrationCollector collector = new ScreenNarrationCollector();

        Probe(UiDocument document) { super(document); }

        /** All narration for the current state, or with {@code onlyChanged} just what changed since the last call. */
        String collect(boolean onlyChanged) {
            collector.update(this::updateNarrationState);
            return collector.collectNarrationText(!onlyChanged);
        }
    }
}
