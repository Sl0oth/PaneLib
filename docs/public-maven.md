# Public Maven hosting

**Status: published and verified on October 21, 2026.** The anonymous repository is
`https://sl0oth.github.io/PaneLib/maven/`. Its first published coordinate is
`dev.pane:panelib:0.9.9-beta.1`, for Minecraft 26.2 and Java 25.

All 25 immutable version files were downloaded anonymously over verified HTTPS and matched the
approved candidate. Independent file and public Maven consumers built successfully, the bundled
JAR matched exactly, and both completed their Minecraft smoke checks on macOS ARM64. Dependency
caches were reused; fresh endpoint downloads were checked separately. See the
[publication audit](audits/public-maven-publication-2026-10-21.md) and
[installation guide](installation.md).

**`1.0.0-beta.1` was added on October 22, 2026.** Its [release](https://github.com/Sl0oth/PaneLib/releases/tag/v1.0.0-beta.1) Maven ZIP is pinned in the
catalog; all 25 of its version files downloaded anonymously matched the candidate, and the public Maven
consumer built and passed its Minecraft smoke check. Both versions stay available.

Download the matching 0.9.9-beta.1 starter, runtime, sources and API reference from the
[GitHub beta release](https://github.com/Sl0oth/PaneLib/releases/tag/v0.9.9-beta.1).
This publication does not update Modrinth.

## How hosting works

GitHub Releases stores each approved, already verified Maven ZIP. The checked-in catalog pins its
version, exact download URL, SHA-256 and byte size. Every Pages deployment downloads and verifies
every catalog entry, merges its versioned files into the documentation site's `maven/` directory,
and generates a combined Maven index and checksums. It also publishes `maven/catalog.json` as the
inventory used to protect the next deployment. Existing binaries are copied unchanged.

Each entry has this shape; placeholders must be replaced with values from the verified candidate:

```json
{
  "version": "<version>",
  "url": "https://github.com/Sl0oth/PaneLib/releases/download/v<version>/panelib-<version>-maven.zip",
  "sha256": "<exact SHA-256 of that ZIP>",
  "size": 0
}
```

The outer object is `{"schemaVersion":1,"releases":[...]}`. `size` is the actual ZIP byte count,
not zero. Every existing entry must remain unchanged. Publish a new version to correct a release;
do not replace an asset or edit the hash of a published coordinate.

## First deployment and publication

1. Finish the [candidate verification](releasing.md), including both extracted consumer builds and
   their Minecraft smoke checks. Review the exact Maven ZIP hash, size, release identity and staged
   catalog entry. Preserve the immutable candidate and submitted 0.9.8 snapshot.
2. Once deployment is authorized, deploy the workflow on current `main` with the **empty** committed
   catalog. This bootstraps a live empty `maven/catalog.json`. A live 404 is accepted only when the
   proposed catalog is empty; an unavailable or invalid catalog otherwise blocks deployment.
3. After release upload is authorized, upload that exact `panelib-<version>-maven.zip` as a public
   asset on release `v<version>`, alongside the runtime JAR, the demo JAR and `SHA256SUMS` and nothing else (see
   [releasing](releasing.md)). A private draft asset is not an anonymous download source.
4. Verify the public asset matches the reviewed hash and size, then append the approved entry to
   `distribution/maven/releases.json` on current `main`. Never delete older entries. This change
   triggers the combined documentation/Maven deployment; it does not upload release assets.
5. Verify anonymous resolution from the public Pages URL using the exact approved version. Remove
   the consumer's bundled-JAR fallback, compare resolved and Loom-nested JAR bytes to the candidate,
   and run the consumer smoke check. Record which downloads were fresh and which dependency caches
   were reused. Only then describe that version and endpoint as publicly verified.

After the first version, repeat the candidate, asset and catalog gates; do not repeat the empty
bootstrap or reset the catalog. Update the published-status text and installation
example only after each new public gate passes. Select its version from the approved catalog, not from
the current development version in `gradle.properties`.

## Deployment checks

The `Documentation` workflow serializes Pages work and runs only for `main`. Before building, it
checks that its commit is still current `main` and fetches the deployed catalog. The assembler
requires that baseline and rejects removed or modified entries. Missing assets, mismatched bytes,
invalid archives or metadata failures stop the whole deployment. A documentation-only change still
assembles all Maven versions.

Immediately before deployment, the workflow rechecks the live catalog and current `main`. A stale
run must be replaced by a run from the latest commit. Fix a failed build and rerun current `main`;
do not work around it by uploading a docs-only Pages artifact or deleting catalog history. The
previous successful site remains the published version when assembly fails before deployment.

The build needs `contents: read`; the deploy job adds `pages: write` and `id-token: write`. The
automatic `GITHUB_TOKEN` can read the current main revision. No personal access token, developer
login, GitHub Packages credentials or Sonatype account is needed for this hosting path. Consumers
need no credentials. Creating the public GitHub release remains a separately authorized action.

For a local reconstruction, use an absent or empty `website/dist/maven/` output directory; the
assembler refuses to overwrite an existing tree. First obtain the live baseline, then run:

```sh
python3 scripts/check_maven_deployment.py baseline --catalog distribution/maven/releases.json --output build/maven-deployment/previous.json
python3 website/build.py
python3 scripts/build_maven_site.py --catalog distribution/maven/releases.json --output website/dist/maven --previous-catalog build/maven-deployment/previous.json
```

The assembler also accepts `--asset-dir` for locally available approved ZIPs. Local reconstruction
does not deploy, create a release, or prove anonymous public consumption.

## Hosting limits and recovery

This is a static repository sharing the existing documentation site's capacity. Keep track of the
full expanded Maven tree, site size, build time and download traffic against
[GitHub Pages limits](https://docs.github.com/en/pages/getting-started-with-github-pages/github-pages-limits).
Every historical release asset must remain available; losing one deliberately blocks later site
deployments rather than silently removing its Maven version. Keep independent copies of approved
assets and manifests for recovery.

If growth or availability requires a different host, copy the complete verified Maven tree to
durable HTTPS static hosting or a managed Maven repository. Preserve every coordinate and byte;
verify anonymous consumers before changing the documented URL. Do not delete older versions to
make room or silently substitute GitHub Packages, which is a different consumer setup. Moving to
Maven Central would be a separate namespace/account and publishing decision.
