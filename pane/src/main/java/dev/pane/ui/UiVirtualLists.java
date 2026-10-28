package dev.pane.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Composited virtual lists. Each list keeps a bounded window of rows, rasterized once into slots
 * of a GPU atlas and moved by the GPU while scrolling, so the page surface never repaints for a
 * scroll. Interaction geometry for the visible rows is republished by {@link #refresh()}.
 */
final class UiVirtualLists {
    static final int ATLAS_GUTTER = 1;

    /** Live screen state for one row paint, including a fresh animation clock. */
    interface RowState { UiVirtualListRequest.DynamicState at(long now); }

    private final Map<String, Double> offsets;
    private final RowState rowState;
    final Map<String, Layer> layers = new LinkedHashMap<>();
    List<UiHitRegion> hits = List.of();
    List<UiInputRegion> inputs = List.of();
    List<UiTooltip> tooltips = List.of();
    List<UiMenu> menus = List.of();
    List<UiDialogRequest> dialogs = List.of();
    final Map<String, NativeBounds> nativeBounds = new LinkedHashMap<>();
    final Map<String, UiRect> nativeOverlayLayers = new LinkedHashMap<>();
    /** True while the last page paint handed its lists to this compositor. */
    boolean active;
    long rowRasterCount, rowUploadPixels, atlasAllocations, rowBufferAllocations, lastRowPaintNanos;
    int lastRowNonTransparentPixels;

    UiVirtualLists(Map<String, Double> offsets, RowState rowState) {
        this.offsets = offsets;
        this.rowState = rowState;
    }

    /** Adopts this paint's lists, keeping atlases whose geometry and quality are unchanged. */
    void reconcile(List<UiVirtualListRequest> requests, float qualityScale, long now) {
        Set<String> retained = new HashSet<>();
        for (UiVirtualListRequest request : requests) {
            retained.add(request.id);
            Layer layer = layers.get(request.id);
            if (layer == null || !layer.matches(request, qualityScale)) {
                if (layer != null) layer.close();
                layer = new Layer(request, qualityScale);
                layers.put(request.id, layer);
            } else {
                layer.request = request;
                layer.rows.values().forEach(row -> row.dirty = true);
            }
            layer.currentOffset = Math.max(0, Math.min(request.maxOffset(),
                offsets.getOrDefault(request.id, 0D)));
            ensureRows(layer, now);
        }
        var iterator = layers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Layer> entry = iterator.next();
            if (retained.contains(entry.getKey())) continue;
            entry.getValue().close();
            iterator.remove();
        }
    }

    boolean ensureRows(Layer layer, long now) {
        int pixelWidth = Math.max(1, Math.round(layer.request.viewport.width() * layer.qualityScale));
        int pixelHeight = Math.max(1, Math.round(layer.request.rowHeight * layer.qualityScale));
        layer.ensureAtlas(pixelWidth, pixelHeight);
        int firstVisible = layer.request.firstVisible(layer.offset());
        int lastVisible = layer.request.lastVisible(layer.offset());
        int first = Math.max(0, firstVisible - 4);
        int last = Math.min(layer.request.itemCount, lastVisible + 4);
        boolean changed = false;
        // Recycle rows that left overscan before admitting new rows. Their BufferedImage, pixel
        // array, and direct upload buffer are hot-path storage, not per-dataset-item allocations.
        var iterator = layer.rows.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Row> entry = iterator.next();
            if (entry.getKey() >= first && entry.getKey() < last) continue;
            layer.recycle(entry.getValue());
            iterator.remove();
            changed = true;
        }
        for (int index = first; index < last; index++) {
            Row row = layer.rows.get(index);
            if (row == null) {
                row = layer.acquire(index);
                layer.rows.put(index, row);
            }
            if (row.dirty || row.image == null) {
                renderRow(layer, row, now);
                changed = true;
            }
        }
        // At the top/bottom only one side of overscan is populated. Warm enough reusable storage
        // for the largest two-sided window now, before motion begins, so the scroll hot path never
        // allocates images, pixel arrays, or direct upload buffers.
        int maximumBuffers = Math.min(layer.request.itemCount,
            (int) Math.ceil(layer.request.viewport.height() / layer.request.stride()) + 10);
        int uploadHeight = pixelHeight + ATLAS_GUTTER * 2;
        int uploadBytes = Math.multiplyExact(Math.multiplyExact(pixelWidth, uploadHeight), Integer.BYTES);
        while (layer.rows.size() + layer.recycledRows.size() < maximumBuffers) {
            Row spare = new Row(-1);
            spare.ensure(pixelWidth, pixelHeight);
            spare.uploadBuffer(uploadBytes);
            rowBufferAllocations++;
            layer.recycledRows.addLast(spare);
        }
        return changed;
    }

    private void renderRow(Layer layer, Row row, long now) {
        long started = System.nanoTime();
        int pixelWidth = Math.max(1, Math.round(layer.request.viewport.width() * layer.qualityScale));
        int pixelHeight = Math.max(1, Math.round(layer.request.rowHeight * layer.qualityScale));
        boolean allocateRowBuffer = row.image == null || row.width != pixelWidth || row.height != pixelHeight;
        row.ensure(pixelWidth, pixelHeight);
        if (allocateRowBuffer) rowBufferAllocations++;
        Arrays.fill(row.pixels, 0);
        Graphics2D graphics = row.image.createGraphics();
        List<UiHitRegion> hits = new ArrayList<>();
        Map<String, NativeBounds> nativeRows = new LinkedHashMap<>();
        Map<String, UiRect> nativeOverlayRows = new LinkedHashMap<>();
        List<UiScrollRegion> scrolls = new ArrayList<>();
        List<UiInputRegion> inputs = new ArrayList<>();
        List<UiTooltip> tips = new ArrayList<>();
        List<UiMenu> rowMenus = new ArrayList<>();
        List<UiDialogRequest> rowDialogs = new ArrayList<>();
        Map<String, UiButtonState> rowButtons = new HashMap<>();
        Map<String, UiNarration> rowNarrations = new HashMap<>();
        try {
            graphics.scale(layer.qualityScale, layer.qualityScale);
            float rowY = layer.request.viewport.y() + row.index * layer.request.stride();
            graphics.translate(-layer.request.viewport.x(), -rowY);
            UiCanvas canvas = new UiCanvas(graphics, row.image);
            UiVirtualListRequest.DynamicState dynamic = rowState.at(now);
            UiAnimationClock rowClock = dynamic.animationClock();
            layer.request.paintRow(canvas, row.index, dynamic,
                new UiVirtualListRequest.RowOutput(hits, nativeRows, nativeOverlayRows, scrolls, inputs, tips, rowMenus, rowDialogs, rowButtons, rowNarrations));
            row.customAnimating = rowClock.active();
            row.animationIds=rowClock.usedIds();
        } finally {
            graphics.dispose();
        }
        // Duplicate the first and last source scanline into a one-pixel atlas gutter. Linear
        // filtering can then move the quad by fractions of a GUI pixel without sampling the
        // neighboring row slot and producing a crawling separator or text ghost.
        int uploadHeight = pixelHeight + ATLAS_GUTTER * 2;
        int bytes = Math.multiplyExact(Math.multiplyExact(pixelWidth, uploadHeight), Integer.BYTES);
        ByteBuffer upload = row.uploadBuffer(bytes);
        int nonTransparent = 0;
        for (int x = 0; x < pixelWidth; x++) upload.putInt(UiPixelTransfer.swapRedBlue(row.pixels[x]));
        for (int argb : row.pixels) {
            if ((argb >>> 24) != 0) nonTransparent++;
            upload.putInt(UiPixelTransfer.swapRedBlue(argb));
        }
        int lastScanline = (pixelHeight - 1) * pixelWidth;
        for (int x = 0; x < pixelWidth; x++) upload.putInt(UiPixelTransfer.swapRedBlue(row.pixels[lastScanline + x]));
        upload.flip();
        row.atlasSlot = Math.floorMod(row.index, layer.atlasRows);
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(
            layer.atlas.getTexture(), upload, 0, 0, 0, row.atlasSlot * layer.atlasSlotHeight,
            pixelWidth, uploadHeight
        );
        row.buttons = Map.copyOf(rowButtons);
        row.narrations = Map.copyOf(rowNarrations);
        row.hits = List.copyOf(hits);
        row.nativeBounds = Map.copyOf(nativeRows);
        row.nativeOverlayLayers = Map.copyOf(nativeOverlayRows);
        row.inputs = List.copyOf(inputs);
        row.tooltips = List.copyOf(tips);
        row.menus = List.copyOf(rowMenus);
        row.dialogs = List.copyOf(rowDialogs);
        row.dirty = false;
        rowRasterCount++;
        rowUploadPixels += (long) pixelWidth * uploadHeight;
        lastRowNonTransparentPixels = nonTransparent;
        lastRowPaintNanos = System.nanoTime() - started;
    }

    /**
     * Blits the visible rows of every list, optionally limited to a native cover layer.
     * Returns true when the cached window changed and interaction geometry must be refreshed.
     */
    boolean render(GuiGraphicsExtractor graphics, long now, UiRect overlayClip, float layoutScale) {
        boolean interactionsChanged = false;
        for (Layer layer : layers.values()) {
            if (ensureRows(layer, now)) interactionsChanged = true;
            UiRect viewport = layer.request.viewport;
            UiRect visible = overlayClip == null ? viewport : viewport.intersection(overlayClip);
            if (visible.width() <= 0 || visible.height() <= 0) continue;
            int sx = (int) Math.floor(visible.x() / layoutScale);
            int sy = (int) Math.floor(visible.y() / layoutScale);
            int sr = (int) Math.ceil(visible.right() / layoutScale);
            int sb = (int) Math.ceil(visible.bottom() / layoutScale);
            graphics.enableScissor(sx, sy, sr, sb);
            int first = Math.max(0, layer.request.firstVisible(layer.offset()) - 1);
            int last = Math.min(layer.request.itemCount, layer.request.lastVisible(layer.offset()) + 1);
            float guiWidth = viewport.width() / layoutScale;
            float guiHeight = layer.request.rowHeight / layoutScale;
            int drawWidth = Math.max(1, Math.round(guiWidth));
            int drawHeight = Math.max(1, Math.round(guiHeight));
            // Scroll once at the parent transform. Previously each row independently rounded
            // (baseY - offset), so adjacent rows changed pixel spacing on different frames and
            // appeared to move in a wave. Float transforms preserve one coherent list plane.
            graphics.pose().pushMatrix();
            graphics.pose().translate(0, -(float) (layer.offset() / layoutScale));
            try {
                for (int index = first; index < last; index++) {
                    Row row = layer.rows.get(index);
                    if (row == null || layer.atlas == null) continue;
                    UiRect destination = rowGuiRect(layer.request, index, layoutScale);
                    float atlasHeight = layer.atlasSlotHeight * (float) layer.atlasRows;
                    float sourceTop = row.atlasSlot * layer.atlasSlotHeight + ATLAS_GUTTER;
                    float v0 = sourceTop / atlasHeight;
                    float v1 = (sourceTop + layer.atlasRowHeight) / atlasHeight;
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(destination.x(), destination.y());
                    graphics.pose().scale(destination.width() / drawWidth, destination.height() / drawHeight);
                    try {
                        graphics.blit(layer.atlas.getTextureView(), layer.sampler,
                            0, 0, drawWidth, drawHeight, 0, 1, v0, v1);
                    } finally {
                        graphics.pose().popMatrix();
                    }
                }
            } finally {
                graphics.pose().popMatrix();
            }
            if (overlayClip == null) drawScrollbar(graphics, layer, layoutScale);
            graphics.disableScissor();
        }
        return interactionsChanged;
    }

    static UiRect rowGuiRect(UiVirtualListRequest request, int index, float layoutScale) {
        return new UiRect(
            request.viewport.x() / layoutScale,
            (request.viewport.y() + index * request.stride()) / layoutScale,
            request.viewport.width() / layoutScale,
            request.rowHeight / layoutScale
        );
    }

    private static void drawScrollbar(GuiGraphicsExtractor graphics, Layer layer, float layoutScale) {
        UiRect viewport = layer.request.viewport;
        if (layer.request.maxOffset() <= 0) return;
        float trackHeight = Math.max(0, viewport.height() - 8);
        float thumbHeight = Math.max(18, trackHeight * viewport.height()
            / Math.max(viewport.height(), layer.request.contentHeight));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        float travel = Math.max(0, trackHeight - thumbHeight);
        float thumbY = viewport.y() + 4 + travel * (float) layer.offset()
            / Math.max(1, layer.request.maxOffset());
        int x = Math.round((viewport.right() - 4) / layoutScale);
        int top = Math.round((viewport.y() + 4) / layoutScale);
        int bottom = Math.round((viewport.y() + 4 + trackHeight) / layoutScale);
        int thumbTop = Math.round(thumbY / layoutScale);
        int thumbBottom = Math.round((thumbY + thumbHeight) / layoutScale);
        graphics.fill(x, top, x + Math.max(1, Math.round(2 / layoutScale)), bottom,
            UiCanvas.fade(layer.request.theme.border(), .45F));
        graphics.fill(x, thumbTop, x + Math.max(1, Math.round(2 / layoutScale)), thumbBottom,
            UiCanvas.fade(layer.request.theme.mutedText(), .65F));
    }

    /** The cached row painting this control or tooltip target, while lists are composited. */
    RowKey rowOf(String controlId) {
        if (controlId == null || !active) return null;
        for (Map.Entry<String, Layer> layerEntry : layers.entrySet()) {
            for (Map.Entry<Integer, Row> rowEntry : layerEntry.getValue().rows.entrySet()) {
                Row row = rowEntry.getValue();
                for (UiHitRegion hit : row.hits) if (controlId.equals(hit.id())) {
                    return new RowKey(layerEntry.getKey(), rowEntry.getKey());
                }
                for (UiTooltip tooltip : row.tooltips) if (controlId.equals(tooltip.id())) {
                    return new RowKey(layerEntry.getKey(), rowEntry.getKey());
                }
            }
        }
        return null;
    }

    /**
     * What the narrator says about a control inside a cached row: its own description or tooltip as
     * the label, placed at its row's position in the whole list.
     */
    UiNarration rowNarration(String controlId) {
        RowKey key = rowOf(controlId);
        if (key == null) return null;
        Layer layer = layers.get(key.listId());
        Row row = layer == null ? null : layer.rows.get(key.index());
        if (row == null) return null;
        UiNarration own = row.narrations.get(controlId);
        String label = own == null ? "" : own.label();
        if (label.isEmpty()) for (UiTooltip tooltip : row.tooltips) if (controlId.equals(tooltip.id())) { label = tooltip.text(); break; }
        return new UiNarration(UiNarration.Role.LIST_ROW, label, own == null ? null : own.state(), key.index() + 1, layer.request.itemCount);
    }

    /** Marks the rows of every id dirty; false when any id is not in a cached row. */
    boolean markRows(String... controlIds) {
        if (controlIds == null || controlIds.length == 0 || !active) return false;
        boolean found = false;
        for (String id : controlIds) {
            if (id == null) continue;
            RowKey key = rowOf(id);
            if (key == null) return false;
            Layer layer = layers.get(key.listId());
            Row row = layer == null ? null : layer.rows.get(key.index());
            if (row != null) {
                row.dirty = true;
                found = true;
            }
        }
        return found;
    }

    void markDirty(java.util.Collection<RowKey> keys) {
        for (RowKey key : keys) {
            Layer layer = layers.get(key.listId());
            Row row = layer == null ? null : layer.rows.get(key.index());
            if (row != null) row.dirty = true;
        }
    }

    record RowKey(String listId, int index) { }

    final class Layer implements AutoCloseable {
        UiVirtualListRequest request;
        final float qualityScale;
        final Map<Integer, Row> rows = new LinkedHashMap<>();
        final ArrayDeque<Row> recycledRows = new ArrayDeque<>();
        DynamicTexture atlas;
        GpuSampler sampler;
        int atlasWidth;
        int atlasRowHeight;
        int atlasSlotHeight;
        int atlasRows;
        double currentOffset;

        Layer(UiVirtualListRequest request, float qualityScale) {
            this.request = request;
            this.qualityScale = qualityScale;
        }

        double offset() {
            currentOffset = Math.max(0, Math.min(request.maxOffset(),
                offsets.getOrDefault(request.id, 0D)));
            return currentOffset;
        }

        boolean matches(UiVirtualListRequest next, float nextQualityScale) {
            return request.itemCount == next.itemCount
                && Float.compare(request.rowHeight, next.rowHeight) == 0
                && Float.compare(request.gap, next.gap) == 0
                && request.viewport.equals(next.viewport)
                && Float.compare(qualityScale, nextQualityScale) == 0;
        }

        void ensureAtlas(int pixelWidth, int pixelRowHeight) {
            int capacity = Math.max(1, Math.min(request.itemCount,
                (int) Math.ceil(request.viewport.height() / request.stride()) + 18));
            if (atlas != null && atlasWidth == pixelWidth && atlasRowHeight == pixelRowHeight
                && atlasRows == capacity) return;
            if (atlas != null) atlas.close();
            atlasSlotHeight = pixelRowHeight + ATLAS_GUTTER * 2;
            atlas = new DynamicTexture(() -> "pane-virtual-list-" + request.id,
                pixelWidth, Math.multiplyExact(atlasSlotHeight, capacity), true);
            atlasAllocations++;
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            atlasWidth = pixelWidth;
            atlasRowHeight = pixelRowHeight;
            atlasRows = capacity;
            rows.values().forEach(row -> row.dirty = true);
        }

        Row acquire(int index) {
            Row row = recycledRows.pollFirst();
            if (row == null) return new Row(index);
            row.rebind(index);
            return row;
        }

        void recycle(Row row) {
            row.clearInteractions();
            recycledRows.addLast(row);
        }

        @Override public void close() {
            rows.values().forEach(Row::close);
            rows.clear();
            recycledRows.forEach(Row::close);
            recycledRows.clear();
            if (atlas != null) atlas.close();
            atlas = null;
            sampler = null;
        }
    }

    static final class Row implements AutoCloseable {
        int index;
        BufferedImage image;
        int[] pixels;
        ByteBuffer uploadBuffer;
        int width;
        int height;
        int atlasSlot;
        boolean dirty = true;
        boolean customAnimating;
        Set<String> animationIds=Set.of();
        Map<String, UiButtonState> buttons = Map.of();
        Map<String, UiNarration> narrations = Map.of();
        List<UiHitRegion> hits = List.of();
        Map<String, NativeBounds> nativeBounds = Map.of();
        Map<String, UiRect> nativeOverlayLayers = Map.of();
        List<UiInputRegion> inputs = List.of();
        List<UiTooltip> tooltips = List.of();
        List<UiMenu> menus = List.of();
        List<UiDialogRequest> dialogs = List.of();

        Row(int index) { this.index = index; }

        void rebind(int index) {
            this.index = index;
            dirty = true;
            customAnimating = false;
            animationIds=Set.of();
            clearInteractions();
        }

        void clearInteractions() {
            buttons = Map.of();
            hits = List.of();
            nativeBounds = Map.of();
            nativeOverlayLayers = Map.of();
            inputs = List.of();
            tooltips = List.of();
            menus = List.of();
            dialogs = List.of();
        }

        void ensure(int width, int height) {
            if (image != null && this.width == width && this.height == height) return;
            close();
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
            this.width = width;
            this.height = height;
        }

        ByteBuffer uploadBuffer(int bytes) {
            if (uploadBuffer == null || uploadBuffer.capacity() < bytes) {
                uploadBuffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
            }
            uploadBuffer.clear();
            uploadBuffer.limit(bytes);
            return uploadBuffer;
        }

        @Override public void close() {
            image = null;
            pixels = null;
            uploadBuffer = null;
            width = 0;
            height = 0;
        }
    }

    /** Republishes visible-row interaction geometry. Returns true when the tooltip set changed. */
    boolean refresh() {
        List<UiHitRegion> nextHits = new ArrayList<>();
        List<UiInputRegion> nextInputs = new ArrayList<>();
        List<UiTooltip> tips = new ArrayList<>();
        List<UiMenu> rowMenus = new ArrayList<>();
        List<UiDialogRequest> rowDialogs = new ArrayList<>();
        nativeBounds.clear();
        nativeOverlayLayers.clear();
        for (Layer layer : layers.values()) {
            float shiftY = -(float) layer.offset();
            UiRect clip = layer.request.viewport;
            int first = Math.max(0, layer.request.firstVisible(layer.offset()) - 1);
            int last = Math.min(layer.request.itemCount, layer.request.lastVisible(layer.offset()) + 1);
            for (int index = first; index < last; index++) {
                Row row = layer.rows.get(index);
                if (row == null) continue;
                for (UiHitRegion hit : row.hits) {
                    UiRect moved = new UiRect(hit.x(), hit.y() + shiftY, hit.width(), hit.height()).intersection(clip);
                    if (moved.width() <= 0 || moved.height() <= 0) continue;
                    nextHits.add(new UiHitRegion(hit.id(), hit.action(), moved.x(), moved.y(), moved.width(), moved.height(),
                        hit.trackX(), hit.trackWidth(), hit.hitShape() == null ? null
                            : java.awt.geom.AffineTransform.getTranslateInstance(0, shiftY).createTransformedShape(hit.hitShape())));
                }
                for (UiInputRegion input : row.inputs) {
                    UiRect moved = input.bounds().translate(0, shiftY).intersection(clip);
                    if (moved.width() <= 0 || moved.height() <= 0) continue;
                    float[] ys = input.caretYs();
                    for (int i = 0; i < ys.length; i++) ys[i] += shiftY;
                    nextInputs.add(new UiInputRegion(input.id(), input.key(), moved, input.maxLength(),
                        input.multiline(), input.caretXs(), ys,
                        input.lines().stream().map(line -> line.translate(0, shiftY)).toList(), input.masked(), input.readOnly()));
                }
                for (UiTooltip tooltip : row.tooltips) {
                    UiRect moved = tooltip.anchor().translate(0, shiftY).intersection(clip);
                    if (moved.width() > 0 && moved.height() > 0) tips.add(new UiTooltip(tooltip.id(), tooltip.text(), moved));
                }
                for (UiMenu menu : row.menus) {
                    UiRect moved = menu.anchor().translate(0, shiftY).intersection(clip);
                    if (moved.width() > 0 && moved.height() > 0) rowMenus.add(new UiMenu(menu.id(), moved, menu.items(), menu.selectedItemId(), menu.style()));
                }
                rowDialogs.addAll(row.dialogs);
                double nativeShift = shiftY * layer.request.nativeScale;
                row.nativeBounds.forEach((id, bounds) -> nativeBounds.put(id,
                    new NativeBounds(bounds.x(), bounds.y() + nativeShift, bounds.width(), bounds.height())));
                row.nativeOverlayLayers.forEach((id, bounds) -> nativeOverlayLayers.put(id, bounds.translate(0, shiftY)));
            }
        }
        hits = List.copyOf(nextHits);
        inputs = List.copyOf(nextInputs);
        List<UiTooltip> nextTooltips = List.copyOf(tips);
        boolean tooltipsChanged = !tooltips.equals(nextTooltips);
        tooltips = nextTooltips;
        menus = List.copyOf(rowMenus);
        dialogs = List.copyOf(rowDialogs);
        return tooltipsChanged;
    }

    void clearInteractions() {
        hits = List.of();
        inputs = List.of();
        tooltips = List.of();
        menus = List.of();
        dialogs = List.of();
        nativeBounds.clear();
        nativeOverlayLayers.clear();
    }

    /** Releases every atlas and row buffer; interaction geometry is cleared by the caller. */
    void close() {
        layers.values().forEach(Layer::close);
        layers.clear();
        active = false;
    }
}
