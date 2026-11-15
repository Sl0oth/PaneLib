# PaneLib {{version}} — local beta candidate

Client-side Fabric UI library for Minecraft 26.2, Java 25, Fabric Loader 0.19.3+ and Fabric API.
The runtime installs no standalone screen or hotkey; a consuming mod supplies those.

This beta makes common screens shorter to write, adds settings forms and new controls, lets HUDs take
clicks, and follows high refresh rate displays.

- One-call screens: `UiScreen.open("My mod", ui -> { ... })` builds and shows a screen.
  `screen.onAction` and `screen.onChange` run a handler for one action or value, and
  `screen.settings(file, defaults)` loads and saves a screen's values in a properties file.
- Settings forms: `ui.form` lays out labelled rows and scrolls when they do not fit: toggles, sliders,
  text, dropdowns, steppers, key bindings, colours and buttons.
- New controls: `ui.keybind` (keys and, if you allow them, mouse buttons, named as Minecraft names
  them), `ui.colorPicker` and `ui.stepper`.
- New layouts: `ui.scrollHorizontal` scrolls sideways, and `ui.variableList` is a scrolling list whose
  rows have different heights. Sliders and text inputs work inside rotated or scaled groups.
- Clickable HUDs: `hud.setInteractive(true)` lets a HUD's buttons, toggles, checkboxes, steppers and
  sliders take the mouse while chat is open.
- High refresh rates: animation repaints once per displayed frame, where it used to stop near 48
  frames a second on a 144 Hz display. `UiRenderSettings.withMaximumFrameRate` sets a ceiling.
- Lighter: opening a screen paints once, showing a HUD fades on the GPU, and a surface no longer keeps
  a second copy of its pixels, about 15 MB less for a 2560×1440 screen.
- Game-style menus: slanted and cut-corner shapes, `withSkew`, `UiTextEffect` for outlined, shadowed
  and leaning display text, `rays`, and `ui.loop` and `ui.pulse` for animation that never ends. The demo
  adds two menus built with them, Skydrop and Overdrive.
- Themes: a `with...` method for every `UiTheme` token, `danger`, `success` and `warning` colours, and
  document-wide button, input and menu styles.

Everything from 1.0.0-beta.3 is included, and nothing public was removed: a mod built against
1.0.0-beta.3 runs on this version. See CHANGELOG.md for the full list.

The starter ZIP includes its exact runtime JAR, build wrapper, source, license and version descriptor.
Unpack it, install Java 25, then use its README. Runtime, source and Javadoc JARs have different roles;
only the runtime belongs in Minecraft mods. The API ZIP opens as an offline reference.
The demo JAR is a separate mod: install it next to the runtime and Fabric API; it opens on the
title screen and H reopens it.
The Maven ZIP is a candidate repository payload, not proof of public hosting or release publication.

Real-client checks for this beta ran on macOS ARM64 only: 1,406 unit tests and 20 in-game fixtures.
The Windows 11 desktop last ran the 1.0.0-beta.3 branch, so forms, the new controls, clickable HUDs
and the refresh-rate change are not yet tested on Windows. Linux desktop behavior, real IME keyboards
and universal performance are not certified by these files. Existing tests cover bounded behaviors,
not every modpack combination. See the candidate manifest for exact evidence.

Prepared locally only. Publication requires a separate release decision; never overwrite a published
version with different bytes. Keep the existing submitted snapshot unchanged.
