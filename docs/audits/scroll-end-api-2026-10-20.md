# W04 — end scrolling after content layout

Completed locally October 20, 2026. User approved the next audit repair: reusable end scrolling,
optional following without pulling readers away from older content, and migration/testing of Gather.
Review stayed local. No release, publication or version bump; the submitted snapshot is preserved.

## Original problem and plan

`scrollTo` intentionally clamps against the latest registered extent. Appending and immediately
scrolling therefore saw the previous content height. Gather worked around this with paint-time
`rendered-count`/`rendered-conversation` values, a `follow-tail` flag, a `UiScreen.tick` override,
and a request to scroll one million pixels after detecting that paint had finished.

The repair records an end intent and resolves it at viewport registration, when the new measured
height is already available and before children, hits or virtual rows draw. Following is a separate
opt-in policy. The implementation requires neither a layout retry nor a consumer timer.

## Public API and lifecycle

- `UiScreen.scrollToEnd(id)` requests the end once, including before the first paint or directly
  after appending. It invalidates once and marshals worker calls to the client thread.
- Both `UiContext.scroll` and `virtualList` accept an overload with `UiScrollFollow.WHEN_AT_END`.
  Existing overloads default to `NONE`. First registration starts at the top unless requested.
- `UiScrollEndState` tracks prior extent and explicit navigation targets separately from consumer
  values. When following, a previous position within half a layout unit of the end stays attached
  through growth, wrapping or viewport resizing. Wheel intent takes priority over the old displayed
  offset, so scrolling upward detaches before the first interpolated wheel frame.
- Reading older content preserves its numeric layout offset, clamped if the available range
  shrinks. An invalid wheel target is cancelled on shrink. Returning to the bottom reattaches.
  Content that entirely fits is considered at the end; later growth can follow.
- Explicit numeric navigation, wheel movement and moving keyboard/virtual-row reveal cancel
  pending end commands. Page changes and removal cancel pending commands too. An absent viewport
  waits for its next registration without repeatedly invalidating. Same-ID offset semantics remain.
- Empty virtual lists now register a zero extent and do not allocate a row atlas. Nonempty virtual
  lists resolve their offset before visible-row selection; the bounded atlas path remains in use.
- End requests are immediate position changes, not a new smooth-scroll animation API. This is
  numeric offset preservation, not item-identity anchoring across arbitrary reorders or reflow.

Gather now uses IDs such as `history:general` and `history:dm:Maya`, the follow policy, and a direct
`scrollToEnd` call on send. Channel navigation explicitly resets its own viewport to zero. Removed
the tick override, handshake values, flag and enormous offset. Incoming model changes need only an
ordinary screen invalidation; there is still no network service in this local example.

## Verification

- Full library build, Javadoc/artifact checks and demo compilation succeed.
- **125 unit tests pass**, zero failures/errors/skips. Eight new tests cover new-extent resolution,
  one-shot consumption, follow/detach/resize, in-flight wheel cancellation, shrinking targets,
  absent and conversation-scoped IDs, lifecycle cancellation, empty lists and settled resolutions.
  A raster/hit/native-slot test proves resolution occurs before painting/registration. A virtual
  fallback test reaches row 999 while painting only a bounded visible window.
- **39 focused Minecraft checks pass** in a new world-free isolated client. Ordinary viewport:
  before-first-paint intent; one paint for append-and-end; new last-row hit geometry; absent/hidden
  viewport; incoming append; wheel-up plus append in the same event batch; reading older content;
  pinned/unpinned resize; filtering; optional following. Virtual atlas: 10,000 rows, bounded row
  rasterization, append, explicit reveal, reader preservation, empty/repopulate and filtering during
  wheel motion. Lifecycle: page/removal cancellation. Gather: actual Enter send, remote append
  simulation, scrolling back down, conversation changes before paint, resize/rewrap and search.
  Settled/absent scenarios have zero extra idle paints or virtual-row uploads.
- **34 existing Gather Minecraft interaction checks pass**: message entry, drafts, navigation,
  reactions, settings Save/Reset/validation, role dropdown keyboard selection, notifications,
  compact layouts, server menus and idle rendering.
- The final Gather end screenshot was visually inspected: the newest wrapped messages remain
  above the composer and the scrollbar is at the bottom. Pixel/geometry evidence is bounded to
  these fixtures; there is no universal FPS, multiplayer or cross-platform claim.

One initial test compilation failed because the new headless test used the wrong internal
`UiContext` constructor arguments. Only the fixture constructor needed correction. The subsequent
unit/build run and both client suites pass; the initial build log remains available.

Reproduce:

```sh
./gradlew :pane:build :pane-demo:classes :pane-demo:configureClientLaunch --no-daemon --max-workers=2
python3 examples/discord-inbox/launch.py --scroll-end-test
python3 examples/discord-inbox/launch.py --test
```

Logs/source baselines/integrity results: `build/audit/scroll-end/`.
Focused screenshots: `pane-demo/run-scroll-end/screenshots/` (`scroll-end-regular.png`,
`gather-scroll-end-send.png`, `gather-scroll-end-reading.png`, `gather-scroll-end-final.png`).
The earlier interactive Wayfarer client was left alone; these checks ran in separate frozen
runtimes. Concurrent clients mean these are functional checks, not controlled performance benchmarks.

Both successful clients loaded runtime SHA-256
`bfed635423c8f6fdb79339683a8b90415f4e5b0ffbd7310a5af0673262d5bc47`.
All 139 compiled library classes match that JAR; ZIP integrity passes. All 12 protected submitted
snapshot files remain unchanged. The API guide and Gather README describe the supported path.

## Remaining boundaries

Hosts still measure wrapped content and invalidate when their model changes; PaneLib is not a
network listener or observable collection. Use stable separate IDs for separate datasets. Explicit
end following is vertical and instant; item-anchored scroll preservation and new animated end-jump
options are not part of this repair. Existing virtual-row nesting/interaction constraints remain.
W05 styled popup triggers is next; remaining W/D items and release work are still open.
