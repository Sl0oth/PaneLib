# W11 — native Minecraft font support

User requested proper, easy vanilla-font support, a migrated example and actual Minecraft tests.
This is a local implementation and verification checkpoint, not a publication or full vanilla
widget-theme release. Existing submitted artifacts and unrelated work were preserved.

## Supported API

`NativeOverlayContext.text(slotId, String/Component[, UiMinecraftTextStyle])` uses Minecraft's font
renderer in the existing native overlay pass. A normal `UiContext.nativeSlot` defines position,
width and line height. `UiMinecraftTextStyle.hud()` and `.label()` provide immutable presets;
fluent options cover ARGB color, shadow, alignment and ellipsis/clip overflow. No consumer raw
graphics, GUI/DPI conversion, manual alpha or copied bitmap font is needed.

Translated/styled components and Minecraft resource-font identifiers keep their native behavior.
Source code for the installed Minecraft 26.2 Font, GuiGraphicsExtractor, MutableComponent,
TranslatableContents and resource-pack metadata contracts was inspected before implementation.
No external source review or source sharing occurred.

The library snapshots changed component values, including children and component translation
arguments. This avoids Minecraft MutableComponent's cached visual order remaining stale after
an in-place edit. Equal strings/components reuse layout. Color/alignment changes reuse measurement;
width, shadow/overflow, font/resource revision and language changes invalidate the appropriate line.
Each owner has a bounded 256-entry cache, released at lifecycle boundaries. There are no new CPU
raster surfaces. Native glyphs still draw each visible frame, like vanilla text and native items.

`UiHud.nativeTextCount()` / `UiScreen.nativeTextCount()` expose submissions; corresponding
`nativeTextLayoutCount()` methods expose cumulative remeasurement. HUD fade multiplies the supplied
alpha while preserving RGB for Minecraft's font pipeline. Rectangular clipping restores the parent
scissor/pose; popup and native cover composition remain above the text.

## Example migration

The restoration fixture's former font-less vanilla-style panel is now the reusable
`pane-demo/src/main/java/dev/pane/demo/VanillaHudExample.java` factory. It draws vanilla-style
bevels and heart textures, four native items, a translated Inventory title, a live level label and
a caption. It supplements the existing HUD and does not change the player's state. Consumers own
attachment, placement and close. Wayfarer's typography/art direction is unchanged.

Usage and limits: [native text guide](../native-minecraft-text.md). Reproduce after the documented
build with `examples/wayfarer-hud/launch.py --font-test`. `--restoration-test` exercises the migrated
example inside the existing vanilla restoration suite. Each flag is a separate invocation.

## Verification

Final build, Javadoc and packaged artifact checks pass: **157 unit tests, zero failures/errors**.
Four added unit tests cover immutable styles, GUI-independent glyph scale/fit budgets, alpha
composition and safe absent/tiny/invalid/transparent slots without Minecraft initialization.

Actual isolated Minecraft 26.2 results on the same final runtime:

| Fixture | Checks |
| --- | ---: |
| Native font API | 43 |
| Vanilla HUD restoration and migrated example | 33 |
| Native HUD items/compass/lifecycle | 18 |
| Wayfarer menu/player preview regression | 28 |
| **Total** | **122** |

The focused fixture covers styled/translated text, supplementary codepoints, ellipsis/tiny widths,
clipping, alignment, equal-value caching, mutable children/styles/translation arguments, bounded
cache capacity, no panel uploads for native-only label updates, scaling, slot removal, fade/F1/
screens/disable/close, world-free screens, partial-scroll suppression, native covers and dialogs.

An actual temporary resource pack changes a font's A advance from 5 to 10 and a retained translation
from Before reload to After reload. The font width changes 20→40 and cached lines refresh after
Minecraft's resource reload. The pack lives only in the isolated run directory. Stable periods
show zero extra line measurements, panel paints or panel uploads. These are work counters, not an
FPS or cross-platform performance guarantee.

Screenshots were inspected and checked numerically:

- Half-opacity text has 0.50196 times the opaque text's pixel energy on black.
- Zero-opacity text leaves zero text pixels; the clipped label spills zero pixels past its slot.
- The native overlay cover remains uniform with no text leaking through.
- The red underlying line occupies 3,986 pixels before the dialog and zero inside the open dialog.

Captures: `pane-demo/run-native-font/screenshots/vanilla-font-hud.png`, `vanilla-font-gui-scale.png`,
`vanilla-font-scaled.png`, `vanilla-font-screen.png`, `vanilla-font-modal.png`. The migrated example
is also captured in `pane-demo/run-hud-restoration-regression/screenshots/vanilla-styled-hud.png`.
All final fixture clients exited normally; an earlier interactive client was left alone.

The initial fixture compile used three incorrect existing helper signatures; these were corrected
before runtime testing. Its build log remains. The first native-font run passed 35 checks; added
resource-translation/modal checks passed 39; the final component-snapshot regression passed 43.
Only the final four fixture logs are counted for the final runtime. No failed run is counted as proof.

## Artifact and scope

Runtime SHA-256: `7b6ad6514db13aa4dba253d21277fa2748c35c218315d1b8106359cd8cf29c31`.
All **158** compiled library classes match the JAR byte-for-byte. ZIP integrity passes; all **12**
files in `release-snapshots/0.9.8-submitted` remain unchanged. Version remains 0.9.8. Exact log
hashes, checks and pixel results are in `build/audit/vanilla-font/final-verification.json`.

This completes native single-line font support. It does not replace Java2D typography in text
editors, add a wrapping native paragraph control, or provide a complete vanilla widget theme.
Native slots retain their rectangular/partial-scroll visibility rules. Testing was on this Mac,
not arbitrary modpacks or Windows/Linux. No version bump, release staging, upload or publication.
