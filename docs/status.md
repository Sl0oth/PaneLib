# PaneLib source status — October 21, 2026

PaneLib `0.9.9-beta.1` is published on
[GitHub Releases](https://github.com/Sl0oth/PaneLib/releases/tag/v0.9.9-beta.1) and the
[anonymous Maven repository](public-maven.md). The preserved submitted `0.9.8` snapshot remains
unchanged. This release does not update Modrinth or establish its approval status.

A clean source rebuild reproduced all nine release files byte for byte. Public endpoint downloads
matched all 25 immutable version files. Independent file and public Maven consumers built, matched
the nested runtime bytes, and passed actual Minecraft smoke checks on macOS ARM64. See the
[publication audit](audits/public-maven-publication-2026-10-21.md). This establishes the published
beta's consumption path; the remaining adoption limits below still apply.

The development source is now **unreleased 0.9.9-beta.2**, with [grapheme-safe editing](audits/grapheme-editing-2026-10-21.md).
The published artifacts described above are unchanged. [Platform testing](platform-testing.md)
separates hosted build/client checks from pending Windows desktop validation.

## Current public examples

`./gradlew :pane-demo:runExamples` opens the one showcase launcher at the title screen:

- PaneOS: a desktop in the spirit of a modern Mac, with liquid glass dock, widgets, Control Center,
  Spotlight and eleven working windowed apps, all drawn in code.
- Wayfarer: an authored game menu with a native player preview, plus a textured gameplay HUD.
- Pane Mail: a light, dense inbox with search, local message actions, and composition.
- Vanilla HUD: native Minecraft font labels, items, and vanilla texture treatment.

The launcher uses installed Java 25 and its own profile, and needs no private save, Python
launcher, or Mac-specific Java path. HUD examples wait for you to create or join a world. See the
[example index](../examples/README.md).

The independent [first-screen starter](../examples/first-screen/README.md) is the recommended
consumer entry point: five controls, persistent settings, parent restoration, optional single-player
pause, and an isolated packaged-JAR smoke test. The [migration guide](migrating-to-panelib.md)
explains how to replace one existing mod screen without rewriting its game logic.

Older showcases (YouTube, Gather, Briarkeep, Atlas/Studio, Design Lab and the style gallery) were
removed on October 22 so the demo module holds only the three examples above and their tests.

## Implemented in current source

- Retained screens and input-free gameplay HUDs, with cached textures and bounded quality settings.
- Custom Java drawing, bundled fonts, images with fit/sampling options, procedural textures,
  shadows, gradients, clipping, and transforms.
- Restylable controls, custom control painters, menus, dialogs, toasts, and configurable input/
  dialog typography. Custom styling preserves the built-in interaction and focus contracts.
- Responsive layouts, document reference scaling, HUD placement policies, smooth scrolling,
  explicit end scrolling, follow-at-end behavior, and virtualized fixed-height lists.
- Keyboard focus/navigation, clipboard editing, pointer caret/selection, Unicode grapheme-safe
  editing in unreleased beta.2 source (published beta.1 remains codepoint-safe), and bounded undo/redo. These do not imply complete narrator or IME support.
- Native item/entity/player slots, world-free player appearance/framing, and single-line Minecraft
  font labels. Resource-pack font/language changes refresh native text layout.
- Finite scalar/angle animations, sequences, bounded spring curves, and transformed drawing/button
  groups. Motion stops scheduling repaints when settled; reduced motion is available.
- HUD ownership and vanilla-element replacement/restoration, with world/session cleanup.
- Parent/backdrop integration, raw input callbacks, owner-aware typed screen events, and opt-in
  single-player pause. Multiplayer and LAN-shared worlds follow Minecraft's own pause rules.
- Completion-driven invalidation, targeted virtual-row updates, retained-state update rules,
  viewport resizing, and opt-in performance diagnostics.
- Runtime/source/Javadoc artifacts, artifact-boundary checks, and independent consumer builds.

## Evidence and its limits

Each audit records the tested source/artifact, fixture scope, and runtime environment. Do not add
historical check counts together and treat that as one test run of the current binary.

- [Curated examples and portable launches](audits/example-refresh-2026-10-21.md): the completed
  example-refresh baseline, including real-client checks and packaged-consumer validation.
- [Native Minecraft fonts](audits/vanilla-font-api-2026-10-21.md): label rendering, resource reloads,
  clipping, and HUD/native-layer interaction checks at that artifact revision.
- [API and efficiency work](audits/api-comparison-and-efficiency-2026-10-21.md): scoped upload,
  placement, and state-projection measurements. These are not general FPS guarantees.

The October 16 Atlas reports and October 17 independent audit remain historical evidence. Their
old feature inventories and issue status do not supersede the newer per-change reports or current
source.

## Limits to consider before adopting

- The public Maven beta is available; a final 1.0 API compatibility guarantee is not established.
- Minecraft narrator semantics and IME composition remain incomplete.
- Native Minecraft text currently supports single-line labels, not editable or wrapped native-font
  paragraphs. There is no complete built-in vanilla widget theme.
- HUDs do not receive control input; use a `UiScreen` for interactive UI.
- Affine groups support drawing/buttons, not rotated/scaled editors, sliders, scroll containers,
  or native slots. Document scaling and translation have separate supported paths.
- Large popup lists, arbitrary native-layer composition, and cross-mod HUD conflicts need their
  documented constraints respected; a showcase does not prove every combination.
- Local real-client evidence is macOS ARM64. Windows/Linux GPU behavior, general performance
  guarantees, and broad automated visual-regression coverage remain unproven.
- Some internal historical fixtures still rely on private disposable saves or duplicated launcher
  helpers. That remaining developer-tooling debt does not affect the curated public launch tasks.

Test the packaged library with your own mod, target platforms, window sizes, and modpack before
making broader compatibility or performance promises.
