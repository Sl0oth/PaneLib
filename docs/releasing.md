# Prepare and verify a local release

Install Java 25 and Python 3. Run these commands from the repository root. On Windows,
use `gradlew.bat` and pass `-PpythonExecutable=python` if needed.

```sh
./gradlew prepareRelease verifyRelease
```

This builds and tests the library, publishes its Maven module **into `build/maven/` only**,
tests the packaging tool, and stages `build/release/<version>/`. Nothing is uploaded.
The version comes from root `gradle.properties`. The starter descriptor, runtime metadata,
Maven metadata, filenames and release notes must agree. Update the current coordinate in
the root README when changing that version; the packaging check detects drift.

The candidate contains runtime, source and Javadoc JARs, an offline API ZIP, a standalone
starter ZIP, a local Maven repository ZIP, release notes, a manifest and SHA-256 checksums.
The runtime JAR is the only library JAR players install. The starter includes the matching
runtime, wrapper and source; it does not depend on the checkout.

## Verify what a developer downloads

Replace `<version>` with the version in `gradle.properties`:

```sh
python3 scripts/verify_release_consumer.py "build/release/<version>" --smoke
```

This extracts two separate consumers under `build/release-consumer/`. One uses the bundled
runtime; the other resolves the unpacked Maven repository with its `libs/` directory removed.
Both build and open real Minecraft clients sequentially. The smoke test exercises editing,
Save, disk persistence, resizing, Cancel/Escape and reopening. The Maven consumer also embeds
the library with Loom `include`; verification compares the nested bytes to the candidate.
Logs and `verification.json` stay with the disposable projects. Omit `--smoke` for build-only
checks; that does not establish Minecraft runtime behavior. Existing dependency caches can
be reused, so this is not a proof of a first network download or public hosting.

## Immutability and reproducibility

Running preparation again accepts identical bytes and refuses changed bytes at the same
destination. It never replaces the starter's source-tree `libs/` file or an older release.
While iterating on a local candidate, use a new destination:

```sh
python3 scripts/prepare_release.py --output build/release-review/candidate-2
python3 scripts/prepare_release.py --verify build/release-review/candidate-2
```

The assembly command needs current build outputs. `--verify` checks an existing candidate
without rebuilding or consulting the current checkout's binaries. Checksums detect accidental
changes; they are not signatures or a substitute for a trusted download source.

To check repeatability on the same toolchain, save the staged checksums, rerun the library
build and local Maven publication with `--rerun-tasks`, then prepare again. JAR timestamps and
file order are normalized and Javadoc timestamps disabled. Different toolchain/compression
versions still need their own evidence; byte identity across all operating systems is not claimed.

## Publication is a separate step

The release-candidate workflow runs these checks and retains artifacts; it has read-only
repository permissions and does not create a release. Public Maven hosting is prepared for GitHub
Pages at `https://sl0oth.github.io/PaneLib/maven/`, but is **not published yet**. The committed catalog
is empty; a staged catalog under `build/publication/` is only a review artifact.
Before publication, check the actual destination's version history, choose an unused release
identity, validate that exact candidate, and record its hashes and platform evidence. Never
replace the preserved `release-snapshots/0.9.8-submitted` snapshot or overwrite a published version.

Follow [public Maven hosting](public-maven.md) for the empty-catalog bootstrap, approved release-asset
upload, append-only catalog change, Pages deployment and anonymous consumer verification. Upload the
already verified candidate ZIP byte for byte. Do not rebuild an older release to deploy documentation
or add a newer version. Keep publication separate from `prepareRelease` and consumer verification.

See [installation](installation.md) for developer instructions and the
[improvement plan](audits/grade-improvement-plan-2026-10-21.md) for remaining gates.
