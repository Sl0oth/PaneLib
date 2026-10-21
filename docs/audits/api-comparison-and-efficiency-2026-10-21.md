# PaneLib API comparison and efficiency pass — October 21, 2026

The user authorized continuing the audit, researching other APIs and making measured efficiency
improvements without removing features. This pass stays local; no publication/version bump.

## Primary API references and decisions

- [Qt high-DPI model](https://doc.qt.io/qt-6/highdpi.html): separate logical layout units from raw
  image pixels. PaneLib already has this split; W08 now derives HUD backing pixels from the final
  displayed size while retaining explicit ceilings. No Qt dependency or code was copied.
- [JavaFX ImageView](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/image/ImageView.html):
  image aspect/fit, source viewport and smooth filtering are explicit choices. W09 adds cover,
  contain and stretch, alignment, and nearest/bilinear/bicubic filtering with shared mapping.
- [Qt BorderImage](https://doc.qt.io/qt-6/qml-qtquick-borderimage.html): nine regions preserve borders
  while resizing interiors. A nine-slice helper fits PaneLib's textured HUD use case, but is an
  ergonomic follow-up, not required to complete image sampling/placement. It is not implemented
  or silently substituted for existing art in this pass.

Avoid importing general scene graphs, layout solvers, reactive frameworks, networking or asset
projection pipelines. PaneLib's retained painter, existing layout primitives and native overlays
remain the architecture. The next valuable items are native bitmap-font integration (W11),
portable consumer examples (D02), and optional nine-slice drawing with exact corner tests.

## W08 — HUD resolution and budgets

`UiHud.setRenderSettings` reuses `UiRenderSettings`. Default `hud()` retains 2048×2048 / 2x ceilings
(up to 16 MiB for one RGBA buffer). `highQuality`, `typography`, `lowMemory` or explicit settings are
opt-in. These bounds apply per surface, not total CPU+GPU+layer-scratch memory.

HUD placement is resolved first. Desired raster scale is its final displayed physical size divided
by logical widget size, including placement magnification and viewport fit. Ceilings are then
applied; actual buffers may shrink below 1x for small fitted widgets. Integer rounding is included
in reported actual scale; painting fills the resulting buffer exactly on each axis.
`rasterQuality()` exposes desired/actual scale and dimensions; `upscaled()` exposes budget-limited
magnification. `rasterBytes()` exposes one RGBA surface size, zero after release.

Position-only changes and equal settings reuse pixels. Size/quality changes allocate only if
integer backing dimensions change. Stable frames reuse the quality record. Wayfarer opts into
highQuality (3x); default consumers do not silently receive larger ceilings. The previous W07
placement test's shrink assertion was updated: shrinking should now reclaim the oversized raster,
while moving without changing size should continue reusing it.

Final focused Minecraft result: **19 checks passed**, including default-limit reporting, 3x
physical rendering, shrinkage, memory cap, equivalent settings, GUI scale, viewport fitting, F1,
native slots and hide/show/disable/close. The sharp screenshot was visually inspected. See final
regression log/count table in `build/audit/hud-quality/final-verification.json`.

## D01 — update only the panels that consume changing state

Both Wayfarer and Bramble now project sampled data into location, vitals, equipment and navigation
records. Journal and badge are static and no longer receive unused changing state. Wayfarer keeps
fractional heading as its separate animated compass input. Bramble retains its existing coordinate
and heading displays. Actual sampling frequency and displayed fields remain unchanged.

The 14-check Minecraft fixture injects controlled state into real rendered Wayfarer widgets. It
first reproduces the old shared-update pattern, then applies the new projection and records work:

| Controlled update | Changed panels | Uploaded pixels |
| --- | ---: | ---: |
| Old shared-state pattern | 6 | 1,227,120 |
| New altitude-only projection | 1 | 128,000 |

That is **89.57% fewer uploaded pixels for this workload**, not a claim of equivalent FPS gains or
universal gameplay speedup. Health changes repaint vitals only; inventory display changes repaint
equipment only; biome changes repaint location only; heading changes repaint the compass only.
100 equal updates and settled idle produced zero paints/uploads. No displayed feature was removed.
The actual Minecraft fixture exercises Wayfarer; Bramble's matching state projection was reviewed
and compiled, not independently certified by that fixture.

## Native slot mapping cache

Before this pass every presented HUD frame built a new map and transformed NativeBounds for each
native slot, even if neither placement nor slots changed. `UiHudNativeCache` reuses that map until
source geometry, target bounds (including fade offset), or GUI scale changes. Paints compare slot
geometry before invalidating the map. Lifecycle release clears it. Native items still submit live
on every visible frame; their contents are not cached as screenshots.

A headless test checks 10,000 stable resolutions return the same mapping, plus movement, GUI scale,
source mutation/invalidation. Minecraft observes zero native-placement rebuilds while idle and
when equipment text repaints without changing slot geometry. `nativePlacementCount()` is a
cumulative diagnostic, not a GPU timer. Existing native fade/placement/lifecycle regressions are
included in the final batch.

## W09 — explicit image controls and shared mapping

`UiImageOptions` provides COVER/CONTAIN/STRETCH, NEAREST/BILINEAR/BICUBIC and 0..1 alignment.
`UiImagePlacement.fit` gives the exact full image bounds, clipped viewport and source-point mapping.
`UiCanvas.image` draws caller-owned BufferedImage pixels; `minecraftImage` shares the existing
resource/dynamic texture cache and returns Optional placement (empty for a missing texture).
`minecraftTextureRegion` gains a sampling overload. Existing texture methods keep nearest behavior.

Wayfarer draws its photographic background with bilinear cover and passes the returned mapping
to its player/shadow placement. The duplicated centered-crop formula is removed; authored source
foot coordinates remain art direction. No new image decoding/cache layer was introduced.

Four unit tests check fit/alignment/source mapping, invalid geometry, clipping, nearest-vs-filtered
pixels and graphics-state restoration. The actual resource fixture passes nine checks: dynamic
pixels, cover/contain resource drawing, mapped center, missing asset, explicit invalidation, real
resource-pack reload and idle retention. The first screenshot caught the startup splash; its
capture was moved after the overlay, and that fixture was rerun. The splash capture was not used
as proof of rendered image quality. Wayfarer's full regression checks the migrated real menu.

## Evidence and limits

Build/Javadoc/artifact verification: 153 unit tests, zero failures/errors. Final artifact SHA-256:
`d0ef1ccf763e5bb5a8216942b27712aa4abbccba67799b52bda1860baa88a692`.
All 151 compiled library classes match the runtime JAR. ZIP integrity passes; all 12 protected
submitted files remain unchanged. Logs are in `build/audit/hud-quality/` and
`build/audit/image-options/`. All eight final fixtures used that exact frozen library JAR:

| Minecraft fixture | Passed checks |
| --- | ---: |
| HUD quality | 19 |
| HUD update efficiency | 14 |
| Responsive placement | 55 |
| Native slots and compass | 18 |
| Vanilla HUD restoration | 33 |
| Wayfarer menu | 28 |
| Images and resource reload | 9 |
| Gather regression | 34 |
| **Total** | **210** |

The final verification JSON records each log path and SHA-256. Clients exited normally.
The sharp HUD, image filtering, Wayfarer lobby and compact Gather settings captures were visually
inspected. Gather settings navigation now uses descriptive `paintedButton` controls instead of
blank buttons with separately painted labels. This completes that part of D03; reference-scaling
cleanup remains separate. No public features were removed.

The Minecraft results use isolated local clients and copied fixture worlds, not the user's open
showcase save. This does not certify Windows/Linux, arbitrary modpacks, or every font/image asset.
W11, D02 and remaining D/N queue items are still open. Nothing is published or release-staged.
