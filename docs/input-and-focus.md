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

## Editing behavior and limits

Unmodified Left/Right collapses a selection to its start/end. Textareas normalize supplied
CRLF and CR line endings to LF in stored state so editing and painted caret offsets agree.
Password fields mask their display and disable Copy/Cut; Paste and explicit deletion remain
available. Masking is not encrypted storage: consumers still own the plain-text state and its lifetime.
These changes are in the post-submission working source; the saved 0.9.8 submitted binary predates them.

Ctrl-word navigation/deletion (Option on macOS also supported) moves between locale-neutral Unicode
word segments. Left stops at a word start; Right stops at its end. Shift extends selection.
Double-click selects the clicked word or separator; masked fields select all without exposing word
boundaries. Shift-click extends from the existing anchor. PageUp/PageDown move through painted
textarea lines by a viewport with one line of overlap, preserving the preferred column.
These are not a claim of complete native platform shortcut parity. Textareas retain wheel
browsing independently of the caret and reveal it when editing or navigating. Wheel movement is
immediate, three line heights per unit; overflowing end stops consume the wheel, while a short
editor lets its parent scroll. Fields use shaped text and one bounded focused-layout cache. Full grapheme editing,
IME composition and narrator semantics are still missing.

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

## Scope and limits

Keyboard controls are available, but narrator roles/labels/announcements and IME composition are not implemented. Codepoint-safe editing does not imply complete grapheme-cluster support. Windows/Linux hardware, multiplayer and universal accessibility claims require separate validation.

## Slider keys and control coverage

Focus a slider with Tab or `screen.focus(id)`. Left/Down subtract 1% of the numeric range;
Right/Up add 1%; Page Down/Up change 10%; Home/End select the endpoints. Values clamp to the range
and emit VALUE events only when changed. Control/Alt/Command shortcuts do not change a slider.
Keys work from the declared fallback before the first pointer interaction, and respect modal scopes.
Slider rail/thumb colors follow the theme's raised surface and text colors.

| Input capability | Current scope |
| --- | --- |
| Buttons, toggles, radio/segmented choices | Tab focus and Enter/Space activation |
| Sliders | Tab, arrows, Page Up/Down, Home/End |
| Fields/textareas | Caret, selection, clipboard, undo/redo, Unicode codepoint boundaries |
| Custom painted/virtual list rows | Consumer supplies ids/actions and application selection policy |
| Narrator roles, labels, values and announcements | Not implemented; keyboard support is not narrator support |
| IME composition and grapheme-cluster editing | Not implemented; use cases requiring these need another input path |

## Multiline and popup keyboard navigation

Textareas support Up/Down through painted lines, preserving the preferred horizontal position across short lines. Shift extends the selection. Home/End select the current visual line's start/end; Ctrl/Cmd-Home/End select the whole value's endpoints. Soft-wrapped lines use the same caret positions as pointer input.

Open menus and dropdowns support Up/Down (wrapping), Home/End, Tab/Shift-Tab, Enter/Space to select, and Escape to dismiss. Keyboard selection or dismissal restores focus to the trigger. These controls still do not provide narrator semantics or IME composition.

Ordinary scroll containers may be nested inside translated groups. Their wheel targets and child hit regions follow the painted coordinates and ancestor clipping; clipping does not alter the original scroll range.

Dropdowns initially focus the stored choice whether opened with a mouse or keyboard. Up/Down move from that choice; reopening uses the most recently selected value. Action menus have no stored choice and begin navigation at the first/last item.

## Keyboard access through scrolling forms

Tab and Shift-Tab visit controls registered by an ordinary `ui.scroll` painter, including off-screen ones. Focusing a control scrolls its ancestors from inner to outer to reveal it, without animating past intermediate controls. Pointer targets remain clipped. Input and activation work immediately after focus, even before the next paint. Controls outside the declared content extent, hidden layers, and modal-blocked controls remain excluded. Keep control ids unique and stable. Declare a `contentHeight` that covers every control completely; a control extending past that extent cannot be fully revealed.

Virtual lists only register their painted rows. A consumer controls selection across unpainted virtual rows using `scrollVirtualItemIntoView`; Tab does not materialize every row in a large virtual list.

Textareas wrap at Unicode line-break opportunities, keeping ordinary words intact. Oversized words fall back to codepoint boundaries; this is not grapheme-aware editing. Explicit newlines and whitespace remain in the value. At a soft boundary, End uses the end of the preceding visual line and Home uses the current visual line; pointer placement preserves that distinction. Right/Left move a codepoint rather than spending a keypress switching visual sides. End includes any trailing spaces at the shared wrap boundary, and Shift-End selects them. Text inserted there uses that logical position and may reflow onto the next visual line.

Text keys should be absent or contain a String. Painting preserves values of other types, but
editing replaces them with text; do not bind a text control to an object-valued key. Textareas
canonicalize CRLF and CR to LF without emitting a value event. Known textarea keys normalize
on `UiScreen.setValue`; keys not yet painted normalize on their next paint or edit. Keep external
state synchronized through actual edit events rather than expecting normalization to emit one.

Consumer controls and custom tooltip rectangles must not use these built-in interaction IDs:
`dialog-confirm`, `dialog-cancel`, `dialog-body`, `dialog-backdrop`, the form `menu:<menu-id>:<item-id>`,
or prefixes `menu-body:`, `toast-dismiss:`. A two-part ID such as `menu:settings` remains valid. Registration throws `IllegalArgumentException` with the offending
ID, even for clipped targets. Use application-specific names such as `settings:confirm`.
These restrictions apply to control IDs, not state keys or consumer action strings.

Menus and built-in dialogs composite from a cropped retained texture above native overlays and
custom native-cover layers. They do not duplicate the page texture. Dialog backdrops dim native
content; the existing backdrop blur applies to Pane's page chrome, not native items/entities.
In a native overlay callback, check `overlay.pointerBlocked()` before showing background hover
feedback or tooltips. The flag covers menus, dialogs and custom focus scopes; entity previews
still receive the actual pointer position. Custom code using raw graphics must honor this flag.

`pointerBlocked()` is conservative for custom focus scopes: it blocks background native feedback.
If your own modal scope contains native slots, decide whether a slot belongs to that modal and
whether the pointer is within its bounds before showing its tooltip. The flag does not identify
individual native slots as members of a scope.

Popup textures reuse unchanged visual content during background animation. Hover, focus and wheel
updates repaint the popup independently. Dialog blur processes the dirty clip plus kernel padding;
its two temporary buffers are reused for equal-size regions and released on dialog close, page
change, or after half a second without blur work. Consumer calls to `backdropBlur` use temporary
storage and do not retain the dialog workspace.

Built-in dialogs wrap title and message in a shared scrollable copy viewport while keeping actions
visible. Wheel, Page Up/Down and Home/End browse copy; Tab follows the visual Cancel/Confirm order.
Long actions stack and their labels ellipsize within the button. Explain consequential actions in
the dialog copy rather than relying on a very long button label. Focus and hover have visible
styling. Extremely small windows clip text but retain bounded geometry and keyboard actions.

```java
UiDialogStyle dialogStyle = UiDialogStyle.builder()
    .maxWidth(480)
    .padding(20)
    .maxHeightFraction(.85F)
    .build();
```

These layout settings are validated, cached with the dialog content and clamped to the viewport.
They add no new surface or idle repaint loop. `dialogScrollSnapshot()` exposes the copy viewport
and offset for integration tests. Closing or reopening a dialog resets its copy scroll.

### Textarea browsing lifecycle

`screen.editorScrollSnapshot(id)` reports the last painted control bounds and current independent
vertical offset. Read it on the client thread. Wheel browsing preserves the caret and consumer
value, stays within the editor, and performs no paint when already at an end stop. A subsequent
edit or navigation reveals the caret; clicking a visible line uses its displayed position.

Blur preserves browsing. Keyboard refocus reveals the caret. Width/typography changes clamp and
reveal when focused; background value changes on an unfocused editor only clamp. Page changes
and removal clear editor offsets. Cached virtual rows retain them until those rows are recycled.
No new texture, smoothing animation or idle timer is allocated for editor browsing.

Full grapheme/IME/RTL editing, Page Up/Down navigation, held-pointer edge autoscroll, and revealing
a caret through a parent shorter than the textarea remain tracked follow-ups.
