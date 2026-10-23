# PaneLib {{version}} — local beta candidate

Client-side Fabric UI library for Minecraft 26.2, Java 25, Fabric Loader 0.19.3+ and Fabric API.
The runtime installs no standalone screen or hotkey; a consuming mod supplies those.

This candidate separates the current fixes from the preserved submitted 0.9.8 artifact.

- Liquid glass (`UiGlass`, `liquidGlass`) that refracts and frosts what lies behind it, reused between paints.
- GPU-animated groups of controls (`gpuGroup`, `gpuTransition`, `dragLayer`), region-only state repaints
  (`repaintScope`), opt-in local focus repaints and outside-only shadows; blur costs the same at any radius
  and large glass, blur and shadow work splits across CPU cores.
- Pointer-driven painting, timed refreshes, bounded drags, presentation groups, painted sliders, deferred
  focus, tooltip styling, menu shortcut hints, disabled rows, group dividers and right-click context menus.
- Grapheme-safe text editing.
- PaneOS, a desktop showcase with working windowed apps, replaces the Beacon example.
- Supported player preview appearance/framing and world-free title-screen use.
- Native HUD items and font labels, ownership, placement, quality controls and vanilla restoration.
- Shared styled popup triggers/dialog typography and reusable font/image support.
- Smooth reusable animation and explicit scroll-to-end/follow behavior.
- Owner-aware screen callbacks and opt-in singleplayer pause, with existing constructors preserved.
- Fixed stale slider dragging after screen/control changes; repeated equal scalar state stays idle.
- Curated PaneOS, Wayfarer, Pane Mail and HUD recipes use the public APIs.
- Independent starter with real saved/draft settings, matching binary dependency and isolated smoke test.
- Reproducible archives, exact dependency/version checks, checksums and a local Maven repository bundle.

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
