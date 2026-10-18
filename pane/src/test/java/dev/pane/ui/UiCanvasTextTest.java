package dev.pane.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

final class UiCanvasTextTest {
    @Test
    void wrappingHonoursWidthNewlinesAndLongWords() {
        var image = new BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            UiCanvas canvas = new UiCanvas(graphics);
            List<String> lines = canvas.wrapText("one two three\nfour supercalifragilistic", 42, 10,
                Font.PLAIN, UiCanvas.SANS);
            assertEquals("one two", lines.getFirst());
            assertFalse(lines.stream().anyMatch(line -> canvas.textWidth(line, 10, Font.PLAIN) > 42.01F));
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void textWidthUsesTheSameFontSelectionAsText() {
        var image = new BufferedImage(320, 120, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            UiCanvas canvas = new UiCanvas(graphics);
            String label = "Trail Snacks";
            float measured = canvas.textWidth(label, 12, Font.BOLD, UiCanvas.MONO);
            float expected = graphics.getFontMetrics(new Font(UiCanvas.MONO, Font.BOLD, 1).deriveFont(12F))
                .stringWidth(label);

            assertEquals(expected, measured);
            assertEquals(0F, canvas.textWidth(null, 12, Font.BOLD, UiCanvas.MONO));
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void richTextIsOptInAndPreservesMinecraftStyles() {
        UiRichText plain = UiRichText.plain("§aLiteral &ltext");
        assertEquals("§aLiteral &ltext", plain.plainText());
        assertEquals("§aLiteral &ltext", plain.spans().getFirst().text());

        UiRichText legacy = UiRichText.legacy("§aGreen §lBold §oItalic §nUnder §mStrike §rPlain ✦");
        assertEquals("Green Bold Italic Under Strike Plain ✦", legacy.plainText());
        assertTrue(legacy.spans().stream().anyMatch(span -> span.color() != null && span.color() == 0x55FF55));
        assertTrue(legacy.spans().stream().anyMatch(UiRichText.Span::bold));
        assertTrue(legacy.spans().stream().anyMatch(UiRichText.Span::italic));
        assertTrue(legacy.spans().stream().anyMatch(UiRichText.Span::underlined));
        assertTrue(legacy.spans().stream().anyMatch(UiRichText.Span::strikethrough));

        UiRichText ampersand = UiRichText.legacyAmpersand("&bAqua &lBold AT&T");
        assertEquals("Aqua Bold AT&T", ampersand.plainText());
        assertTrue(ampersand.spans().stream().anyMatch(UiRichText.Span::bold));
    }

    @Test
    void componentRgbAndDecorationsRenderAndEllipsizeWithoutCodes() {
        Component component = Component.literal("Custom RGB ")
            .withStyle(Style.EMPTY.withColor(0x12ABEF).withBold(true))
            .append(Component.literal("italic underline")
                .withStyle(Style.EMPTY.withItalic(true).withUnderlined(true).withStrikethrough(true)));
        UiRichText rich = UiRichText.component(component);
        assertEquals("Custom RGB italic underline", rich.plainText());
        assertTrue(rich.spans().stream().anyMatch(span -> Integer.valueOf(0x12ABEF).equals(span.color())));

        var image = new BufferedImage(320, 120, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            UiCanvas canvas = new UiCanvas(graphics);
            float originalWidth = canvas.formattedTextWidth(rich, 12, UiCanvas.SANS);
            UiRichText shortValue = canvas.ellipsizeFormatted(rich, originalWidth * .55F, 12, UiCanvas.SANS);
            assertTrue(shortValue.plainText().endsWith("…"));
            assertTrue(canvas.formattedTextWidth(shortValue, 12, UiCanvas.SANS) <= originalWidth * .55F + .01F);
            assertTrue(canvas.formattedText(shortValue, 4, 4, 12, 0xFFFFFFFF) > 0);
            assertTrue(canvas.ellipsizeFormatted(rich, 0, 12, UiCanvas.SANS).isEmpty());
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void shortOffsetShadowStaysInsideItsDeclaredBoundsWhenPartiallyClipped() {
        BufferedImage image = new BufferedImage(320, 220, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            UiCanvas canvas = new UiCanvas(graphics, image);
            // Same public tab geometry as the regression fixture: 34px high, spread 5, offset y=3.
            graphics.setClip(new Rectangle(60, 70, 200, 70));
            canvas.shadow(0x7D1C1008, 5, 0, 3, 82, 88, 156, 34, 2);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) continue;
                assertTrue(x >= 77 && x < 243 && y >= 86 && y < 130,
                    "shadow pixel escaped expected clipped tab bounds at " + x + ',' + y);
            }
        } finally {
            graphics.dispose();
        }
    }

    @Test
    void partialShadowRepaintHasNoTransparentRightOrBottomClipEdgeAtFractionalScale() {
        BufferedImage image = new BufferedImage(360, 240, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(new java.awt.Color(0xFFF4F0E4, true));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            // Simulate UiScreen's one physical dirty rectangle at a non-unit retained scale.
            int pixelX = 54, pixelY = 63, pixelW = 213, pixelH = 117;
            for (int y = pixelY; y < pixelY + pixelH; y++) {
                for (int x = pixelX; x < pixelX + pixelW; x++) image.setRGB(x, y, 0);
            }
            float scale = 1.5F;
            graphics.scale(scale, scale);
            graphics.clip(new java.awt.geom.Rectangle2D.Float(pixelX / scale, pixelY / scale,
                pixelW / scale, pixelH / scale));
            UiCanvas canvas = new UiCanvas(graphics, image);
            canvas.fill(0xFFF4F0E4, 0, 0, image.getWidth() / scale, image.getHeight() / scale);
            canvas.shadow(0x7D1C1008, 5, 0, 3, 82, 88, 156, 34, 2);
            canvas.roundRect(0xFFFFFDF5, 82, 88, 156, 34, 2);
            for (int y = pixelY; y < pixelY + pixelH; y++) for (int x = pixelX; x < pixelX + pixelW; x++) {
                assertTrue((image.getRGB(x, y) >>> 24) != 0, "transparent partial-clip edge at " + x + ',' + y);
            }
        } finally {
            graphics.dispose();
        }
    }
}
