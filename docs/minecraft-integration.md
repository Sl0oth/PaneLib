# Minecraft backdrop, input, close, and asynchronous data

PaneLib can own every pixel, sit transparently over Minecraft, or appear as a modal child of
another screen. Configure those lifecycle choices with `UiScreenOptions`; do not recreate them in
a mixin or screen subclass.

## Parent screen and panorama

Capture the current screen before opening PaneLib. Supplying a parent makes Escape restore it and,
unless overridden, extracts the parent's background underneath transparent parts of PaneLib.

```java
Screen parent = Minecraft.getInstance().gui.screen();

UiScreenOptions options = UiScreenOptions.builder()
    .parent(parent)
    .backdrop(UiBackdrop.PARENT_BACKGROUND)
    .build();

UiScreen screen = new UiScreen(document, overlays, (UiEventHandler) event -> {
    handleEvent(event);
}, options);

Minecraft.getInstance().gui.setScreen(screen);
```

Backdrop modes are:

- `NONE`: PaneLib extracts nothing; paint an opaque full-screen document.
- `MINECRAFT`: the current in-world treatment or title panorama/menu background.
- `PARENT_BACKGROUND`: the parent screen's background only. This is the usual choice when opening
  from the title or multiplayer screen.
- `PARENT_SCREEN`: the complete parent UI beneath PaneLib. Use it for a modal editor or picker.

The retained texture preserves alpha. To let Minecraft show through, do not cover the whole canvas
with an opaque fill. A restrained tint is enough:

```java
ui.canvas().fill(0xB80A0C10, 0, 0, ui.width(), ui.height());
ui.canvas().grain(0x10000000, .05F, 0, 0, ui.width(), ui.height());
```

This makes a screen feel embedded in the game while retaining crisp text, arbitrary geometry, and
event-driven idle rendering.

## Escape and custom back behavior

With a parent configured, ordinary Escape restores it automatically. Add a close hook only when
the mod needs to save, confirm, or redirect first:

```java
.onClose(screen -> {
    saveBrowserState();
    return false; // continue with PaneLib's normal parent restoration
})
```

Return `true` after fully handling navigation yourself. `screen.closeToParent()` is available to
buttons and custom handlers.

Dialogs and menus receive Escape before the screen close hook, so Escape closes the topmost layer
instead of unexpectedly leaving the whole screen.

## Raw keys and true double-clicks

Text editing and modal dismissal keep priority. Unhandled keys then reach `onKey`, before
Minecraft's default screen navigation:

```java
.onKey((screen, key) -> {
    if (key.key() == GLFW.GLFW_KEY_DOWN) {
        selectNextServer();
        screen.scrollVirtualItemIntoView("servers", selectedIndex);
        screen.invalidateVirtualItem("servers", previousIndex);
        screen.invalidateVirtualItem("servers", selectedIndex);
        return true;
    }
    if (key.key() == GLFW.GLFW_KEY_UP) {
        selectPreviousServer();
        screen.scrollVirtualItemIntoView("servers", selectedIndex);
        screen.invalidateVirtualItem("servers", previousIndex);
        screen.invalidateVirtualItem("servers", selectedIndex);
        return true;
    }
    return false;
})
```

`onClick` receives every mouse button, the target id from the latest retained paint, exact layout
coordinates, and Minecraft's real double-click flag:

```java
.onClick((screen, click) -> {
    if (click.doubleClick() && click.targetId() != null
            && click.targetId().startsWith("server-row:")) {
        joinServer(click.targetId().substring("server-row:".length()));
        return true;
    }
    return false;
})
```

Return `true` to consume the press. Return `false` to let the control run its normal PaneLib action.
A modal dialog blocks the raw page click handler.

## Asynchronous server data

PaneLib does not repaint every frame to poll external caches. Instead, invalidate when a request
actually completes. `invalidate`, `setValue`, and `toast` are safe to call from an asynchronous
completion thread; PaneLib queues the mutation onto Minecraft's render thread.

```java
CompletionStage<ServerStatus> request = pingServer(address);
screen.invalidateWhenComplete(request);

logoFuture.whenComplete((logo, error) -> screen.invalidate());
statusFuture.thenAccept(status -> screen.setValue("status:" + address, status));
```

Do not call `invalidate()` every tick. A group of completions before the next render still produces
one retained-surface repaint.

## Resizing

PaneLib checks the active `GuiGraphicsExtractor` viewport on every extracted frame. If macOS or a
window manager exposes a resized drawable before Minecraft sends `Screen.resize`, PaneLib closes
the stale texture and reallocates it to cover the active viewport before painting. Consumers do not
need resize callbacks or manual invalidation.

`surfacePixelWidth()` and `surfacePixelHeight()` are diagnostics for test harnesses; layout code
should use `ui.width()`, `ui.height()`, `ui.content()`, and responsive layouts instead.
