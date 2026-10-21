package dev.pane.demo;

import dev.pane.ui.UiCanvas;

/** One bundled font registration per client, shared by the public recipes. */
final class ExampleFonts {
    static final String FAMILY = load();
    private static String load() {
        String name = "pane-demo-roboto";
        var source = ExampleFonts.class.getResourceAsStream("/assets/pane_demo/fonts/Roboto-Variable.ttf");
        return UiCanvas.registerFont(name, source) ? name : UiCanvas.SANS;
    }
}
