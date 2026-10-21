# Discord native consumer/API exercise — October 20, 2026

## Current black-theme and resolution pass

Gather now uses the newer supplied black desktop layout while preserving its original names,
messages, vector avatars and landscape. Opted the consumer into `UiRenderSettings.typography()`.
Actual runtime diagnostic: layout1440×907, pixel2880×1814, desiredScale2.0, actualScale2.0.
The no-upscaling assertion passes. This is native raster-resolution evidence, not exact font
or app parity; Roboto remains the supplied substitute font.

34 real-client checks and 8 footer pixel checks pass. The taller layout needed a longer
scroll fixture to exceed its viewport; the first failure was insufficient fixture overflow.
Final log: `build/audit/gather-crisp-final.log`; screenshot: `examples/discord-inbox/screenshots/gather-crisp.png`.
No core library edits were needed for this pass.

## Current status after menu-style work

`UiMenuStyle` is now public, with styled popup overloads on `menuButton` and
`dropdown`. Gather's server menu and role selector use it. The fixed-popup-style
findings below describe the earlier implementation.

The current Gather artwork already showed the Friends footer before this change.
One redundant `profile()` paint was removed; no underlying renderer defect was
reproduced. Fresh screenshots pass eight avatar/name pixel assertions across
navigation, redraw, compact resizing and restoring the window. These are visible
pixel checks, not merely proof that the painter called its drawing methods.

Gather retains its existing original vector artwork and bundled Roboto. This work
does not claim exact Discord font/artwork matching. The older screenshot-texture
reference pass below is historical; the active consumer no longer uses those assets.

Validation: 103 unit tests, build/artifact checks, 33 Gather client checks, 34 styled
popup client checks, and 8 footer pixel checks passed. Logs are in
`build/audit/menu-style-{build,gather,popup}.log` and `menu-footer-pixels.log`.


## Scope

Native Minecraft 26.2 PaneLib demo with one fictional server, six channels, three DMs,
message drafts, Enter-to-send, search, reaction toggles, and five settings sections.
All state is local to the screen. No core library changes made for this consumer.
Existing unrelated source edits, including concurrent API additions, were preserved.

## Concrete API findings

1. **Menu/dropdown typography and paint overrides are the clearest remaining styling gap.**
   The server menu uses public `menuButton` correctly and gets real focus/navigation/dismissal,
   but popup text is much smaller than the 14–16 point surrounding desktop UI. Its trigger
   cannot take the same `UiButtonStyle` as a normal restylable button. Recommended: public
   menu/option style with font, row height, padding, colors and icon support.
2. **Historical button-alignment workaround, now addressed by painted controls.** Settings navigation needs left
   aligned labels. The consumer retains normal button semantics by painting an empty-label
   button, then placing its own text. Recommended: alignment and horizontal padding overrides.
   The reference redesign now uses the new `paintedButton` API for custom labels and native
   interaction semantics. This no longer blocks the reference view.
3. **Follow-new-message scrolling needs coordination after layout.** `scrollTo` is clamped to
   the currently known extent. The consumer records its painted conversation/count and waits
   in `Screen.tick()` for that layout before requesting the end. No fixed sleep is used.
   Recommended: a public follow-end / scroll-to-end-after-layout option, with opt-out while
   the user is reading older messages.
4. **Icon API correction:** PaneLib already had `UiIcon` and `iconButton`; prior feedback that
   no reusable icon API existed was too broad. Some Discord-specific artwork still needs custom
   paths. The reference redesign successfully uses `UiSymbol.vector`, `paintedButton` and
   `UiInputAppearance`. Do not duplicate those capabilities based on old feedback.
5. **No new core input defect established.** Single-line `submit` already gives Enter-to-send.
   The first server-menu keyboard probe incorrectly pressed Enter before selecting an item;
   selecting the first item with Home is the proper pointer-to-keyboard transition.

## Test isolation

The first run reached role/settings checks, then failed with `ZipFile invalid LOC header`
when a concurrent build replaced the loaded PaneLib JAR. This was a runtime/build race,
not evidence of a scroll API defect. The launcher now freezes the library JAR, consumer
classes/resources, and launch configuration in a private directory before starting Minecraft.
A separate `run-discord` directory prevents options/log/screenshot contention.

## Evidence

Screenshots live at `pane-demo/run-discord/screenshots/discord-*.png`.
Final test: **28 checks PASS**, process exit 0. Log: `build/audit/discord-final.log`.
Frozen library SHA-256: `8ebbb68af4def8a5cc15894837b789772533530d92e0b61d72c5b1b31bc1b0ea`.
Inspected the final server and compact settings screenshots; no missing category/headset glyphs.

This is Mac real-client evidence, not cross-platform, accessibility/narrator, full IME,
production chat-networking, real permissions enforcement or universal performance proof.

Visual reference: [Discord desktop display guide](https://discord.com/blog/making-discord-on-desktop-look-just-right-display-settings-to-ease-the-eyes).

## Supplied-reference redesign

`DiscordReferenceView.java` targets the supplied 860×484 Friendos server and Add Friend
screenshots. It uses a 44px rail, 140px channel sidebar, 150px member list, compact message
rows, sampled colors and screenshot texture regions for avatars and embedded artwork.
Text, scrolling, hit targets, fields and navigation remain native PaneLib. Friends tabs, local
request validation, DMs and existing settings remain interactive. No library-core edits.

**Remaining fidelity blocker for the library owner:** `UiPopupLayout.ROW_HEIGHT` is fixed
at 27; `UiScreen` draws popup labels at size 8 with fixed offsets. `menuButton` does not
accept the custom paint style available to normal buttons. Add a public `UiMenuStyle` with
font family/size, row height, padding, background/border/hover colors and radius, plus trigger
customization. Apply the same style metrics to rendering, pointer hit regions, scrolling and
keyboard reveal. The demo paints over the trigger but preserves the native popup; it does
not implement an unrelated substitute menu to conceal this gap.

Roboto is a substitute for the historical Discord font; matching font files are an asset
requirement, not a missing custom-font API. Screenshot artwork is low resolution and Pingu
is static. Settings are functional sample pages, not a claimed pixel match without a supplied
settings reference. Utility decorations and local voice preview are not Discord services.

Final reference test: **32 checks PASS**, process exit 0. Log: `build/audit/discord-reference-final.log`.
Frozen PaneLib SHA256: 8ebbb68af4def8a5cc15894837b789772533530d92e0b61d72c5b1b31bc1b0ea
Private runtime: /var/folders/yn/sk47tgx16n15j5_d8qwzfq6r0000gq/T/panelib-discord-ofnq6xdw

Inspected `discord-server-final.png` and `discord-friends-final.png`. Main server layout
is substantially closer to the reference. **Unresolved visual issue:** Friends footer avatar
and name are absent even though `profile()` issues those draws; footer buttons remain visible.
Repeating the profile paint did not resolve it. This requires a minimal render-order/clip
reproduction before assigning a core-library defect; passing interaction checks do not cover it.
