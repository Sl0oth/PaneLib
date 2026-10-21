package dev.pane.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UiValueKeyTest {
    @Test void consumerKeysRoundTripWithoutActionDelimiters() {
        for (String key : new String[]{"normal", "a=b", "", "a:b=é🚀", "\n=\u0000"}) {
            String encoded = UiValueKey.encode(key);
            assertFalse(encoded.contains("="));
            assertEquals(key, UiValueKey.decode(encoded));
        }
    }
}
