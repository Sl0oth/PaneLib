# PaneLib design handoff — current library focus

Current direction: improve PaneLib itself and prove very different visual systems with functional
Minecraft UI. The earlier Peripheral showcase concept is deferred. Claude has not been dispatched;
this pass implemented the visuals directly using original code-drawn geometry and existing assets.

## Latest iteration

Atlas now provides a motion studio and live HUD editor alongside the three earlier designs.
`ShowcaseHub.java` and `FieldHudDemo.java` are the current integration references. Use `-Pstudio`
for the disposable field world. Reusable APIs now include `UiTransform`, `UiTimeline`, delayed
entrances, cached drawing, and `UiHud`. See `huds-and-composable-motion.md` and the captured GIFs
under `docs/previews/`. Preserve exact hit polygons, current-value retargeting, reduced motion,
input-free HUD playback, and session cleanup when refining visuals. Claude was not dispatched.

## Current reference

`pane-demo/src/main/java/dev/pane/demo/DesignLabShowcase.java` contains three pages sharing one
expedition-planning model. Trailcraft has an inventory grid and route journal, Skybound has an
open hero layout and floating cards, and Blackline has a command rail and compact tactical rows.
Use `./gradlew :pane-demo:runClient -PdesignLab`, then 1/2/3 or the header controls.

Keep these distinct silhouettes. A later visual pass should refine spacing, typography, restrained
decoration, and narrow-window behavior within each design, rather than applying a universal
opaque panel shell. Preserve readable text, keyboard focus, and a visible world/panorama.
No proprietary extracted artwork or paid external asset generation is needed.

## Supported engineering pieces

- `UiShell.NONE` or custom shells; ARGB fills and existing canvas primitives/textures.
- `UiLayer` plus `UiContext.withLayer` for reusable bounded group opacity; nested `withOpacity`
  multiplies per-operation alpha. Keep scratch layers in per-screen state, not global singletons.
- Existing interruptible `ui.animate` with stable IDs and explicit repaint bounds.
- `UiContext.withTranslation` keeps drawing and input targets together during a slide.
- `UiScreen.setMotionScale(0)` provides a runtime reduced-motion control.
- Virtual lists retain their atlas while toasts are present. Toast fading is independent of page
  rasterization. Ordinary partial paints use full-width upload bands.

See `layered-ui-and-motion.md` for examples and constraints. Blur only samples the current CPU
surface; it does not blur the Minecraft world or a separately composed row atlas. It is optional
and off by default. CPU group fades do not fade native item/entity overlays.

## Constraints to retain

Idle and stationary overlays must stop repainting. Do not introduce indefinite decorative motion,
per-frame I/O, per-row blur, or image/font decoding in painters. Bounds must include translated
positions and shadows. A fully hidden group must have no input targets. Test scrolling, fade
reversal, focus, and resize in the actual client before expanding the design.

Menus/dialogs still suspend atlas composition; native world blur, IME, narrator semantics, and
Windows/Linux GPU verification remain outside this iteration. Review current working-tree changes
before editing; this repository contains substantial pre-existing work.
