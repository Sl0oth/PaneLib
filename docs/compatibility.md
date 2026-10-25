# Compatibility (draft)

This is a draft of what PaneLib will promise from 1.0.0. Until 1.0.0 every release is a beta and
any of it may still change. Nothing here is decided until the maintainer accepts it.

## Version numbers

From 1.0.0, PaneLib follows semantic versioning for its public API:

| Release | May contain |
|---|---|
| Patch (1.0.1) | Fixes only. No API changes. |
| Minor (1.1.0) | New API, deprecations, and fixes. Code built against 1.0 still compiles and runs. |
| Major (2.0.0) | Removals and other breaking changes, each listed in the changelog with what to use instead. |

A release targets one Minecraft version. Moving to a new Minecraft version is a minor release when
PaneLib's own API is unchanged; Minecraft's own changes still apply to mods.

## What the public API is

The public API is every `public` type and member in `dev.pane.ui`. These are not part of it:

- `dev.pane.ui.internal` and `dev.pane.mixin`, public only because Minecraft's loader must see
  them; the Javadoc lists them, each marked **Internal**;
- `dev.pane.mcef`, which is not built into the library at all;
- package-private types and members.

## What stays compatible within 1.x

- Public types, methods, constructors and fields keep their signatures and meaning.
- Record components keep their order and types; new records may be added.
- Enum constants are not removed. New constants may be added, so a `switch` over a PaneLib enum
  should have a `default` branch.
- Default arguments and fallbacks keep their documented meaning.
- Something to be removed is deprecated for at least one minor release first, with its
  replacement named in the Javadoc.

## What may change in any release

- **Exact pixels.** Default looks, antialiasing and colours may be refined, as when beta.2 made
  default tooltip text readable on light themes. Documented geometry, such as where a control's
  hit area is, does not change without notice.
- **Diagnostics.** Counters and states such as `paintCount()`, `settled()`, `glassLensRenderCount()` and
  `performanceSnapshot()` describe the current implementation; what they count may change.
- **Performance.** Timings and memory use are not part of the contract, though regressions are bugs.
- **Narration wording.** Narrated text follows Minecraft's own translations and may follow them
  as Minecraft changes them.
- **Diagnostic system properties**, such as `-Dpane.ui.plainRaster=true`.

## Experimental

These are new or still being shaped. They may change in a minor release, with the change listed
in the changelog:

| API | Since | Why |
|---|---|---|
| `UiNarration`, `UiContext.narration` | 1.0.0-beta.3 | New; roles and wording may grow as more controls are narrated. |
| `UiScreen.compositionText` | 1.0.0-beta.3 | Diagnostic view of input-method composition. |
| `UiContext.gpuGroup`, `gpuTransition`, `dragLayer` | 1.0.0-beta.1 | How glass inside GPU layers sees its backdrop may change. |
| `UiCanvas.liquidGlass`, `UiGlass` | 1.0.0-beta.1 | The parameters' ranges and the look may be tuned. |

## Checking the documentation

`./gradlew :pane:javadoc -Pdoclint=all,-missing` checks that the Javadoc is well formed and
links correctly; it reports nothing today. `-Pdoclint=all` also lists missing `@param` and
`@return` tags. Every public type and member has a description.
