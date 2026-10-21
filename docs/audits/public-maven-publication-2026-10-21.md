# Public Maven publication — October 21, 2026

PaneLib `0.9.9-beta.1` is published as a GitHub prerelease and available anonymously from
`https://sl0oth.github.io/PaneLib/maven/`, coordinate `dev.pane:panelib:0.9.9-beta.1`.
This completes the public-distribution portion of phase 1. No Modrinth update was performed.

## Source and immutable artifacts

- Release tag `v0.9.9-beta.1` targets reviewed source commit
  `d4ecb1c37daeca9b6a1ad77fb386435443827304`.
- The isolated publication checkout started from public main. Only reviewed build/source/docs and
  required licensed example resources were added. Private saves, reference pictures, local review
  notes, marketing exports, caches and test TLS keys were excluded.
- A clean build passed 157 library unit tests and 58 release-tooling tests. All nine release files
  reproduced byte for byte. Existing candidates and the submitted 0.9.8 snapshot were preserved.
- Fixed release-task ordering: when both tasks are requested, `verifyRelease` now runs after
  `prepareRelease`. Standalone verification still checks existing files without rebuilding them.
- Runtime SHA-256: `decec47b8faf4aab6893620b290f84195bfd7f5842a79bc4575a8b32ddae9a55`.
- Maven ZIP SHA-256: `c9e06601ba095657daf55a53a06be392a7c222f63d100ceafa43321eaeb42931`.
- Release bundle notes remain the original preparation-stage notes. The GitHub release page and
  current installation documentation record the completed publication separately.

## Public proof

1. [Bootstrap deployment](https://github.com/Sl0oth/PaneLib/actions/runs/39654020138) passed and
   served the empty catalog over public HTTPS.
2. The approved Maven ZIP was downloaded anonymously from GitHub Releases and matched its pinned
   hash and byte size before the catalog entry was committed.
3. [Version deployment](https://github.com/Sl0oth/PaneLib/actions/runs/39654307146) passed, serving
   the complete pinned version and catalog alongside the documentation.
4. The public consumer verifier fetched all 25 immutable version files using verified HTTPS,
   without credentials or insecure TLS fallbacks. Every byte matched the candidate repository.
5. Two independent starter projects were extracted. Maven mode removed its bundled `libs/`
   directory; the file consumer used its packaged runtime. Both built successfully. Loom's nested
   runtime bytes and the consumer's exact Fabric dependency matched the approved version.
6. Both actual Minecraft clients passed the Save/persistence, input, resize, Cancel/Escape and
   reopen smoke checks on macOS ARM64, Java 25, Minecraft 26.2. Dependency caches were reused;
   fresh anonymous endpoint checks were separate from Gradle's cache behavior.

## CI follow-up and limits

The initial Ubuntu CI build, unit tests, artifact check and independent consumer compilation passed.
Its first client fixture passed 56 checks but failed the low-memory upscaling assertion: that probe
requested 1x rendering on a 1x display, below the preset's 1.5x ceiling. Retina had accidentally
provided the missing precondition in local runs. The fixture now requests 2x UI scaling, explicitly
checks the desired scale exceeds the ceiling and that actual scale respects it, and retains the
original upscaling assertion. All 59 checks passed in an isolated local Minecraft client. This
changes test setup only, not the published library JAR. The same 59 assertions then passed in
Ubuntu CI, but its Gradle output-capture gate rejected an empty capture.

Inspection of pinned Loom 1.17.19 identified its automatic Linux/CI Xvfb launcher: it starts a
nested display even when the workflow supplies one, and its alternate execution path does not
forward configured stdout/stderr capture. Both workflows now install a job-local init script
which sets Loom's supported `useXvfb` property to false and requires the workflow's `DISPLAY`
before `runClient`. The ordinary JavaExec capture, required PASS markers, FAIL detection, timeouts
and nonzero-exit checks remain. Nested starter runs inherit this CI configuration without changing
the released starter. A real local client passed all 59 assertions through this path, while an
injected FAIL still caused the Gradle task to fail. A missing display is rejected before launch.
The full Ubuntu rerun is recorded separately; these targeted results do not imply platform-wide
certification.

This distribution check does not certify Windows desktops, all Linux GPU drivers, narrator/IME,
all modpacks, long-term host availability or a final 1.0 API guarantee. Pages remains static hosting
with its documented size/build/bandwidth limits. Published versions must never be replaced.

Local machine-readable evidence is under `build/audit/publication/` (not a release asset).
The public consumer workspace suffix is `6deae1607f41420398af980fcd013698`. Its verifier record's
`published: false` means that the verification command itself does not publish; publication is
established by the separate release/deployment evidence above.
