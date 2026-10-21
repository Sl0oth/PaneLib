# PaneLib source status — October 21, 2026

This page describes the current source checkout and local candidate, not a newly published release.
Root `gradle.properties` supplies the candidate version. The preserved submitted `0.9.8` snapshot
remains unchanged; post-submission fixes have a new version and matching artifacts. Use the matching
runtime, starter descriptor, and checksums. A local Maven repository ZIP tests module consumption,
not public hosting. Nothing here establishes Modrinth approval or publication.

The local release gate now checks immutable, repeatable packaging and both extracted file/Maven
consumers in Minecraft. See the [release packaging audit](audits/release-packaging-2026-10-21.md)
and [maintainer commands](releasing.md). Public hosting remains open.
The [Maven hosting setup](public-maven.md) and local HTTPS checks are prepared; release upload,
deployment and verification of the public endpoint are still pending.

## Current public examples

`./gradlew :pane-demo:runExamples` opens the curated shelf at the title screen:

- Wayfarer: an authored game menu with a native player preview, plus a textured gameplay HUD.
- Gather: a chat layout with channels, direct messages, menus, and settings.
- Pane Mail: a light, dense inbox with search, local message actions, and composition.
- Vanilla HUD: native Minecraft font labels, items, and vanilla texture treatment.

Dedicated public launch tasks use installed Java 25 and separate profiles. They require no private
save, Python launcher, or Mac-specific Java path. HUD examples wait for you to create or join a
world. See the [example index](../examples/README.md).

The independent [first-screen starter](../examples/first-screen/README.md) is the recommended
consumer entry point: five controls, persistent settings, parent restoration, optional single-player
pause, and an isolated packaged-JAR smoke test. The [migration guide](migrating-to-panelib.md)
explains how to replace one existing mod screen without rewriting its game logic.

YouTube and older duplicate showcases are retired from the public lineup. Atlas and other retained
internal fixtures remain available for regression work; their historic layouts and world setup are
not the current first-run experience.

## Implemented in current source

- Retained screens and input-free gameplay HUDs, with cached textures and bounded quality settings.
- Custom Java drawing, bundled fonts, images with fit/sampling options, procedural textures,
  shadows, gradients, clipping, and transforms.
- Restylable controls, custom control painters, menus, dialogs, toasts, and configurable input/
  dialog typography. Custom styling preserves the built-in interaction and focus contracts.
- Responsive layouts, document reference scaling, HUD placement policies, smooth scrolling,
  explicit end scrolling, follow-at-end behavior, and virtualized fixed-height lists.
- Keyboard focus/navigation, clipboard editing, pointer caret/selection, Unicode codepoint-safe
  editing, and bounded undo/redo. These do not imply complete narrator or IME support.
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

- A local Maven repository bundle is available for candidate testing; no public Maven host or
  final 1.0 API compatibility guarantee is established.
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
