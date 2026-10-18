# Active Atlas performance follow-up — 2026-10-16

This pass profiles the existing Atlas motion and live Field HUD without adding effects or
changing animation durations, frame limits, antialiasing, layout, or controls. The already
running interactive client stays open on its existing classes; the updated code is tested
in separate development clients.

## Kept renderer changes

- `UiLayer` uses premultiplied Java2D scratch/cache pixels. Minecraft-facing main, row,
  tooltip, toast, and HUD texture surfaces retain their existing straight-alpha format.
- Cached layers scan their alpha bounds once when their revision changes. Later draws transform
  a shared-storage crop with a transparent guard pixel, preserving coordinates and bilinear
  edge sampling. Empty caches skip composition. Cache bounds and revision behavior stay the same.
- This reduces CPU compositing work; it does not replace the CPU renderer with a GPU scene graph.

A trial with premultiplied main/HUD surfaces was discarded: converting the pixels back for
Minecraft's straight-alpha texture path erased the drawing gain. No such upload conversion
remains in the shipping library.

## Measurement method

The dev-only `AtlasBenchmark` runs two warm-up cycles, four measured copies of the existing
expansion/reversal/retarget/sequence/input script, then an actual-world HUD phase with health
changes. It stores preallocated per-frame samples and marks measured phases in JFR. Screenshots
are disabled during measurement. A fresh copy of the same closed disposable test-world fixture
is used for each final run; the user's interactive world is not opened or copied.

Configuration: Apple M4 Pro, 48 GiB system RAM; Java 25; Minecraft 26.2; 854×480 window points,
1708×960 framebuffer, GUI scale 2, 120 FPS cap, VSync off, render distance 16, simulation distance
12. An existing interactive development client remains open during both measurements. Its
concurrent CPU/GPU load is a limitation; these are local comparative measurements, not isolated
hardware certification or a low-end-PC performance claim.

Frame intervals measure successive client extraction starts, including intervening Minecraft
work. They are not GPU presentation/fence timestamps. Extraction timings cover Pane's screen
work; raster/upload timings cover paints only. Upload timings measure CPU submission, not GPU
completion. Thread allocation counters cover the timed render-thread regions; sampled heap/GC
values cover the entire Minecraft process and cannot establish Pane's retained-memory footprint.

## Results

The accepted pair is `before-ordered` → `after-ordered`. Both passed all six scripted motion
cycles, including the corrected Sequence action ordering. The UI phase covers approximately
17.96 seconds per run. The earlier comparison files remain as exploratory evidence.

| UI metric | Before | After |
| --- | ---: | ---: |
| Client frame interval p50 | 17.431 ms | 11.183 ms |
| Client frame interval p95 | 23.088 ms | 18.869 ms |
| Client frame interval p99 | 25.436 ms | 22.423 ms |
| Pane extraction p95, all frames | 10.115 ms | 7.912 ms |
| Pane extraction p99, all frames | 11.767 ms | 9.647 ms |
| Pane extraction p95, painted frames | 11.019 ms | 9.117 ms |
| Paint raster p95 | 9.086 ms | 7.158 ms |
| Paint upload submission p95 | 2.078 ms | 2.063 ms |
| Mean paint raster | 8.108 ms | 6.122 ms |
| Uploaded pixels | 677,915,866 | 660,306,330 |
| Upload throughput | 144.03 MiB/s | 140.26 MiB/s |
| Render-thread allocation | 2.155 MiB/s | 2.148 MiB/s |
| UI frames / main paints | 1,208 / 690 | 1,466 / 678 |

The principal improvement is CPU raster/composition: extraction p95 falls **21.8%**, mean paint
raster falls **24.5%**, and frame interval p95 falls **18.3%**. All-frame extraction includes cheap
retained/no-paint frames; the painted-frame results confirm this is not only a changed ratio of
cheap frames. Paint scheduling still uses the existing 16 ms throttle. Total uploads and
allocation rate are essentially unchanged; this pass does not claim a bandwidth or allocation
breakthrough.

**A frame interval p95 of 18.87 ms still misses the 16.67 ms budget for consistent 60 FPS.**
This is an improvement, not certification of high-refresh or consistently 60 FPS operation.

JFR supports the same diagnosis: Java2D samples fell from 234/824 to 165/801, and
`TransformHelper.Transform` leaf samples from 142 to 80, over the marked UI phases. Sampling
counts are supporting attribution, not precise CPU percentages or GPU timings. A large remaining
native sample group is `glfwSwapBuffers` at Minecraft's presentation boundary; this can include
driver/presentation waiting and does not distinguish GPU execution from active CPU work.
Remaining Pane costs are alpha-filled shapes, transformed cached images, and pixel transfer.

The live HUD remained inexpensive: all-frame extraction p95 **1.066 → 1.004 ms**;
paint raster p95 **1.234 → 1.213 ms**; upload submission p95 **0.217 → 0.212 ms**;
frame interval p95 **13.852 → 12.294 ms**. HUD paint counts varied (185 → 150), so its total
upload/allocation deltas are not evidence of an equivalent per-update improvement. The retained
HUD surface path is unchanged; treat its small timing shifts as normal run variation.

Whole-process sampled peak heap during UI was **1,315 → 1,438 MiB**, and JVM GC time over the
whole run was **272 → 275 ms**. Neither demonstrates a retained-memory reduction. A separate
point-in-time sample of an earlier optimized benchmark process showed about **2.50 GiB RSS** and
108.6% CPU (one core = 100%); it includes Minecraft, native buffers, and world rendering, and has
no matched baseline RSS sample. The interactive client was still running at that check. No
low-end or Windows performance is inferred from these numbers.

The further small trial (premultiplied main surfaces) regressed extraction and was rejected.
The remaining expensive native presentation path cannot be safely optimized through a local
Pane drawing tweak. A larger next step would be GPU composition of moving retained layers plus
GPU/presentation timing under controlled foreground load. That is a renderer change requiring
separate API, clipping, input, and multi-platform validation; it was not speculatively added here.

## Validation and platform limits

- **48 unit tests passed**, including cache crop/storage sharing, group opacity, transforms,
  input geometry, and the existing pixel-transfer regression.
- Deterministic translucent/rotated text, shapes, shadows, and overlap comparison on dark/light
  backgrounds: maximum visible channel difference **1/255**, mean **0.0172/255**. Images are in
  `pane/build/render-check/`; the crop uses the same backing storage.
- **31 live Studio PASS markers**, including actual movement/attack input, H/J bindings, health,
  reduced motion, viewport/GUI scale, F1, screen hiding, zero idle paints, and unload/reload.
  Actual client screenshots were captured and the console was visually inspected.
- Artifact boundary check passed. **49 broader client PASS markers**, including continuous and
  virtual scrolling, recycled-row input, resize, idle retention, and GPU readback:
  **6,562,560 bytes checked, zero differences**.

One earlier optimized measurement (`after-final`) reported a spring-retarget assertion failure
in its sixth motion cycle. It is excluded from the accepted pair. The full Studio flow and the
subsequent six-cycle run passed without that failure. Added click/retarget diagnostics did not
reproduce it; its cause is **unresolved**, not certified fixed. A separate concrete harness bug was
found: tick 76 switched mode and clicked the same retained button immediately, dispatching the
previous mode's action. The harness now waits for a new paint, checks the Sequence revision
increment, and asserts delivery before its next phase. Both comparison runs use this corrected
script. That ordering bug is not established as the cause of the earlier tick-76 retarget assertion. The failed raw log/recording are
preserved. This limits confidence in the repeated synthetic input harness and is not silently
counted as a passing run.

Windows runtime validation remains outstanding. Read-only discovery found Parallels unable to
connect to its service, zero running Fusion VMs, and only a Linux guest in the checked default
Fusion directory. No Windows guest was confirmed; guests elsewhere are not ruled out. No VM was
started, repaired, installed, or purchased. The changed native renderer uses platform-neutral
Java2D images and existing Fabric/Minecraft APIs, with logical font fallbacks. Static review and
a Mac build are not Windows GPU/input proof.

## Reproduction and evidence

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-25.jdk/Contents/Home \
  ./gradlew :pane-demo:runClient -Pbenchmark -Pprofile -PbenchLabel=sample
```

The benchmark uses `pane-demo/run-benchmark`, separate from the interactive `run` directory.
For a matched comparison, restore the same disposable world fixture and options before each run.
Never run a second client against an open save. Normal functional checks can use `-Pisolated`
with `-PstudioTest` or `-Pautotest -PautotestExit` to use this separate directory as well.

Raw logs and JFR recordings: `build/performance/`. Raw frame CSVs:
`pane-demo/run-benchmark/{label}-{ui,hud}.csv`. JFR summaries can be regenerated with Java's
`jfr print --json` selecting `dev.pane.AtlasPhase`, `jdk.ExecutionSample`,
`jdk.NativeMethodSample`, and `jdk.ObjectAllocationSample`, then passing the JSON to
`python3 tools/analyze-atlas-jfr.py <events.json>`.

Changed renderer files in this follow-up: `UiLayer.java`, `UiCanvas.java`. Added/updated test and
profiling support: `AtlasBenchmark.java`, `StudioAutoTest.java`, `ShowcaseHub.java`,
`PaneDemoClient.java`, `pane-demo/build.gradle`, `UiLayerTest.java`,
`UiPremultipliedRenderTest.java`, `tools/analyze-atlas-jfr.py`, and
`tools/compare-atlas-benchmarks.py`. Other workspace changes predate this follow-up. Nothing
was committed or published; the interactive client's loaded code was not restarted or replaced.
