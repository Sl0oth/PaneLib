# Internal Minecraft regression fixtures

For hands-on examples, use the public Gradle tasks in [examples/README.md](../../examples/README.md).
These Python entry points run one automated fixture, require Python 3.9+ and the same installed
Java 25 toolchain as the project, and fail if the client hangs, reports a fixture failure, exits
nonzero, or never prints its completion marker.

From the repository root:

```sh
python3 tools/fixtures/wayfarer.py --test --fresh
python3 tools/fixtures/wayfarer.py --popup-trigger-test --fresh
python3 tools/fixtures/wayfarer-hud.py --font-test --fresh
```

Each command builds first. `runtime.init.gradle` exports the configured toolchain, exact library
artifact/version, demo outputs and Loom runtime classpath. The runner copies mutable project
outputs and launch/logging configuration into a temporary directory. It validates the copied
JAR metadata and integrity, records its SHA-256, then removes the private runtime on every exit.
Dependency-cache files remain shared. Coordinate builds during the short export/copy phase;
after the client starts, rebuilding project outputs cannot overwrite its frozen classes/JAR.

Fresh fixture profiles disable first-run onboarding and tutorial toasts so they do not obscure
capture assertions. Existing unrelated fixture options are preserved.

No private save, copied personal settings, fixed artifact version, Mac Java path, or parsed JVM
argument cache is required. HUD fixtures create their own deterministic scratch world when absent.
`--fresh` starts a new profile under that fixture's normal run directory. Without it, a previous
fixture profile can be reused; simultaneous use is rejected by a PID-bearing lock file. A lock
left after an uncatchable kill should only be removed after checking that recorded PID has exited.

Run `--help` on each entry point for its mutually exclusive test modes. Existing compatibility
commands under `examples/*/launch.py` still work: no arguments opens the public example, and a
test flag uses this runner. `--menu-only` is already a test mode; do not combine it with `--test`.
The HUD's `--capture` saves its capture; `--capture --keep-open` leaves it open until the timeout
or manual exit. Bare `--keep-open` remains an alias for that capture. The default client timeout
is 600 seconds; `--timeout 900` changes it. Capture-only mode without arguments is retained only
for the internal HUD entry point, not the public launcher.

Each run prints its isolated profile and log path. Timestamped `fixture-*.log` and matching JSON
runtime evidence are retained in that profile, including failed runs. `--fresh` profiles contain
disposable saves/screenshots and can be deleted after their client exits and evidence is saved.
The runner stops only the subprocess it created; it does not stop another game or Gradle process.

Offline tooling verification (synthetic files and tiny Python subprocesses, no Minecraft):

```sh
python3 -m unittest discover -s tools/fixtures/tests -v
```

The offline suite exercises strict arguments, fresh profile isolation, artifact metadata,
classpath boundaries/space-containing paths, logging rewrites, platform JVM flags, failed-start
cleanup, deadline enforcement and verdicts. Actual Windows/Linux client behavior still needs
those platforms; these synthetic checks do not establish it.

Ubuntu CI owns the virtual display with `xvfb-run`. The workflows install
`external-display.init.gradle` in that job's Gradle init directory, so all nested starter builds
also reuse that display. Pinned Loom 1.17.19 otherwise starts a second Xvfb process and fails to
forward the configured output capture. The script disables only that duplicate launcher, requires
`DISPLAY` before a client starts, and preserves completion markers, FAIL checks, timeouts and
process-exit checks. It is not installed in a developer's Gradle home by this project.
