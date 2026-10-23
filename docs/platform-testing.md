# Platform testing

The build workflow runs Windows, macOS and Ubuntu jobs. The automated build gate passed on
October 21, 2026 at source commit `e9fbc41d3d0fcf17a5a9eeec5d9f2619203847df`:
[Unicode/platform run 39665812338](https://github.com/Sl0oth/PaneLib/actions/runs/39665812338).
The preceding portability-only [run 39664808951](https://github.com/Sl0oth/PaneLib/actions/runs/39664808951)
also passed. No release or Modrinth upload was performed then; that beta.2 development line was later
released as 1.0.0-beta.1.

| Hosted runner | Java unit tests | Release tooling | Fixture tooling | Independent consumer |
| --- | --- | --- | --- | --- |
| macOS 26 ARM64 | 1,265 passed | 61 passed | 21 passed | Built |
| Windows Server 2025 x64 | 1,265 passed | 57 passed, 4 skipped | 21 passed | Built |
| Ubuntu 24 x64 | 1,265 passed | 61 passed | 21 passed | Built and Minecraft smoke passed |

The four Windows skips are explicitly POSIX process-group integration tests. Mocked Windows
process handling is a separate check; these skips do not prove real Windows client-process cleanup.
All ten Ubuntu Minecraft fixture steps passed under Xvfb/software rendering, including the expanded
84-check Unicode editing fixture. Local macOS checks also passed that editing fixture and the
independent binary consumer's editing, Save/persistence, Cancel, resize and reopen flow.

The tested beta.2 runtime SHA-256 is `7f246d3e08d3df526873f99b4a506fcf3e551ca31ec5ee6f1d2c3ef753ff2938`
on macOS/Linux and `4fae8c52b0980f5f5bbc898a6134d35b0ddb53b15850058781e97238fb86dc3a` on Windows.
Those hashes predate `.gitattributes`; since it, the Windows desktop run below built the published runtime byte for byte. Still, do not claim byte parity for every archive or toolchain. A detailed comparison of the preceding beta.1
baseline found all 159 compiled classes identical; only CRLF endings in LICENSE and mixin JSON
changed the runtime archive. Source/Javadoc archives also had line-ending differences. The current
matrix retains each platform's own hashes; release-byte reproducibility is a separate concern.

## Automated scope

Every platform uses Java 25, Python 3.12 and the repository's Gradle wrapper to:

- Build the library and demo, execute library unit tests, and check the library artifact boundary.
- Run the offline release and fixture tooling suites; their platform-specific skips remain visible.
- Compile the independent starter against that job's exact runtime JAR, without project-source linkage.
- Retain command logs, JUnit reports, source revision, runner OS/architecture/image, Java/Python
  versions and SHA-256 hashes of produced library, demo and starter JARs.

Artifacts are named `panelib-platform-<runner>` and `panelib-build-<runner>`. Compare the recorded
revision and runtime hashes when combining evidence; merely recording hashes does not establish
byte parity between operating systems. Failed jobs also preserve available diagnostics. Matrix
fail-fast is disabled so one failure does not hide the other platforms' results.

Only the Ubuntu job runs the existing graphical Minecraft suite and starter smoke test, using
software rendering under Xvfb. Its display init script is installed only in that ephemeral job;
Windows and macOS build jobs do not launch Minecraft. Linux reuses its nonvisual build outputs.
Software-rendered checks establish the exercised behavior, not desktop GPU performance or visual
quality on other systems. Offline process/path mocks are not real Windows client evidence.

The workflow supports manual dispatch as well as pushes and pull requests. Actual runner details
are recorded because `*-latest` labels can change. The matrix and cross-platform Python shell use
[GitHub's workflow syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax).

## Windows desktop checks

On October 22, 2026, a real Windows desktop ran the automated client suite at source commits
`a1e1667` to `03aba39`: Windows 11 Pro x64, AMD Ryzen 7 3700X, NVIDIA GeForce RTX 2080 SUPER (driver
32.0.15.9649), 3440x1440 at 100% display scale, Temurin Java 25.0.4.1, Minecraft 26.2 and Fabric
Loader 0.19.3. Clients ran in the signed-in desktop session, not a hosted runner.

| Check | Result |
| --- | --- |
| Library unit tests | 1,310 passed |
| Release tooling, `prepareRelease verifyRelease`, as an ordinary (non-elevated) user | 56 passed, 7 skipped (4 POSIX-only, 3 needing symlink rights); candidate staged and verified |
| Published 1.0.0-beta.1 runtime and demo JARs in a clean production client | PaneOS 38 and launcher 33 checks passed |
| Development fixtures (16, from `exampleTest` to `adoptionTest`) | All passed, including the 84-check editing fixture with Ctrl shortcuts |
| Independent starter consumers, bundled JAR and unpacked Maven | Both built; Minecraft smoke passed; nested JAR matched |
| Window and in-world HUD fixture (`windowHudTest`) | 71 checks passed: maximize, restore, fullscreen and GUI scale 2, then a HUD in a fresh survival world |

PaneOS on this machine at 1512x945: a full repaint averaged 31.7 ms, a scoped click 25.1 ms, window
drag 27.3 ms per step with no full repaints, opening and closing windows 9.3 and 11.8 ms per frame,
and dark mode 141.6 ms.

The run found and fixed three Windows problems:

- Git for Windows checked text out with CRLF line endings, so the runtime's `fabric.mod.json`
  differed from its processed copy and packaging failed. `.gitattributes` now keeps LF everywhere.
- Candidates were staged with `mkdtemp`, whose owner-only ACL hid a candidate made from an elevated
  shell from the same user's ordinary processes. Candidates are now ordinary folders.
- Three packaging tests created symlinks, which ordinary Windows accounts may not do without Developer
  Mode. They now skip with that reason, as the POSIX-only tests do.

Built on Windows, the runtime JAR is byte-identical to the published `1.0.0-beta.1` runtime
(`eba50cad…5d68`), and the sources and demo JARs match the macOS build. Javadoc differed only
because the machines had different Java 25 updates.

`windowHudTest` keeps the launcher open while it maximizes, restores, enters and leaves fullscreen and
changes GUI scale. After each change it checks that the surface is drawn at the GUI area's full
resolution, every target is on screen, and a click sent through Minecraft's own mouse handler opens
Pane Mail. In a fresh survival world it counts vanilla's hotbar, health, food and crosshair renderers:
they draw without a PaneLib HUD, stop while the Wayfarer HUD replaces them, and return when it is
toggled off with J or removed. The HUD stays on screen across a GUI-scale change and hides with F1.
The PC was reached through Parsec, so the active display was Parsec's virtual 1920x1200 monitor; it
passes the same way on macOS ARM64.

### 150% display scale

With Windows display scale at 150% (GLFW content scale 1.50), every fixture above passed again: the 16
development fixtures and `windowHudTest`, with no failed check. Windows reports the window in physical
pixels, so the framebuffer matched the window and Minecraft chose GUI scale 3 automatically; PaneLib's
surfaces stayed at full resolution at every size. PaneOS timings matched 100% within a few
milliseconds (full repaint 33.6 ms, scoped click 25.8 ms, window drag 28.1 ms).

Screenshots from that run showed a library bug on both platforms: default tooltips on a light theme
drew the theme's dark text on their near-black plate, so the launcher's card tips were empty pills.
Unset tooltip text now switches to a readable colour; `windowHudTest` hovers a card and captures it.

Still to try by hand: moving between monitors with different DPI, and actual narrator and IME behavior.
macOS and Linux desktop/GPU combinations also need their own recorded runs; an automated build or
Ubuntu Xvfb pass cannot substitute for them.
