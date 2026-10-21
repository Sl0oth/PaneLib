# Public Maven hosting

**Status: prepared locally, not published.** The planned anonymous repository URL is
`https://sl0oth.github.io/PaneLib/maven/`, with coordinates `dev.pane:panelib:<exact-version>`.
The checked-in `distribution/maven/releases.json` catalog is empty. A candidate catalog under
`build/publication/` is for review only. Neither a local build nor this document establishes that
a public Maven download works.

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
   asset on release `v<version>`. Keep its manifest, checksums and other approved release artifacts
   with the release. A private draft asset is not an anonymous download source.
4. Verify the public asset matches the reviewed hash and size, then append the approved entry to
   `distribution/maven/releases.json` on current `main`. Never delete older entries. This change
   triggers the combined documentation/Maven deployment; it does not upload release assets.
5. Verify anonymous resolution from the public Pages URL using the exact approved version. Remove
   the consumer's bundled-JAR fallback, compare resolved and Loom-nested JAR bytes to the candidate,
   and run the consumer smoke check. Record which downloads were fresh and which dependency caches
   were reused. Only then describe that version and endpoint as publicly verified.

After the first version, repeat the candidate, asset and catalog gates; do not repeat the empty
bootstrap or reset the catalog. Replace the planned-status text and add a concrete installation
example only after the public gate passes. Select its version from the approved catalog, not from
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
