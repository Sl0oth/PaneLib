# Pane Mail — inbox

A bright, dense mail layout with folders, search, categories, selection, starring and a working local composer. Sample messages stay in memory; nothing is sent or connected to Gmail.

From the repository root, with Java 25 installed:

```sh
./gradlew :pane-demo:runExamples
```

On Windows use `gradlew.bat`. No world or Python installation is needed. Finish Minecraft's first-run accessibility screen if shown. Escape returns to the inbox, then Minecraft; H reopens it. M opens Mail from another example.

Read `pane-demo/src/main/java/dev/pane/demo/GmailShowcase.java`. It uses semantic painted controls, styled native editors, retained scroll containers and the typography rendering preset. Its dense desktop layout uses fixed point sizing deliberately: a larger window shows more mail rather than making every row larger.

Developer regression:

```sh
./gradlew :pane-demo:runClient -Pisolated -PgmailTest
```

The scroll test uses a smaller window to guarantee overflow; a roomy inbox correctly has nothing to scroll. See [all examples](../README.md).
