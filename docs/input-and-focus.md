# Styled inputs, editing, and modal focus

Use `UiInputStyle` when an application's text fields need the same explicit typography as its
other drawing. Existing overloads still work. The style changes font size, weight, family,
padding, and multiline spacing together with caret measurement, selection, pointer placement,
clipping, wrapping, and caret-following scroll.

```java
UiInputStyle inputStyle = UiInputStyle.defaults()
    .font(UiCanvas.MONO, 14, java.awt.Font.PLAIN)
    .padding(12, 6)
    .lineHeight(22);
String name = ui.textField("name", "name", "Project name",
    24, 80, 320, 38, "", 120, inputStyle);
String notes = ui.textArea("notes", "notes", "Notes",
    24, 130, 320, 120, "", 4000, inputStyle);
```

The same final style parameter is available on the explicit-height `passwordField` overload.
A null family inherits `UiTheme.fontFamily()`. Multiline spacing is at least the font metric
height; text and carets stay clipped when a control is shorter than its text. `UiCanvas.textHeight`
complements `textWidth` for custom layout. Public lengths and caret indices use UTF-16 units,
but pointer positions, navigation, deletion, and truncated insertion never split a surrogate pair.
This is codepoint safety, not full grapheme-cluster or IME composition support.

## Editing and undo

Native fields and textareas support Ctrl/Cmd-Z, Ctrl/Cmd-Shift-Z, and Ctrl/Cmd-Y. Each edit stores
its prior text and selection. An actual programmatic `setValue` replacement clears that key's
history; setting an identical value preserves it. Page changes and screen removal release histories.
At most sixteen input-key histories are retained per screen; each stack stores at most 32 edits
and 65,536 UTF-16 units. Oversized snapshots are not kept. This is plain text editing, not a syntax
editor, terminal, full document model, or unbounded history.

Custom editors can use the same public `UiTextHistory` model independently:

```java
UiTextHistory history = new UiTextHistory(32, 65_536);
history.record(new UiTextHistory.Snapshot("old", 3, 3),
               new UiTextHistory.Snapshot("new", 3, 3));
var previous = history.undo(new UiTextHistory.Snapshot("new", 3, 3));
```

## Programmatic focus and custom modals

`screen.focus(id)` returns false if a control has not been painted or is outside the active
focus scope. `screen.clearFocus()` clears keyboard focus without changing field contents.
These APIs run on the client thread.

A custom sheet needs an explicit focus scope; drawing a dark backdrop alone does not establish
modal behavior. Open it in an application event callback, not inside the painter:

```java
screen.setValue("sheet-visible", true);
screen.pushFocusScope("rename-sheet", List.of("rename", "cancel", "save"), "cancel", () -> {
    screen.setValue("sheet-visible", false);
    screen.popFocusScope("rename-sheet");
});
```

Paint those same retained control ids while `sheet-visible` is true. On Save or Cancel, hide the
sheet and pop its scope. The callback is a consumer-authored example; library code does not
perform file writes or permissions decisions. Escape is consumed and invokes the provided
callback; the overload without one consumes Escape without dismissing the sheet.

The scope blocks background typing, submit, pointer actions, scroll, and page shortcuts
**immediately**, even before the sheet's first paint. Initial focus is applied after that paint.
Tab and Shift-Tab follow the eligible controls' paint order. The list is an id whitelist, not
an alternate order. Include a scroll viewport's id if that modal owns a scroll region. Menus
opened by an eligible trigger may navigate their own popup items.

The normal `UiScreenOptions.onKey` and `onClick` handlers are bypassed while a custom scope is
active. Use scoped Pane controls and the ordinary event listener for modal actions. This avoids
running old application-wide background shortcuts; arbitrary custom keyboard handlers are not
automatically scoped. `focusScopeId()` exposes the active scope for application-owned integration.

Nested scopes close in reverse order. Closing restores the prior eligible focus and selection,
clamped to the current value, using the last painted controls. A target absent at restore remains
pending until the next paint, then is discarded if still absent. Page changes and screen removal
discard scopes. Applications own sheet visibility: close/hide a sheet before navigation, and
reestablish a scope before accepting input if reusing a screen with a visible custom sheet. Close a built-in
dialog before opening/popping a custom scope around it.

Built-in confirmation dialogs also block stale background input immediately, default to Cancel,
and restore their trigger's focus when closed. This changes the previous close behavior, which
cleared focus. A modal built from custom drawing remains distinct from a built-in `UiDialog`.

## Scroll and raster diagnostics

```java
screen.scrollViewport("results").ifPresent(bounds -> { /* layout units */ });
screen.scrollSnapshot("results").ifPresent(scroll -> {
    double shown = scroll.offset();
    double destination = scroll.targetOffset();
});
UiRasterQuality quality = screen.rasterQuality();
boolean samplingBelowPhysicalTarget = quality.upscaled();
```

Scroll bounds are from the most recent paint and snapshots contain current displayed/target
offsets. They expose no mutable renderer handles or text content. Before the first paint a
viewport is absent. Raster diagnostics include the last texture dimensions, layout dimensions,
desired physical scale, and actual raster scale. An undersampling flag is a quality diagnostic,
not an FPS, sharpness, or GPU-memory measurement.

`UiRenderSettings.typography()` is an **opt-in** 5120×3200 / 4x ceiling. A full-size RGBA buffer
alone is 62.5 MiB; CPU, native staging, and GPU copies add to that. Defaults are unchanged. Choose
it only with suitable memory headroom; apps can keep `lowMemory()` and inspect `upscaled()`.
It does not fix small layouts: pair the desired quality ceiling with an appropriate `UiScaling`
recipe, such as `UiScaling.standard().range(1, 2.5F)`.

Minecraft mouse events arrive in Minecraft GUI units, not necessarily operating-system window
points. Multiply event x/y by `screen.layoutScale()` to obtain Pane layout coordinates; divide
Pane `regionBounds`/`scrollViewport` coordinates by that value when generating a GUI event.
On Retina, a GUI scale that happens to match the display scale can hide this distinction.
Framebuffer pixels, OS points, Minecraft GUI units, and Pane layout units are separate spaces.

Native Minecraft items/entities remain native overlays drawn after retained content. Use the
existing native-overlay layer/cover integration for popups rather than assuming Java2D drawing
will cover every later native item. See [Minecraft integration](minecraft-integration.md).

## Validation and scope

The 2026-10-17 integration follow-up promotes general needs from Peripheral's MC 26.1.2
compatibility snapshot into the current PaneLib MC 26.2 checkout. Peripheral itself was not
modified. Its app transport, GUI polling, script state, JSON, native game actions, and package
changes are not classified as library bugs.

Input geometry/history tests, public-API client assertions, screenshots, and existing regressions
are recorded in `build/api-improvements/`. Run the title-only public-API fixture with:

```sh
./gradlew :pane-demo:runClient -Pisolated -PapiTest
```

This is local macOS client evidence. Narrated label/role/state support and direct Minecraft
narrator testing remain deferred; keyboard checks are not screen-reader certification. Menu
font styling, richer layout/wrapping helpers, custom list/slider keyboard parity, and a full code
editor remain separate work. No Windows/Linux, multiplayer, high-FPS, or universal accessibility
claim follows from these API checks.
