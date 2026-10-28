# PaneLib {{version}} — local beta candidate

Client-side Fabric UI library for Minecraft 26.2, Java 25, Fabric Loader 0.19.3+ and Fabric API.
The runtime installs no standalone screen or hotkey; a consuming mod supplies those.

This beta makes PaneLib faster, adds the narrator and input methods, and adds the pieces a scripting
mod needed: read-only text views, a HUD editor and a list of the controls on screen.

- Faster: large fills, strokes and images draw on several cores with exactly the same pixels. A full
  PaneOS repaint went from about 33 ms to 13–18 ms on an RTX 2080 SUPER, and a click from 26 ms to 8–13.
- Narrator: built-in controls describe themselves to Minecraft's narrator in its own words, including
  their state, dialogs, toasts and list rows. `ui.narration` labels controls you paint yourself.
- Input methods: focusing a text input turns Minecraft's IME on; Chinese, Japanese and Korean
  composition draws at the caret, and a committed word undoes in one step.
- Text views: `ui.textView` is read-only text that wraps, scrolls, selects and copies, with optional
  line numbers, coloured runs (`UiRichText`), clickable links and `UiScrollFollow.WHEN_AT_END` for logs.
- HUD editor: `UiHudEditor` is a ready-made screen where players drag HUDs into place. HUDs gained
  offsets from their anchor, `setSize` and `setScalesWithGui`.
- `UiScreen.controls()` lists the controls the last paint drew with their narrator label and bounds,
  so tests and automation can find "the Save button" by its words.
- Styled icon buttons and menu buttons, seven new icons, `showDialog` from code, and `requestFocus`
  with a selection.
- A screen can paint the world behind it (`worldSnapshot`), and the demo has an in-world Field kit.

Everything from 1.0.0-beta.2 is included. See CHANGELOG.md for the full list and fixes.

The starter ZIP includes its exact runtime JAR, build wrapper, source, license and version descriptor.
Unpack it, install Java 25, then use its README. Runtime, source and Javadoc JARs have different roles;
only the runtime belongs in Minecraft mods. The API ZIP opens as an offline reference.
The demo JAR is a separate mod: install it next to the runtime and Fabric API; it opens on the
title screen and H reopens it.
The Maven ZIP is a candidate repository payload, not proof of public hosting or release publication.

Real-client checks ran on macOS ARM64. The speed, narrator and IME work also ran on a Windows 11
desktop; the text views, HUD editor and `controls()` were tested on macOS only. Linux desktop behavior,
real IME keyboards and universal performance are not certified by these files. Existing tests cover
bounded behaviors, not every modpack combination. See the candidate manifest for exact evidence.

Prepared locally only. Publication requires a separate release decision; never overwrite a published
version with different bytes. Keep the existing submitted snapshot unchanged.
