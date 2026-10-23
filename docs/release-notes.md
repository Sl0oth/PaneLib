# PaneLib {{version}} — local beta candidate

Client-side Fabric UI library for Minecraft 26.2, Java 25, Fabric Loader 0.19.3+ and Fabric API.
The runtime installs no standalone screen or hotkey; a consuming mod supplies those.

This beta fixes a tooltip bug and ships the demo as its own mod.

- Default tooltips on light themes are readable again. Their near-black plate used to draw the theme's
  dark text, so the tip showed as an empty pill. Unset tooltip text now keeps the theme colour only when
  it reads on the plate (`UiTooltipStyle.plate()`, `textOn(themeText)`).
- `panelib-demo-{{version}}.jar` is the PaneOS, Wayfarer, Pane Mail and HUD examples as a separate mod.
- Verified on a Windows 11 desktop alongside macOS: unit tests, every client fixture (also at 150%
  display scale), the starter consumers and the release jars in a clean production client.
- New `windowHudTest` fixture: maximize, restore, fullscreen and GUI scale with real clicks, then vanilla
  HUD hiding and restoration in a fresh survival world.
- Release tooling: LF line endings in every checkout so Windows builds match macOS byte for byte,
  ordinary candidate folders, and `--upload` to collect exactly the files a release attaches.

Everything from 1.0.0-beta.1 is included: liquid glass, GPU-animated groups of controls, region-only
repaints, live input, menus, grapheme-safe editing and PaneOS.

The starter ZIP includes its exact runtime JAR, build wrapper, source, license and version descriptor.
Unpack it, install Java 25, then use its README. Runtime, source and Javadoc JARs have different roles;
only the runtime belongs in Minecraft mods. The API ZIP opens as an offline reference.
The demo JAR is a separate mod: install it next to the runtime and Fabric API; it opens on the
title screen and H reopens it.
The Maven ZIP is a candidate repository payload, not proof of public hosting or release publication.

Real-client checks ran on macOS ARM64 and a Windows 11 desktop. Linux desktop behavior, narrator, IME and universal
performance are not certified by these files. Existing tests cover bounded behaviors, not every
modpack combination. See the candidate manifest and the accompanying audit for exact evidence.

Prepared locally only. Publication requires a separate release decision; never overwrite a published
version with different bytes. Keep the existing submitted snapshot unchanged.
