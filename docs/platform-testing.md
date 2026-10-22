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
Do not claim cross-platform archive byte parity. A detailed comparison of the preceding beta.1
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

## Windows desktop checks — pending

Use a disposable profile and record the exact PaneLib SHA-256, source commit, Minecraft/Fabric/Java
versions, Windows version, GPU/driver, display scale and test date. Keep logs and representative
screenshots with a pass/fail result for each check:

- Start the standalone consumer with Java 25; open settings, edit, Save, close and reopen. Confirm
  persistence, Cancel, Escape and parent-screen restoration.
- Resize, maximize/restore and change Minecraft GUI scale. Exercise Windows display scaling and,
  when available, moving between monitors with different DPI. Check text clarity and pointer alignment.
- Test keyboard focus, Tab/Shift-Tab, clipboard copy/paste, selection, undo/redo and multiline scrolling.
- Open and dismiss menus/dialogs; verify focus restoration and that background controls stay inactive.
- Reload resources and check bundled-font fallback, native Minecraft text, items and player previews.
- Enter a scratch world, toggle each public HUD, change GUI scale, leave the world and confirm vanilla
  HUD restoration. Record any renderer/modpack additions; the base configuration is a separate result.
- Check actual narrator behavior and IME input separately. Existing limitations remain; passing ordinary
  text entry does not establish narration, grapheme correctness or IME composition support.

Windows hands-on results remain pending until performed. macOS and Linux desktop/GPU combinations
also need their own recorded runs; an automated build or Ubuntu Xvfb pass cannot substitute for them.
