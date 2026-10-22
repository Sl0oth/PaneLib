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
            case "restore" -> { screen.setValue("min:" + arg, false); raise(screen, arg); }
            case "hideothers" -> {
                for (String id : PaneOsShowcase.stack(screen.stringValue("z", ""))) if (!id.equals(arg)) screen.setValue("min:" + id, true);
            }
            case "unlock" -> unlock(screen);
            case "lock" -> {
                screen.setValue("cc", false); screen.setValue("spot", false); screen.setValue("nc", false); screen.setValue("apps", false);
                screen.setValue("lock:pw", "");
                screen.setValue("locked", true);
                screen.requestFocus("lock:field");
            }
            case "restart" -> {
                screen.setValue("locked", true);
                screen.setValue("lock:pw", "");
                screen.setValue("boot:at", (double) System.currentTimeMillis());
                screen.requestFocus("lock:field");
            }
            case "nc" -> {
                if (arg.equals("clear")) screen.setValue("notice:history", "");
                else { screen.setValue("nc", !screen.booleanValue("nc", false)); screen.setValue("cc", false); }
            }
            case "apps" -> { screen.setValue("apps", !screen.booleanValue("apps", false)); screen.setValue("spot", false); screen.setValue("cc", false); }
            case "network" -> { screen.setValue("wifi:network", arg); screen.setValue("wifi", true); }
            case "settings" -> { openApp(screen, "settings"); screen.setValue("settings:pane", Double.parseDouble(arg)); }
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
            case "go" -> {
                openApp(screen, "files");
                screen.setValue("files:side", switch (arg) { case "recents" -> 1; case "applications" -> 2; case "desktop" -> 3; default -> 4; });
            }
            case "leave" -> screen.closeToParent();
            case "desk" -> desk(screen, arg);
            case "rem" -> screen.setValue("rem:" + arg, !screen.booleanValue("rem:" + arg, arg.equals("2")));
            case "music" -> music(screen, arg);
            default -> PaneOsApps.action(screen, verb, arg);
        }
    }

    static void openApp(UiScreen screen, String id) {
        screen.setValue("spot", false);
        screen.setValue("cc", false);
        screen.setValue("nc", false);
        if (!id.equals("launch")) screen.setValue("apps", false);
        var client = PaneOsShowcase.client();
        switch (id) {
            case "mail" -> { client.gui.setScreen(GmailShowcase.createScreen(screen)); return; }
            case "wayfarer" -> { client.gui.setScreen(WayfarerShowcase.createScreen(screen)); return; }
            case "launch" -> { screen.setValue("apps", !screen.booleanValue("apps", false)); return; }
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

    private static void unlock(UiScreen screen) {
        if (!screen.booleanValue("locked", true)) return;
        screen.setValue("locked", false);
        screen.setValue("lock:pw", "");
        screen.clearFocus();
        if (!screen.booleanValue("welcomed", false)) {
            screen.setValue("welcomed", true);
            PaneOsShowcase.notify(screen, "settings", "Welcome to PaneOS", "Everything here is drawn by Pane. Press ⌘ Space to search.");
            screen.setValue("notice:at", (double) System.currentTimeMillis() + 700);
        }
    }

    private static void desk(UiScreen screen, String arg) {
        int split = arg.indexOf(':');
        String verb = split < 0 ? arg : arg.substring(0, split), key = split < 0 ? "" : arg.substring(split + 1);
        switch (verb) {
            case "none" -> screen.setValue("desk:sel", "");
            case "open" -> { openApp(screen, key.equals("shot") ? "photos" : key.equals("notes") ? "notes" : "files"); }
            case "info" -> PaneOsShowcase.notify(screen, "files", key.equals("desktop") ? "Desktop" : "Get Info",
                key.equals("desktop") ? "248.6 GB available on PaneOS HD." : "Created today · Stored on PaneOS HD.");
            case "trash" -> {
                screen.setValue("desk:trashed:" + key, true);
                screen.setValue("desk:sel", "");
                PaneOsShowcase.notify(screen, "trash", "Moved to Trash", "You can find it in Trash until you empty it.");
            }
            case "newfolder" -> screen.setValue("desk:folders", screen.numberValue("desk:folders", 0) + 1);
            case "wallpaper" -> { openApp(screen, "settings"); screen.setValue("settings:pane", 4D); }
            default -> screen.setValue("desk:sel", verb);
        }
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
            case "lock:pw" -> unlock(screen);
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
