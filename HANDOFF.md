# Pane — handoff

Working notes for whoever picks this up next. Disposable; delete once it's stale.

Repo lives at `~/Documents/ChatGPT/Gui Lib` (folder not renamed). Remote is
`https://github.com/Sl0oth/PaneLib` (private, `main`). Working tree is clean and pushed
as of commit `55e376b`.

---

## 0. Read this first — two hard rules

**Never write the owner's real name anywhere.** Not in code, commits, licences, mod
metadata, or notes. They keep their legal identity separate from the handle `Sl0oth`. Use
`Sl0oth`. This was violated earlier in the project and cost a full history rewrite; see §6,
which is still **open and needs the owner**.

**The renderer stays native.** No MCEF, Chromium, HTML, CSS, JS, or webview as the base.
The old MCEF prototype is deliberately preserved in `dev.pane.mcef`, excluded from the
source set in `pane/build.gradle`. Do not delete it, do not let it into the build.

Other standing constraints: no bitmap or AI-generated UI assets — everything is drawn from
code. Avoid magenta. Restrained single accent colours. Target the polish of Modrinth /
ChatGPT / the owner's macOS app Aster; avoid generic "AI dashboard" styling.

---

## 1. What Pane is

A 100% client-side Fabric UI library for building desktop-quality Minecraft screens
entirely from code. Minecraft 26.2, Fabric Loader 0.19.3, Java 25, Loom 1.17-SNAPSHOT.

**Rendering model.** Painters draw into an AWT `BufferedImage`; that surface is uploaded to
one `DynamicTexture` and blitted as a single cached GPU texture per frame. It repaints
*only* on resize, explicit invalidation, or for the duration of a finite animation. Idle
repaint count is 0 — there is an automated check for exactly this. Minecraft's own items
and entities render in a later stratum via slots the painter reserves.

**Two Gradle modules:**

```
pane/       dev.pane.ui     the public API — modid "pane", no entrypoint
            dev.pane.mcef   preserved browser prototype, excluded from build
pane-demo/  dev.pane.demo   showcase + dev harness — modid "pane_demo"
```

`pane-demo` consumes `pane` through a normal project dependency, the same way any other mod
would. Verified: `pane-0.1.0.jar` contains only `dev/pane/ui`, `pane-demo-0.1.0.jar` only
`dev/pane/demo`.

**Do not re-add `splitEnvironmentSourceSets()`.** It hides the client source set from
consuming projects, which breaks the library. Pane is client-only and uses `main`;
`"environment": "client"` in the manifest gates it at runtime.

---

## 2. Public API

| Type | Role |
|---|---|
| `UiDocument` | pages, theme, shell, scaling, page shortcuts |
| `UiPage` | one route: id, nav label/icon, accent, painter |
| `UiScreen` | the `Screen`. Owns surface, input, invalidation, animation |
| `UiContext` | what a painter gets: canvas, theme, content rect, state, hit regions |
| `UiCanvas` | all drawing (46 public members) |
| `UiPath` | free-form lines and bezier curves |
| `UiShell` | pluggable chrome; `NONE` or `UiSidebarShell` |
| `UiTheme` | colour/radius/density/motion tokens; `dark()`, `light()` |
| `UiScaling` | how layout responds to window size |
| `UiRect` | rectangle in layout units |
| `NativeOverlayContext` | draw real `ItemStack`s and `LivingEntity`s into reserved slots |

**Three coordinate spaces**, and the distinction matters:

- *GUI space* — what Minecraft hands you in mouse events and `width`/`height`.
- *Layout space* — what painters draw in. One unit is one OS point, divided by
  `UiTheme.density()` and modulated by `UiScaling`. `UiScreen.layoutScale()` converts.
- *Surface space* — the rasterised texture, supersampled toward the physical framebuffer,
  capped at 2560×1440 equivalent.

`nativeSlot` handles the conversion, so native items stay aligned across GUI-scale and
window changes.

**Interaction model** is stringly-typed actions on hit regions. Reserved prefixes:
`page:`, `toggle:`, `slider:`, `select:`. Anything else fires an `action` event on the
`UiEventListener`. `select:key=value` is the radio/tab/filter primitive — see
`UiContext.selectable` / `selected` / `stringValue`.

**Animation uses two independent clocks.** `UiContext.motion()` is page-transition
progress, for entrance motion. `UiContext.interactionMotion()` is hover/press progress.
They were one clock, which meant every hover replayed the page entrance and the whole
layout visibly jumped. **Do not merge them again** — there is a regression check for it.

---

## 3. Running and verifying

```sh
./gradlew build                     # both modules
./gradlew runClient                 # normal launch; demo auto-opens on title screen
./gradlew runClient -Pplay          # loads a scratch world, opens showcase, hands you control
./gradlew runClient -Pautotest      # full automated verification, ~90s
./gradlew runClient -PdemoPage=settings
```

In game: `H` opens the showcase, `G` opens the style gallery, `1`–`5` switch pages, `Esc`
closes. Key mappings only fire when no screen is open, so it's Esc first, then `G`.

`DemoAutoTest` (`pane-demo`, inert unless the flag is set) creates a throwaway singleplayer
world and asserts:

- slider track mapping at 0 / 0.25 / 0.5 / 1.0 — **PASS**
- toggle click changes state — **PASS**
- selection controls (radio groups) change state — **PASS**
- hover does not move page content, drift 0.0 layout units — **PASS**
- survives 4 window resizes including 430×300 — **PASS**
- idle repaints over 5s = 0 — **PASS**

It reports `INCONCLUSIVE` rather than `FAIL` if the cursor moved during the idle sample,
because hover legitimately invalidates the surface. Screenshots land in
`pane-demo/run/screenshots/`.

**Verification discipline the owner expects:** run the real build and exercise the actual
flow before claiming anything works. Where a fix is behavioural, prove the test catches the
old bug by briefly reintroducing it. Report honestly what is solid and what is weak.

---

## 4. What's been done

Audited and fixed, all verified in a live client:

- **Slider values were offset** — the padded grab rectangle was used to map the value, so
  the ends were unreachable (0.058 / 0.942) and the knob lagged the cursor. `UiHitRegion`
  now carries a separate value track.
- **Hover replayed the page entrance** — see §2. Split into two clocks.
- **Scaling** — was `clamp(guiScale, 1, 2)`, accidentally right on the owner's machine and
  wrong elsewhere. Now DPI-correct with a damped adaptive response (`UiScaling`), so a
  maximised window makes the interface larger instead of just emptier.
- **The library was a theme, not a library** — `paintShell` lived inside `UiScreen`, so
  every consumer got the demo's sidebar and its literal strings. Extracted to `UiShell`.
- **Per-repaint allocation** — a fresh `BufferedImage` plus a fresh `int[]` from `getRGB`,
  ~13 MB of churn per paint. Both retained now, swizzle happens in place.
- **Per-frame allocation while idle** — `Map.copyOf` and a context object every frame even
  with no overlay renderer.
- **Per-frame `ItemStack` construction** on the render thread; `getDefaultInstance()` throws
  "Components not bound yet" before a world loads. Cached lazily.
- Sampler was `getRepeat` instead of `getClampToEdge` for a fullscreen blit; hover was not
  recomputed when regions moved under a stationary cursor; digit shortcuts were unbounded
  and always on; entity sizing ignored the bounding box; unbounded static font cache; AWT
  could open a macOS display connection (now headless).
- **Dead controls** — the shape picker, settings index, waypoint filters and waypoint rows
  registered no hit region at all. Added `UiContext.selectable` and wired them.
- **Expressive primitives** — `UiCanvas` could only draw flat rounded rectangles in one
  typeface, clipped to rectangles. That, not the shell, is why every page looked alike.
  Added gradients, per-corner radii, shadow/glow, rounded and circular clipping, rotation
  and scale, group opacity, `UiPath` curves, font families with tracking and ellipsis, and
  procedural textures (grain, stripes, dots, grid, scanlines, vignette) rendered from cached
  tiles through `TexturePaint` — no bitmap assets.
- **`StyleGallery`** — five pages on `UiShell.NONE`, each owning the whole surface:
  Terminal, Editorial, Aurora, Blueprint, Brutalist. They share no theme and no shell. This
  is the proof the library is general-purpose. Each painter is 41–55 lines.

---

## 5. What to do next — recommended order

**1. Scrolling.** The structural blocker. You cannot build a settings page, mod list or
inventory with more than one screenful. Content taller than its container is simply clipped.

**2. Layout helpers.** Every coordinate today is hand-computed arithmetic
(`bodyY + 36 + (slotH + 7) * 2`). It is easy to make Pane *look* like anything and tedious
to *position* it; this is also why demo pages stranded empty space in tall windows until
patched by hand. A `UiLayout` with rows/columns/stacks/spacers would roughly halve painter
code and make screens survive resizing by construction.

**Do 1 and 2 as one pass.** Scrolling needs a content-height concept and layout helpers are
what naturally produce that height; bolting scroll onto hand-computed coordinates later is
much worse.

**3. Text input and keyboard focus.** No config UI exists without a text field. Also
unlocks tab navigation and accessibility/narration, of which there is currently none.

**4. Minecraft textures as fills.** MC art currently only appears *over* the surface via
`nativeSlot` in the overlay stratum. Filling a card with a block texture, or clipping a skin
into a circle, is a big unlock and very on-brand.

**5. Smaller:** multi-stop gradients (two stops only today), gradient/textured strokes, real
gaussian blur for frosted glass (`shadow` currently stacks translucent rings), custom `.ttf`
fonts, blend modes, tooltips/popovers (`UiScreen.regionBounds` already gives the anchor).

**6. Publishing.** Maven artifact and semantic versioning so another mod can
`modImplementation` it instead of copying source. Nothing is compatibility-stable yet.

**Known weak spots:** only tested on macOS/arm64 with a HiDPI display — the pixel swizzle in
`UiScreen.upload` assumes little-endian, and the scaling work specifically changes behaviour
on other setups. No unit tests or CI; `DemoAutoTest` needs a real game launch and a log read.
Action strings are a flat namespace with reserved prefixes.

---

## 6. OPEN — needs the owner, not an agent

The real name was committed early (it was in `fabric.mod.json` from the original files, and
was then propagated into `LICENSE` and a commit message). History has been rewritten and
force-pushed; the current branch, every blob in every reachable commit, all author and
committer fields, and the local object store are clean.

**But force-pushing does not remove orphaned commits from GitHub.** Verified still live:

```sh
gh api repos/Sl0oth/PaneLib/commits/cbed453c4e8cc91d3dd11db4205f0566c4ba96a5 \
  --jq '.commit.author.name'      # -> the real name
```

The repo is private, so exposure is limited to accounts with access — but those objects come
with it if it is ever made public.

**The only guaranteed purge is deleting and recreating the repository.** An agent attempt to
run `gh repo delete` was blocked by the permission classifier, and the current token lacks
the `delete_repo` scope anyway. The owner needs to either:

- delete it at **github.com/Sl0oth/PaneLib → Settings → Danger Zone**, then have an agent
  recreate and `git push -u origin main` (local history is already clean), or
- explicitly authorise an agent to retry the delete after granting `delete_repo`.

Until then this is unresolved. Do not treat it as done.
