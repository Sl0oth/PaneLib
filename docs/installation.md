# Install PaneLib

PaneLib is a **client-side library**, separate from its example showcase. Installing its JAR alone
adds no screen or key binding. Your mod supplies those. The target is Minecraft **26.2**, Fabric
Loader **0.19.3 or newer**, **Java 25**, and **Fabric API**. Other versions/loaders are not claimed.

## Public Maven dependency

The latest beta is [`1.0.0-beta.1`](https://github.com/Sl0oth/PaneLib/releases/tag/v1.0.0-beta.1). Add this repository and dependency to your Minecraft 26.2
Fabric mod's `build.gradle`:

```groovy
repositories {
    maven {
        url = uri('https://sl0oth.github.io/PaneLib/maven/')
        content { includeModule('dev.pane', 'panelib') }
    }
}
dependencies {
    implementation 'dev.pane:panelib:1.0.0-beta.1'
}
```

No GitHub login or token is needed. Install the matching runtime with your mod, or deliberately
bundle it with Loom `include` as described below. Fabric API remains a separate dependency.
The runtime JAR is on the
[GitHub beta release](https://github.com/Sl0oth/PaneLib/releases/tag/v1.0.0-beta.1). To try the starter
without building the library, download that release's source code, then run the starter against Maven:

```sh
cd examples/first-screen
./gradlew runClient -PpaneRepository=https://sl0oth.github.io/PaneLib/maven/
```

The checkout's root `gradle.properties` selects the matching released version.

## Fastest first screen

Use the [independent starter](../examples/first-screen/README.md). In a source checkout:

```sh
./gradlew :pane:build
cd examples/first-screen
./gradlew runClient
```

The starter reads the current version from root `gradle.properties` and selects that built runtime
JAR. It shares release metadata only, not implementation sources or Gradle project dependencies.
The ignored old convenience JAR in `examples/first-screen/libs/` is not selected in checkout mode.

For a standalone starter ZIP (built locally by `prepareRelease`, and attached to the 0.9.9-beta.1
release), run `./gradlew runClient` inside `pane-first-screen/`. Its included
`panelib-release.properties` selects the exact bundled library version in `libs/`. Both modes open
at the title screen; no world is needed. On a fresh normal profile, complete Minecraft's first-start
accessibility screen before the starter opens. `-PsmokeTest` uses its own disposable profile.

Source and the published beta bundle are available for testing. Future local candidates are not
published releases until their upload and public verification have completed.

## Local Maven repository dry run

The release bundle includes a repository ZIP with this layout:

```text
panelib-maven-<version>/
  dev/pane/panelib/<version>/
    panelib-<version>.pom
    panelib-<version>.jar
    panelib-<version>-sources.jar
    panelib-<version>-javadoc.jar
```

Unpack it and point the starter at the directory containing `dev/`:

```sh
./gradlew runClient "-PpaneRepository=/path/to/unpacked-repository"
```

The version still comes from `panelib-release.properties` or the source checkout. Use
`-PpaneVersion=<exact-version>` only when deliberately choosing another version. The starter accepts
local paths, `file:` URLs, or HTTPS URLs, restricts this repository to the `dev.pane:panelib` module,
and validates the resolved runtime's id/environment/version/API before compilation.

The public repository above has been verified with the matching released starter. Use an exact
version from the [published catalog](https://sl0oth.github.io/PaneLib/maven/catalog.json), not an
unpublished development version. `-PpaneJar=/path/to/panelib.jar` remains available for a specific
file, including renamed files, but cannot be combined with `-PpaneRepository`.
Maintainer setup is documented in [public Maven hosting](public-maven.md).

## Add PaneLib to an existing mod

For a local file dependency, copy the matching runtime JAR into `libs/`; it may be renamed to
`panelib.jar`:

```groovy
dependencies {
    minecraft 'com.mojang:minecraft:26.2'
    implementation 'net.fabricmc:fabric-loader:0.19.3'
    implementation 'net.fabricmc.fabric-api:fabric-api:0.157.0+26.2'
    implementation files('libs/panelib.jar')
}
```

For a Maven dependency, set `paneVersion` to the exact release version and `paneRepository` to your
unpacked repository path or `https://sl0oth.github.io/PaneLib/maven/` in your project's `gradle.properties`:

```groovy
def paneVersion = providers.gradleProperty('paneVersion').get()
repositories {
    maven {
        url = uri(providers.gradleProperty('paneRepository').get())
        content { includeModule('dev.pane', 'panelib') }
    }
}
dependencies {
    implementation("dev.pane:panelib:${paneVersion}") {
        version { strictly(paneVersion) }
    }
}
```

The starter demonstrates stricter exclusive repository routing and binary validation. Its module
coordinate is also suitable for Loom `include` when intentionally bundling the dependency.

Minecraft 26.2 is unobfuscated: use `net.fabricmc.fabric-loom` (the starter pins `1.17.19`), normal
`implementation` dependencies, and no mappings dependency. Use an installed Java 25 toolchain for
both compilation and the development client:

```groovy
java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
tasks.withType(JavaCompile).configureEach { options.release = 25 }
tasks.withType(JavaExec).configureEach {
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
}
```

Declare the runtime requirement in your mod's `fabric.mod.json`. This fragment uses a Gradle-expanded
version, not a literal placeholder left in the final JAR:

```json
{
  "environment": "client",
  "depends": {
    "fabricloader": ">=0.19.3",
    "minecraft": "~26.2",
    "java": ">=25",
    "fabric-api": "*",
    "panelib": "=${paneVersion}"
  }
}
```

Merge this into your existing manifest. Using the same `paneVersion` selected for the dependency:

```groovy
processResources {
    inputs.property('paneVersion', paneVersion)
    filesMatching('fabric.mod.json') { expand(paneVersion: paneVersion) }
}
```

For file mode, define `paneVersion` from the matching release metadata as well. An exact version is
the conservative starting point; widen compatibility only after testing the versions you claim.
The Java package is `dev.pane.ui`; the Fabric mod id and Maven artifact name are `panelib`.
On Windows use `gradlew.bat`; quote command-line property arguments containing spaces.

## Deliver to players

**Separate dependency:** ship your mod JAR and tell players to install the matching PaneLib runtime
JAR and Fabric API into `mods/`. Mark both as required dependencies on your distribution page.
`implementation` alone does not package the library for players.

**Bundled dependency:** use the same module coordinate for `implementation` and Loom `include`.
The starter's `./gradlew build -PbundlePane` demonstrates this in local-file and repository modes.
Its exact-file mode uses restricted Ivy metadata to retain the supplied filename while providing
the module capability Loom requires. Verify `META-INF/jars` and the generated manifest before
shipping. Fabric API remains a separate dependency. Preserve the MIT notice and document bundling.

Never install source or Javadoc JARs as mods. Client-only means the server does not need PaneLib.
The demo module and its H/J bindings are separate from the library.

## Version and evidence boundaries

Root `gradle.properties` is the local candidate version authority. Candidate packaging copies that value
into the standalone starter descriptor, Maven coordinates, metadata, notes, and manifest. The
preserved submitted 0.9.8 snapshot stays unchanged; current candidate fixes use a new version.
The append-only public catalog, once deployed, records published versions independently of later
development bumps. Keep checksums and the release manifest with local candidate artifacts.
No Maven Local cache is required for the starter or local repository dry run.

The automated client task requires a completion marker, rejects failure output, and times out after
five minutes. Software-rendered CI is a functional check, not GPU performance certification.
See [migration considerations](migrating-to-panelib.md) before expanding an integration.
