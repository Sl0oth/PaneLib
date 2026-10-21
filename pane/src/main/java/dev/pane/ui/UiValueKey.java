package dev.pane.ui;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Keeps arbitrary consumer keys separate from internal action delimiters. */
final class UiValueKey {
    private UiValueKey() { }
    static String encode(String key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
    }
    static String decode(String key) {
        return new String(Base64.getUrlDecoder().decode(key), StandardCharsets.UTF_8);
    }
}
