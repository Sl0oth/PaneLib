# PaneLib documentation

PaneLib is a native Minecraft UI library for Fabric. It draws with Java and Minecraft's own
renderer: no HTML, webview, Chromium, bitmap UI assets, or generated images are part of the
runtime.

This folder is the source of truth for the library documentation. It is deliberately plain
Markdown so it can be published on a website without being tied to a documentation platform.
The repository README remains the short project overview and demo launcher guide.

## Read next

- [Build your first screen](getting-started.md)
- [Controls, layout, scrolling, and text input](ui-basics.md)
- [Styled inputs, undo, modal focus, and diagnostics](input-and-focus.md)
- [Canvas, themes, animation, and performance](drawing-and-theming.md)
- [Performance diagnostics and measurement](performance.md)
- [Minecraft backdrop, raw input, close behavior, and asynchronous data](minecraft-integration.md)
- [Recreate the PaneLib paper-craft design](paper-craft.md)
- [Use the local Maven artifact](installation.md)
- [What is ready and what is still missing](status.md)
- [Layered UI and motion](layered-ui-and-motion.md)
- [HUDs and composable motion](huds-and-composable-motion.md)

## Documentation rule

Every example here is written against the public API. The clean-room consumer test is not allowed
to read the renderer implementation; if the documentation forces it to guess, the documentation
or API is considered broken.

