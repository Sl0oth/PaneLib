# Grapheme-safe editing — October 21, 2026

Status: implemented in unreleased `0.9.9-beta.2` source. Published beta.1 and the submitted
0.9.8 snapshot remain unchanged. No new release or Modrinth upload is part of this change.

## Consumer behavior

Existing textField, passwordField, textArea and UiTextHistory callers need no new options.
Public offsets and insertion limits remain UTF-16 units. Internally, cursor movement, selection,
word edges, deletion, pointer hit positions, wrapping and truncation use complete extended graphemes.
An accented letter, skin-tone emoji, regional-indicator flag or ZWJ family is one editing unit.
A cluster wider than the editor is kept intact rather than split to fit.

Edits recompute boundaries for the complete result: adding ZWJ or deleting a separator can join
previously independent characters. The caret advances to the end of that joined cluster. Undo and
redo restore the corresponding text and selection. Password masking retains one bullet per UTF-16
unit; navigation uses the original value's boundaries. Existing programmatic values are not truncated.
Raw wrapping helpers retain original offsets across CRLF and CR; textareas still normalize to LF.

The implementation uses the JDK's locale-neutral character BreakIterator, whose Java 25 default
follows extended grapheme boundaries under Unicode 16. There is no new runtime dependency.
Sources: [Java 25 BreakIterator](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/BreakIterator.html),
[Java 25 Unicode version](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Character.html),
and the [Unicode 16 conformance corpus](https://www.unicode.org/Public/16.0.0/ucd/auxiliary/GraphemeBreakTest.txt).
The corpus and its Unicode license are checked in as test resources only.

## Verification

- Before the fix, the new 1,097-test baseline failed 697 checks.
- The complete local Java suite now passes **1,265 tests**, including all **1,093 official Unicode 16
  grapheme cases**, original editing tests, shaped/masked pointer geometry, narrow wrapping, CRLF,
  isolated surrogates, UTF-16 limits and text larger than the boundary-cache budget.
- The isolated Minecraft editing fixture passes **84 checks on macOS ARM64**: keyboard navigation,
  deletion and selection; clipboard/history; joined ZWJ and flag edits; insertion limits; masked fields;
  painted vertical movement and pointer placement. The fixture restores the previous clipboard.
- [Hosted run 39665812338](https://github.com/Sl0oth/PaneLib/actions/runs/39665812338) passed at
  implementation commit `e9fbc41d3d0fcf17a5a9eeec5d9f2619203847df`: all 1,265 Java tests and
  independent consumer builds on Windows/macOS/Linux, plus all ten Ubuntu Minecraft fixture steps.
  The expanded editing fixture passed all 84 checks there too.
- The macOS independent consumer also passed against the exact beta.2 development JAR: editing,
  keyboard Save, disk persistence, Cancel/Escape, resize and reopening. Local runtime SHA-256:
  `7f246d3e08d3df526873f99b4a506fcf3e551ca31ec5ee6f1d2c3ef753ff2938`.
- [Platform testing](../platform-testing.md) records runner versions, tooling skips, per-platform hashes
  and remaining desktop checks. Windows hands-on testing remains a separate pending gate.

A fresh local source review found repeated scans of oversized values. Snapshot construction,
selection replacement and key handling now reuse a local boundary map. The shared cache holds at
most four maps and 65,536 UTF-16 units per thread; map entries contain only offsets and weak source
references. Large values are not cached, and wrapping shares one map across all lines. This bounds
cache retention and avoids repeated per-character segmentation; it is not an FPS or latency claim.

## Remaining limits

This is plain-text cluster editing, not complete international text support. IME composition,
narrator semantics, visual bidi navigation, universal font/emoji coverage, physical keyboard layouts
and desktop GPU behavior are not established by these checks. Minecraft fixture events are injected
through actual screen handlers; they are not OS-level keyboard or input-method tests. The published
beta.1 does not acquire this behavior until a separately approved new release is built and published.
