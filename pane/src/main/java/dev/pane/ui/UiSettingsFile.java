package dev.pane.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Reads and writes a screen's saved values as a properties file a player can edit by hand. */
final class UiSettingsFile {
    private UiSettingsFile() { }

    /** Each default must be a string, a boolean or a finite number; that type is what loads and saves. */
    static void requireSupported(Map<String, ?> defaults) {
        for (var entry : defaults.entrySet()) {
            Object value = entry.getValue();
            boolean number = value instanceof Number n && Double.isFinite(n.doubleValue());
            if (entry.getKey() == null || entry.getKey().isBlank() || !(value instanceof String || value instanceof Boolean || number))
                throw new IllegalArgumentException("Setting " + entry.getKey() + " needs a string, boolean or finite number default");
        }
    }

    /**
     * The value of every key in {@code defaults}, read from {@code file} as that default's type.
     * A missing or unreadable file, a missing key and a value of the wrong type all give the default.
     * Numbers load as doubles.
     */
    static Map<String, Object> load(Path file, Map<String, ?> defaults) {
        var saved = new Properties();
        if (file != null && Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) { saved.load(in); }
            catch (IOException | IllegalArgumentException unreadable) { saved.clear(); }
        }
        var values = new LinkedHashMap<String, Object>();
        for (var entry : defaults.entrySet()) values.put(entry.getKey(), parse(saved.getProperty(entry.getKey()), entry.getValue()));
        return values;
    }

    private static Object parse(String text, Object fallback) {
        Object standard = fallback instanceof Number number ? (Object) number.doubleValue() : fallback;
        if (text == null) return standard;
        if (fallback instanceof String) return text;
        String trimmed = text.trim();
        if (fallback instanceof Boolean)
            return trimmed.equalsIgnoreCase("true") ? Boolean.TRUE : trimmed.equalsIgnoreCase("false") ? Boolean.FALSE : standard;
        try {
            double number = Double.parseDouble(trimmed);
            return Double.isFinite(number) ? (Object) number : standard;
        } catch (NumberFormatException invalid) {
            return standard;
        }
    }

    /** Writes {@code values} beside the old file and swaps it in, so a failed save keeps the last good one. */
    static void save(Path file, Map<String, ?> values, String comment) throws IOException {
        var output = new Properties();
        for (var entry : values.entrySet()) if (entry.getValue() != null) output.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
        Path folder = file.toAbsolutePath().getParent();
        if (folder != null) Files.createDirectories(folder);
        Path draft = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(draft)) { output.store(out, comment); }
        try {
            Files.move(draft, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(draft, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
