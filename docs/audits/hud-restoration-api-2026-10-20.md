# W03 — scoped vanilla HUD restoration

Completed locally on October 20, 2026. User requested the next audit fix, proper Minecraft
testing, and clarification of vanilla-styled HUD support. Work stayed local; no external review,
publication or release staging. Source version remains the development 0.9.8.

## Cause and plan

Wayfarer and Bramble replaced nine Fabric vanilla HUD elements with unconditional empty callbacks,
discarding the original callback. Disabling a custom widget therefore could not restore vanilla.

The repair preserves the callback chain, binds suppression to individual widgets, adds immediate
activation/deactivation, migrates both examples, and tests the actual game lifecycle. It does not
add another renderer, modify the vanilla HUD implementation, or change default HUD behavior.

## Public contract and implementation

- `UiHud.replaceVanilla(Identifier...)` replaces that widget's declaration. Empty arguments release
  its claims while keeping the widget. Duplicates are collapsed; null/unknown registry IDs fail
  without changing its existing declaration. `replacedVanillaElements()` is immutable.
- No vanilla element is affected until a mod explicitly declares a replacement. An unattached,
  disabled or closed HUD, or one without a world/player, never suppresses original callbacks.
- `setEnabled(false)` immediately releases the surface and motion, stops native submissions,
  and releases active suppression. Values and the visible preference survive reactivation.
- `setVisible(false)` keeps its claim through the existing exit fade, restoring vanilla when
  the fade reaches zero. A disabled widget does not finish an exit fade over restored vanilla.
- Default screen suppression releases active claims; `showOnScreens` keeps them active with the
  widget. F1 retains Minecraft/Fabric's normal gates. World changes clear world resources/state;
  declarations and enabled/visible preferences survive and resume appropriately on rejoin.
- `close()` removes owner references and resources permanently. Closed HUDs cannot register new
  claims or be reactivated; another HUD may reuse their attachment ID.
- `UiHudReplacements` installs one delegating Fabric wrapper per requested element. If any owner
  is eligible, the original chain is suppressed; otherwise it is called. Removing one owner does
  not restore through another active owner. Empty wrappers retain no owner references and simply
  delegate. They stay installed rather than overwriting another mod's later wrapper on close.
- Wrappers registered earlier are inside the suppressed/restored chain. Later wrappers retain
  their own outer behavior. A mod that discards `original`, or removes an element entirely, can
  bypass this integration; no universal modpack compatibility is claimed.

Both examples now assign hotbar/XP/held-item claims to their belt, health/armor/food/air claims
to vitals, and the crosshair claim to the crosshair widget. J toggles their seven widgets through
`setEnabled`. No raw `HudElementRegistry.replaceElement` remains in either showcase.
Test-only wrappers still exist in the audit fixture to count real original calls.

## Evidence

- Full `:pane:build` and demo compilation succeed. **117 unit tests pass** with no skips/errors.
  Four new tests cover repeated toggles without stacked wrappers, overlapping owners, earlier/
  later wrappers, declaration changes, failed update preservation and clearing owner references.
- **33 actual Minecraft checks pass** in the copied Wayfarer fixture world. Coverage includes
  J input through the demo keybinding; original/native counts on off/on; animated hide/show;
  per-widget ownership; screens/show-on-screens; F1; overlapping claims and final-owner release;
  two disconnect/rejoin cycles (disabled and enabled); declaration validation/clearing; close;
  closed-HUD rejection and attachment-ID reuse. Counter wrappers delegate to the real vanilla
  callbacks rather than replacing them with a fake renderer.
- **18 existing native-HUD/compass Minecraft regression checks pass** on the same runtime.
  Native fade/visibility/scale/close/unload and north-crossing/continuous scrolling remain working.
- Actual screenshots were visually inspected: vanilla hotbar, health, armor, food, XP and
  crosshair return after disabling. A small vanilla-style supplementary HUD also renders.
- First client run passed 31/32 checks. Its re-enable assertion incorrectly counted frames before
  the queued key press was processed. The fixture now verifies key consumption, takes the baseline
  afterward, then observes subsequent frames. No runtime code was changed to conceal that failure.
  The corrected fixture passes 33/33; the earlier log is retained.

Reproduce:

```sh
./gradlew :pane:build :pane-demo:classes :pane-demo:configureClientLaunch --no-daemon --max-workers=2
python3 examples/wayfarer-hud/launch.py --restoration-test
python3 examples/wayfarer-hud/launch.py --test
```

Logs/baselines/verification: `build/audit/hud-restoration/`. `client.log` is the initial run;
`client-final.log` is the corrected fixture; `native-regression.log` is W02 regression.
Previous W02 raw captures were preserved in `previous-w02-captures/` before the regression rerun.

Both successful clients used runtime SHA-256
`d433b94ed093d8700c990953b8b9497e762962a83db94026f91c37f17c748586`.
All 136 compiled library classes match the JAR; ZIP integrity passes. All 12 protected files in
the submitted release snapshot are unchanged. No personal world or production server was used.
Bramble is migrated and compiled; no separate new Bramble client capture is claimed.

Saved full-resolution JPEG exports, each under 5 MiB:

- `examples/wayfarer-hud/screenshots/vanilla-hud-restored.jpg`
- `examples/wayfarer-hud/screenshots/vanilla-style-example.jpg`

## Vanilla-style support and remaining work

Existing Minecraft resource textures, square pixel borders and native items can produce a
vanilla-looking HUD. The fixture proves those existing features, with decorative hearts and four
item slots; it is not a newly implemented health system. Minecraft texture copies use nearest
sampling within the canvas, but final HUD composition still uses linear filtering.

There is no complete vanilla preset or first-class Minecraft bitmap-font provider in the retained
text API. `UiCanvas.text` uses Java2D/custom TTF fonts. A raw native text callback would reintroduce
manual transform/fade workarounds, so none was added. W11 records supported vanilla font/theme work;
pixel sampling belongs with existing W08/W09. These are separate from completed restoration.

W04 post-layout end scrolling is next in the existing audit sequence. Remaining W/D items,
cross-platform/modpack checks and release preparation are not marked complete by this repair.
