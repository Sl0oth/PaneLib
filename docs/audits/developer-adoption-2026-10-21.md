# Developer adoption audit — October 21, 2026

## Result

A moderate Fabric developer can now start from a small packaged-JAR consumer and migrate one
settings screen without a screen subclass or holder-array callback workaround. Layout and art
direction still take deliberate design work. This is source and local-client readiness, not a
new release, universal performance guarantee, or automatic conversion of vanilla widget trees.

The fresh adoption reviewer found one remaining first-run blocker: the starter's ignored `libs/`
JAR was the submitted binary, sharing the current `0.9.8` label but missing new APIs. The primary
instructions now select the exact source-built JAR, and the starter rejects incompatible artifacts
before compilation with recovery instructions. The reviewer rechecked those changes and found no
remaining blocker within the bounded adoption review.

## Changes and reasons

| Finding | Change | Evidence |
| --- | --- | --- |
| Settings screens required subclassing merely to pause | `UiScreenOptions.pauseGame(true)`; default remains false; Wayfarer and starter migrated | Actual singleplayer clock stops and resumes; remote/LAN pause rules remain Minecraft's responsibility |
| Event handlers needed one-element owner arrays | `UiScreen.create(document, overlay, (screen, event) -> ..., options)` | ACTION/VALUE/SUBMIT/PAGE owner routing, old constructor callbacks and null handler verified; four curated factories and starter migrated |
| Equal scalar state updates unnecessarily dirtied screens | Coalesce equal String/boxed primitive/null assignments after normalization | 100 repeated equal assignments stay idle; changed values/types, async ordering and textarea undo work |
| Mutable object notifications must survive the optimization | Lists, arbitrary objects and mutable enum instances still invalidate | Same-instance list and enum mutation repaint checks pass |
| Sliders retained stale drag capture | Clear capture on removal; reconcile control id/action/scope/current geometry after paint and before drag | Five baseline failures reproduced in Minecraft; all pass after fix, including disappearance/reappearance without pointer movement and enabled same-id replacement |
| Clearing a builder parent retained an implicit backdrop | Clear implicit inherited backdrop when parent becomes null; preserve explicit backdrop | Both contracts verified in client fixture |
| Starter smoke could overwrite normal example settings | Separate `run-example-smoke` and test-only class; explicit Java 25 launcher | Normal config/options hashes stay unchanged; Save/Cancel/Escape/reopen and persistence verified |
| Fixture launchers duplicated Mac/private-save assumptions | Shared runner; Gradle-exported runtime/toolchain/artifact; frozen outputs; fresh scratch worlds | Actual fresh-profile screen/HUD runs; 21 offline harness checks |
| Onboarding examples and docs disagreed | Complete compiled starter snippet, migration guide, current status/index, curated launch paths and website navigation | Exact Markdown Java factory compiled in independent consumer; local links and website build checked |

The drag fix adds a current-hit lookup only while a slider capture exists. It does not schedule a
new idle repaint. Scalar coalescing is intentionally conservative: it does not infer immutability
from `equals`, records, lists or enums. These checks prove avoided retained paints for this case;
they do not establish an FPS improvement or a whole-library performance percentage.

## Validation

`build/audit/adoption/` contains raw logs, screenshots and machine-readable evidence.

- 157 library unit tests pass from a forced execution; build/Javadoc and artifact-boundary check pass.
- 21 offline runner tests cover CLI validation, runtime metadata, copied outputs, cleanup, platform
  flags, profile ownership, bounded timeout and completion/failure verdicts. These are not real
  Windows/Linux client tests.
- 300 checks pass across ten actual Minecraft fixtures: adoption 40; editing 7; editor 24;
  Wayfarer 28; Gather 34; Pane Mail 24; popup 34; native fonts 43; HUD restoration 33; gallery/live
  HUD ownership 33. Each frozen run requires its marker, no failure line, timely exit and exit zero.
- Baseline adoption log deliberately fails five of 28 checks. Final adoption log passes 40 checks.
  Do not count the failing baseline as passing evidence.
- Independent current-JAR consumer compiles the exact getting-started factory, builds a bundled
  variant whose embedded JAR bytes match, and exercises real persistence and discard behavior.
  See `consumer-final-verification.json` for final smoke and normal-profile preservation evidence.
- Existing packaged starter ZIPs and old ignored JARs are preserved as historical matching bundles.
  The incompatible-old-JAR probe fails before compilation, as intended.
- Wayfarer, Gather compact settings, Pane Mail and control-test captures were visually inspected.
  The first fresh-world native-font capture had a Minecraft tutorial toast over part of the test
  panel; fixture-only onboarding options now suppress that toast, with a replacement 43-check capture run passing and visually inspected.

Reproduce from the repository root with installed Java 25:

```sh
./gradlew :pane:build :pane:verifyPaneArtifact
python3 -m unittest discover -s tools/fixtures/tests -v
python3 tools/fixtures/wayfarer.py --adoption-test --fresh
python3 tools/fixtures/wayfarer.py --examples-test --fresh
python3 tools/fixtures/wayfarer.py --test --fresh
python3 tools/fixtures/discord-inbox.py --test --fresh
python3 tools/fixtures/wayfarer.py --mail-test --fresh
python3 tools/fixtures/wayfarer-hud.py --font-test --fresh
python3 tools/fixtures/wayfarer-hud.py --restoration-test --fresh
./gradlew -p examples/first-screen runClient -PsmokeTest -PpaneJar=../../pane/build/libs/panelib-0.9.8.jar
```

`--fresh` matters: ordinary fixture invocations may deliberately reuse an existing isolated profile.
Fresh HUD runs created their own world, then performed test-only staging there. Public HUD examples
continue to sample the actual world and inventory without staging gameplay. The runner copies
mutable project outputs; dependency-cache entries remain shared, and export/copy still requires
build coordination. CI now includes the adoption fixture, but that new CI job was not run remotely.

## Fresh review and remaining scope

Two fresh read-only local Codex reviewers assessed adoption and runtime/tooling. The adoption
reviewer's incompatible-artifact and stale-doc findings were fixed and rechecked. The runtime
review found no must-fix regression; its suggested action-mismatch test was added and passed.
No private source was sent to an external review service.

The next useful adoption step is a separately versioned matching release/starter and dependable
artifact distribution. There is no hosted public Maven repository configured. This pass deliberately
does not publish or silently replace the submitted release. A new version is required before distribution.

Remaining limitations are explicit: Minecraft 26.2/Fabric/Java 25 target; actual runtime checks on
macOS ARM64 only; reused dependency caches rather than a network-empty install; incomplete narrator,
IME and full grapheme-cluster editing; native font helper handles single-line labels rather than
editable bitmap text or a complete vanilla widget theme. Arbitrary modpacks and Windows/Linux GPUs
remain unverified. No visual mockup or unit test substitutes for those checks.

Git status was unavailable because Apple's command-line Git requires an unaccepted Xcode license.
No license was accepted and no Git staging, commit, push, upload or release operation was performed.
Protected snapshot hashes and packaged class parity are recorded separately in final verification.

## Final artifact identity

- Library SHA-256: `5f8b6ad0e4e6b435377c075ffa460b50fe0c83e80b66e8fd170cb52ae06d282f`.
  All 159 compiled library classes match the runtime JAR.
- Demo SHA-256: `67e9e7e23d499df337ea904116ad43bdeba866ecd1a0713b32bcc9c16ee11ae4`.
  All 81 compiled demo classes match the demo JAR.
- All 12 protected submitted snapshot files are unchanged. Final source/JAR/consumer records:
  `build/audit/adoption/final-verification.json`. Independent final smoke exited zero and preserved
  normal settings/options and the old bundled library file.
