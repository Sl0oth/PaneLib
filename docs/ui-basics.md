# PaneLib UI basics

## State and events

Controls keep their state on the `UiScreen`. Give each control a stable id and state key, then
read the current value while painting. The event listener receives the change so your mod can
persist it or react to it. Scroll positions are separate internal state and do not emit consumer `VALUE` events.
Repeated equal immutable scalar values (`String` and boxed primitive values)
do not repaint a `UiScreen`. Mutable objects passed through `UiScreen.setValue` remain explicit
update notifications. Prefer immutable snapshots for complex data; external model changes still
need `screen.invalidate()` or a targeted row invalidation.

```java
boolean compact = ui.booleanValue("compact-mode", false);
ui.toggle("compact-toggle", "compact-mode", x, y, compact);

double volume = ui.numberValue("volume", 0.6);
ui.slider("volume-slider", "volume", x, y + 40, 240, 0, 1, volume);
```

The six-argument slider stores a normalized value from `0` to `1`. The eight-argument overload
shown above stores a value between the supplied minimum and maximum and returns the current value.

`UiContext.selectable(...)` is the base for tabs, filter chips, and radio groups. Read it back
with `ui.selected(key, value)` or `ui.stringValue(key, fallback)`.

PaneLib's internal state actions are isolated. Consumer actions may contain text such as
`page:`, `toggle:`, or `menu:` without colliding with built-in controls. Prefer the typed event
constructor when starting a new mod:

```java
UiScreen.create(document, nativeOverlay, (screen, event) -> {
    if (event.type() == UiEventType.VALUE && event.key().equals("volume")) {
        setVolume(event.numberValue(0.5));
    }
});
```

### Scroll position API

Use `screen.scrollTo("settings", offset)` to move an ordinary or virtual scroll viewport. Offsets are layout units, clamped to the content extent. A request before first paint is clamped when the viewport registers. Worker calls are marshalled to the client thread. Read `screen.scrollSnapshot("settings")` for displayed and target offsets.

Migration from earlier 0.9.8 candidates: `setValue("scroll:<id>", ...)` no longer scrolls, and wheel actions no longer emit `VALUE` events named `scroll:<id>`. Those keys now belong entirely to the consuming mod. Use `scrollTo` and snapshots instead; filter/persist your application's own fields explicitly. Same-id scroll offsets persist across pages within a screen.

### Append content and follow the end

Use `scrollToEnd` immediately after changing the model. PaneLib resolves the request when the
viewport registers its **new** content height, before drawing content, hit targets or virtual rows.
Do not wait for a tick/repaint, store a rendered-message count, or use an oversized numeric offset.

```java
// On a local send, on the client thread:
messages.add(message);
screen.scrollToEnd("history:" + conversationId); // also requests the repaint

// During painting, with the freshly measured wrapped content height:
ui.scroll("history:" + conversationId, viewport, contentHeight,
    UiScrollFollow.WHEN_AT_END, history -> paintMessages(history, messages));

// Incoming data: repaint, allowing the policy to decide whether to follow.
messages.add(incoming);
screen.invalidate();
```

`scrollToEnd(id)` is a one-time, immediate position request, not a smooth scrolling animation.
It works before first paint. If the viewport is absent, it waits for registration without an idle
paint loop. Multiple requests coalesce. `scrollTo`, wheel navigation, or keyboard/virtual-row reveal
that moves the viewport cancel an older pending request. Page changes and screen removal cancel
unfinished requests; offsets still retain their existing per-ID semantics. Worker calls to
`scrollToEnd` are marshalled to the client thread, but your mutable model must also be updated safely.

`WHEN_AT_END` follows content growth and viewport resizing only when the previous scroll position
was at the end (within half a layout unit). While wheel motion is in flight, its intended target
decides attachment: scrolling away detaches immediately, before the first interpolated frame;
scrolling back to the end reattaches. Reading older content preserves the numeric offset, clamped
if filtering removes that range. A viewport whose content entirely fits is considered at the end,
so it follows if that content later grows. This is not item-identity anchoring across arbitrary
reordering or text reflow. First registration starts at the top unless an explicit end request exists.

The existing overloads default to `UiScrollFollow.NONE`. The same optional policy is available for
virtual lists, whose empty state now registers a zero extent:

```java
ui.virtualList("log", viewport, entries.size(), 24, 2,
    UiScrollFollow.WHEN_AT_END, (row, item) -> paintEntry(row, entries.get(item.index())));
```

Use separate stable IDs for different conversations/datasets. For example, Gather uses
`history:general` and `history:dm:Maya`; selecting a channel explicitly scrolls its own ID to zero,
which also cancels any old pending command for that channel. Ordinary and virtual views share the
same end-scrolling behavior. No scrollbar-specific controller or consumer timer is needed.

## Fonts

Pane defaults to a dependable native sans-serif face. To make every built-in control and overlay
match a product's type system, bundle a `.ttf` or `.otf` with your mod and register it once during
client setup. The font stays inside Pane's renderer; it does not install anything on the player's
computer.

```java
try (InputStream input = MyClient.class.getResourceAsStream(
        "/assets/my_mod/fonts/MyFont-Regular.ttf")) {
    UiCanvas.registerFont("my-ui-font", input);
}

UiTheme theme = UiTheme.dark().withFontFamily("my-ui-font");
```

Pass the same family to custom canvas text when you want hand-drawn labels to match:

```java
ui.canvas().text("A matching heading", x, y, 18, ui.theme().text(), Font.BOLD,
    ui.theme().fontFamily());
```

If loading fails, Pane safely falls back to its normal sans-serif font. Include the font's license
next to the resource when you redistribute it. Pane Mail, Gather, and Wayfarer share the bundled
Roboto family under the SIL Open Font License.

## Minecraft-formatted text

Ordinary `text(...)` stays literal. Use `UiRichText` only for values that are allowed to contain
formatting, such as a server MOTD. Prepare the value when a response or dataset changes and reuse
it from the row painter:

```java
UiRichText motd = UiRichText.component(serverStatus.description());

// Later, from a retained or virtual-row painter:
ui.canvas().formattedText(motd, x, y, 8, ui.theme().mutedText(), ui.theme().fontFamily());
```

`component(...)` preserves Minecraft component colors, including arbitrary RGB, plus bold,
italic, underline, strikethrough, obfuscation, explicit newlines, and Unicode symbols. For APIs
that return old-style strings, choose the parser explicitly:

```java
UiRichText nativeCodes = UiRichText.legacy("§aOnline §7— §lSurvival");
UiRichText webApiCodes = UiRichText.legacyAmpersand("&aOnline &7— &lSurvival");
UiRichText literal = UiRichText.plain("AT&T and §a stay visible");
```

Ampersands are recognized only when followed by a valid formatting code. Normal Pane labels never
interpret either marker. For a fixed-width row, preserve styles while preparing an ellipsized copy:

```java
UiRichText rowMotd = ui.canvas().ellipsizeFormatted(motd, availableWidth, 8,
    ui.theme().fontFamily());
```

Cache that result with the row's other prepared strings when its width is stable. Pane uses its
high-quality retained font rasterizer; custom Minecraft resource-pack font providers are not
silently substituted for the document font.

## Minecraft texture and skin fills

Use `minecraftTexture` to cover-fill a shape from Minecraft's active resource manager. It reads
the same resource packs the player is using and samples with nearest-neighbor filtering, so block
pixels stay clean when enlarged. Put the draw call inside a normal Pane clip to make a rounded
texture card or circle.

```java
Identifier ore = Identifier.withDefaultNamespace("textures/block/diamond_ore.png");

ui.canvas().withRoundedClip(x, y, 220, 116, 14, () ->
    ui.canvas().minecraftTexture(ore, x, y, 220, 116)
);
```

`minecraftTextureRegion` draws a specific source crop. That is useful for player skins; the
vanilla head is at `(8, 8, 8, 8)` and the optional hat layer is `(40, 8, 8, 8)`.

```java
Identifier skin = Minecraft.getInstance().player.getSkin().body().texturePath();
ui.canvas().withCircleClip(cx, cy, 32, () -> {
    ui.canvas().minecraftTextureRegion(skin, 8, 8, 8, 8, cx - 32, cy - 32, 64, 64);
    ui.canvas().minecraftTextureRegion(skin, 40, 8, 8, 8, cx - 32, cy - 32, 64, 64);
});
```

Both methods return `false` instead of crashing when a texture is not ready. Resource-pack reloads
clear decoded images automatically and refresh retained screens/HUDs on their next rendered frame.
For a dynamic texture changed under the same identifier, upload its new pixels first, then call
`UiCanvas.invalidateMinecraftTexture(id)`. This drops that decoded image and schedules refresh by
revision; it does not upload your texture. `clearMinecraftTextureCache()` remains available for
bulk changes. Invalidations between frames coalesce into one repaint.

If you cache image drawing in your own `cachedLayer`, include
`UiCanvas.minecraftTextureRevision()` in that layer's revision key (alongside your own content
revision). PaneLib cannot replace consumer-owned cached content automatically. Custom AWT fonts
registered through `registerFont` remain consumer-owned; Minecraft font-provider reloads do not
replace them. The `T` reference screen demonstrates Minecraft textures in a real world.

## Layout and scrolling

Use `UiLayout` to reserve rectangles instead of hand-calculating a long vertical form. Its
`contentHeight()` tells a scroll container exactly how much content it owns.

```java
UiRect viewport = new UiRect(x, y, width, height);
UiLayout form = ui.column(new UiRect(x, y, width, 0)).gap(8);
UiRect account = form.next(96);
UiRect notifications = form.next(132);

ui.scroll("settings", viewport, form.contentHeight(), page -> {
    page.card(account.x(), account.y(), account.width(), account.height());
    page.toggle("email-alerts", "email-alerts", account.right() - 44, account.y() + 48, true);
    page.card(notifications.x(), notifications.y(), notifications.width(), notifications.height());
});
```

For long, fixed-height data sets, use `virtualList`. PaneLib keeps a bounded set of visible and
overscan rows in one GPU atlas. A scroll frame changes their GPU positions without rerasterizing
the page chrome, and only newly exposed or changed rows upload pixels. All rows move through one
shared fractional GPU transform, so text baselines and separators keep constant spacing instead of
independently rounding up or down. Guard pixels around each atlas slot prevent cross-row filtering:

```java
ui.virtualList("servers", viewport, servers.size(), 42, 1, (list, item) -> {
    ServerEntry server = servers.get(item.index());
    paintServerRow(list, item.bounds(), server);
});
```

When asynchronous data changes one row, invalidate that row rather than the screen:

```java
fetchPing(server).whenComplete((ping, error) -> {
    if (error == null) server.setPing(ping);
    screen.invalidateVirtualItem("servers", rowIndex);
});
```

`invalidateVirtualItem` is safe from a network or image-loading thread. A visible row is
rerasterized and partially uploaded; an off-screen row is painted later if it enters the viewport.
Use `screen.invalidate()` only when the change affects page chrome, layout, or many rows.

For consumer-owned arrow selection, reveal the newly selected index without duplicating viewport
math or starting a second scrolling implementation:

```java
selectedIndex = Math.max(0, Math.min(servers.size() - 1, selectedIndex + direction));
screen.scrollVirtualItemIntoView("servers", selectedIndex);
screen.invalidateVirtualItem("servers", previousIndex);
screen.invalidateVirtualItem("servers", selectedIndex);
```

Tune scroll distance and finite smoothing once for the document:

```java
UiDocument.builder("Server browser")
    .scrollSettings(new UiScrollSettings(46, 105))
    // ...
```

Use `UiScrollSettings.immediate()` when reduced-motion behavior should have no interpolation.
Smooth scrolling follows a continuously moving target; repeated macOS trackpad deltas never
restart the animation or accumulate a delayed post-release jump. During kinetic motion PaneLib
keeps moving hit targets current, suppresses pointless hover flicker across passing rows, and
restores the correct hover when motion settles.

`retainedTextureAllocationCount()`, `virtualAtlasAllocationCount()`, and
`virtualRowBufferAllocationCount()` are test diagnostics. Their deltas should all remain zero while
a warmed virtual list scrolls; normal screen opening and resizing are allowed to allocate.

For a responsive card collection, the parameters after the bounds are minimum cell width, row
height, and gap. Use `contentHeight(itemCount)` as the scroll extent:

```java
UiGrid cards = ui.responsiveGrid(viewport, 180, 112, 12);
for (int index = 0; index < projects.size(); index++) {
    UiRect card = cards.cell(index);
    drawProject(ui, projects.get(index), card);
}
```

Drawing and controls inside the callback keep normal layout coordinates. Pane clips them, moves
them with the scroll offset, and keeps mouse hit targets aligned. The Settings demo is the live
working example.

## Keyboard focus and text fields

For explicit input fonts, bounded undo/redo, programmatic focus, modal focus scopes, and
viewport/quality diagnostics, see [styled inputs and focus](input-and-focus.md).

Click a field or use Tab to focus it. Shift-Tab moves backwards. Built-in controls show a focus
ring; Enter and Space activate the focused button, toggle, or selectable control.

```java
String query = ui.textField(
    "project-search", "project-search", "Search projects...",
    x, y, 280, "", 80
);
```

Text fields are single line. They support typing, keyboard and pointer selection, click-to-place,
drag selection, double-click selection, left/right arrows, Home/End, backspace/delete, and
standard copy, cut, and paste shortcuts. Enter emits a `submit` event for the field's state key.

`textField` returns the current string. Use the full overload when you need an explicit height:

```java
String name = ui.textField("name", "name", "Project name", x, y, 280, 34, "", 80);
```

```java
if (ui.focused("project-search") && !query.isBlank()) {
    drawSuggestions(query);
}
```

Search results, autocomplete, and fuzzy corrections belong to the consuming mod. Pane provides
the input primitive; the Discover demo uses it to offer “Sodium” when someone types `soduim`.

Use `passwordField` when the stored value should be painted as bullets:

```java
ui.passwordField("api-token", "api-token", "Paste token", x, y, 260, "");

String limitedToken = ui.passwordField(
    "limited-token", "limited-token", "Paste token",
    x, y + 40, 260, 34, "", 128
);
```

For notes and descriptions, `textArea` provides soft wrapping, multiline paste, Enter for a new
line, selection, and the same state/event behavior:

```java
String notes = ui.textArea("notes", "notes", "Add notes...", x, y, 320, 110, "", 2000);
```

## Common controls

PaneLib provides ordinary form primitives so a consumer does not have to paint and wire them by
hand:

```java
ui.checkbox("snap", "snap", "Snap panes to grid", x, y, 220, true);
ui.radio("gap-6", "pane-gap", "6", "6 px", x, y + 28, 80, false);
ui.radio("gap-10", "pane-gap", "10", "10 px", x + 90, y + 28, 80, true);

String section = ui.tabs("settings-tabs", "settings-section", List.of(
    new UiSelectOption("appearance", "Appearance"),
    new UiSelectOption("motion", "Motion")
), x, y + 58, 280, 32, "appearance");
```

`iconButton` accepts `UiIcon.CHECK`, `CLOSE`, `PLUS`, `MINUS`, `SEARCH`, `CHEVRON_DOWN`,
`ARROW_RIGHT`, `MENU`, `HOME`, `INFO`, `WARNING`, `PLAY`, `PAUSE`, `MORE`, `COPY`, `BOOKMARK`,
`COLUMNS`, or `TRASH`. Use `UiCanvas.path()` for a brand-specific icon. `badge` and `progress` are
intentionally non-interactive display primitives. `tag` paints an optional removable pill and
returns its measured width for flow layout.

```java
float nextX = x;
nextX += ui.tag("client-tag", "client", "remove-client-tag",
    nextX, y, 22, 0xFF1F2B26, 0xFF9BE7BD) + 6;
ui.tag("stable-tag", "stable", null,
    nextX, y, 22, 0xFF25282D, ui.theme().mutedText());
```

A `null` remove action makes the tag display-only. For a removable tag, handle its normal action
event and stop painting it after your state says it was removed.

## Tooltips

Tooltips are short, native hover descriptions. They wait briefly before appearing, stay inside
the screen, and choose above or below the anchor so they do not cover the control being explained.
The default delay is 450ms. Set it per document; use `0` for an immediate tooltip.

PaneLib composites the tooltip from a small retained overlay texture. Showing or hiding one does
not repaint the page or move a `virtualList` row between raster paths, so row text remains
pixel-stable through hover.

```java
var document = UiDocument.builder("My mod")
    .tooltipDelayMillis(700)
    // pages, theme, and shell...
    .build();
```

Call `tooltip` after the control it describes:

```java
ui.button("sync", "Sync now", "sync", x, y, 88, 30,
    UiContext.ButtonStyle.PRIMARY, UiContext.Shape.ROUNDED);
ui.tooltip("sync", "Check for updates now.");
```

For a custom-drawn shape that has no click action, give Pane its painted rectangle. It becomes
hoverable for the tooltip but is not clickable or part of Tab traversal.

```java
ui.canvas().circle(ui.accent(), x + 10, y + 10, 7);
ui.tooltip("connection-dot", "Connected to the local server.", x + 3, y + 3, 14, 14);
```

## Menus

`menuButton` gives a normal-looking button a native popup menu. The popup anchors to the button,
stays inside the screen, and its item action is delivered through the usual `UiEventListener`.
It closes after a choice, when the user clicks away, or when they press Escape.
When a menu was opened with the mouse, those dismissals also clear its mouse focus ring. A
keyboard-opened menu keeps focus on its trigger so the player can continue navigating by keyboard.

```java
ui.menuButton("waypoint-actions", "Actions", x, y, 82, 30,
    UiContext.ButtonStyle.SECONDARY, UiContext.Shape.PILL,
    List.of(
        new UiMenuItem("copy", "Copy coordinates", "copy-waypoint"),
        new UiMenuItem("share", "Share with party", "share-waypoint")
    ));
```

Keep menu item ids unique within that menu. Their action strings are your mod's normal action
strings; Pane does not own or interpret them.

## Styling popup triggers

Popup content and the control that opens it are independently styled. Use `UiButtonStyle` for
`menuButton`, `dropdown`, and `dialogButton` triggers. Existing enum-style overloads remain available.
For example, build these immutable styles once and reuse them:

```java
UiButtonStyle trigger = UiButtonStyle.builder()
    .fontFamily("My bundled font").fontStyle(Font.PLAIN).textSize(14)
    .background(0xFF232629).hoverBackground(0xFF34383C)
    .pressedBackground(0xFF171A1D).text(0xFFF2F4F7)
    .radius(3).borderWidth(1).build();
UiMenuStyle popup = UiMenuStyle.builder()
    .fontFamily("My bundled font").textSize(14).rowHeight(34).build();

ui.menuButton("actions", "Server actions", x, y, 180, 36,
    trigger, enabled, items, popup);
ui.dropdown("role", "selected-role", x, y + 46, 180, 36,
    "member", options, trigger, enabled, popup);
ui.dialogButton("deploy", "Ready to explore", x, y + 92, 220, 48,
    trigger, enabled, confirmation);
```

Omit `enabled` to use the enabled convenience overload. A selector inherits the theme's surface,
text color, and font family when the corresponding trigger fields are unset; its default font
weight is plain. Explicit trigger typography measures and draws the selected label consistently,
vertically centers it, and clips/ellipsizes long labels before the arrow. The returned/bound value
is unchanged. `UiMenuStyle` still controls only popup rows, and `UiDialogStyle` controls only the
confirmation body, including its typography.

For textures, icons, asymmetrical headings, or other authored controls, use `paintedMenuButton`,
`paintedDropdown`, or `paintedDialogButton` instead of painting over an empty built-in label:

```java
ui.paintedMenuButton("server", "Trailside server menu", x, y, width, height,
    3, enabled, items, popup, (canvas, state) -> {
        canvas.roundRect(state.pressed() ? 0xFF252528
            : state.hovered() ? 0xFF161619 : 0xFF08080A, x, y, width, height, 3);
        canvas.text("Trailside", x + 12, y + 10, 14, 0xFFFFFFFF,
            Font.BOLD, "My bundled font");
        UiIcon.CHEVRON_DOWN.draw(canvas, 0xFFAAAAAA, x + width - 20, y + 12, 8, 1);
    });
```

The painter receives `UiButtonState`: label, enabled, hovered, pressed, and focused. PaneLib applies
disabled opacity once, draws the focus ring after your painter, and owns activation, anchoring,
selection, dismissal and focus restoration. Draw the control inside this callback; do not add a
second hit region or emit internal action strings. Use `ui.animate` with bounded repaint geometry
inside the painter if you want eased effects; boolean state changes alone do not start a timer.
`paintedDialogButton` takes a `UiDialog` before the painter. `paintedDropdown` follows the dropdown's
ID/key/geometry/fallback/options arguments, then takes radius, enabled, popup style and painter.
Its painter state's label is the selected option's display label (or unmatched value; an empty
unmatched value uses the ID as a semantic fallback).

New menu/dialog triggers require descriptive labels, exposed through `screen.buttonState(id)` and
normal tooltips. This is semantic metadata, not automatic narrator support. Disabled triggers have
no pointer/keyboard targets or popup registration; disabling an open trigger and invalidating the
screen closes its popup on the next paint. A menu with no items is drawn disabled. Dropdowns with
no options retain the existing no-control behavior. Translations, clipped scroll areas, affine
button groups and retained virtual rows use the existing shared input/composition paths.

These APIs add no polling loop or independent textures. Painter callbacks run when their retained
page/row needs painting, not once per game frame. Keep painters side-effect-free and reuse styles
when their values do not change. See Gather's server heading/role selector, Wayfarer's deploy button,
and the video example's icon menus for migrations.

## Dropdowns

`dropdown` is for one stored value chosen from a compact list. It uses the same safe anchoring
and dismissal behavior as menus, but updates the state key and emits a `value` event instead of
an action event.

```java
String accentPreset = ui.dropdown("accent-preset", "accent-preset", x, y, 140, 30,
    "system", List.of(
        new UiSelectOption("system", "System accent"),
        new UiSelectOption("ocean", "Ocean blue"),
        new UiSelectOption("forest", "Forest green")
    ));
```

The returned value and `ui.stringValue("accent-preset", "system")` stay in sync. The Settings
showcase contains the live example.

## Confirmation dialogs

`dialogButton` opens a native modal confirmation dialog from an ordinary-looking button. While it
is open, it sits above the page and receives pointer input first. Confirm and cancel actions use
your normal `UiEventListener`; Escape and clicking the backdrop use the cancel action.

```java
ui.dialogButton("delete-profile", "Delete", x, y, 76, 30,
    UiContext.ButtonStyle.DANGER, UiContext.Shape.PILL,
    new UiDialog(
        "Delete this profile?",
        "This cannot be undone.",
        "Delete", "delete-profile",
        "Cancel", "cancel-delete-profile"
    ));
```

Use this for an action that matters. Do not use it for normal navigation or a reversible setting;
a menu, toggle, or toast is less disruptive there. The Waypoints showcase has a live Reset
example.

For a dialog that matches a custom screen, pass a `UiDialogStyle` as the final constructor
argument. Every color and the panel radius can be overridden; anything omitted still follows the
document theme.

```java
UiDialogStyle danger = UiDialogStyle.builder()
    .backdrop(0xBB02080D)
    .surface(0xFF0B1D29).border(0xFF5BAFCB)
    .titleColor(0xFFD8F4FF).messageColor(0xFF8AB4C3)
    .cancelBackground(0xFF153344).cancelBorder(0xFF35667B).cancelText(0xFFD8F4FF)
    .confirmBackground(0xFFB9414F).confirmText(0xFFFFFFFF)
    .radius(2)
    .build();

var dialog = new UiDialog("Hold sequence?", "The current vector will be preserved.",
    "Hold", "hold-sequence", "Continue", "continue-sequence", danger);
```

### Dialog fonts and text layout

Bundle a TTF or AWT-compatible OTF in your mod resources and register it once during client setup.
Use an alias owned by your mod; registration does not download fonts or depend on fonts installed
on the player's computer. Check the boolean result so a missing or invalid asset is visible during
development. `registerFont` closes the supplied stream.

```java
boolean loaded = UiCanvas.registerFont("my-mod:heading",
    MyClient.class.getResourceAsStream("/assets/my_mod/fonts/Heading.ttf"));
if (!loaded) throw new IllegalStateException("Could not load bundled heading font");

UiDialogStyle typography = UiDialogStyle.builder()
    .fontFamily(UiCanvas.SANS)
    .titleFontFamily("my-mod:heading")
    .titleSize(24).messageSize(14).buttonSize(13)
    .titleFontStyle(java.awt.Font.BOLD)
    .messageFontStyle(java.awt.Font.PLAIN)
    .buttonFontStyle(java.awt.Font.BOLD)
    .titleLineSpacing(3).messageLineSpacing(5)
    .maxWidth(480).padding(20)
    .build();
```

`fontFamily` sets a common family/registered alias. `titleFontFamily`, `messageFontFamily`, and
`buttonFontFamily` override each group independently. An unset group inherits the common family,
then the document theme. `Font.PLAIN`, `Font.BOLD`, `Font.ITALIC`, and `Font.BOLD | Font.ITALIC` are
supported; these are style flags, not a variable-font weight/axis API.

Sizes are layout units (finite 1–128). Line spacing is extra space after each measured line
(finite 0–128), not an absolute line height. Defaults remain title 12 bold, message 8 plain,
buttons 8 bold, and 2 units of extra line spacing. Measurement and drawing use the same resolved
fonts. Actions grow vertically when space allows, stack for long labels, and ellipsize safely.
Overflowing title/message content scrolls. Extremely small windows can clip oversized action
text; buttons stay bounded and keyboard reachable.

Register fonts before opening screens. If replacing/registering a font while a screen is open,
do so on the client thread and call `screen.invalidate()` (or invalidate affected HUD widgets).
Dialogs then remeasure and redraw against the new font revision. A failed registration returns
false and preserves the previous alias. Registration alone does not schedule global repaint work.
Scrolling and focus reuse the measured lines; unchanged styles reuse the cache even when rebuilt.
`screen.dialogLayoutCount()` exposes measurement count for diagnostics.

## Toasts

`UiScreen.toast(...)` shows a short, non-blocking acknowledgement at a screen edge. Toasts fade
in and out, stack up to the document limit, avoid the focused control when another corner is
free, and can include an × button for manual dismissal. A repeated id replaces its earlier toast.

```java
screen.toast(new UiToast(
    "profile-saved",
    "Profile saved",
    "Your accessibility settings are up to date.",
    4_000
));
```

When actions inside the event handler need to show a toast, keep the screen as a field on the
controller that opens it:

```java
private UiScreen screen;

void open() {
    screen = new UiScreen(document, overlays, this::handleUiEvent);
    Minecraft.getInstance().gui.setScreen(screen);
}

void handleUiEvent(UiEvent event) {
    if (event.type() == UiEventType.ACTION && event.key().equals("save")) {
        screen.toast(UiToast.builder("saved", "Saved").build());
    }
}
```

The three-argument constructor uses a three-second duration. Durations are clamped between 250ms
and 30 seconds.

For full control, use the builder. Each toast can select a text icon, a corner, a visual style,
and whether the × button is available.

```java
UiToastStyle success = UiToastStyle.builder()
    .background(0xFF13291F).border(0xFF4EBF83)
    .titleColor(0xFFE0F8E9).messageColor(0xFF9ACCAA)
    .iconBackground(0xFF256842).iconColor(0xFFFFFFFF)
    .radius(14)
    .build();

screen.toast(UiToast.builder("saved", "Profile saved")
    .message("Your changes are ready.")
    .icon("✓")
    .durationMillis(4_000)
    .placement(UiToastPlacement.TOP_RIGHT)
    .style(success)
    .dismissible(false)
    .build());
```

Any color or the radius left out of `UiToastStyle` inherits from the document theme. Set global
defaults once on the document for ordinary toasts:

```java
.toastSettings(new UiToastSettings(
    UiToastPlacement.BOTTOM_RIGHT,
    6,    // maximum visible
    18,   // screen-edge margin
    10,   // gap between toasts
    240   // fade duration in milliseconds
))
```

Use a toast after a successful, reversible action; use a dialog before an action that needs
confirmation.

## Native Minecraft overlays

`ui.nativeSlot(id, x, y, width, height)` reserves a layout rectangle for Minecraft's own item,
entity or font renderer. Supply the matching content through the `NativeOverlayRenderer` passed to the
screen. Pane converts the coordinates for you and hides a native overlay when it would be clipped
by a scroll viewport.

```java
NativeOverlayRenderer overlays = nativeUi -> {
    nativeUi.item("paper-stack", new ItemStack(Items.PAPER));
    nativeUi.entity("player-preview", Minecraft.getInstance().player);
};

UiScreen screen = new UiScreen(document, overlays, event -> handle(event));
```

The ids passed to `item` and `entity` must match slots painted by `ui.nativeSlot`. `player(...)`
remains as a compatibility alias, but use `entity(...)` in new code: it supports a player, villager,
iron golem, or any other `LivingEntity`.

### Player previews without a world

Use `playerPreview` for a title screen, skin selector or fixed character portrait. It needs no
player entity, loaded level, connection or integrated server:

```java
// Reserve this rectangle in the page painter, in normal PaneLib layout coordinates.
ui.nativeSlot("avatar", 400, 60, 220, 400);

// Keep immutable presentation settings outside the per-frame callback.
UiPlayerPreview presentation = UiPlayerPreview.standing()
    .bodyYaw(210)
    .headRotation(-12, 0)
    .cameraPitch(-8)
    .heightFraction(.8F)
    .feet(.5F, .95F);

NativeOverlayRenderer overlays = nativeUi ->
    nativeUi.playerPreview("avatar", resolvedPlayerSkin, presentation);
```

`resolvedPlayerSkin` is Minecraft's `PlayerSkin`, including its wide/slim model. For a bundled
costume, construct it from `new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath(
"your_mod", "costumes/explorer"))` and `PlayerModelType.WIDE` or `SLIM`. The resource texture path
is relative to `textures/` and omits `.png`: that example resolves to
`assets/your_mod/textures/costumes/explorer.png`. Use a compatible Minecraft skin layout.

The two-argument overload uses `UiPlayerPreview.standing()`. A null skin selects Minecraft's
bundled default. The helper does not fetch accounts/profiles or change the account/world player's
skin. Missing custom resources use Minecraft's missing texture; it does not silently download a
replacement. Supply an already resolved skin if your mod handles remote profiles.

Angles are degrees. Body yaw 180 faces the viewer; head angles are relative to the body.
Negative camera pitch looks down. Height is the requested **unprojected** 1.8-block body height
as a fraction of the slot, also limited by a 1.1-block-wide standing frame. Foot coordinates are
normalized within the slot; the default is bottom-center with a small inset. Rotating the camera
can extend the projected soles around that anchor. Extreme anchors/rotations may intentionally
clip, so leave padding. This API is for a standing avatar, not animated poses, equipment or capes.

Fixed settings ignore the pointer. `.followPointer(true)` adds bounded head tracking without
repainting the retained UI; menus/dialogs/custom focus scopes suspend tracking while they block
background interaction. Missing/hidden/zero-size slots submit nothing. Draws contribute to
`performanceSnapshot().nativeEntityCount()`. Native overlays use rectangular clipping and cannot
inherit rounded Canvas clips; partially scrolled slots follow the existing hide policy.

No quaternion, render-state object, raw graphics call or GUI-scale conversion belongs in the
consumer. `NativeOverlayContext.bounds()` is diagnostic geometry in **physical backing pixels**;
`playerPreview` converts it internally. See Wayfarer for a skin and fixed-camera example.

### Entity card previews

Use Minecraft's normal entity types to create a client-only preview for the active client level.
Do not add the preview to that level. Recreate it when the player changes world, and render only
from the native-overlay callback.

```java
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
```

```java
private ClientLevel previewLevel;
private LivingEntity ironGolem;
private LivingEntity villager;
private int nextPreviewId = -2_000_000;

private void refreshPreviews(ClientLevel level) {
    if (level == previewLevel) return;
    previewLevel = level;
    ironGolem = preview(level, "iron_golem");
    villager = preview(level, "villager");
}

private LivingEntity preview(ClientLevel level, String id) {
    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(id));
    Entity entity = type == null ? null : type.create(level, EntitySpawnReason.LOAD);
    if (entity != null) entity.setId(nextPreviewId++);
    return entity instanceof LivingEntity living ? living : null;
}

NativeOverlayRenderer overlays = nativeUi -> {
    ClientLevel level = Minecraft.getInstance().level;
    if (level == null) return;
    refreshPreviews(level);
    nativeUi.entity("iron-golem-card", ironGolem);
    nativeUi.entity("villager-card", villager);
};
```

Reserve the matching layout rectangles in the painter:

```java
ui.nativeSlot("iron-golem-card", x + 20, y + 42, 116, 132);
ui.nativeSlot("villager-card", x + 156, y + 42, 96, 132);
```

`entity(...)` uses Minecraft's inventory-preview camera and GUI lighting. It automatically fits
the entity's live bounding box into the supplied rectangular slot, preserves the entity's current
pose, and follows the cursor without changing the entity. Coordinates are layout-space coordinates
from `nativeSlot`; Pane converts them to Minecraft's GUI scale. Native entities render after the
retained Pane texture by default, so they are not affected by a Pane rounded/circle clip. Pane hides
a slot that would be only partly visible in a scroll viewport rather than allowing the entity to
escape it.

### Covering native previews

The default remains compatible: native items and entities stay above the retained Pane surface.
When a later Pane detail sheet, modal, drawer, popover, or overlapping card must cover a native
preview, paint that Pane region normally and register its final rectangular bounds with
`nativeOverlayLayer`. Pane re-composites only that named part of its retained surface after native
rendering, so the cover is visually above the preview without suppressing or recreating its slot.

```java
import java.awt.Font;

UiRect detailSheet = new UiRect(ui.width() - 260, 24, 236, ui.height() - 48);

// The preview stays topmost everywhere except inside this declared Pane layer.
ui.nativeSlot("selected-villager", card.x() + 20, card.y() + 44, 120, 150);

if (detailsOpen) {
    var g = ui.canvas();
    g.shadow(0x78000000, 12, 0, 4,
        detailSheet.x(), detailSheet.y(), detailSheet.width(), detailSheet.height(), 14);
    g.roundRect(0xFF14232D,
        detailSheet.x(), detailSheet.y(), detailSheet.width(), detailSheet.height(), 14);
    g.text("Villager details", detailSheet.x() + 18, detailSheet.y() + 20,
        12, 0xFFF2F8FB, Font.BOLD, ui.theme().fontFamily());

    ui.nativeOverlayLayer("villager-details-sheet",
        detailSheet.x(), detailSheet.y(), detailSheet.width(), detailSheet.height());
}
```

Layer ids are stable names for the current paint; registering the same id again replaces its
bounds. The four bounds values are the exact layout-space rectangle passed to the Pane painter:
do not multiply them by Minecraft's GUI scale, backing-pixel scale, or retained-surface scale.
Pane carries that rectangle through `withTranslation` and scroll clipping, then converts its two
edges once when it composites the retained cover. This makes the declared edge and the visible
cutoff coincide at non-1 GUI scales. Layers do not create hit regions or redraw work by themselves.
They are rectangular, matching native slot semantics: leave padding if the Pane cover has rounded
corners. The layer is a retained compositing boundary, not a second painter—paint the actual cover
yourself before registering it.

Minecraft 26.2 requires every rendered entity to have an ID. Real entities already do; give each
client-only preview a stable, unused negative ID as shown above. Do not add preview entities to the
client level just to obtain an ID.

The API intentionally supports `LivingEntity` previews only. Non-living entities such as boats,
item frames, and projectiles use different vanilla render paths and are not part of this card-preview
API yet. Fixed yaw/pitch camera controls are also not exposed in 0.9.8; the standard cursor-following
inventory preview is the supported orientation.

## Coordinate spaces

- **GUI space** is Minecraft's mouse and screen coordinate system.
- **Layout space** is where Pane painters work; it stays physically consistent across GUI scales.
- **Surface space** is the supersampled cached texture Pane uploads to the GPU.

`nativeSlot` handles native-renderer conversion. `nativeOverlayLayer` deliberately keeps its
rectangle in layout space until the final retained composite, so it aligns with the Pane content it
covers at every GUI scale. `UiScreen.layoutScale()`, `regionBounds(id)`, and `trackBounds(id)` are
available for your own popovers and tooltips.

## Image fit, filters and source-point mapping

Existing `minecraftTexture` and `minecraftTextureRegion` calls retain nearest-neighbor sampling.
For photographs or explicit fitting, use the new options path:

```java
var options = new UiImageOptions(UiImageOptions.Fit.COVER,
    UiImageOptions.Sampling.BILINEAR, .5F, .5F);
var image = ui.canvas().minecraftImage(backgroundTexture,
    new UiRect(0, 0, ui.width(), ui.height()), options);
image.ifPresent(placement -> {
    float footX = placement.mapX(890); // coordinates in the original source image
    float footY = placement.mapY(492);
    // Position related content from the same mapping used to draw the image.
});
```

`COVER` fills and clips, `CONTAIN` preserves the entire image with remaining space left transparent,
and `STRETCH` fills without preserving aspect ratio. Alignment 0/0 keeps the left/top edges; .5/.5
centers; 1/1 keeps right/bottom. Alignment distributes cropping or letterbox space; it is not a
request to put an arbitrary source pixel at the viewport center. `mapX/mapY` can return coordinates
outside the viewport when a source point is cropped out.

`UiImageOptions.pixelArt()` is centered nearest cover; `photo()` is centered bilinear cover.
Bicubic is available explicitly. Filtering cannot create missing detail in a low-resolution asset.
`UiImagePlacement.fit(sourceWidth, sourceHeight, viewport, options)` computes geometry without
loading an asset. `canvas.image(bufferedImage, viewport, options)` draws caller-owned pixels.
`minecraftImage` returns empty when the resource/dynamic texture is unavailable and shares the
existing reload/invalidation cache. No network download is performed.

Exact crops can choose filtering with the final `UiImageOptions.Sampling` parameter on
`minecraftTextureRegion`. All source/target geometry must be finite and positive on the new
placement path; alignment must be in 0..1. Drawing preserves the surrounding clip, transform,
opacity and interpolation hints. Nine-slice borders remain a proposed separate helper.


## Vanilla font labels

Use `NativeOverlayContext.text(slotId, String/Component[, UiMinecraftTextStyle])` with a reserved
native slot on either a screen or HUD. Height is one line height; width bounds ellipsis/clipping.
The helper handles scaling, translations/resource fonts, styled spans, opacity and ordering.
Stable lines reuse measurement; updated text does not require repainting an unchanged CPU panel.
See [native Minecraft text](native-minecraft-text.md) for the complete recipe and limitations.
