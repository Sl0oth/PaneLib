# Build your first PaneLib screen

Start with the [independent five-control starter](../examples/first-screen/README.md). It includes
imports, a Fabric client entrypoint, opening the screen, H to reopen, and settings persistence.
It runs against the **packaged library JAR**, without the demo module or PaneLib source on its classpath.
Use [installation](installation.md) first if you are adding PaneLib to an existing Fabric mod.

## The parts you own

- A `UiDocument` holds your theme, scaling policy, pages, and optional shell.
- A `UiPage` painter draws the current view and declares its controls. It runs when the view changes,
  rather than on every game frame.
- A `UiScreen` holds control values, focus, scrolling, and screen lifecycle. Open it through Minecraft
  on the client thread. Your mod still owns the key binding or menu button that opens it.
- An event handler performs actions, updates your model, and saves configuration. Painting must not
  perform network requests, save files, or change the active screen.

Keep game/server logic and persistence outside the painter. You can migrate one screen at a time;
see [switching an existing mod to PaneLib](migrating-to-panelib.md).

## A complete screen factory

Put this class in your consuming mod's client source set. It uses only the public PaneLib API and
Minecraft's `Screen` type. Replace `example.pane` with your package.

```java
package example.pane;

import dev.pane.ui.NativeOverlayRenderer;
import dev.pane.ui.UiContext;
import dev.pane.ui.UiDocument;
import dev.pane.ui.UiEventType;
import dev.pane.ui.UiPage;
import dev.pane.ui.UiRect;
import dev.pane.ui.UiScreen;
import dev.pane.ui.UiScreenOptions;
import dev.pane.ui.UiTheme;
import net.minecraft.client.gui.screens.Screen;

public final class MyModScreen {
    private MyModScreen() {}

    public static UiScreen create(Screen parent, Runnable onSave) {
        var home = new UiPage("home", "Home", "", 0xFF58A6FF, ui -> {
            UiRect body = ui.content().inset(24);
            ui.canvas().fill(ui.theme().background(), 0, 0, ui.width(), ui.height());
            ui.canvas().textMedium("Hello, PaneLib", body.x(), body.y(), 24, ui.theme().text());
            ui.button("save-button", "Save", "save",
                body.x(), body.y() + 40, 88, 32,
                UiContext.ButtonStyle.PRIMARY, UiContext.Shape.ROUNDED);
        });
        var document = UiDocument.builder("My mod")
            .theme(UiTheme.dark())
            .page(home)
            .build();
        return UiScreen.create(document, NativeOverlayRenderer.NONE, (screen, event) -> {
            if (event.type() == UiEventType.ACTION && event.key().equals("save")) {
                onSave.run();
            }
        }, UiScreenOptions.builder().parent(parent).pauseGame(true).build());
    }
}
```

From an existing client key-binding or menu-button handler:

```java
var client = net.minecraft.client.Minecraft.getInstance();
client.gui.setScreen(MyModScreen.create(client.gui.screen(), () -> {
    System.out.println("Save clicked"); // replace with your application's save operation
}));
```

This small factory demonstrates drawing, an action, and Escape returning to the previous screen.
It opts into pausing ordinary single-player play while open. The default is non-pausing; multiplayer
and worlds shared over LAN continue running.
It intentionally only logs Save. The [starter's `save()` method](../examples/first-screen/src/main/java/example/pane/FirstScreenClient.java)
shows actual configuration persistence and separates saved settings from the editable draft.

## State and redraws

A control's **id** identifies its focus/hit target; its **state key** identifies the value. They can
be the same string, but they are different roles. Keep ids stable and unique within a screen.
The `UiScreen.create` handler receives the owning screen, so actions can read/edit its values
without an array holder or mutable global. Initialize saved values with `screen.setValue("name", savedName)` before opening. Read the value in
the painter with `ui.stringValue("name", "Player")`, or in Save with
`screen.stringValue("name", "Player")`. A control's fallback argument is a default, not a reset on
subsequent paints.

Controls invalidate their own changes. Repeating an unchanged immutable scalar (`String` and boxed primitive values) with `screen.setValue(...)` does not repaint. A mutable object passed to
`UiScreen.setValue` remains an explicit update notification; immutable snapshots such as records
or `List.copyOf(...)` make your model easier to reason about, especially when also used by a HUD.
If your painter reads an external model, update that model on the client thread and call
`screen.invalidate()` when the data changes. Do not invalidate every tick to poll it.
For one changed virtual row, use `invalidateVirtualItem` instead.

## Layout and navigation

Use `ui.width()`, `ui.height()`, `ui.content()`, and the layout helpers for responsive coordinates.
`UiScaling.standard()` is the default; choose `UiScaling.fixed()` when you want fixed point sizes,
or configure a reference size on the document for an authored design. Do not multiply each control
by Minecraft's GUI scale or the monitor's pixel ratio. PaneLib maps drawing and input together.

Each `UiPage` has an id, label, icon text, accent color, and painter. A `UiShell` supplies optional
chrome around the active page. `UiSidebarShell` is available for sidebar navigation; leaving the
shell out gives you a full-bleed screen.

Call `screen.showPage("page-id")` to switch pages. `UiDocument.Builder.pageShortcuts(true)` optionally
maps keys `1` through `9` to the first nine pages. See [UI basics](ui-basics.md) for controls,
scrolling, styling, and native player/item slots, and [Minecraft integration](minecraft-integration.md)
for backdrops, parent screens, and async completion.
