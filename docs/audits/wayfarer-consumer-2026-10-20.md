# Wayfarer native game-interface consumer

> Current status, October 20: the opening sections below describe an earlier iteration.
> The active main menu now uses a CC0 photographic background and a world-free avatar;
> it does not load a world. W01 subsequently replaced the raw helper with the supported
> playerPreview API; see [the implementation evidence](player-preview-api-2026-10-20.md). See the
> [current workaround audit](showcase-workarounds-2026-10-20.md) and `NEXT_SESSION.md`
> for current evidence. Preserve the older checks as historical results.

Original sky-island lobby built with public PaneLib controls and generated scene art. The user
requested replacing the illustrated explorer with a real Minecraft player, then removing the
drone and tether. Active background: `skyport-empty.png`. No existing game assets copied.

Native texture composition, gradients, custom font/typography, buttons, vectors, finite
entrance/tab/equipment animations, mission selection, loadout, journal reward, text field,
slider, reduced-motion toggle, confirmation and briefing state are exercised. The character
is a real local player rendered through nativeSlot and NativeOverlayContext.graphics.
WayfarerPlayerPreview poses a disposable AvatarRenderState: fixed yaw/downward camera,
standing body, no walk/swing or pointer tilt. It never writes the actual player entity.
Its feet and soft contact shadow share a point in the background cover-crop coordinates.
This keeps placement tied to the platform in wide and taller windows.
The preview screen pauses its isolated singleplayer world; the native renderer still draws
the player every frame while the retained UI becomes idle. The render snapshot now uses the packaged original explorer skin; the account/world player is unchanged.

24 actual-client assertions pass; log `build/audit/wayfarer-grounded.log`.
The player-region left/right cursor screenshots have zero differing RGB pixels; see
`build/audit/wayfarer-grounded-pixels.log`. Lobby and compact placement visually inspected.
Raw graphics entity draws bypass PaneLib nativeEntityCount; this fixture uses its own
renderer submission counter instead of claiming the built-in count measures custom draws. Surface diagnostic:
1440×810 logical, 2880×1620 raster, desired/actual scale2.0. Screenshots visually inspected.
Tests cover mission/tab selection, retained equipment, one-time XP reward, text input, reduced
motion, crew state, confirmation focus/cancel/confirm, briefing return, compact layout and idle.
The initial fixture observed window resizing during navigation; stabilization gates resolved
that harness failure. No claim of cross-platform testing or full game implementation.

No library-core changes required. Native player previews currently require an active client
world. The built-in convenience method forces the inventory camera's cursor orientation,
so this demo uses the existing graphics escape hatch. A small public preview-options API
(fixed camera, standing pose, model scale, foot anchor) would remove that Minecraft-specific
consumer code. Scene placement/shadows belong in the consumer; a general animation engine
is not needed for this result.
UiDialogStyle controls colors/dimensions but does not expose font metrics like UiMenuStyle;
its fixed text sizing is visible beside the larger lobby typography. Existing defaults were
retained instead of replacing the modal with custom input handling.

Launch uses a copied test world in run-wayfarer or a new creative scratch world. It never
opens a production server. Everything except the player preview is local UI state; no actual
expedition, matchmaking, purchases, persistent progression or audio is implemented.

## Explorer skin pass

A standard Classic/Wide 64×64 RGBA skin now matches the supplied cream/navy explorer
reference with orange scarf, amber visor and dark boots. All base face alpha values are 255;
unused atlas space is transparent. Built-in imagegen produced the retained material sheet;
PackSkin.java exports it to Minecraft UVs. The standard outer layer adds helmet rim, collar
and backpack depth. No custom cloth or backpack mesh is claimed.

25 client checks PASS in build/audit/wayfarer-skin.log, including exact packaged texture
resolution. The final screenshot was visually inspected. An initial ResourceTexture id
included the automatically added textures/ prefix and .png suffix, causing a missing texture;
corrected before final validation. This was consumer code, not a PaneLib rendering defect.

## CC0 photographic background replacement

Active background and mission thumbnails now use Fouriesburg Mountain Lookout by Dimitrios
Savva / Jarod Guest, from Poly Haven, under CC0. Original, export, source/license metadata
and hashes are in examples/wayfarer/backgrounds/. Conversion is a spherical-to-perspective
projection of the original photograph, with no generative image processing. The generated
background remains archived in the project but is not selected by WayfarerShowcase.

25 client checks PASS in build/audit/wayfarer-photo.log; real-client appearance inspected.
The custom skin is unchanged and remains AI-generated; the disclosure stays visible.
This background replacement alone is not evidence of Modrinth policy compliance.
