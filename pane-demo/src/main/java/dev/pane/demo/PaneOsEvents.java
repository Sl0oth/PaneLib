package dev.pane.demo;

import dev.pane.ui.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** PaneOS behaviour: window management, settings, and each app's actions. */
final class PaneOsEvents {
    private PaneOsEvents() { }

    static void handle(UiScreen screen, UiEvent event) {
        switch (event.type()) {
            case ACTION -> handleAction(screen, event.key());
            case SUBMIT -> submit(screen, event.key(), event.value());
            default -> { }
        }
    }

    static void handleAction(UiScreen screen, String action) {
        int split = action.indexOf(':');
        String verb = split < 0 ? action : action.substring(0, split), arg = split < 0 ? "" : action.substring(split + 1);
        switch (verb) {
            case "open" -> openApp(screen, arg);
            case "close" -> { if (!arg.isEmpty()) { screen.setValue("open:" + arg, false); screen.setValue("max:" + arg, false); } }
            case "min" -> { if (!arg.isEmpty()) screen.setValue("min:" + arg, true); }
            case "zoom" -> { if (!arg.isEmpty()) screen.setValue("max:" + arg, !screen.booleanValue("max:" + arg, false)); }
            case "front" -> raise(screen, arg);
            case "cc" -> { screen.setValue("cc", !screen.booleanValue("cc", false)); screen.setValue("spot", false); }
            case "spot" -> toggleSpotlight(screen);
            case "wifi", "bt", "airdrop", "focus" -> {
                boolean on = !screen.booleanValue(verb, !verb.equals("focus"));
                screen.setValue(verb, on);
                if (verb.equals("focus") && on) PaneOsShowcase.notify(screen, "settings", "Focus is on", "Notifications are silenced until you turn it off.");
            }
            case "darkmode" -> screen.setValue("dark", !screen.booleanValue("dark", false));
            case "appearance" -> screen.setValue("dark", arg.equals("dark"));
            case "accent" -> screen.setValue("accent", Integer.parseInt(arg));
            case "wallpaper" -> screen.setValue("wallpaper", Integer.parseInt(arg));
            case "widgets" -> screen.setValue("widgets", !screen.booleanValue("widgets", true));
            case "magnify" -> screen.setValue("magnify", !screen.booleanValue("magnify", true));
            case "timer" -> {
                boolean running = screen.numberValue("timer:end", 0) > System.currentTimeMillis();
                screen.setValue("timer:end", running ? 0D : (double) (System.currentTimeMillis() + 5 * 60_000));
                if (!running) PaneOsShowcase.notify(screen, "calendar", "Timer started", "5 minutes. It counts down in the menu bar.");
            }
            case "shot" -> {
                screen.setValue("cc", false);
                screen.setValue("shot:count", screen.numberValue("shot:count", 0) + 1);
                String name = "Screenshot " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("h.mm.ss", Locale.ENGLISH));
                screen.setValue("desk:shot", name);
                PaneOsShowcase.notify(screen, "photos", "Screenshot", name + " was added to the Desktop.");
            }
            case "about" -> {
                if (arg.isEmpty()) { openApp(screen, "settings"); screen.setValue("settings:pane", 9); }
                else PaneOsShowcase.notify(screen, arg, PaneOsShowcase.displayName(arg), "Version 26.2 · Drawn entirely with Pane.");
            }
            case "help" -> PaneOsShowcase.notify(screen, "settings", "PaneOS Help",
                "Drag windows by their title bar. ⌘ Space searches, ⌘ W closes, ⌘ M minimizes.");
            case "go" -> { openApp(screen, "files"); screen.setValue("files:side", arg.equals("recents") ? 1 : 4); }
            case "leave" -> screen.closeToParent();
            case "desk" -> {
                if (arg.startsWith("open:")) openApp(screen, "files");
                else screen.setValue("desk:sel", arg);
            }
            case "rem" -> screen.setValue("rem:" + arg, !screen.booleanValue("rem:" + arg, arg.equals("2")));
            case "music" -> music(screen, arg);
            default -> PaneOsApps.action(screen, verb, arg);
        }
    }

    static void openApp(UiScreen screen, String id) {
        screen.setValue("spot", false);
        screen.setValue("cc", false);
        var client = PaneOsShowcase.client();
        switch (id) {
            case "mail" -> { client.gui.setScreen(GmailShowcase.createScreen(screen)); return; }
            case "wayfarer" -> { client.gui.setScreen(WayfarerShowcase.createScreen(screen)); return; }
            case "launch" -> { toggleSpotlight(screen); return; }
            case "downloads" -> { id = "files"; screen.setValue("files:side", 5); }
            case "trash" -> { id = "files"; screen.setValue("files:side", 9); }
            default -> { }
        }
        if (PaneOsShowcase.app(id) == null) return;
        boolean wasOpen = screen.booleanValue("open:" + id, false);
        if (!wasOpen) screen.setValue("launch:" + id, screen.numberValue("launch:" + id, 0) + 1);
        screen.setValue("open:" + id, true);
        screen.setValue("min:" + id, false);
        raise(screen, id);
        if (id.equals("notes")) screen.requestFocus("notes:editor");
        if (id.equals("terminal")) screen.requestFocus("terminal:input");
    }

    static void raise(UiScreen screen, String id) {
        if (PaneOsShowcase.app(id) == null) return;
        var z = PaneOsShowcase.stack(screen.stringValue("z", ""));
        if (!z.isEmpty() && z.getLast().equals(id)) return;
        z.remove(id);
        z.add(id);
        screen.setValue("z", String.join(",", z));
    }

    static void toggleSpotlight(UiScreen screen) {
        boolean open = !screen.booleanValue("spot", false);
        screen.setValue("spot", open);
        screen.setValue("cc", false);
        if (open) { screen.setValue("spot:q", ""); screen.requestFocus("spot:field"); }
        else screen.clearFocus();
    }

    private static void submit(UiScreen screen, String key, String value) {
        switch (key) {
            case "spot:q" -> {
                var results = PaneOsShowcase.results(value);
                if (!results.isEmpty()) openApp(screen, results.getFirst().id());
            }
            default -> PaneOsApps.submit(screen, key, value);
        }
    }

    private static void music(UiScreen screen, String arg) {
        int track = (int) screen.numberValue("music:track", 0), count = PaneOsApps.TRACKS.length;
        switch (arg) {
            case "play" -> screen.setValue("music:playing", !screen.booleanValue("music:playing", false));
            case "next" -> { screen.setValue("music:track", (track + 1) % count); screen.setValue("music:playing", true); }
            case "prev" -> { screen.setValue("music:track", (track + count - 1) % count); screen.setValue("music:playing", true); }
            default -> { screen.setValue("music:track", Integer.parseInt(arg)); screen.setValue("music:playing", true); }
        }
        screen.setValue("music:started", (double) System.currentTimeMillis());
    }
}
