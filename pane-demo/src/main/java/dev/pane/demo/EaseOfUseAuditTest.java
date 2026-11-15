package dev.pane.demo;

import dev.pane.ui.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/**
 * One-call screens, action handlers, saved settings and paint-once opening, in a title client.
 * Never opens a saved world.
 */
final class EaseOfUseAuditTest {
    private static final Map<String, Object> DEFAULTS = Map.of("name", "Player", "hints", true, "volume", 50);
    private Path file;
    private UiScreen screen, animated, paged, controls;
    private int feedFirst, feedRows;
    private int ticks, checks, saves, cancels, handlerActions;
    private int pagedBefore, accentChanges, columnChanges;
    private boolean failed;

    private UiScreen settingsScreen() {
        return UiScreen.of("Ease of use", ui -> {
            ui.canvas().fill(ui.theme().background(), 0, 0, ui.width(), ui.height());
            UiLayout column = ui.column(new UiRect(24, 24, 300, 400)).gap(12);
            ui.textField("name", "Display name", column.next(30), "unset");
            ui.toggle("hints", 24, 80, false);
            ui.slider("volume", 24, 120, 240, 0, 100, 0);
            ui.button("save", "Save", "save", column.spacer(120).next(32));
            ui.button("cancel", "Cancel", "cancel", 24, 250, 100, 32);
        }).settings(file, DEFAULTS)
            .onAction("save", owner -> { saves++; check(owner.saveSettings(), "saveSettings writes the file"); })
            .onAction("cancel", owner -> { cancels++; owner.onClose(); });
    }

    private UiScreen controlsScreen() {
        return UiScreen.of("Controls", ui -> {
            ui.canvas().fill(ui.theme().background(), 0, 0, ui.width(), ui.height());
            ui.form("form", new UiRect(20, 16, 380, 330), form -> form
                .section("Controls")
                .toggle("Show hints", "hints", true)
                .stepper("Columns", "columns", 1, 9, 1, 3)
                .keybind("Open menu", "open", GLFW.GLFW_KEY_H)
                .keybind("Attack", "attack", GLFW.GLFW_KEY_G, true)
                .color("Accent", "accent", 0xFF58A6FF, false));
            ui.scrollHorizontal("strip", new UiRect(420, 16, 200, 60), 1200, strip -> {
                for (int i = 0; i < 12; i++) strip.card(420 + i * 100, 20, 92, 48);
            });
            // A slider turned a quarter turn runs down the page; a text field drawn a quarter larger.
            ui.withTransform(UiTransform.identity().translate(660, 100).rotate(90), 0, 0, group -> group.slider("tilt", 0, 0, 120, 0, 100, 0));
            ui.withTransform(UiTransform.identity().translate(420, 300).scale(1.25F), 0, 0, group -> group.textField("nick", "Nickname", 0, 0, 120, ""));
            feedRows = 0; feedFirst = -1;
            ui.variableList("feed", new UiRect(420, 90, 200, 200), 400, i -> 18 + 9 * (i % 5), 4, (row, item) -> {
                if (feedFirst < 0) feedFirst = item.index();
                feedRows++;
                row.card(item.bounds().x(), item.bounds().y(), item.bounds().width() - 8, item.bounds().height());
            });
        }).onChange("accent", owner -> accentChanges++).onChange("columns", owner -> columnChanges++);
    }

    void tick(Minecraft client) {
        try {
            if (screen == null) {
                if (!(client.gui.screen() instanceof TitleScreen)) return;
                file = client.gameDirectory.toPath().resolve("ease-test").resolve("settings.properties");
                Files.deleteIfExists(file);
                screen = settingsScreen().open();
                check(client.gui.screen() == screen, "open() shows the screen");
                check(screen.parentScreen().orElse(null) instanceof TitleScreen, "of() returns to the screen open before it");
                return;
            }
            if (screen.paintCount() == 0) return;
            ticks++;
            if (ticks == 30) {
                // 1.5 s is far past the 180 ms opening transition, which used to paint about 11 times.
                check(screen.paintCount() <= 3, "a page that ignores its transition paints once on open, painted " + screen.paintCount());
                check(screen.settled(), "the unread transition still ends");
                check(screen.stringValue("name", "").equals("Player") && screen.booleanValue("hints", false)
                    && screen.numberValue("volume", -1) == 50, "settings load their defaults when no file exists");
                screen.focus("name");
                key(screen, GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL);
                "Alex".codePoints().forEach(cp -> screen.charTyped(new CharacterEvent(cp)));
                screen.focus("volume");
                key(screen, GLFW.GLFW_KEY_HOME, 0); key(screen, GLFW.GLFW_KEY_RIGHT, 0);
                screen.focus("hints"); key(screen, GLFW.GLFW_KEY_SPACE, 0);
                check(!screen.booleanValue("hints", true), "single-key toggle is focusable and toggles its key");
                check(!Files.exists(file), "edits stay a draft until saved");
                check(screen.focus("save"), "rectangle button is focusable");
                key(screen, GLFW.GLFW_KEY_ENTER, 0);
                check(saves == 1, "onAction runs its handler once");
                var saved = new Properties();
                try (var in = Files.newInputStream(file)) { saved.load(in); }
                check("Alex".equals(saved.getProperty("name")) && "false".equals(saved.getProperty("hints"))
                    && "1.0".equals(saved.getProperty("volume")), "saved file holds the edited values: " + saved);
                screen.setValue("name", "Unsaved");
                screen.focus("cancel"); key(screen, GLFW.GLFW_KEY_ENTER, 0);
                check(cancels == 1 && client.gui.screen() instanceof TitleScreen, "a second action handler closes to the parent");
            }
            if (ticks == 34) {
                UiScreen reloaded = settingsScreen();
                check(reloaded.stringValue("name", "").equals("Alex") && !reloaded.booleanValue("hints", true)
                    && reloaded.numberValue("volume", -1) == 1, "a new screen loads what was saved, not the discarded draft");
                animated = UiScreen.of("Animated", ui -> {
                    float in = ui.motion();
                    ui.canvas().fill(ui.theme().background(), 0, 0, ui.width(), ui.height());
                    ui.canvas().text("Entering", 24 + (1 - in) * 40, 24, 20, ui.theme().text());
                }).open();
            }
            if (ticks == 64) {
                check(animated.paintCount() >= 5, "a page that reads its transition repaints through it, painted " + animated.paintCount());
                check(animated.settled(), "the read transition ends");
                var document = UiDocument.builder("Pages")
                    .page(UiPage.of("first", "First", ui -> ui.canvas().fill(0xFF101418, 0, 0, ui.width(), ui.height())))
                    .page(UiPage.of("second", "Second", ui -> {
                        ui.canvas().fill(0xFF181410, 0, 0, ui.width(), ui.height());
                        ui.button("go", "Go", "go", 24, 24, 90, 30);
                    })).build();
                paged = UiScreen.create(document, UiScreenOptions.defaults()).onAction("go", owner -> handlerActions++).open();
            }
            if (ticks == 84) {
                pagedBefore = paged.paintCount();
                paged.showPage("second");
            }
            if (ticks == 114) {
                int painted = paged.paintCount() - pagedBefore;
                check(painted >= 1 && painted <= 3, "switching to a page that ignores its transition paints once, painted " + painted);
                check(paged.focus("go"), "styleless button on the new page is focusable");
                key(paged, GLFW.GLFW_KEY_ENTER, 0);
                check(handlerActions == 1, "create(document, options) screens handle actions through onAction");
                paged.onAction("go", null);
                key(paged, GLFW.GLFW_KEY_ENTER, 0);
                check(handlerActions == 1, "a removed handler no longer runs");
                controls = controlsScreen().open();
            }
            if (ticks == 134) {
                check(controls.numberValue("columns", -1) == 3 && controls.numberValue("open", -1) == GLFW.GLFW_KEY_H
                    && controls.colorValue("accent", 0) == 0xFF58A6FF, "form rows store their defaults under their keys");
                click(controls, "columns:up");
                check(controls.numberValue("columns", -1) == 4, "stepper plus adds one step");
                for (int i = 0; i < 9; i++) click(controls, "columns:down");
                check(controls.numberValue("columns", -1) == 1, "stepper stops at its minimum");
                check(columnChanges == 4, "onChange runs once for each change of its key, ran " + columnChanges);

                click(controls, "open");
                key(controls, GLFW.GLFW_KEY_K, 0);
                check(controls.numberValue("open", -1) == GLFW.GLFW_KEY_K, "a key binding takes the next key pressed");
                key(controls, GLFW.GLFW_KEY_J, 0);
                check(controls.numberValue("open", -1) == GLFW.GLFW_KEY_K, "and stops listening after it");
                click(controls, "open");
                key(controls, GLFW.GLFW_KEY_ESCAPE, 0);
                check(controls.numberValue("open", 0) == UiKeyNames.UNBOUND && client.gui.screen() == controls,
                    "Escape while listening unbinds the key and keeps the screen open");

                click(controls, "attack");
                var anywhere = new net.minecraft.client.input.MouseButtonEvent(5, 5, new net.minecraft.client.input.MouseButtonInfo(1, 0));
                controls.mouseClicked(anywhere, false); controls.mouseReleased(anywhere);
                check(controls.numberValue("attack", 0) == UiKeyNames.mouse(1) && UiKeyNames.isMouse((int) controls.numberValue("attack", 0)),
                    "a binding that takes mouse buttons stores the button clicked while listening");
                String shown = UiKeyNames.name(UiKeyNames.mouse(1)), letter = UiKeyNames.name(GLFW.GLFW_KEY_K);
                check(!shown.isBlank() && !shown.startsWith("key.") && letter.equalsIgnoreCase("k"), "names come from Minecraft: " + shown + ", " + letter);
                check(!UiKeyNames.isDown(GLFW.GLFW_KEY_K) && !UiKeyNames.isDown(UiKeyNames.mouse(1)), "isDown reads the real key and button state");

                var tilt = controls.regionBounds("tilt").orElseThrow();
                check(tilt.height() > tilt.width() * 2, "a slider rotated a quarter turn stands upright: " + tilt);
                double scale = controls.layoutScale(), tiltX = tilt.centerX() / scale;
                var grab = new net.minecraft.client.input.MouseButtonEvent(tiltX, (tilt.y() + tilt.height() * .25) / scale, new net.minecraft.client.input.MouseButtonInfo(0, 0));
                controls.mouseMoved(grab.x(), grab.y()); controls.mouseClicked(grab, false);
                double quarter = controls.numberValue("tilt", -1);
                check(quarter > 12 && quarter < 38, "pressing a quarter of the way down it sets about a quarter: " + quarter);
                var pull = new net.minecraft.client.input.MouseButtonEvent(tiltX + 30, (tilt.y() + tilt.height() * .8) / scale, new net.minecraft.client.input.MouseButtonInfo(0, 0));
                controls.mouseDragged(pull, 30, 0); controls.mouseReleased(pull);
                double most = controls.numberValue("tilt", -1);
                check(most > 68 && most < 92, "dragging down, even off to the side, follows its own track: " + most);

                click(controls, "nick");
                "Zed".codePoints().forEach(cp -> controls.charTyped(new CharacterEvent(cp)));
                check(controls.stringValue("nick", "").equals("Zed"), "a text field in a scaled group takes focus and typing");

                check(controls.focus("accent:hue"), "colour sliders are focusable");
                key(controls, GLFW.GLFW_KEY_HOME, 0);
                var strip = controls.scrollSnapshot("strip").orElseThrow();
                check(strip.horizontal() && strip.maximumOffset() > 0, "sideways region reports its width");
                double x = strip.viewport().centerX() / controls.layoutScale(), y = strip.viewport().centerY() / controls.layoutScale();
                controls.mouseScrolled(x, y, -2, 0);
                double swiped = controls.scrollSnapshot("strip").orElseThrow().targetOffset();
                check(swiped > 0, "a sideways swipe scrolls the strip");
                controls.mouseScrolled(x, y, 0, -2);
                check(controls.scrollSnapshot("strip").orElseThrow().targetOffset() > swiped, "the wheel scrolls it when no vertical region is under the pointer");
                var feed = controls.scrollSnapshot("feed").orElseThrow();
                controls.mouseScrolled(feed.viewport().centerX() / controls.layoutScale(), feed.viewport().centerY() / controls.layoutScale(), 0, -3);
                check(controls.scrollSnapshot("feed").orElseThrow().targetOffset() > 0, "a variable-height list scrolls");
                check(feed.maximumOffset() > 5000, "and its height is the sum of its rows");
            }
            if (ticks == 154) {
                check(controls.numberValue("accent:hue", -1) == 0 && controls.colorValue("accent", 0) != 0xFF58A6FF
                    && (controls.colorValue("accent", 0) >>> 24) == 0xFF, "moving the hue slider changes the stored colour: "
                    + controls.stringValue("accent", ""));
                check(accentChanges == 1, "a colour its sliders changed is announced under the colour's own key, " + accentChanges + " times");
                check(controls.scrollSnapshot("strip").orElseThrow().offset() > 0, "the strip has moved on screen");
                check(feedFirst > 0 && feedRows > 0 && feedRows < 20, "only rows in view are painted: " + feedRows + " from " + feedFirst);
                controls.setValue("accent", "#00FF00");
                // The field has repainted with "Zed", so its carets are where the scaled text is.
                clickNick(3);
                controls.charTyped(new CharacterEvent('A'));
                check(controls.stringValue("nick", "").equals("AZed"), "a click at its left edge puts the caret before the text: " + controls.stringValue("nick", ""));
            }
            if (ticks == 166) net.minecraft.client.Screenshot.grab(client.gameDirectory, "ease-controls.png", client.gameRenderer.mainRenderTarget(), 1, m -> {});
            if (ticks == 174) {
                check(Math.abs(controls.numberValue("accent:hue", -1) - 120) < .01, "a colour set from code moves the sliders");
                clickNick(-3);
                controls.charTyped(new CharacterEvent('!'));
                check(controls.stringValue("nick", "").equals("AZed!"), "and one at its right edge after it: " + controls.stringValue("nick", ""));
                Files.deleteIfExists(file);
                System.out.println("[EaseTest] " + checks + " checks");
                System.out.println("[EaseTest] complete " + (failed ? "FAIL" : "PASS"));
                client.stop();
            }
        } catch (Throwable error) {
            System.out.println("[EaseTest] complete FAIL " + error);
            error.printStackTrace();
            client.stop();
        }
    }

    /** Clicks the scaled text field {@code inset} layout units in from its left edge, or from its right when negative. */
    private void clickNick(float inset) {
        var nick = controls.regionBounds("nick").orElseThrow();
        double scale = controls.layoutScale();
        var event = new net.minecraft.client.input.MouseButtonEvent(((inset < 0 ? nick.right() : nick.x()) + inset) / scale, nick.centerY() / scale,
            new net.minecraft.client.input.MouseButtonInfo(0, 0));
        controls.mouseClicked(event, false); controls.mouseReleased(event);
    }

    private static void click(UiScreen target, String id) {
        var bounds = target.regionBounds(id).orElseThrow();
        double x = bounds.centerX() / target.layoutScale(), y = bounds.centerY() / target.layoutScale();
        var event = new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0));
        target.mouseMoved(x, y); target.mouseClicked(event, false); target.mouseReleased(event);
    }

    private static void key(UiScreen target, int key, int modifiers) { target.keyPressed(new KeyEvent(key, 0, modifiers)); }

    private void check(boolean ok, String text) {
        checks++;
        failed |= !ok;
        System.out.println("[EaseTest] " + text + ": " + (ok ? "PASS" : "FAIL"));
    }
}
