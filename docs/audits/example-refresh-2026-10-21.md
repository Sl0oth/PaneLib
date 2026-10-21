# Curated examples, portable launches and scaling cleanup — October 21

The public lineup is now Wayfarer (menu and HUD), Gather, Pane Mail and a small vanilla HUD recipe. The independent first-screen consumer remains the starting point for a new mod.

## What changed

- A new cream/ink example shelf replaces Atlas as the default landing screen. It has real example previews, semantic keyboard targets, responsive document scaling and an explicit HUD-off action.
- YouTube is archived outside the compiled source set; its keybinding, Gradle flag and dedicated obsolete fixture branches are removed. Its four migration assertions were removed from the popup suite; the remaining 36 library assertions still pass. Gather separately verifies a real migrated server menu.
- Briarkeep/Bramble launch buttons and unused old reference/skyport art are archived. Atlas/design/texture fixtures remain developer tools, with no public hotkeys. No library features were removed.
- Six Fabric Loom run configurations declare Java 25 and dedicated profiles. Normal launch needs no Python, frozen runtime, cached argfile, fixed JAR name or private save. A user still installs Java 25; this does not install a JDK automatically.
- The live HUD controller owns only the selected recipe. Its sampling path never invokes capture staging, changes inventory/game mode, or opens a save. Worlds are created/joined through Minecraft normally. J toggles, H opens examples; switching/off closes the former owner.
- Gather, Wayfarer and the shelf use `UiScaling.reference(...).adaptivity(1).range(...)`, removing per-control coordinate/font/input scale multipliers. Wayfarer route photos use filtered image fitting. Mail keeps fixed point sizing deliberately to show more rows on larger windows.
- Gather has six original personal avatar illustrations shared between chat and settings. Public recipes share one bundled font registration, avoiding repeated font parsing and global font-cache invalidation on every open.
- Mail's scroll regression now requests a genuinely overflowing compact window. The previous full-window assumption was a fixture error, not a reason to force unnecessary scrolling in the application.
- Current READMEs explain live usage, supported APIs and fictional content. Earlier capture notes are retained under `examples/archive/notes/`.

## Verification

Actual isolated Minecraft 26.2 clients on macOS ARM64 / Java 25:

| Fixture | Passing checks |
| --- | ---: |
| Public gallery, compact layout, fresh-world HUDs, H/J and HUD off | 33 |
| Wayfarer interactions/player menu | 28 |
| Gather messages/settings/popups/compact layout | 34 |
| Pane Mail compose/search/folders/scroll | 24 |
| Generic styled popup triggers | 36 |
| Historical Studio regression and developer H/J | 30 |
| **Total** | **185** |

The independent first-screen consumer also passes input/slider/keyboard Save/disk persistence, resize, parent restore and reopen against the packaged JAR. A fresh unit-test execution passes 157 tests with zero failures/errors/skips. Current library/demo class bytes match their built JARs (158/80 classes); archives have valid CRCs and no VideoSiteShowcase class. All 12 submitted snapshot files match their starting hashes.

Evidence: `build/audit/example-refresh/final-verification.json` and sibling logs. Actual final captures: `pane-demo/run-examples/gallery/screenshots/` (shelf, compact shelf, each menu, live HUDs, vanilla restored). Screenshots were visually inspected, including original avatars and text clarity. Initial fixture assumptions about empty-inventory item counts and a few compile mistakes were corrected before the final passing runs.

Library SHA-256: `7b6ad6514db13aa4dba253d21277fa2748c35c218315d1b8106359cd8cf29c31`.
Demo SHA-256: `bfbc820f5306835585958c6f6950e3b34e33ec177321de0c905269aa43841946`.

## Limits / continuation

D02's public-launch acceptance criteria and the retained examples' D03 scaling migration are met. Internal frozen-client fixture helpers remain separate and partially duplicated; older deterministic HUD fixtures still depend on a private copied save. Consolidating that developer harness is a remaining tooling cleanup, not a requirement to launch the public examples.

No Windows/Linux runtime, multiplayer compatibility or narrator/IME proof is added here. The normal Gradle launches use Loom's platform handling and Java toolchain discovery, but were executed only on macOS. Fresh-world checks use a new disposable creative world, not a production server. Wayfarer remains fictional UI; mail/chat actions remain local. This is not a full title-screen replacement across every future disconnect.

The library JAR is unchanged from W11. No version bump, publication, release staging, external review or changes to the protected submitted snapshot occurred.
