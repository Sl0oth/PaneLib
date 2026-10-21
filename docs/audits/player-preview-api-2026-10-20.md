# W01 implementation plan and evidence

Scope: world-free standing player previews and migration of Wayfarer. No HUD, scrolling,
image-fitting, or unrelated control changes. Preserve the submitted release snapshot.

## Contract

- `NativeOverlayContext.playerPreview(slotId, PlayerSkin[, UiPlayerPreview])` renders a fresh,
  isolated standing avatar. No level, player entity, server, profile lookup or network request.
- `UiPlayerPreview` is an immutable configuration: body/head rotation (degrees), camera pitch,
  requested unprojected body height as a fraction of the slot, normalized foot anchor, and
  optional bounded pointer head tracking. Fixed by default; blocking overlays suppress tracking.
- A resolved Minecraft PlayerSkin supports wide/slim models. Null chooses Minecraft's bundled
  default. Invalid resource IDs retain Minecraft's missing-texture behavior, documented explicitly.
- Library owns render-state defaults, full-bright lighting, rectangular slot submission, physical
  to GUI conversion, width fit and diagnostics. No global mutable render state or skin cache.
- Existing entity/player methods remain unchanged. New API is named separately to avoid making
  calls such as player(id, null) ambiguous.
- Native slots retain existing partial-scroll/shape clipping limitations. Fit is a standing-model
  framing envelope, not pixel bounds for arbitrary animation/capes. No animation/cape API in W01.

## Sequence and acceptance

1. Preserve source hashes and raw Wayfarer implementation as comparison evidence.
2. Implement options, internal state/geometry adapter and public overlay methods; test validation,
   physical/GUI placement, fixed/pointer behavior, missing/empty slots and immutable options.
3. Migrate Wayfarer to one supported draw call; delete all raw state/quaternion/GUI conversion and
   its custom draw counter. Keep authored shadow/photograph placement.
4. Rebuild the packaged library and run isolated Minecraft: verify no world/player/server, actual
   native counter, wide/slim/default skins, fixed vs tracking images, hidden/missing slots, GUI
   scale and resize, modal coverage, retained idle and closing/reopening. Inspect screenshots.
5. Review changed code, record failures and fixes, update consumer docs and audit queue. No release.

Evidence and final results will be appended after validation. A unit PASS is not Minecraft proof.

## Result — complete locally

Implemented `UiPlayerPreview`, the internal `PlayerPreviewRenderer`, and two
`NativeOverlayContext.playerPreview` overloads. Existing entity/player signatures are unchanged.
Wayfarer now has one supported draw call and immutable presentation settings; its raw render
state, quaternions, GUI-scale conversions and custom submission counter were deleted. Its art,
photograph mapping and authored shadows remain consumer responsibilities.

Validation:
- `:pane:build :pane-demo:classes` succeeded, including Javadoc and runtime artifact checks.
- 109 unit tests, zero failures/errors/skips (six new focused tests).
- Isolated Minecraft 26.2: **18 checks passed**, no level/player/integrated server. Includes
  idle retained UI with continuing native submissions, GUI scales 2/3, compact resize, title
  return/reopen, wide/slim/default skins, absent/hidden/restored slots, modal pointer blocking,
  and resource reload.
- Pixel checks: fixed wide/slim/default previews and Wayfarer each have **zero changed pixels**
  across different delivered pointer positions. Tracking changes **131882 pixels**; wide/slim
  model comparison changes **87230 pixels**. This proves visible model/tracking differences,
  not merely successful submission counters.
- Visually inspected final Wayfarer, compact framing, diagnostic models, hidden slots and modal
  ordering. Full-size final screenshot: `examples/wayfarer/screenshots/player-preview-api.png`.
- All 129 compiled library classes match the tested JAR. SHA256:
  `ef8377cbcd1bebefbedaf37353014c3bfbc587d7085f4b413876cc7b5bb04499`.
- All 12 protected submitted-snapshot files retain their original hashes. No publication.

Evidence: `build/audit/player-preview/{build.log,build-final.log,client.log,pixels.json,integrity.json}`.
Pixel verification is reproducible with `python3 docs/audits/probes/check_player_preview.py`
using Pillow after the client fixture. Screenshots remain in `pane-demo/run-wayfarer/screenshots/`.

## Review and failed-check resolution

Local source review checked additive method compatibility (including existing null entity calls),
no world/account mutation or remote skin lookup, fresh per-submission render state, finite options,
slot/scale conversion, native counters, and unchanged existing entity rendering. The returned
render state is not cached or mutated across deferred draws. The default skin resolves through
Minecraft; custom missing resources follow Minecraft's missing-texture policy.

The first run's screenshots exposed a fixture problem: OS cursor warps did not reach this macOS
background client. A subsequent run with an explicit coordinate-change assertion failed that
assertion (`client-focus-failure.log`). The final fixture delivers Minecraft's own private mouse
callback with test-only reflection, then verifies received coordinates and changed screenshot
pixels. No reflection or input-injection workaround exists in the public API or Wayfarer consumer.
The initial submission-only run is not counted as proof of pointer behavior.

Automatic approval review rejected sending the focused source packet to Claude. The user then
explicitly requested local review instead; no source was sent and no independent external review
is claimed. Local implementation and Minecraft validation were completed under that direction.

Limits: standing pose only; no automatic profile downloads, equipment, cape animation or arbitrary
pose system. Extreme frame anchors/rotations can clip; rectangular/partial-scroll native-slot
policy is unchanged. Physical tests used this Mac's Retina display; cross-platform hardware was
not tested. Other audit items W02–W09/D01–D04 remain open and were not implemented in this pass.
