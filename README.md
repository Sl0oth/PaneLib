# PaneLib

PaneLib is a client-side Fabric library for building desktop-quality Minecraft screens and HUDs
in plain Java. It draws natively: no browser, HTML, CSS or MCEF. A screen paints only when
something changes and is otherwise one reused GPU texture, so idle screens cost almost nothing.

**Latest:** [`1.0.0-beta.2`](https://github.com/Sl0oth/PaneLib/releases/tag/v1.0.0-beta.2) (beta)
· Minecraft 26.2 · Fabric Loader 0.19.3+ · Fabric API · Java 25

**Full documentation:** [sl0oth.github.io/PaneLib](https://sl0oth.github.io/PaneLib/)


## What's in it

| Area | What you get | Added in |
|---|---|---|
| Drawing | Rounded shapes, paths, gradients, shadows, glow, backdrop blur, textures, clipping, transforms | 0.9.x |
| Liquid glass | `UiCanvas.liquidGlass`: refraction, dispersion, frost, tint and specular edges | 1.0.0-beta.1 |
| Text | Bundled fonts, multi-style text, Minecraft colour and formatting codes (`UiRichText`) | 0.9.8 |
| Controls | Buttons, toggles, sliders, checkboxes, radios, tabs, tags, progress bars, dropdowns | 0.9.x |
| Text input | Fields, text areas, passwords; undo/redo, clipboard, Unicode-safe editing | 0.9.x |
| Popups | Menus, dropdowns, dialogs, toasts and tooltips | 0.9.x |
| Menu extras | Shortcut hints, disabled rows, dividers and right-click menus | 1.0.0-beta.1 |
| Tooltips | Default tooltips pick readable text on any theme | 1.0.0-beta.2 |
| Layout | Grids, stacks, smooth scrolling, virtual lists that handle 10,000+ rows | 0.9.2 |
| Motion | Timelines, springs and per-control animations | 0.9.x |
| GPU motion | GPU-animated groups of controls and drag layers | 1.0.0-beta.1 |
| Repaints | Region-only repaints; a static screen paints once and stays idle | 1.0.0-beta.1 |
| Keyboard | Focus, Tab and Shift-Tab, Enter/Space, Escape and back navigation, raw key callbacks | 0.9.1 |
| Minecraft content | Native items, entities, player skins and textures inside your UI | 0.9.x |
| Backgrounds | Title panorama, in-world, the parent screen or full-screen | 0.9.1 |
| HUDs | In-game HUDs that can replace vanilla elements and put them back | 0.9.x |
| Demo mod | PaneOS (a Mac-like desktop), Wayfarer menu and HUD, Pane Mail inbox | 1.0.0-beta.2 |

## Tested on

| Platform | Result |
|---|---|
| macOS (Apple silicon) | 1,311 unit tests and every in-game test pass |
| Windows 11 (RTX 2080 SUPER) | Every in-game test passes at 100% and 150% display scale, including maximize, fullscreen, GUI scale and HUDs |
| Linux | Builds and unit tests pass in CI; not yet tested in game |

## Known limits

- Beta: the API may still change before 1.0.0.
- Minecraft 26.2 only.
- Not yet supported or tested: IME composition (such as Chinese or Japanese input), the narrator,
  and moving between monitors with different scaling.
- Very large full repaints are slow: a full PaneOS repaint takes about 33 ms on an RTX 2080 SUPER.
  Partial repaints and idle screens are much cheaper.

## Try it (players)

1. Download `panelib-1.0.0-beta.2.jar` and `panelib-demo-1.0.0-beta.2.jar` from the
   [release page](https://github.com/Sl0oth/PaneLib/releases/tag/v1.0.0-beta.2).
2. Put both in your Minecraft 26.2 `mods` folder with [Fabric API](https://modrinth.com/mod/fabric-api).
3. Start the game. The demo opens on the title screen; **H** reopens it, **J** toggles the
   selected HUD in a world.

The library on its own adds no screens or keys. You only need it when a mod you use depends on it.

## Use it (modders)

```groovy
repositories {
    maven { url = uri('https://sl0oth.github.io/PaneLib/maven/') }
}
dependencies {
    implementation 'dev.pane:panelib:1.0.0-beta.2'
}
```

Coordinate: `dev.pane:panelib:1.0.0-beta.2`. The repository needs no login or token.

The quickest start is the [first-screen starter](examples/first-screen/README.md): a complete
Fabric mod with five controls, an opening key and real Save/Cancel.

```java
var home = new UiPage("home", "Home", "H", 0xFF1BD96A, ui -> {
    UiRect body = ui.content().inset(24);
    ui.canvas().textMedium("My mod", body.x(), body.y(), 24, ui.theme().text());
    ui.button("save", "Save", "save-config", body.x(), body.y() + 44, 92, 32,
        UiContext.ButtonStyle.PRIMARY, UiContext.Shape.ROUNDED);
});

var document = UiDocument.builder("My mod")
    .theme(UiTheme.dark())
    .page(home)
    .build();

var screen = UiScreen.create(document, NativeOverlayRenderer.NONE, (owner, event) -> {
    if (event.type() == UiEventType.ACTION && event.key().equals("save-config")) {
        saveConfig();
    }
});

Minecraft.getInstance().gui.setScreen(screen);
```


## Building from source

```sh
./gradlew build
./gradlew :pane-demo:runExamples
```

`pane/` is the library and `pane-demo/` is the demo mod. Both need Java 25.

## License

See [LICENSE](LICENSE).
