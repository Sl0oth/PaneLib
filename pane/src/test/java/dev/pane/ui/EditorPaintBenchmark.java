package dev.pane.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;

/** Explicit warmed CPU repaint workload, outside normal tests. Compare only matched environments. */
public final class EditorPaintBenchmark {
    private static final int WIDTH = 520, HEIGHT = 280;
    private static final String FAMILY = Font.SANS_SERIF;
    private static final UiInputStyle STYLE = UiInputStyle.defaults().font(FAMILY, 12, Font.PLAIN);
    private static final String SHORT = "Waypoint café e\u0301: ready to explore";
    private static final String LONG = "Waypoint café e\u0301 0123456789 ".repeat(640);
    private static final String AREA = "Waypoint e\u0301 0123456789\n".repeat(2_000);
    private enum Placement { ZERO, TRANSLATED, CLIPPED_SCROLL }
    private record Sample(long wall, long cpu, long allocated) { }
    private record Fingerprint(String pixels, String geometry) { }

    public static void main(String[] args) throws Exception {
        int warmup = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        int measured = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        if (warmup < 20 || measured < 20) throw new IllegalArgumentException("Use at least 20 warmup and 20 measured rounds");
        var bean = ManagementFactory.getThreadMXBean();
        if (bean.isCurrentThreadCpuTimeSupported() && !bean.isThreadCpuTimeEnabled()) bean.setThreadCpuTimeEnabled(true);
        var allocations = bean instanceof com.sun.management.ThreadMXBean extended ? extended : null;
        if (allocations != null && allocations.isThreadAllocatedMemorySupported()
                && !allocations.isThreadAllocatedMemoryEnabled()) allocations.setThreadAllocatedMemoryEnabled(true);
        boolean cpuAvailable = bean.isCurrentThreadCpuTimeSupported() && bean.isThreadCpuTimeEnabled();
        boolean allocationAvailable = allocations != null && allocations.isThreadAllocatedMemorySupported()
            && allocations.isThreadAllocatedMemoryEnabled();
        long thread = Thread.currentThread().threadId();
        String metadata = "{\"type\":\"environment\",\"java\":" + quote(System.getProperty("java.runtime.version"))
            + ",\"os\":" + quote(System.getProperty("os.name")) + ",\"arch\":" + quote(System.getProperty("os.arch"))
            + ",\"font\":" + quote(new Font(FAMILY, Font.PLAIN, 12).getFontName(Locale.ROOT))
            + ",\"warmup\":" + warmup + ",\"samples\":" + measured
            + ",\"cpu_available\":" + cpuAvailable + ",\"allocation_available\":" + allocationAvailable
            + ",\"scope\":\"CPU UiContext repaint; no GPU, Minecraft frame rate or cross-platform pixel claim\"}";
        System.out.println(metadata);
        var cases = new ArrayList<PaintCase>();
        for (Placement placement : Placement.values()) {
            cases.add(new PaintCase("short_field", SHORT, false, false, placement));
            cases.add(new PaintCase("long_field", LONG, false, false, placement));
            cases.add(new PaintCase("long_area_none", AREA, true, false, placement));
            cases.add(new PaintCase("long_area_selected", AREA, true, true, placement));
        }
        try {
            var order = new ArrayList<>(cases);
            var random = new Random(0x50414E45);
            for (int round = 0; round < warmup; round++) {
                Collections.shuffle(order, random);
                for (var test : order) test.paint();
            }
            for (var test : cases) test.before = test.fingerprint();
            for (int round = 0; round < measured; round++) {
                Collections.shuffle(order, random);
                for (var test : order) {
                    long allocated = allocationAvailable ? allocations.getThreadAllocatedBytes(thread) : -1;
                    long cpu = cpuAvailable ? bean.getCurrentThreadCpuTime() : -1;
                    long wall = System.nanoTime();
                    test.paint();
                    wall = System.nanoTime() - wall;
                    cpu = cpuAvailable ? bean.getCurrentThreadCpuTime() - cpu : -1;
                    allocated = allocationAvailable ? allocations.getThreadAllocatedBytes(thread) - allocated : -1;
                    test.samples.add(new Sample(wall, cpu, allocated));
                }
            }
            for (var test : cases) {
                var after = test.fingerprint();
                if (!test.before.equals(after)) throw new AssertionError("Fixed repaint changed pixels or geometry: " + test.name);
                System.out.printf(Locale.ROOT,
                    "{\"type\":\"case\",\"name\":%s,\"utf16_units\":%d,\"samples\":%d,"
                    + "\"wall_median_ms\":%.6f,\"wall_p95_ms\":%.6f,\"cpu_median_ms\":%.6f,\"cpu_p95_ms\":%.6f,"
                    + "\"allocated_median_bytes\":%d,\"allocated_p95_bytes\":%d,\"pixel_sha256\":%s,\"geometry_sha256\":%s}%n",
                    quote(test.name), test.text.length(), measured,
                    percentile(test.samples, 0, .5) / 1e6, percentile(test.samples, 0, .95) / 1e6,
                    cpuAvailable ? percentile(test.samples, 1, .5) / 1e6 : -1,
                    cpuAvailable ? percentile(test.samples, 1, .95) / 1e6 : -1,
                    percentile(test.samples, 2, .5), percentile(test.samples, 2, .95),
                    quote(after.pixels), quote(after.geometry));
            }
        } finally { for (var test : cases) test.graphics.dispose(); }
    }

    private static final class PaintCase {
        final String name, text;
        final boolean area, selected;
        final Placement placement;
        final BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        final UiCanvas canvas = new UiCanvas(graphics, image);
        final UiInteractionState retained = new UiInteractionState();
        final Map<String, Object> state = new HashMap<>();
        final List<Sample> samples = new ArrayList<>();
        List<UiInputRegion> paintedInputs = List.of();
        Fingerprint before;

        PaintCase(String label, String text, boolean area, boolean selected, Placement placement) {
            this.name = label + "_" + placement.name().toLowerCase(Locale.ROOT);
            this.text = text; this.area = area; this.selected = selected; this.placement = placement;
            state.put("value", text);
            retained.offsets.put("parent", 37.5D);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        }

        void paint() {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setColor(new Color(0xFF192028, true)); graphics.fillRect(0, 0, WIDTH, HEIGHT);
            graphics.setComposite(AlphaComposite.SrcOver);
            retained.beginPaint();
            paintedInputs = new ArrayList<>();
            // Cursor at the beginning keeps the selected and unselected area viewport identical.
            var ui = new UiContext(canvas, UiTheme.dark(), WIDTH, HEIGHT, 0xFFE3B85B,
                new UiRect(0, 0, WIDTH, HEIGHT), null, null, 1, 1, false, 0, state,
                new ArrayList<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new HashMap<>(),
                new ArrayList<>(), paintedInputs, new ArrayList<>(), new UiTextInputState("editor", 0, selected ? text.length() : 0),
                new ArrayList<>(), new ArrayList<>(), null, 1, null).withInteractions(retained);
            switch (placement) {
                case ZERO -> editor(ui);
                case TRANSLATED -> ui.withTranslation(31.25F, 19.5F, this::editor);
                case CLIPPED_SCROLL -> ui.scroll("parent", new UiRect(30, 35, 340, 130), 400, this::editor);
            }
            if (paintedInputs.size() != 1 || retained.inputs.size() != 1)
                throw new AssertionError("Expected one visible and one registered editor: " + name);
        }

        void editor(UiContext ui) {
            if (area) ui.textArea("editor", "value", "", 20, 35, 380, 180, "", 65_536, STYLE);
            else ui.textField("editor", "value", "", 20, 35, 380, 40, "", 65_536, STYLE);
        }

        Fingerprint fingerprint() throws Exception {
            var pixels = MessageDigest.getInstance("SHA-256");
            try (var out = digestStream(pixels)) {
                out.writeInt(image.getWidth()); out.writeInt(image.getHeight());
                for (int argb : ((DataBufferInt) image.getRaster().getDataBuffer()).getData()) out.writeInt(argb);
            }
            var geometry = MessageDigest.getInstance("SHA-256");
            try (var out = digestStream(geometry)) {
                out.writeInt(paintedInputs.size());
                for (var input : paintedInputs) writeInput(out, input);
                var keys = new ArrayList<>(retained.inputs.keySet()); Collections.sort(keys);
                out.writeInt(keys.size());
                for (String key : keys) writeInput(out, retained.inputs.get(key));
            }
            return new Fingerprint(HexFormat.of().formatHex(pixels.digest()), HexFormat.of().formatHex(geometry.digest()));
        }
    }

    private static void writeInput(DataOutputStream out, UiInputRegion input) throws IOException {
        out.writeUTF(input.id()); out.writeUTF(input.key());
        out.writeFloat(input.bounds().x()); out.writeFloat(input.bounds().y());
        out.writeFloat(input.bounds().width()); out.writeFloat(input.bounds().height());
        out.writeInt(input.maxLength()); out.writeBoolean(input.multiline()); out.writeBoolean(input.masked());
        writeFloats(out, input.caretXs()); writeFloats(out, input.caretYs());
        out.writeInt(input.lines().size());
        for (var line : input.lines()) {
            out.writeInt(line.start()); out.writeInt(line.end()); out.writeFloat(line.y()); writeFloats(out, line.xs());
        }
        for (int y = 0; y <= HEIGHT; y += 17) for (int x = 0; x <= WIDTH; x += 19) {
            var caret = input.caretPositionAt(x, y); out.writeInt(caret.index()); out.writeBoolean(caret.upstream());
        }
    }

    private static void writeFloats(DataOutputStream out, float[] values) throws IOException {
        out.writeInt(values.length); for (float value : values) out.writeFloat(value);
    }
    private static DataOutputStream digestStream(MessageDigest digest) {
        return new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
    }
    private static long percentile(List<Sample> samples, int metric, double percentile) {
        long[] values = samples.stream().mapToLong(sample -> metric == 0 ? sample.wall : metric == 1 ? sample.cpu : sample.allocated).sorted().toArray();
        return values[Math.max(0, (int) Math.ceil(percentile * values.length) - 1)];
    }
    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
