# Install PaneLib 0.9.8 locally

Until a public Maven host is chosen, publish the release candidate to Maven Local from this
repository:

```sh
./gradlew clean :pane:publishToMavenLocal
```

Minecraft 26.2 is unobfuscated. A standalone 26.2 consumer must use the new Loom plugin id,
must not add an official mappings dependency, and uses normal `implementation` dependencies.
This is a complete minimal `build.gradle`:

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version '1.17.19'
}

repositories {
    mavenLocal()
    maven { url = 'https://maven.fabricmc.net/' }
    mavenCentral()
}

dependencies {
    minecraft "com.mojang:minecraft:26.2"
    implementation "net.fabricmc:fabric-loader:0.19.3"
    implementation "net.fabricmc.fabric-api:fabric-api:0.157.0+26.2"
    implementation "dev.pane:panelib:0.9.8"
}
```

And the matching plugin repositories in `settings.gradle`:

```groovy
pluginManagement {
    repositories {
        maven { url = 'https://maven.fabricmc.net/' }
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Declare the client dependency in the consuming mod's `fabric.mod.json`:

```json
{
  "environment": "client",
  "depends": {
    "fabricloader": ">=0.19.3",
    "minecraft": "~26.2",
    "java": ">=25",
    "fabric-api": "*",
    "panelib": ">=0.9.8"
  }
}
```

The Java package is `dev.pane.ui`. The Fabric mod id and Maven artifact name are `panelib`.

The publication contains the remapped library JAR, source JAR, Javadoc JAR, Gradle module
metadata, and POM. It does not contain the demo or preserved MCEF classes. Run the boundary check
directly with:

```sh
./gradlew :pane:verifyPaneArtifact
```

For the clean-room test, the consuming project must depend on the Maven artifact. Do not add the
PaneLib repository as a composite build or project dependency; that would accidentally give the
consumer access to implementation sources.
