# General API follow-up from Peripheral — 2026-10-17

Implemented in the current PaneLib Minecraft 26.2 checkout after checking the current source and
Peripheral ledger's final reconciliations. This does not upgrade or modify Peripheral's relocated
26.1.2 snapshot. Existing unrelated work was preserved; nothing was published.

| Handoff need | Decision and result |
| --- | --- |
| P03/P12 input typography | Added immutable `UiInputStyle` overloads for fields, password fields, and textareas. The same font/padding metrics drive drawing, caret/selection, clipping, wrapping, and pointer placement. Textareas follow the active caret vertically. |
| P17 programmatic/modal focus | Added `focus`, `clearFocus`, nested `pushFocusScope`/`popFocusScope`, and active-scope diagnostics. Isolation starts immediately, even before painting. Prior focus/selection are restored on close. |
| Built-in dialog isolation | Current source inspection showed retained background input could remain eligible until a later paint. Built-in dialogs now block it immediately, choose Cancel as the keyboard default, and restore trigger focus. This is a current source fix, separate from Peripheral's resolved custom-sheet workaround. |
| P04 undoable editing | Added reusable bounded `UiTextHistory` and native Ctrl/Cmd undo/redo integration. Codepoint-safe navigation, deletion, truncation, and hit carets prevent splitting surrogate pairs. This is a plain-text model, not a syntax/code editor. |
| P05/P07 diagnostics/integration | Added public scroll viewport and offset/target snapshots. Documented Minecraft GUI/layout/OS/framebuffer coordinate distinctions and existing native overlay composition. |
| P01/P02 quality discoverability | Added an opt-in typography raster ceiling and a snapshot reporting physical-resolution undersampling. Existing presets/defaults remain unchanged. Peripheral's earlier preset choices are not treated as renderer defects. |
| P10 narration | Deferred a dedicated semantic/narrator integration. It requires labels, roles, state, focus updates, retained and virtual controls, and direct narrator testing together. A partial adapter would not support an accessibility claim. Keyboard tests here are not narration certification. |

Menu font styling, full code-editor/terminal features, advanced text layout, and custom list/slider
keyboard parity remain separate work. App transport, polling, script synchronization, JSON, native
game behavior, provider calls, and packaging are outside this library change.

## Compatibility and adoption

Existing input overloads remain source-compatible. Their default font size/family remain the same;
vertical alignment/selection now use measured font height. New styles opt into other typography.
The concrete keyboard behavior change is built-in dialog dismissal restoring trigger focus instead
of clearing it; the existing regression assertion was updated to check that intentional behavior.

Custom modal scope whitelists follow the controls' paint order for Tab. Normal raw onKey/onClick
handlers are bypassed while a scope is active, so application-wide shortcuts cannot mutate hidden
controls. Modal behavior uses scoped Pane controls and the event listener. This does not scope an
arbitrary subclass that deliberately bypasses UiScreen's event routing.

Native editor undo retains at most sixteen input-key histories, each with bounded edit/character
budgets. Identical programmatic synchronization preserves history; actual replacement clears it.
Page changes/removal release scopes and history. UTF-16 codepoint safety does not implement full
grapheme clusters or IME composition.

The typography preset permits 5120×3200/4x. Each full-size RGBA buffer is 62.5 MiB, with CPU/native/
GPU copies adding memory cost. It is opt-in; no performance or memory-reduction claim is made.

Usage and examples: [styled inputs and focus](input-and-focus.md).

## Verification

- **55 unit tests passed**; no failures/errors. New coverage checks matching styled metrics,
  codepoint-safe caret/truncation, clipping at excessive padding, bounded history/redo, selection,
  external changes, and quality-cap diagnostics.
- **38 public-API assertions passed** in the actual Minecraft client (39 PASS markers including
  completion). This includes immediate modal blocking, raw shortcut suppression, safe defaults,
  nested/LIFO scopes, focus/caret/selection restoration, page cleanup, Unicode, deletion undo,
  identical-value synchronization, scroll diagnostics, and quality caps.
- **49 broader client PASS markers**, zero failures, including controls, native dialog focus
  restoration, scrolling/virtual atlases, resize, zero idle repaints, and GPU readback of
  **6,562,560 bytes with zero differences**.
- **31 Studio PASS markers**, zero failures, including Atlas motion/input, real world movement
  and attack through the HUD, hotkeys, F1, resize, idle retention, and unload/reload.
- Full `./gradlew build` passed; the library, source JAR, Javadoc JAR, and demo were refreshed.
  Library artifact checks passed; an independent ZIP inspection found the new APIs and no demo
  classes in the library JAR.
- Final local library: `pane/build/libs/panelib-0.9.8.jar`, SHA256 **b3c9ff6da632fc38d1e02da89af2fe57a86e0da3928507473c302abeae165757**.

Authoritative logs: `build/api-improvements/acceptance-final.log`, `core-final.log`,
`studio-final.log`, and `build-final.log`. Earlier compile attempts/fixtures remain historical;
only the final named logs support these results.

The new fixture generates actual Minecraft client input events and screenshots in a title-only
client; it does not open a saved world or mutate application data. Broader regression checks use
only the isolated disposable test world. No running user/Peripheral client was terminated.

Screenshots are in `pane-demo/run-benchmark/screenshots/api-*.png`; styled-input, custom-modal,
compact and large views were captured, with styled-input/modal/compact views visually inspected.
The new API fixture can be rerun with `./gradlew :pane-demo:runClient -Pisolated -PapiTest`.

Limits: local macOS evidence only. No narrator, Windows/Linux, multiplayer, human-usability,
frame-time percentile, or universal accessibility certification follows from this pass.

## Changed files in this pass

- `pane/src/main/java/dev/pane/ui/UiInputStyle.java` (new)
- `pane/src/main/java/dev/pane/ui/UiTextHistory.java` (new)
- `pane/src/main/java/dev/pane/ui/UiTextBoundary.java` (new internal helper)
- `pane/src/main/java/dev/pane/ui/UiRasterQuality.java` (new)
- `pane/src/main/java/dev/pane/ui/UiScrollSnapshot.java` (new)
- `pane/src/main/java/dev/pane/ui/UiContext.java`, `UiScreen.java`, `UiCanvas.java`, `UiRenderSettings.java`
- `pane/src/test/java/dev/pane/ui/UiInputStyleTest.java`, `UiTextHistoryTest.java` (new)
- `pane-demo/src/main/java/dev/pane/demo/ApiImprovementsTest.java` (new dev fixture)
- `pane-demo/src/main/java/dev/pane/demo/PaneDemoClient.java`, `DemoAutoTest.java`, `pane-demo/build.gradle`
- `README.md`, `docs/index.md`, `docs/status.md`, `docs/ui-basics.md`, `docs/input-and-focus.md` (new), this report

Initial source backups are in `build/api-improvements/baseline/`. Other checkout differences predate
this pass. Peripheral files were read only. The external ledger's application fixes were not copied
into this library or relabeled as library defects.
