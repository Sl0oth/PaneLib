# Editor and pixel transfer performance — October 21, 2026

Status: development changes for unreleased `0.9.9-beta.2`. Existing public releases are unchanged.
This pass removes redundant work without changing public APIs, image resolution, text shaping,
caret geometry, animation cadence or feature availability.

## Changes

- Textareas skip selection drawing for lines outside their viewport. Previously a long selection
  constructed highlight shapes and graphics contexts for thousands of fully clipped lines.
- Editor registration avoids intermediate copies when coordinates do not move. An immutable input
  region is reused when clipping leaves its bounds unchanged. Constructors and array accessors
  still make defensive copies; the full keyboard geometry remains available outside the viewport.
- ARGB-to-ABGR conversion uses integer rotation and byte reversal instead of three masks/shifts.
  It preserves the source and destination buffer position, needs no scratch array, and leaves the
  texture upload dimensions and submission path unchanged. A separate bulk-copy experiment was
  slower and was discarded.

## Matched CPU workloads

Same Java 25/macOS ARM64 environment, font and workload, with a saved pre-change runtime JAR
from implementation `e9fbc41`. The editor benchmark uses 20 warmup and 30 measured rounds with
randomized case order and retained text layout. Times below are median elapsed repaint durations
in a CPU-only Java2D harness, not Minecraft frame times. Allocation counts are per repaint.

| Workload | Before | After | Allocated before → after |
| --- | ---: | ---: | ---: |
| Short field, no translation | 0.097 ms | 0.088 ms | 4,536 → 3,624 bytes |
| 17,920-unit field, no translation | 0.348 ms | 0.324 ms | 576,888 → 289,800 bytes |
| 2,000-line area, no selection | 0.775 ms | 0.624 ms | 3,094,400 → 1,301,512 bytes |
| 2,000-line selected area | 1.889 ms | 0.716 ms | 7,206,400 → 1,322,600 bytes |
| Selected area, fractional translation | 1.856 ms | 0.764 ms | 7,303,280 → 2,211,816 bytes |
| Selected area inside clipped scroll | 1.796 ms | 0.711 ms | 7,304,552 → 2,189,208 bytes |

All 12 editor cases retain identical pixel and full input-geometry SHA-256 fingerprints, including
pointer-hit samples. Small field timing differences are too small to claim a general speedup;
the substantial wins are long selection painting and reduced allocation. The harness repeatedly
paints a fixed warm value; it does not establish cold typing, paste or shaping latency.

The pixel benchmark uses the real transfer function with a direct buffer, 80 warmup rounds and
80 measured rounds of eight transfers. Median CPU-work elapsed time:

| Transfer | Before | After |
| --- | ---: | ---: |
| Full 1,708 × 960 | 421.265 μs | 277.010 μs |
| 800 × 350 subregion | 81.963 μs | 55.442 μs |
| 150 × 30 subregion | 1.166 μs | 0.619 μs |
| 380 × 220 HUD | 24.406 μs | 13.812 μs |

All four destination hashes match, source pixels are unchanged and measured transfer allocation is
zero before and after. These are cache-hot CPU transfers; they do not measure the GPU or promise
the same speedup on another architecture.

## Actual Minecraft comparison

Separate sequential client runs used the existing Atlas benchmark and JFR. Both used an 854 × 480
window, 1,708 × 960 framebuffer, GUI scale 4, 120 FPS cap, VSync enabled and render distance 12.
Two warmup cycles precede four measured UI cycles (~18 seconds), followed by ~8 seconds of HUD
measurement in a disposable local world. Both completed the Studio interaction checks.

| Measurement | Before | After |
| --- | ---: | ---: |
| UI upload CPU submission median / p95 | 0.810 / 1.552 ms | 0.691 / 1.371 ms |
| HUD upload CPU submission median / p95 | 0.113 / 0.158 ms | 0.102 / 0.139 ms |
| UI frame interval p95 / p99 | 15.597 / 23.429 ms | 15.340 / 23.143 ms |
| HUD frame interval p95 / p99 | 10.064 / 15.604 ms | 10.686 / 15.344 ms |

Upload CPU cost fell in this comparison. Overall frame intervals are roughly unchanged and the
HUD p95 increased: this is **not** evidence of a general FPS improvement. UI paint counts differed
(704 versus 719) because the workload is time-driven. Atlas does not exercise long text editors;
its allocation rate is essentially unchanged. The baseline JFR identified pixel transfer in 11 of
21 sampled PaneLib Java stacks during measured UI phases. That small Java-only sample motivated
the transfer experiment; it is not a percentage of total client CPU cost or native raster work.

## Reproduce

Save the old runtime JAR before editing, then use the same harness against both versions:

```sh
./gradlew :pane:editorPaintBenchmark -PeditorBaselineJar=/absolute/path/before.jar
./gradlew :pane:editorPaintBenchmark
./gradlew :pane:pixelTransferBenchmark -PpixelBaselineJar=/absolute/path/before.jar
./gradlew :pane:pixelTransferBenchmark
./gradlew :pane-demo:runClient -Pbenchmark -Pprofile -PbenchLabel=before-opt
# Run the same command after rebuilding, with -PbenchLabel=after-opt.
python3 tools/compare-atlas-benchmarks.py before-opt after-opt
```

Run each client/benchmark alone. The optional benchmark tasks have no timing thresholds and add
no runtime dependencies. Logs report environment and fingerprints; compare only matched runs.
JFR files are under `build/performance`; client CSVs are under `pane-demo/run-benchmark`.

## Correctness and limits

The local Java suite passes **1,280 tests**. New coverage includes defensive array ownership,
translated and clipped fields/passwords/textareas, full keyboard versus clipped pointer bounds,
and 360 randomized heap/direct-buffer transfer rectangles including empty regions. A fresh local
source review found no blockers.

Actual macOS Minecraft checks also pass: editing (84 checks), editor viewport behavior (24),
review regressions (60), and the idle/toast performance fixture. Three-second idle, steady-toast
and post-toast-idle windows each rendered 177 frames with **zero retained paints and zero uploaded
pixels**. Toast expiry updated only its own layer; the base surface stayed cached. These checks
use real client handlers, with synthetic input, in isolated run directories.

Windows desktop performance remains untested. These measurements do not establish IME/narrator
support, universal smoothness, lower total system memory, or a particular FPS target. Large text
areas still retain full navigation geometry; further changes need separate measurements and
navigation tests rather than removing that behavior to improve a benchmark.
