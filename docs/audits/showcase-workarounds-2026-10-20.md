# Showcase workaround audit — October 20, 2026

## Result

The examples demonstrate capability, but several results required implementation knowledge that
should not be required of a normal PaneLib consumer. “No core changes required” was too weak a
standard: calling a public raw-rendering escape hatch can still be a workaround.

**Nine API/integration improvement groups and four example/tooling cleanup groups are recorded
below.** They are not thirteen newly reproduced renderer bugs. Each entry distinguishes missing
API, intended policy, consumer mistakes, and evidence. Recommended first work: W01–W04.

The original audit pass changed audit/documentation files only. W01 was subsequently implemented;
its update and linked implementation evidence appear below. Other findings remain open.

## Scope and evidence

Reviewed current implementations of Pane Mail (`GmailShowcase`), Gather (`DiscordShowcase` and
`DiscordReferenceView`), Wayfarer menu/preview, Bramble and Wayfarer HUDs, the earlier Briarkeep
consumer, their fixtures/launchers, and relevant public/core APIs. Included screenshot/asset/banner
work in the scope boundary: those are authoring operations, not substitutes for UI APIs.

Current `pane/src` and `pane-demo/src` are the source authority. Historical notes are not treated
as proof of current defects. The submitted `release-snapshots/0.9.8-submitted` is preserved.

Fresh evidence:
- `:pane:test :pane-demo:classes --rerun-tasks --no-daemon --max-workers=2` succeeded.
- **103 unit tests, zero failures/errors/skips.** All audited examples compile.
- **Six bounded geometry/formula checks** completed. They reproduce compact-HUD collisions,
  show the manual-centering formula depends on scale 1, confirm existing reference scaling,
  and calculate the HUD raster mismatch. Passing this probe means findings reproduced,
  not defects fixed and not a GPU/image-quality measurement.
- All **126 compiled library class files** matched the current development JAR byte-for-byte.
  Its SHA256 is `4b03b1bd943b264ba46a442ada22a60fbdc3d6ecfaa94d6a48b698f7f77e7c1d`.
- Source/example/protected-snapshot hashes were recorded before validation and compared after.
  Evidence: `build/audit/workaround-audit/{before.json,build-tests.log,placement-probe.log,integrity.json}`.
- System Git is unavailable because the machine's Xcode license is unaccepted. No system/license
  settings were changed. This audit uses file manifests rather than claiming a clean Git diff.

Earlier client evidence was inspected, not rerun or relabelled as fresh: Gather 34 checks and
8 footer pixel checks, Wayfarer title/menu 10 checks, Gmail 24 checks before the later capture-only
pass, and the actual Wayfarer HUD frame with `screen=null` and 2029 native item submissions.
There is **no fresh full gameplay, cross-platform, narrator, or long-session performance audit** here.

## API and integration findings

### W01 — World-free avatar preview and presentation options [P1, resolved locally]

**Update:** implemented and migrated Wayfarer; 109 unit tests and 18 focused Minecraft checks
pass. See [implementation evidence](player-preview-api-2026-10-20.md). The description below
records the pre-fix workaround, retained for comparison.

**What I did:** `WayfarerPlayerPreview.java:55-90` creates an `AvatarRenderState`, fills skin,
entity dimensions, light, clothing flags, pose, animation, arm/item states and quaternions, then
calls `NativeOverlayContext.graphics().entity(...)`. It converts native bounds using Minecraft's
GUI scale and keeps its own draw counter. Earlier iterations loaded a scratch world just to obtain
a player. Briarkeep still draws an armour fallback outside a world.

**Root:** `NativeOverlayContext.java:83-126` accepts a living entity and uses the inventory camera,
which follows the cursor. `localPlayer()` is null at the title screen. The raw escape hatch makes
the result possible; it does not make the consumer code easy or version-resilient.

**Fix:** a skin/profile-based avatar preview that requires no world, plus immutable options for
fixed/pointer camera, orientation, standing pose, fit/scale and foot/baseline alignment. Keep
Minecraft render-state construction inside the library and count supported preview submissions
in its diagnostics. Clarify `bounds()` units: its comment says GUI space, but `NativeBounds` stores
physical backing pixels. Use one public conversion path.

**Done when:** the Wayfarer consumer contains no `AvatarRenderState`, quaternion, raw entity draw,
or GUI-scale conversion; it works with no player/level/server; pointer movement does not alter a
fixed preview; resizing and GUI/DPI scales preserve the foot anchor. Native clipping/coverage rules
and skin loading/fallback must remain explicit. Photo lighting and artistic shadows stay consumer art.

### W02 — Native item/entity overlays are absent from UiHud [P1, documented capability gap]

**Resolved locally October 20:** optional native HUD callback, library-owned placement/fade/
lifecycle and helper counters. Both showcase item workarounds removed. 113 unit tests and 18
focused Minecraft checks pass; player-preview regression passes 18 checks.
See [implementation, evidence and limits](hud-native-api-2026-10-20.md). Original finding below.

**What I did:** both HUD drafts attach a second Fabric renderer after the literal
`panelib:retained_huds`, read `belt.snapshot().bounds()`, duplicate the belt's coordinates and
perform pose-stack translations/scaling to render inventory stacks.
`WayfarerHudShowcase.java:47-59`; equivalent Bramble block.

**Root:** `UiHud` accepts only a `UiPainter`. In `paint()` it creates native-bound maps that are
not retained/submitted to a `NativeOverlayRenderer`. The HUD guide explicitly documents that native
slots are not rendered. The registry anchor is an implementation detail, not a stable public
integration contract. This is missing parity with `UiScreen`, not a missing Minecraft item renderer.

**Fix:** a HUD native-overlay callback using the same `nativeSlot`/item/entity contract as screens.
The HUD owns positioning, DPI, fitting, visibility/fade, lifecycle and ordering. Define the callback's
bounds/opacity contract; do not require consumers to reverse-engineer a diagnostic snapshot.

**Done when:** nine actual inventory slots render from slot IDs alone; no external Fabric callback,
private identifier or duplicated transform math; items hide/fade/move with the HUD, including F1,
menus, hide/close, world unload and scale changes. Native counters include these submissions.

### W03 — Vanilla HUD replacement has no scoped lifecycle [P1, integration gap + sample shortcut]

**Resolved locally October 20:** widget-owned `replaceVanilla` declarations and immediate
`setEnabled` toggling preserve/delegate original callbacks; fade-aware hide, close, overlapping
owners and world/screen lifecycle tested. Both showcases migrated and J toggles them.
117 unit tests and 33 Minecraft checks pass. See [W03 evidence](hud-restoration-api-2026-10-20.md).
The original finding follows for historical context.

**What I did:** replaced nine vanilla HUD elements with empty callbacks and discarded the
`original` callback (`WayfarerHudShowcase.java:42-46`, also Bramble). The example has no restoration
path or replacement owner. The extra item callback is also registered separately.

**Consequence:** it is valid for the opt-in one-frame capture, but copying this into a toggleable
mod means hiding/closing the PaneLib HUD does not automatically restore vanilla. It must not become
the recommended “replace your HUD” starter.

**Fix:** an opt-in, closeable replacement registration/group that preserves and delegates to the
wrapped original when inactive. Tie activation to the replacement HUD/group lifecycle. Specify
composition with other mods rather than promising a universal restoration of an earlier registry.
Provide explicit policy for hide versus deactivate. Never disable vanilla just by loading PaneLib.

**Done when:** enable/disable/close, F1, screens, disconnect/rejoin, overlapping replacement claims
and another HUD mod's wrapper have defined tested behavior. No session-global empty renderer remains
when the example is disabled. This can be a small Fabric integration layer, not a new renderer.

### W04 — Append-and-scroll requires a consumer paint/tick handshake [P1, API gap]

**Resolved locally October 20:** `scrollToEnd` resolves against the next registered extent before
drawing; ordinary/virtual viewports support `UiScrollFollow.WHEN_AT_END`. Gather uses scoped
conversation IDs and no longer has the tick/paint handshake or huge offset. 125 unit tests,
39 focused Minecraft checks and 34 Gather regression checks pass.
See [W04 evidence and limits](scroll-end-api-2026-10-20.md). Original finding follows.

**What I did:** Gather writes `rendered-conversation` and `rendered-count` during painting,
sets `follow-tail` on send, subclasses `UiScreen.tick`, waits for the new count, then calls
`scrollTo("history", 1000000)`.
Sources: `DiscordShowcase.java:82-91`, `DiscordReferenceView.java:147`.

**Root:** `UiScreen.scrollTo` (`:422-432`) clamps against the currently registered extent. Calling
it immediately after appending sees the previous content height. There is no end intent that
resolves after layout. The implementation behaves as documented; the basic use case is awkward.

**Fix:** deferred `scrollToEnd`/reveal intent resolved after layout, with an explicit follow-end
policy that can preserve the position while reading older content. Account for filtering, wrapping,
resize, conversation changes and virtual lists. Do not expose an arbitrary render-delay timer.

**Done when:** delete the rendered-count state, tick override and million-pixel offset from Gather.
Sending reaches the newly laid-out last row; remote append does not yank a reader who scrolled up;
no endless repaint loop or extra idle uploads.

### W05 — Popup styling stops at the trigger; custom triggers still use overpainting [P1, partial API gap]

**Resolved locally:** Styled/custom popup-trigger APIs now reuse `UiButtonStyle` and the normal
painter/state contract. Gather, Wayfarer and the video example no longer use blank-label menu/dialog
triggers. Selected-value typography is configurable independently from popup rows. 133 unit tests,
40 focused Minecraft checks, 34 popup regressions, 34 Gather checks and 28 Wayfarer checks pass.
Idle paints/uploads/retained allocations and bounded virtual-row work verified. See
[W05 implementation and evidence](popup-trigger-api-2026-10-20.md). The finding below is historical.


**What I did:** Gather paints a blank `menuButton` and immediately paints its custom server heading
over it (`DiscordReferenceView.java:91-94`). Wayfarer's yellow launch button paints a blank
`dialogButton` then draws its visible button separately (`WayfarerShowcase.java:92-96`).
Gather's role dropdown styles the popup but accepts the trigger's built-in typography.

**Root:** `UiContext.menuButton` and `dialogButton` expose enum button styles, not `UiButtonStyle`
or a painter. `UiContext.dropdown:914-943` hardcodes size 9 and uses the default sans font overload,
not even the theme's font family, for its selected label. `UiMenuStyle` styles popup rows only.
`paintedButton` is available for ordinary actions, but there is no equivalent public custom
menu/dialog trigger contract; consumers should not emit private internal-action strings.

**Fix:** reuse the existing style/painter/state system for these triggers. Separate registration
from presentation if necessary, preserving descriptive labels, focus, disabled behavior and popup
navigation. Keep popup style and trigger style independently configurable.

**Done when:** no blank-label trigger followed by overpainting in Gather or Wayfarer; selected
value and popup can use matching typography; pointer/keyboard, pressed/focus visuals, accessibility
metadata, transformed anchors and disabled controls still work. The current external focus ring
is not claimed broken merely because the trigger is overpainted.

### W06 — Confirmation dialog typography remains hardcoded [P2, API gap]

**Resolved locally:** shared/per-part font families, sizes/styles and line spacing now drive both
measurement and drawing. Bundled fonts use the existing registrar. Wayfarer migrated; 140 unit,
45 focused typography and 52 regression Minecraft checks pass. See
[W06 implementation and evidence](dialog-typography-api-2026-10-20.md). The finding below is historical.

**What I did:** kept the small default dialog text beside Wayfarer's much larger typography.
No fake substitute dialog was built, but this is a fidelity limitation we accepted.

**Root:** `UiDialogStyle` has colors, radius, padding and dimensions, but no font sizes/line heights.
`UiDialogLayout.measure` wraps at 12-point title and 8-point body/actions; `UiScreen.drawDialog`
paints the same fixed sizes. Enlarging maxWidth alone cannot enlarge the type.

**Fix:** immutable dialog typography/action metrics, shared by layout, wrapping, paint, scrolling
and hit geometry. Preserve old defaults and theme inheritance.

**Done when:** the Wayfarer confirmation matches its design system using a public style only;
long/localized copy and a small viewport still scroll and keep actions reachable; cached idle
behavior and focus restoration remain intact.

### W07 — Missing edge-center anchors; the multi-part HUD needs a responsive recipe [P1, mixed API/example]

**Resolved locally:** all four missing anchors and cached logical-viewport placement policies are
implemented. Wayfarer uses a shared responsive recipe without tick-time centering arithmetic.
144 unit tests, 55 focused placement and 51 regression Minecraft checks pass. See
[W07 implementation and evidence](hud-placement-api-2026-10-21.md). The finding below is historical.

**What I did:** used `TOP_LEFT`/`BOTTOM_LEFT` and recomputed `(windowWidth-widgetWidth)/2` every tick
for compass and belt (`WayfarerHudShowcase.java:123-130`). Each of seven HUDs fits independently.

**Root:** `UiHudPlacement.Anchor` has four corners and CENTER, but no TOP_CENTER/BOTTOM_CENTER or
side-center anchors. Fitting each widget cannot prevent sibling collisions. This is not a promise
of automatic collision avoidance with arbitrary other mods.

**Probe:** the current center formula is correct at scale 1. Reusing it at scale 1.5 drifts
96.67 GUI units in the tested viewport. At 640×440, the current left and right bottom widgets both
overlap the equipment belt. This is geometry evidence, not a new visual client capture.

**Fix:** add the missing anchors, then provide a small responsive HUD-group/example policy: decide
what shrinks, stacks or hides at compact sizes. Reuse the existing layout/scaling primitives.
Do not start by building a general automatic layout engine.

**Done when:** no window polling to center a widget; center remains correct at user scale changes;
640×440, reference size and ultrawide have usable, non-overlapping example compositions.

### W08 — HUD raster quality ignores placement scale [P2, source-confirmed quality limitation]

**Current status:** Resolved locally October 21: placement-aware raster settings, quality and byte diagnostics; 19 focused Minecraft checks. Defaults preserve explicit budgets. See [current evidence](api-comparison-and-efficiency-2026-10-21.md). The original finding below is historical.

**Root:** `UiHud.java:97-98` chooses quality from display DPI alone (capped at 2). Placement scale
is applied later (`:109-118`). On a 2× display, a 580-point HUD at scale 1.5 gets a 1160-pixel
surface stretched across 1740 display pixels. The public API permits this enlargement but offers
no HUD render-settings or `UiRasterQuality` equivalent. The current showcase uses scale 1, so
this is not a claim that its final screenshot was upscaled for this reason.

**Fix:** determine target bounds first, include effective displayed scale in the desired raster,
apply an explicit memory ceiling, and expose desired/actual quality and allocation diagnostics.
Keep the low-memory choice available; do not silently raise all budgets.

**Done when:** scale/DPI changes either keep text at intended resolution or report a deliberate
budget limit. Verify both growth and shrinkage, F1/world cleanup and retained idle behavior.

### W09 — Image sampling/fit controls are too implicit for mixed art [P2, usability opportunity]

**Current status:** Core fit/filter/shared mapping resolved locally October 21. Wayfarer uses the shared mapping; resource reload passes. Alignment is crop/letterbox alignment, not a source-point focal API. Nine-slice remains optional and unimplemented. See [current evidence](api-comparison-and-efficiency-2026-10-21.md). The original finding below is historical.

**What I did:** custom crop dimensions for every material panel, external photo projection,
and duplicated cover-crop math to anchor Wayfarer's player to its background.

**Root:** `UiCanvas.minecraftTexture` supports centered cover and `minecraftTextureRegion` supports
exact crops, but `drawMinecraftImage:1029-1039` always uses nearest-neighbor interpolation. There
is no per-draw filter choice or reusable image-placement result mapping source points to the
covered image. Stretching a torn sheet is possible but also stretches its border details.

**Fix:** explicit image options for fit, focal point and sampling (nearest for pixel art; appropriate
filtered sampling for photos), plus optional placement/mapping and nine-slice helpers where useful.
Start with filter/placement options; nine-slice is an ergonomic addition, not a proven blocker.

**Done when:** a pixel skin stays pixel-sharp, a photo downscales without forced nearest sampling,
and a source-space foot anchor follows the same mapping as the drawn image. Resource reload and
cache invalidation continue working. Panoramic projection, sourcing artwork and matching shadows
remain asset/art direction tasks, not responsibilities to hide inside PaneLib.

## Example and tooling cleanup

### D01 — Shared HUD state invalidates unrelated panels [P1, my consumer mistake]

**Current status:** Resolved locally October 21 in both HUD examples. Controlled Wayfarer altitude updates upload 89.57% fewer pixels; static panels remain untouched. Bramble compiled and reviewed; the live performance fixture uses Wayfarer. See [current evidence](api-comparison-and-efficiency-2026-10-21.md). The original finding below is historical.

Both HUDs create one `Live` record with health, inventory, coordinates and heading, then put that
whole record into all six panels every five ticks (`WayfarerHudShowcase.java:132-137`). A change to
heading/coordinates dirties the journal and glider badge even though they do not read live state.
The location panel also receives unused changing fields. This is wasteful dependency grouping;
`UiHud.setValue` correctly coalesces equal values and is not broken.

Split state by what each painter actually uses; don't update static panels. Prove with counters
that moving/turning repaints navigation only, health changes vitals only, inventory changes equipment
only, and idle makes no uploads. Rebuild placement objects only on relevant changes. No new reactive
framework is required to repair these examples.

### D02 — Launchers are local capture tools, not portable starters [P1, tooling]

**Current status:** Public launch criteria resolved October 21 with portable Loom tasks and a fresh-world HUD test. Shared frozen-fixture consolidation and fresh scratch-world creation are now complete in the [developer-adoption pass](developer-adoption-2026-10-21.md). See also [example refresh](example-refresh-2026-10-21.md). Original finding below is historical.

Discord, Wayfarer and both HUD launchers duplicate private-runtime copying and hardcode this Mac's
Zulu Java path, the 0.9.8 JAR filename and Loom-generated arg/config paths. The HUD launchers copy a
pre-existing private test save; their advertised classes/configureLaunch command does not include
`:pane:jar`. In the current checkout class/JAR parity passes, so no stale binary is alleged now.
Freezing runtime files was added after a concurrent build overwrote a loaded JAR; that is a valid
dev isolation measure, not a PaneLib rendering defect.

Keep screenshot fixtures separate. Provide portable Gradle example tasks / minimal independent
consumer examples with declared JAR dependencies, Java toolchain discovery, dedicated run directories
and a source-vs-JAR check where needed. Don't require a user's private fixture world. The existing
`examples/first-screen` already demonstrates independent JAR consumption; extend that pattern
rather than claim no starter exists. Share one supported capture harness instead of copying four.

Done when a fresh checkout can run screen, title-avatar and HUD examples without editing a local
path, manually finding a cached argfile, or inheriting screenshot automation/teleports.

### D03 — Stop copying manual glue where public APIs already exist [P2, examples/discoverability]

**Current status:** Resolved for the retained public examples October 21: semantic settings controls plus document-level scaling in Gather, Wayfarer and the new shelf. Briarkeep is retired from the public lineup. See [example refresh](example-refresh-2026-10-21.md). The original finding below is historical.

Gather settings still paints empty-label ordinary buttons and separate left-aligned labels
(`DiscordShowcase.java:121`), despite `paintedButton` being available. Migrate those controls to
retain descriptive metadata and common state behavior. Simple label alignment/padding would be
nice conveniences, but a new control architecture is not required.

Wayfarer and Gather each have helpers multiplying every coordinate and text size by a reference
scale; Briarkeep has another layout wrapper. `UiScaling.reference(...).adaptivity(1).range(...)`
already expresses reference scaling, as the probe confirms. Try document scaling and existing
`UiLayout`/grid/translation first. Pure scale for editors cannot be solved by blindly wrapping
`UiContext.withTransform`: it deliberately rejects editors/sliders/scrolls/native slots. Document
which approach supports which controls before proposing another scaling subsystem.

The one-element `UiScreen[]` callback capture and manually maintained form/model state are Java
and application-state plumbing; not renderer defects. A screen-aware event-handler convenience may
help later, but should not outrank native previews/HUDs/scrolling.

### D04 — Separate screenshot examples from complete integrations and keep evidence current [P2, docs]

`WayfarerPreview.tick` opens once using a static flag. It is not a persistent replacement of every
future TitleScreen after disconnect. PaneLib can draw the whole title screen; a host-owned startup/
return integration recipe is the missing example. Do not have the library globally replace menus
by default. Screenshot-only scope was explicitly requested, so this is an adoption gap, not a
failed promise that every sample feature is implemented.

The HUD harness includes staging inventory/teleport/time/camera code, plus illustrative mission,
glider and XP-bar content. Keep that out of copyable production snippets. Fake messages/permissions,
local-only rewards and decorative icons are sample data, not workarounds for a UI capability.

Earlier Wayfarer audit paragraphs still say the current player needs a world and describe the old
background. Current README/source say otherwise. Historical “all checks passed” statements also
must not replace later evidence: the latest Pane Mail capture-only pass does not repair or prove
its earlier viewport-dependent scroll fixture. Link a current status and preserve older logs as history.

## Already fixed, already available, or not a library defect

| Concern encountered earlier | Current status |
| --- | --- |
| Per-field input colors/focus/typography | `UiInputStyle` + `UiInputAppearance` exist and are used. Do not recreate this API. |
| Custom ordinary button state/keyboard/disabled handling | `paintedButton` exists. Remaining trigger parity is W05; old ordinary-button overpainting is D03. |
| Reusable icons | `UiIcon`, `UiSymbol.vector`, `iconButton` exist. Bespoke art is not proof of a missing icon API. |
| Menu popup typography/row height | `UiMenuStyle` fixed popup rows. Trigger/select label and confirmation typography remain W05/W06. |
| Blurry full-screen text | Examples now opt into `UiRenderSettings.typography()` and can inspect `rasterQuality()`. Balanced intentionally caps memory. Improve guidance; don't remove budgets. Separate HUD scaling issue is W08. |
| Friends footer thought to be missing | Later actual pixel checks passed after redundant paint was removed. No current core defect established. |
| Raw graphics draw absent from built-in counters | Expected for an escape hatch; improve supported helpers/counters under W01/W02, don't claim a false failed render. |
| First player skin ResourceTexture ID was wrong | My consumer used an extra `textures/` prefix and `.png`. Corrected. A preview API can reduce this trap. |
| Camera under terrain/tree during HUD capture | My fixture sampled an unloaded chunk and later a poor viewpoint. Corrected in the capture harness. Not a UI API problem. |
| Render boundary waits after resize/actions | Valid asynchronous fixture synchronization. Do not undo stale-hit protection to make a script pass. Gather's user-facing append handshake is different (W04). |
| Textures/paper/wood/metal/skins/background licensing | Asset creation/selection and documentation, not renderer workarounds. Existing resource images and custom fonts support them. |
| Character standing on a photographed rock | Authored image-space placement and shadow composition; no terrain/collision simulation is implied. W01 can remove renderer plumbing, not artistic decisions. |
| Banner replacement / JPEG under 5 MiB | External image compositing/export, unrelated to PaneLib's game API. |
| No account mail/chat/voice/matchmaking/game implementation | Deliberate fictional showcase scope, not missing UI features. |

Known adjacent limits remain separate: arbitrary shape masks for native entities and partial-slot
clipping (currently a partly clipped native slot is hidden), narrator/accessibility, editor drag/
caret reveal, resource-cache budgets, and earlier pending independent reviews. Briarkeep works
around the native mask limit by leaving padding inside its arch. Reuse its existing CST02 backlog
entry rather than count it as a newly discovered issue or rush a rendering-engine rewrite.

## Recommended implementation order and acceptance gate

1. **W01: world-free avatar helper/options.** Rewrite Wayfarer to use it and remove raw state code.
2. **W02 + W03: HUD native slots and scoped vanilla replacement.** Delete both extra item callbacks.
3. **W04: post-layout end scrolling.** Delete Gather's paint/tick coordination.
4. **W05 + W06: complete the control styling surface.** Delete blank-label overpainting.
5. **W07 + W08 + D01: responsive, sharp, selectively updated HUDs.** Test compact/scale changes.
6. **D02–D04: portable consumer recipes and accurate current docs.** Make each repaired example usable
   from a fresh mod without its capture harness. W09 can follow with bounded image API additions.

For each step: add the smallest public API that removes the existing workaround; migrate the real
example; test the previously awkward path, lifecycle, keyboard and idle/resource behavior; obtain
the project's required independent review before calling a nontrivial fix complete. Do not claim
success merely because another fallback makes the screenshot look right.

**The adoption gate:** a new Java modder should copy the supported example without importing
Minecraft render-state internals, using `panelib:retained_huds`, waiting in `tick()` for painted
counts, painting blank built-in controls and covering them, or editing a developer's absolute Java
path. Artwork and game-specific state remain theirs to provide.

## Reproduce the bounded placement checks

Compile `docs/audits/probes/ShowcasePlacementProbe.java` with `UiRect.java`, `UiHudPlacement.java`
and `UiScaling.java` using Java 25, then run `ShowcasePlacementProbe`. It does not launch Minecraft
or change a world. The six checks intentionally assert the present behavior; update them to the
new contract during remediation rather than treating their current PASS as a fixed-state test.
