# First PaneLib screen

This is a complete, independent Fabric 26.2 mod. It opens a five-control settings screen at the
Minecraft title screen, saves settings to `config/pane-example.properties`, and restores its parent
on Cancel/Escape. H opens it again in-game. No world is needed for the first launch. On a fresh Minecraft profile, finish its accessibility
onboarding screen first; the example opens when you reach the title screen.

## Run

Install Java 25. From the **repository root**, build the current library, then run the independent
starter:

```sh
./gradlew :pane:build
cd examples/first-screen
./gradlew runClient
```

In this checkout, the starter reads only the release version from the root `gradle.properties`
and selects `pane/build/libs/panelib-<version>.jar`. It does not depend on PaneLib's source project,
Maven Local, or the demo module. An ignored old JAR in this starter's `libs/` cannot override it.

**Unpacked standalone ZIP:** run `./gradlew runClient` inside `pane-first-screen/`. Keep the included
`panelib-release.properties` and `libs/` JAR together. The descriptor supplies the packaged version;
no repository checkout is needed. Do not mix source and JARs from different release bundles.

On Windows, use `gradlew.bat` (or `.\gradlew.bat` in PowerShell) instead of `./gradlew`.
`java -version` should report Java 25; compilation and the client use that toolchain. Gradle discovers
an installed JDK but this starter does not download one. If detection fails, set `JAVA_HOME` to
an installed JDK 25 before launching Gradle. First dependency downloads can take several minutes.

## Select a different binary or repository

To test a specific local JAR, including a renamed file:

```sh
./gradlew runClient "-PpaneJar=/path/to/My Mods/panelib.jar"
```

The selected JAR must match the version from the descriptor/checkout. For an intentional version
change, add `-PpaneVersion=<exact-version>`. The starter checks the Fabric mod id, client environment,
version, and required owner-aware screen API before compiling. An older 0.9.8 JAR fails with a clear
explanation; it is never overwritten. Relative paths resolve from the starter directory.

The release bundle also includes a local Maven repository ZIP. Unpack it, then point to its
`panelib-maven-<version>/` directory, the directory directly containing `dev/`:

```sh
./gradlew runClient "-PpaneRepository=/path/to/unpacked-repository"
```

This resolves the exact `dev.pane:panelib:<version>` module through a restricted repository entry.
`file:` URLs and HTTPS repository URLs are supported. An explicit `-PpaneVersion=<exact-version>`
can select another release; the default comes from the descriptor/checkout. Maven-resolved binaries
receive the same metadata/API check before compilation. Do not combine `-PpaneRepository` and
`-PpaneJar`. The local repository ZIP is a reproducible dry run, not proof of a hosted service:
no public Maven endpoint is configured or invented here.

Keep any override on later smoke/build commands. Without an override, both checkout and ZIP modes
use their matching default binary. The generated `fabric.mod.json` declares that exact PaneLib
version, and optional bundled output contains that same resolved artifact.

## Adapt it

- `src/main/java/example/pane/FirstScreenClient.java` contains the entrypoint, opening binding,
  layout, event handling and real disk persistence. Replace the sample controls with yours.
- State keys (`name`, `hints`, `volume`) store values. Control ids (`name`, `volume`, `save`) identify
  focus and hit targets. Keep each control id stable and unique within a screen.
- Save commits settings; Cancel/Escape discards unsaved edits. H reconstructs the screen from saved data.
- The settings screen opts into `pauseGame(true)`, so ordinary single-player play pauses while it
  is open. It does not stop a multiplayer server or a single-player world shared over LAN.
- `fabric.mod.json` declares both Fabric API and PaneLib as runtime requirements.
- `FirstScreenSmokeTest.java` is optional test code, kept separate from the screen you adapt. Remove
  that class, the `SMOKE`/`smoke` fields, and its tick call when you do not need the sample's fixture.
- The opt-in smoke check exercises the packaged JAR and writes only to `run-example-smoke/`:
  normal settings and accessibility preferences in `run-example/` are left alone.
  `./gradlew runClient -PsmokeTest`. The Gradle task requires `[ConsumerTest] complete PASS`, rejects failure output, and times out after five minutes.
  It verifies typing, keyboard slider/Save, disk persistence, resize, Cancel/Escape discarding draft
  edits, parent restoration and reopen. Logs and screenshots are under `run-example-smoke/`.

The starter uses fixed point sizes for a small form. Your layout still needs a minimum usable
window size or scrolling for longer forms. The source checkout includes `docs/getting-started.md`
and `docs/migrating-to-panelib.md` for state, scaling and lifecycle rules. Released guides are on
the [documentation website](https://sl0oth.github.io/PaneLib/); a standalone starter ZIP does not
include the repository's documentation directory.

## Deliver to players

Run `./gradlew build`. Install `build/libs/pane-first-screen-1.0.0.jar`, the matching PaneLib JAR,
and Fabric API in the player's Fabric 26.2 `mods` folder. PaneLib alone intentionally adds no screen
or key binding. Do not distribute source or Javadoc JARs as mods.

Alternatively, `./gradlew build -PbundlePane` embeds PaneLib as a nested JAR. Players then install
only the example mod and Fabric API. Check the generated `fabric.mod.json`/`META-INF/jars` before
shipping. Choose one delivery approach and document it on your mod's download page.

## Release identity

The current candidate has its own version, separate from the preserved submitted 0.9.8 snapshot.
The repository root `gradle.properties` is authoritative; packaging writes its version into
`panelib-release.properties`. Neither the starter nor this README publishes a release or modifies
older bundles. Retain the candidate checksums and manifest when sharing local test artifacts.
