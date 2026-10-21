# Platform testing

The build workflow now defines Windows, macOS and Ubuntu jobs. This configuration is not a
passing result: record the actual workflow run and all three job outcomes before claiming a
cross-platform build gate. No new release or binary upload is performed by these checks.

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
