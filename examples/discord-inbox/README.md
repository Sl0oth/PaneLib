# Gather — community chat

An app-style conversation layout with original names, avatars and artwork: server channels, DMs, friends, settings, menus and editable messages. It is an offline example, not a connection to Discord or a real messaging service.

From the repository root, with Java 25 installed:

```sh
./gradlew :pane-demo:runGather
```

On Windows use `gradlew.bat`. No world or Python installation is needed. Finish Minecraft's first-run accessibility screen if shown. Escape returns to Minecraft; H reopens the example. From another example, N opens Gather.

Try sending a local DM, switching channels, searching messages, changing appearance, opening the server menu and saving settings. Unsaved settings guard Escape.

Read `pane-demo/src/main/java/dev/pane/demo/DiscordShowcase.java` for state/actions and `DiscordReferenceView.java` for its visual layout. The historical source names are kept to avoid noisy renaming. `UiScaling` now scales drawing, input and popup anchors together; `UiScrollFollow.WHEN_AT_END`, styled popup triggers, semantic painted buttons and native text editors replace the earlier workarounds.

Developer regression: `python3 examples/discord-inbox/launch.py --test`. The normal `launch.py` and macOS `Launch.command` forward to Gradle. See [all examples](../README.md); historical capture notes are archived under `examples/archive/notes/`.
