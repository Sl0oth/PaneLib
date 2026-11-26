# Compatibility and testing

A separate mod built against the download for every listed Minecraft version. All 35 ran in Minecraft on Windows, macOS and Linux. The tests opened screens, edited settings, moved HUDs and checked what appeared on screen. Sodium passed on all 35. Iris passed on all 33 with a matching build. In the Windows performance runs, a HUD left alone for 3,185 frames needed zero repaints or uploads.

Both Linux configurations were virtual. One used Mesa llvmpipe on x86-64 and the other used SVGA3D on ARM64. Linux Pinyin selection and Windows Unicode editing passed on every game. Minecraft’s narrator produced recorded speech on all 35 Linux versions, and on 1.21.4 and 26.1.2 on Windows and macOS. Physical keyboards, Windows IME candidate selection, human listening, NVDA, VoiceOver and bare-metal Linux GPUs remain untested.

On Minecraft 1.16.3 and 1.16.4 only Sodium has a matching release. The 1.17 Iris release uses the Sodium fork bundled by its author. On 1.16.5 with macOS, Iris 1.2.7 passed. Iris 1.4.5 hit the same shader compilation error with and without PaneLib. On 1.20.1 with Sodium 0.5.13, the vanilla launcher’s LWJGL 3.3.1 passed. On ARM64 Linux 26.1.x we reproduced a shutdown crash in the bundled OpenAL library on stock Fabric. All three games exited cleanly with a private OpenAL Soft 1.25.2 build with hardware audio disabled. See the starter guide for the older ARM64 native versions.

The Windows idle-HUD measurement covered 31 Minecraft versions on an RTX 2080 SUPER. After 24 screen open/close cycles per version, zero closed screens or PaneLib surfaces remained in the measured set. In four matched 26.1.2 runs at a 60 FPS cap, the median frame was 16.67 ms for both the earlier and final backport. Both saw occasional long first paints during repeated opening.

Per-version results below cover the exact downloaded JAR. “Sildur” means Sildur’s Enhanced Default was tested separately in addition to the Iris compatibility check.

| Minecraft | Sodium | Iris | Sildur |
|---|---|---|---|
| 26.1 | Passed | Passed | Not tested |
| 26.1.1 | Passed | Passed | Not tested |
| 26.1.2 | Passed | Passed | Passed |
| 1.21.11 | Passed | Passed | Not tested |
| 1.21.9 | Passed | Passed | Not tested |
| 1.21.10 | Passed | Passed | Not tested |
| 1.21.7 | Passed | Passed | Not tested |
| 1.21.8 | Passed | Passed | Not tested |
| 1.21.6 | Passed | Passed | Not tested |
| 1.21.5 | Passed | Passed | Not tested |
| 1.21.4 | Passed | Passed | Passed |
| 1.21.2 | Passed | Passed | Not tested |
| 1.21.3 | Passed | Passed | Not tested |
| 1.21 | Passed | Passed | Not tested |
| 1.21.1 | Passed | Passed | Not tested |
| 1.20.5 | Passed | Passed | Not tested |
| 1.20.6 | Passed | Passed | Not tested |
| 1.20.3 | Passed | Passed | Not tested |
| 1.20.4 | Passed | Passed | Not tested |
| 1.20.2 | Passed | Passed | Not tested |
| 1.20 | Passed | Passed | Not tested |
| 1.20.1 | Passed | Passed | Not tested |
| 1.19.4 | Passed | Passed | Not tested |
| 1.19.3 | Passed | Passed | Not tested |
| 1.19.2 | Passed | Passed | Not tested |
| 1.19.1 | Passed | Passed | Not tested |
| 1.19 | Passed | Passed | Not tested |
| 1.18.2 | Passed | Passed | Passed |
| 1.18 | Passed | Passed | Passed |
| 1.18.1 | Passed | Passed | Not tested |
| 1.17.1 | Passed | Passed | Not tested |
| 1.17 | Passed | Passed | Passed |
| 1.16.5 | Passed | Passed | Passed |
| 1.16.4 | Passed | No matching build | Not tested |
| 1.16.3 | Passed | No matching build | Not tested |
