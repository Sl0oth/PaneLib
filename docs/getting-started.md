# Build your first PaneLib screen

PaneLib 0.9.8 is a release candidate. Publish it to the machine's local Maven repository and
launch the included showcase with:

```sh
./gradlew :pane:publishToMavenLocal
./gradlew runClient
```

In a consuming Fabric client mod, build a `UiDocument`, make one or more `UiPage` painters, then
open a `UiScreen`.

```java
var home = new UiPage(
    "home",
    "Home",
    "H",
    0xFF58A6FF,
    ui -> {
        float x = ui.contentX() + 24;
        float y = ui.contentY() + 24;

        ui.canvas().textMedium("Hello, PaneLib", x, y, 24, ui.theme().text());
        ui.button(
            "save-button", "Save", "save",
            x, y + 40, 88, 32,
            UiContext.ButtonStyle.PRIMARY,
            UiContext.Shape.ROUNDED
        );
    }
);

var document = UiDocument.builder("My mod")
    .brand("My mod")
    .theme(UiTheme.dark())
    .page(home)
    .build();

var screen = new UiScreen(
    document,
    NativeOverlayRenderer.NONE,
    event -> {
        if (event.type() == UiEventType.ACTION && event.key().equals("save")) saveConfig();
    }
);

Minecraft.getInstance().gui.setScreen(screen);
```

The painter is ordinary Java. Use `ui.canvas()` when you want complete visual control, or use
the built-in controls for common patterns. See [UI basics](ui-basics.md) for layout, interaction,
and input.

## Pages and navigation

Each `UiPage` has an id, label, icon text, accent color, and painter. A `UiShell` provides
optional chrome around the active page. The showcase uses `UiSidebarShell`; leave the shell out
for a full-bleed screen you draw yourself.

Call `screen.showPage("page-id")` to switch pages. `UiDocument.Builder.pageShortcuts(true)`
optionally maps keys `1` through `9` to the first nine pages.
