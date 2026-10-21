# PaneLib grade improvement plan — October 21, 2026

Status: phase 1 local packaging and independent-consumer gate implemented and verified on macOS.
Public hosting and download verification remain open; phases 2–4 have not started in this pass.
Hosting implementation and a real local HTTPS consumer check are now prepared; public deployment
awaits approval. See [Maven hosting preparation](maven-hosting-2026-10-21.md).
See [release packaging evidence](release-packaging-2026-10-21.md). The original planning pass changed
no runtime or release identity; implementation now uses local candidate 0.9.9-beta.1 while preserving
the submitted snapshot. Grades are qualitative audit judgments, not benchmarks.
Basis: current source, NEXT_SESSION.md and developer-adoption-2026-10-21.md.

## Working rules

Finish one acceptance gate before starting the next implementation. Preserve existing public APIs
and features; add compatible defaults or an explicit migration when unavoidable. Keep the submitted
0.9.8 snapshot immutable. Every material fix needs the smallest relevant regression, a real example,
and real-client verification where behavior depends on Minecraft. Fix library causes rather than
hiding defects inside examples. Use fresh reviewers after each substantial phase.

## Phase 1 — installation and distribution (C+)

First implementation task: make one authoritative release identity drive library metadata, Maven
coordinates, starter dependencies, docs snippets and artifact filenames. Add a release assembly/check
task that produces matching runtime, sources, Javadoc, starter and checksum manifest, with no private
paths/settings/saves. Preserve older bundles instead of overwriting them. Prepare the next version
as a candidate only after checking existing release identities; do not invent a final published version.

Exercise the existing Maven publication against an isolated local repository first, including a clean
consumer that resolves the module and an optional nested dependency. Reject metadata/version mismatch.
Then configure an anonymous-read public Maven endpoint with controlled authenticated publishing,
immutable versions and no secrets in source. Select the host after checking existing project accounts
and constraints. Document separate installation and bundling from that same release identity.

Acceptance: a clean consumer resolves the exact module, builds, opens the settings screen, saves and
reopens it; nested JAR hash matches; ZIP contents and dependency metadata agree. Local dry-run completion
is separate from actual public download verification. Public publication remains a separate final action.

## Phase 2 — platform coverage (C)

Extend the existing Ubuntu CI configuration into macOS/Windows/Linux build, unit and independent
consumer checks. Make shell commands, paths, separators, fonts and toolchain selection portable.
Run graphical fixtures only on runners that support the required desktop context; retain per-platform
logs, runtime hashes and screenshots. Establish real desktop/GPU checks for resize, high DPI, GUI scale,
font fallback, clipboard, resource reload, menus, player preview and HUD restoration.

Acceptance: the same candidate passes on all three operating systems with recorded environment and
artifact identity. Software-rendered CI proves functional behavior only. Unavailable hardware remains
an explicit pending gate; macOS or synthetic platform tests cannot satisfy Windows/Linux validation.

## Phase 3 — accessibility and international text (C−), three sequential gates

1. Narration: inspect Minecraft's native narrator contract; add role, label, value, enabled/selected
   state and contextual hints for built-in controls, menus, dialogs and custom painted controls.
   Integrate focus and meaningful change announcements with rate/deduplication rules. Never narrate
   password contents. Ensure semantics are opt-in for custom drawing without duplicating hit logic.
   Gate: complete a settings flow keyboard-only with the actual narrator, correct focus restoration,
   useful announcements, and no speech on every repaint.
2. Grapheme-safe editing: replace codepoint-only caret, selection, deletion and truncation boundaries
   with a shared tested grapheme model where appropriate, while retaining UTF-16 API/index contracts.
   Cover combining marks, skin-tone modifiers, flag pairs, joined emoji, clipboard, undo, limits,
   wrapping and pointer placement. Use authoritative Unicode tests during implementation.
   Gate: visual characters are not accidentally split across these operations; existing editing tests pass.
3. IME: first verify which composition and candidate-placement signals Minecraft/GLFW expose on each
   platform. Prototype a supported input bridge before designing a promised public API. Support
   composition updates, commit/cancel, selection replacement, focus changes and candidate placement.
   Gate: actual Japanese/Chinese/Korean input methods tested on applicable target desktops, including
   canceled composition and focus loss. Committed character input alone is not composition support.
   If the backend needs a native extension, document that design/dependency explicitly; do not disguise
   a platform limitation with a screenshot fixture or announce completion without real input-method tests.

## Phase 4 — strengthen every remaining graded aspect

| Aspect / current grade | Work | Acceptance gate |
| --- | --- | --- |
| Reliability / A− | Target lifecycle, resize, popup/focus, resource reload and multi-owner HUD regressions; test representative mod conflicts. | Reproduce each defect before fixing; relevant packaged-client regressions pass; tested mod combinations named. |
| Testing / A− | Organize unit, independent-consumer, client, visual and compatibility suites; attach artifact/environment evidence. Add repeatable visual comparisons with tolerances. | Each promised behavior has the appropriate gate; deliberate failures are caught; transient GPU/font differences do not produce meaningless failures. |
| Efficiency / B+ | Establish idle, single-field update, animation, scrolling and dense-list baselines. Measure paint/upload work, CPU, allocations, texture memory and frame-time tails. Fix measured hot paths. | Before/after under the same scene, artifact configuration and hardware; no removed behavior or visual-quality reduction; no blanket FPS claim. |
| API usability / A− | Review recurring glue and inconsistent options. Add small shared conveniences only when several realistic consumers need them; preserve old constructors/defaults. | An independent consumer uses supported APIs without reflection, internal access or copied lifecycle workarounds. |
| Ease of adoption / B+ | Fresh moderate-developer walkthrough: install, migrate a saved/draft settings screen, add a live HUD, package and upgrade. Record where assistance is required. | Reviewer completes the documented flow; discovered missing steps are fixed. Agent review is supplemented by actual human feedback when available. |
| Documentation / A− | Build snippets in CI; maintain versioned installation/migration references and an honest support matrix. Keep current status linked to exact test evidence. | Documented code compiles against its advertised released artifact; links and commands work; historical reports are clearly dated. |
| Examples / A− | Keep the curated lineup small; annotate reusable recipes and separate illustrative data from application logic and fixture staging. | Each recipe demonstrates current public API, handles normal resize/focus/lifecycle, and accurately states what is local/sample-only. |
| Visual flexibility / A | Preserve distinct styles and test long labels, localization, scaling and custom fonts; add styling capability only for demonstrated gaps. | Several visually different examples remain readable and interactive at supported sizes without library forks or sample-only fixes. |

## Regrading and release gate

After each phase, a fresh reviewer repeats the relevant adoption flow and records a new grade with
its evidence and remaining limits. Aim for A− overall once clean distribution, three-platform evidence
and meaningful accessibility support are established; do not automatically raise a grade because code
was added. Finish with one exact candidate, matching starter/docs/checksums, regression results and a
clear release decision. This plan does not authorize uploading or claim that all listed work is complete.
