# Install PaneLib

PaneLib is a **client-side library**, separate from its example showcase. Installing its JAR alone
adds no screen or key binding. Your mod supplies those. The target is Minecraft **26.2**, Fabric
Loader **0.19.3 or newer**, **Java 25**, and **Fabric API**. Other versions/loaders are not claimed.

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

For an unpacked starter ZIP, run `./gradlew runClient` inside `pane-first-screen/`. Its included
`panelib-release.properties` selects the exact bundled library version in `libs/`. Both modes open
at the title screen; no world is needed. On a fresh normal profile, complete Minecraft's first-start
accessibility screen before the starter opens. `-PsmokeTest` uses its own disposable profile.

Source and local candidate bundles are available for testing. A public binary download exists only
when a release is actually published on [GitHub Releases](https://github.com/Sl0oth/PaneLib/releases)
or the distribution platform. A prepared local bundle, tag, or draft is not a published release.

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

The planned public repository is `https://sl0oth.github.io/PaneLib/maven/`. It is **not published yet**;
the checked-in release catalog is empty. Continue using the local repository or bundled starter.
The ZIP verifies repository consumption locally; it does not prove public downloading.
`-PpaneJar=/path/to/panelib.jar` remains available for a specific file, including renamed files,
but cannot be combined with `-PpaneRepository`.

After publication is verified, use the exact approved version from the repository's release catalog
and its matching starter. A development version in root `gradle.properties` does not establish that
the coordinate is available online. The public repository will allow anonymous reads with no GitHub
login or token. Maintainer setup and publication gates are in [public Maven hosting](public-maven.md).

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
unpacked repository path (or a real hosted URL, once one exists) in your project's `gradle.properties`:

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
