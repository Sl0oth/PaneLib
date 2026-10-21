# Local release packaging gate — October 21, 2026

The first implementation step of the grade-improvement plan is complete locally: consistent
release identity, reproducible immutable packaging, and independently extracted consumers.
This does not complete public distribution or establish Windows/Linux desktop support.

## What changed

- Root version is `0.9.9-beta.1`, separating current fixes from the preserved submitted `0.9.8`.
  GitHub's release and tag endpoints returned no entries during the read-only identity check.
  Modrinth version availability was not checked; this is not a published identity reservation.
- Starter defaults select the current checkout binary or its packaged version descriptor.
  Exact local-file and exclusive Maven modes validate artifact id, version, environment and API
  before compilation; generated Fabric metadata requires the same exact version.
- `prepareRelease` builds/tests and stages matching runtime, sources, Javadoc, API reference,
  starter, local Maven payload, release notes, manifest and checksums. `verifyRelease` checks it.
  The previous packager's overwrite of the source starter JAR was removed.
- Archives have reproducible order/timestamps; Javadoc timestamps are disabled. Staging is atomic,
  rejects changed content at an existing destination, and accepts identical reruns.
- Validation rejects stale/mismatched build inputs, unexpected packages/resources, unsafe ZIP paths,
  symlinks, private filesystem paths, metadata drift and damaged payload checksums.
- `verify_release_consumer.py` extracts the actual starter twice, builds local-file and Maven
  consumers, compares bundled JAR bytes, and optionally launches both clients with `--smoke`.
- Installation, starter and migration guidance now describe the tested default paths. Website
  version tokens render from root properties. Maintainer instructions are in `docs/releasing.md`.
- The manual candidate CI workflow performs these checks and retains diagnostics. It was updated
  locally; no GitHub Actions run or remote publication was performed.

## Evidence

Candidate: `build/release/0.9.9-beta.1/`.

Runtime SHA-256:
`decec47b8faf4aab6893620b290f84195bfd7f5842a79bc4575a8b32ddae9a55`.

- Forced full library rebuild: 157 unit tests, zero failures/errors/skips; artifact checks pass.
- 28 offline tooling tests pass: 23 packaging cases plus five consumer-runner cases. A fresh local
  reviewer reproduced a timeout leaving a child process alive; the runner now owns and cleans its
  process group. Regression tests cover a TERM-ignoring descendant, interruption, an unrelated
  process left alive, and failure markers despite successful exit. Windows cleanup is mocked only.
  The reviewer reran all 28 tests and candidate verification with no remaining actionable blocker.
- All 159 runtime classes match the previously tested adoption binary; this pass changes release
  packaging and metadata, not library runtime code. The previous 300-check suite was not rerun.
- Both extracted consumers built and passed actual Minecraft smoke checks: typing, keyboard slider,
  Save and disk persistence, resize, Cancel/Escape draft discard, parent restoration and reopen.
  Runtime provenance in each log points to its own extracted JAR/repository.
- The Maven consumer has no `libs/` fallback. Its Loom-included nested JAR is byte-identical to the
  candidate, and generated Fabric dependency is exactly `=0.9.9-beta.1`.
- A forced second library/Maven build produced an identical complete staged candidate. Existing
  submitted files, old release files and source starter convenience binary all retained their hashes:
  20 preserved files, zero changes.
- The checkout starter also builds without property overrides. Website rendering passed for all
  13 guides with resolved version tokens. The local client screenshot was visually inspected.

Logs, preservation hashes and machine-readable results: `build/audit/release-packaging/`.
Consumer logs/screenshots live under the workspace recorded in `consumer-final.log` and
`build/release-consumer/<version>-<unique-id>/verification.json`.

## Remaining gate

No public Maven endpoint is configured. Choose/configure the hosting destination, then validate
anonymous resolution of the exact published artifact from an independent consumer. Public uploads,
release creation and tags remain separate actions. Do not upgrade distribution to A based only on
local repository success. Platform coverage and accessibility remain the next major C-grade areas.

Evidence is macOS ARM64, Java 25, Minecraft 26.2, Loader 0.19.3 and Fabric API 0.157.0+26.2.
Dependency caches were reused. This is not a first-network-install, cross-platform, IME/narrator,
modpack-compatibility or performance certification. Git CLI remains unavailable because of the
unaccepted Xcode license; no license was accepted and no commit/push was made.
