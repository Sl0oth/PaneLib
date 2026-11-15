package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** The short control calls, document-wide styles, theme copies, saved settings and frame pacing. */
class UiEaseOfUseTest {
    private static final class Paint implements AutoCloseable {
        final BufferedImage image = new BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = image.createGraphics();
        final UiCanvas canvas = new UiCanvas(g, image);
        final UiInteractionState interactions = new UiInteractionState();
        final List<UiHitRegion> hits = new ArrayList<>();
        final List<UiMenu> menus = new ArrayList<>();
        final List<UiInputRegion> inputs = new ArrayList<>();
        final Map<String, Object> values = new HashMap<>();
        UiContext ui() {
            return new UiContext(canvas, UiTheme.dark(), 320, 200, 0xFFFFAA00, new UiRect(0, 0, 320, 200), null, null,
                .5F, 1, true, 0, values, hits, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new ArrayList<>(), inputs, new ArrayList<>(), new UiTextInputState(null, 0, 0), menus, new ArrayList<>(), null, 1, null)
                .withInteractions(interactions);
        }
        int[] pixels() { return image.getRGB(0, 0, 320, 200, null, 0, 320); }
        public void close() { g.dispose(); }
    }

    private static UiHitRegion hit(Paint p, String id) {
        return p.hits.stream().filter(h -> h.id().equals(id)).findFirst().orElseThrow();
    }

    @Test void shortButtonMatchesTheRoundedNeutralButton() {
        try (var brief = new Paint(); var full = new Paint(); var cell = new Paint()) {
            brief.ui().button("save", "Save", "save", 10, 10, 90, 30);
            full.ui().button("save", "Save", "save", 10, 10, 90, 30, UiContext.ButtonStyle.SECONDARY, UiContext.Shape.ROUNDED);
            cell.ui().button("save", "Save", "save", new UiRect(10, 10, 90, 30));
            assertArrayEquals(full.pixels(), brief.pixels());
            assertArrayEquals(full.pixels(), cell.pixels());
            assertEquals("save", hit(brief, "save").action());
            assertEquals(new UiRect(10, 10, 90, 30), new UiRect(hit(cell, "save").x(), hit(cell, "save").y(), hit(cell, "save").width(), hit(cell, "save").height()));
        }
    }

    @Test void boundsButtonWithAColourRoleMatchesItsLongForm() {
        try (var brief = new Paint(); var full = new Paint()) {
            brief.ui().button("go", "Go", "go", new UiRect(20, 40, 100, 32), UiContext.ButtonStyle.PRIMARY);
            full.ui().button("go", "Go", "go", 20, 40, 100, 32, UiContext.ButtonStyle.PRIMARY, UiContext.Shape.ROUNDED);
            assertArrayEquals(full.pixels(), brief.pixels());
        }
    }

    @Test void documentButtonStyleDrawsButtonsThatNameNoStyle() {
        var style = UiButtonStyle.builder().background(0xFF224466).border(0xFF224466).borderWidth(0).radius(0).build();
        try (var themed = new Paint(); var explicit = new Paint(); var plain = new Paint()) {
            themed.interactions.buttonStyle = style;
            themed.ui().button("a", "Apply", "apply", 10, 10, 90, 30);
            explicit.ui().button("a", "Apply", "apply", 10, 10, 90, 30, style);
            plain.ui().button("a", "Apply", "apply", 10, 10, 90, 30);
            assertArrayEquals(explicit.pixels(), themed.pixels());
            assertEquals(0xFF224466, themed.image.getRGB(14, 14));
            assertNotEquals(0xFF224466, plain.image.getRGB(14, 14));
        }
    }

    @Test void documentInputAndMenuStylesReachControlsThatNameNoStyle() {
        var input = UiInputStyle.defaults().font(UiCanvas.MONO, 13, java.awt.Font.BOLD).padding(4, 3);
        var menu = UiMenuStyle.builder().background(0xFF302010).build();
        var options = List.of(new UiSelectOption("a", "Alpha"), new UiSelectOption("b", "Beta"));
        try (var themed = new Paint(); var explicit = new Paint()) {
            themed.interactions.inputStyle = input;
            themed.interactions.menuStyle = menu;
            themed.ui().textField("name", "name", "Name", 10, 10, 200, "Steve");
            themed.ui().dropdown("kind", "kind", 10, 60, 200, 30, "a", options);
            explicit.ui().textField("name", "name", "Name", 10, 10, 200, 30, "Steve", 256, input);
            explicit.ui().dropdown("kind", "kind", 10, 60, 200, 30, "a", options, menu);
            assertArrayEquals(explicit.pixels(), themed.pixels());
            assertSame(menu, themed.menus.getFirst().style());
        }
    }

    @Test void singleKeyControlsUseTheKeyAsTheirId() {
        try (var p = new Paint()) {
            var ui = p.ui();
            ui.toggle("hints", 10, 10, true);
            assertTrue(ui.checkbox("sound", "Sound", 10, 40, 120, true));
            assertEquals(40, ui.slider("volume", 10, 70, 200, 0, 100, 40));
            assertEquals("Player", ui.textField("name", "Display name", 10, 100, 200, "Player"));
            assertEquals("Steve", ui.textField("skin", "Skin", new UiRect(10, 140, 200, 28), "Steve"));
            assertEquals("b", ui.dropdown("kind", new UiRect(10, 170, 200, 26), "b",
                List.of(new UiSelectOption("a", "Alpha"), new UiSelectOption("b", "Beta"))));
            for (String id : List.of("hints", "sound", "volume", "kind")) assertNotNull(hit(p, id));
            assertEquals(Boolean.TRUE, p.values.get("hints"));
            assertEquals(Boolean.TRUE, p.values.get("sound"));
            assertEquals("Player", p.values.get("name"));
            var skin = p.inputs.stream().filter(i -> i.id().equals("skin")).findFirst().orElseThrow();
            assertEquals("skin", skin.key());
            assertEquals(28, skin.bounds().height());
        }
    }

    @Test void readingThePageTransitionIsRecordedAndUnreadPaintsAreNot() {
        try (var p = new Paint()) {
            var ui = p.ui();
            ui.button("a", "A", "a", 10, 10, 80, 30);
            ui.toggle("t", 10, 60, false);
            assertFalse(p.interactions.live.motionRead, "built-in controls do not read the page transition");
            assertEquals(.5F, ui.motion());
            assertTrue(p.interactions.live.motionRead);
            p.interactions.beginPaint();
            assertFalse(p.interactions.live.motionRead, "each paint starts unread");
            assertTrue(p.ui().animating());
            assertTrue(p.interactions.live.motionRead, "a painter that asks whether the page is animating depends on it");
        }
    }

    @Test void repaintWaitFollowsTheDisplayAndKeepsCappedFramesEven() {
        // A 144 Hz display used to animate on every third frame; it now animates on every frame.
        long frame144 = 1_000_000_000L / 144;
        assertTrue(UiFrameRate.intervalNanos(144, 0) < frame144);
        assertTrue(UiFrameRate.intervalNanos(144, 0) > frame144 * 8 / 10);
        for (int hz : new int[]{60, 75, 120, 144, 165, 240, 360}) {
            long frame = 1_000_000_000L / hz, wait = UiFrameRate.intervalNanos(hz, 0);
            assertTrue(wait <= frame && wait > frame / 2, "every frame at " + hz);
        }
        // A ceiling lands on a whole number of display frames at or under that rate.
        assertFrames(144, 60, 3);
        assertFrames(120, 60, 2);
        assertFrames(240, 60, 4);
        assertFrames(60, 60, 1);
        assertFrames(60, 144, 1);
        assertFrames(165, 30, 6);
        assertEquals(UiFrameRate.intervalNanos(60, 0), UiFrameRate.intervalNanos(0, 0), "an unknown display counts as 60 Hz");
        assertEquals(UiFrameRate.intervalNanos(60, 0), UiFrameRate.intervalNanos(-1, 0));
    }

    private static void assertFrames(int hz, int ceiling, int frames) {
        double frame = 1_000_000_000D / hz;
        long wait = UiFrameRate.intervalNanos(hz, ceiling);
        assertTrue(wait > frame * (frames - 1) && wait <= frame * frames, hz + " Hz capped at " + ceiling + " waits " + frames + " frames");
    }

    @Test void renderSettingsKeepTheirOldConstructorAndClampTheFrameRate() {
        assertEquals(0, new UiRenderSettings(2560, 1440, 2).maximumFrameRate());
        assertEquals(new UiRenderSettings(2560, 1440, 2, 0), UiRenderSettings.balanced());
        assertEquals(60, UiRenderSettings.lowMemory().maximumFrameRate());
        assertEquals(15, UiRenderSettings.balanced().withMaximumFrameRate(1).maximumFrameRate());
        assertEquals(480, UiRenderSettings.balanced().withMaximumFrameRate(9000).maximumFrameRate());
        assertEquals(0, UiRenderSettings.balanced().withMaximumFrameRate(-5).maximumFrameRate());
        var capped = UiRenderSettings.highQuality().withMaximumFrameRate(90);
        assertEquals(4096, capped.maximumWidth());
        assertEquals(3, capped.maximumScale());
        assertEquals(90, capped.maximumFrameRate());
    }

    @Test void everyThemeTokenHasACopyMethodThatChangesOnlyThatToken() throws Exception {
        UiTheme base = UiTheme.dark();
        for (var component : UiTheme.class.getRecordComponents()) {
            String name = component.getName();
            if (name.endsWith("Radius")) continue;
            Class<?> type = component.getType();
            Object value = type == int.class ? (Object) 0xFF123456 : type == float.class ? (Object) 1.25F : "Serif";
            var copy = UiTheme.class.getMethod("with" + Character.toUpperCase(name.charAt(0)) + name.substring(1), type).invoke(base, value);
            for (var other : UiTheme.class.getRecordComponents()) {
                Object expected = other.getName().equals(name) ? value : other.getAccessor().invoke(base);
                assertEquals(expected, other.getAccessor().invoke(copy), name + " copy, reading " + other.getName());
            }
        }
        UiTheme round = base.withRadii(2, 4, 6);
        assertEquals(2, round.smallRadius());
        assertEquals(4, round.mediumRadius());
        assertEquals(6, round.largeRadius());
        assertEquals(base, round.withRadii(base.smallRadius(), base.mediumRadius(), base.largeRadius()));
    }

    @Test void olderThemeConstructorsStillWorkAndThemesCarryStatusColours() {
        UiTheme dark = UiTheme.dark();
        var fourteen = new UiTheme(dark.background(), dark.surface(), dark.raisedSurface(), dark.border(), dark.text(), dark.mutedText(),
            dark.accent(), dark.accentText(), dark.smallRadius(), dark.mediumRadius(), dark.largeRadius(), dark.density(), dark.motionScale(), dark.fontFamily());
        var thirteen = new UiTheme(dark.background(), dark.surface(), dark.raisedSurface(), dark.border(), dark.text(), dark.mutedText(),
            dark.accent(), dark.accentText(), dark.smallRadius(), dark.mediumRadius(), dark.largeRadius(), dark.density(), dark.motionScale());
        assertEquals(dark, fourteen);
        assertEquals(dark, thirteen);
        for (UiTheme theme : List.of(dark, UiTheme.light()))
            for (int colour : new int[]{theme.danger(), theme.success(), theme.warning()}) assertEquals(0xFF, colour >>> 24);
        assertNotEquals(dark.danger(), UiTheme.light().danger());
    }

    @Test void pageShorthandNeedsOnlyAnIdALabelAndAPainter() {
        UiPage page = UiPage.of("home", "Home", ui -> { });
        assertEquals("home", page.id());
        assertEquals("Home", page.navigationLabel());
        assertEquals("", page.navigationIcon());
        assertEquals(0xFF, page.accent() >>> 24);
        assertThrows(IllegalArgumentException.class, () -> UiPage.of("Not An Id", "x", ui -> { }));
    }

    @Test void documentsHoldTheirDefaultControlStyles() {
        var button = UiButtonStyle.builder().radius(3).build();
        var input = UiInputStyle.defaults().font(UiCanvas.MONO, 13, java.awt.Font.BOLD).padding(4, 3);
        var menu = UiMenuStyle.builder().background(0xFF302010).build();
        var plain = UiDocument.builder("Plain").page(UiPage.of("main", "Main", ui -> { })).build();
        assertTrue(plain.buttonStyle().isEmpty());
        assertSame(UiInputStyle.defaults(), plain.inputStyle());
        assertSame(UiMenuStyle.defaults(), plain.menuStyle());
        var styled = UiDocument.builder("Styled").page(UiPage.of("main", "Main", ui -> { }))
            .buttonStyle(button).inputStyle(input).menuStyle(menu).build();
        assertSame(button, styled.buttonStyle().orElseThrow());
        assertSame(input, styled.inputStyle());
        assertSame(menu, styled.menuStyle());
    }

    @Test void settingsLoadAsTheirDefaultsTypeAndSurviveASaveAndReload(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("nested").resolve("mod.properties");
        var defaults = new LinkedHashMap<String, Object>();
        defaults.put("name", "Player");
        defaults.put("hints", true);
        defaults.put("volume", 50);
        assertEquals(Map.of("name", "Player", "hints", true, "volume", 50D), UiSettingsFile.load(file, defaults), "no file gives the defaults");

        UiSettingsFile.save(file, Map.of("name", "123", "hints", false, "volume", 12.5), "Test");
        var loaded = UiSettingsFile.load(file, defaults);
        assertEquals("123", loaded.get("name"), "a numeric-looking name stays a string");
        assertEquals(false, loaded.get("hints"));
        assertEquals(12.5, loaded.get("volume"));
        assertFalse(Files.exists(file.resolveSibling("mod.properties.tmp")));

        Files.writeString(file, "name=Alex\nhints=maybe\nvolume=loud\nextra=ignored\n");
        loaded = UiSettingsFile.load(file, defaults);
        assertEquals("Alex", loaded.get("name"));
        assertEquals(true, loaded.get("hints"), "a value that is not a boolean gives the default");
        assertEquals(50D, loaded.get("volume"), "a value that is not a number gives the default");
        assertFalse(loaded.containsKey("extra"));

        Files.writeString(file, "volume=NaN\nhints= FALSE \n");
        loaded = UiSettingsFile.load(file, defaults);
        assertEquals(50D, loaded.get("volume"));
        assertEquals(false, loaded.get("hints"));
        assertEquals("Player", loaded.get("name"));
    }

    @Test void settingsRejectDefaultsTheyCannotSave() {
        assertThrows(IllegalArgumentException.class, () -> UiSettingsFile.requireSupported(Map.of("list", List.of())));
        assertThrows(IllegalArgumentException.class, () -> UiSettingsFile.requireSupported(Map.of("bad", Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> UiSettingsFile.requireSupported(Map.of(" ", "x")));
        UiSettingsFile.requireSupported(Map.of("a", "x", "b", true, "c", 1, "d", 2.5F));
    }
}
