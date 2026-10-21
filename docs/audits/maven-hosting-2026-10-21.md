# Maven hosting preparation — October 21, 2026

Status: implementation and local transport validation complete; public publication pending.
No release, tag, source push, workflow dispatch or Pages deployment was performed in this pass.

## Chosen hosting path

Use the project's existing GitHub Pages site at `https://sl0oth.github.io/PaneLib/maven/`, keeping
the existing `dev.pane:panelib` coordinate. The public docs returned HTTP 200, while the Maven
catalog returned 404. GitHub's repository API reports a public repository with Pages enabled;
its release listing was empty. These reads do not establish that the proposed Maven URL is live.

GitHub Releases will hold reviewed, unchanged Maven ZIPs. An append-only catalog pins each URL,
version, size and SHA-256. Every docs deployment assembles every approved version and regenerates
the aggregate Maven metadata and checksum sidecars. Version directories retain their exact bytes.
The checked-in catalog is still empty. A populated proposed catalog and exact upload inventory are
staged in `build/publication/0.9.9-beta.1/`, including `PUBLISH_PLAN.md` for review.

GitHub Packages' Maven registry requires consumer authentication, which would add installation
friction. Pages uses the existing account and workflow instead. This is a static Maven repository,
not Maven Central. GitHub Pages capacity and rate limits still apply.

## Changes

- `scripts/build_maven_site.py` validates catalogs, archive layout, identities, historical dependency
  metadata, sidecar hashes and contained runtime/source/Javadoc artifacts. It bounds download and
  expanded sizes, rejects symlinks/private paths, preserves old versions and stages atomically.
- `scripts/check_maven_deployment.py` verifies the current main commit and compares the proposed
  catalog against published history. Only an empty bootstrap can accept a missing live catalog.
  Network errors never become permission to reset history. Redirects are rejected before following
  them, so the GitHub API token cannot be forwarded to a different host.
- The Pages workflow includes all Maven versions on every deployment, serializes deployment work,
  restricts it to main, and rechecks live history/current main immediately before deployment.
  No additional consumer credentials or personal access token is needed for this design.
- `verify_release_consumer.py --repository-url HTTPS_URL` freshly downloads and compares every
  immutable version file before Gradle resolves the repository. Maven mode has no bundled fallback;
  nested library bytes and generated dependency metadata must match the candidate.
- Public installation guidance separates approved catalog versions from the working-tree candidate.
  The developer instructions and public status remain explicitly planned, not live.

## Verification

- 58 offline Python tests pass: 28 existing packaging/runner cases, 16 static Maven assembly cases,
  five live-history/commit guard cases, and nine HTTPS endpoint cases. Network is mocked/blocked
  for the endpoint unit tests. No remote workflow run is claimed.
- The actual candidate assembled into a 32-file Maven tree. A second assembly was byte-identical.
  All 29 hashed files across the candidate, submitted snapshot, old release and starter binary stayed unchanged.
- Real HTTPS was exercised through a loopback TLS server with certificate and hostname validation
  enabled. Its temporary certificate was added to a copy of Java's trust store; system trust settings
  were untouched. The custom `--ca-file` configures Python only, so this fixture separately supplied
  the JVM's copied trust store. Normal public HTTPS uses the platform's trusted authorities.
- All 25 immutable Maven version files were freshly downloaded and compared to the candidate.
  The first Gradle run downloaded the POM, module metadata and runtime JAR over HTTPS anonymously.
  Later runs may use checksum-based artifact caches; their evidence records that separately.
- Extracted file and HTTPS Maven consumers built, matched their nested JAR bytes and passed actual
  Minecraft typing, slider, Save/persistence, resize, Cancel/Escape, parent restoration and reopen.
  The final runner used the redirect guard. Existing non-PaneLib dependency caches were reused too.
- Two interim evidence-harness assertions expected Gradle to re-download already cached files and
  failed after the consumer itself passed. The harness now records downloads and cache reuse honestly;
  endpoint bytes are independently downloaded on every verification. This was a fixture expectation,
  not a library or TLS failure.
- The empty bootstrap site built locally; the live empty-history guard and public current-main guard
  passed read-only. Website generation still builds 13 guides. Fresh local review found and resolved
  the redirect-forwarding issue; no remaining blocker was reported in the reviewed preparation scope.

Runtime remains `0.9.9-beta.1`, SHA-256
`decec47b8faf4aab6893620b290f84195bfd7f5842a79bc4575a8b32ddae9a55`.
No library Java, starter ZIP or candidate bytes changed. The prior 157 library unit tests are prior
evidence, not newly rerun tests in this hosting-only pass.

Evidence: `build/audit/maven-hosting/`, its `final-verification.json`, HTTPS fixture records and the
consumer workspaces referenced there. Actual clients were macOS ARM64, Java 25, Minecraft 26.2.
Windows/Linux and public anonymous download verification remain open.

## Approval and remaining publication gate

Review `build/publication/0.9.9-beta.1/PUBLISH_PLAN.md`, then authorize the source/setup push,
GitHub pre-release upload and Pages deployment. The release tag must match the tested source,
not the older public main commit. Bootstrap the empty catalog first; upload verified assets;
append the reviewed catalog entry; deploy; then repeat anonymous HTTPS consumer verification against
the real public endpoint. Only that final check satisfies the public-distribution gate.
Modrinth publication is not part of this proposal.

References: [GitHub Maven authentication](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry),
[Pages deployment workflows](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages),
[Pages limits](https://docs.github.com/en/pages/getting-started-with-github-pages/github-pages-limits),
[Gradle repository types](https://docs.gradle.org/current/userguide/supported_repository_types.html).
