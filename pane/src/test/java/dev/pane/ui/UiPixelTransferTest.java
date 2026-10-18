package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

final class UiPixelTransferTest {
    @Test void fullThenPartialUploadPreservesBackingColorsAndUntouchedNativePixels() {
        int[] source = {0xFF123456, 0x80123456, 0xFFABCDEF, 0x00010203, 0xFF0000FF, 0xFFFF0000};
        int[] original = source.clone();
        var bytes = ByteBuffer.allocateDirect(source.length * 4).order(ByteOrder.nativeOrder());
        var target = bytes.asIntBuffer();
        UiPixelTransfer.copy(source, target, 3, 0, 0, 3, 2);
        assertArrayEquals(original, source, "AWT backing must remain ARGB for blur and partial paints");
        assertEquals(0xFF563412, target.get(0));
        assertEquals(0x80563412, target.get(1));
        int[] uploaded = new int[6];
        target.get(0, uploaded);
        source[4] = 0x80224466;
        UiPixelTransfer.copy(source, target, 3, 1, 1, 1, 1);
        assertEquals(0x80224466, source[4]);
        assertEquals(0x80664422, target.get(4));
        for (int i = 0; i < 6; i++) if (i != 4) assertEquals(uploaded[i], target.get(i));
        UiPixelTransfer.copy(source, target, 3, 0, 0, 3, 2);
        assertEquals(0xFF563412, target.get(0), "a second full copy must not swap colors back");
    }
}
