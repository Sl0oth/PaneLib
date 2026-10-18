package dev.pane.ui;

/**
 * A render-thread performance sample for one {@link UiScreen}. GPU timings are submission costs:
 * Minecraft's renderer performs the actual backend work later in its extraction/drawing pipeline.
 */
public record UiPerformanceSnapshot(
    String screenId,
    String pageId,
    long screenAgeNanos,
    long firstPaintNanos,
    long frameCount,
    int paintCount,
    long idleRepaintCount,
    long dirtyRequestCount,
    long dirtyRectangleCount,
    long dirtyRequestedPixels,
    int lastDirtyPixels,
    int surfacePixels,
    long lastRasterNanos,
    long lastUploadNanos,
    long lastCompositeNanos,
    long lastNativeOverlayNanos,
    int nativeItemCount,
    int nativeEntityCount,
    int activeAnimationCount,
    long retainedTextureAllocations,
    long virtualAtlasAllocations,
    long virtualRowBufferAllocations
) {
    /** Compact, allocation-free-to-format summary for a consumer-owned logger. */
    public String summary() {
        return "PaneLib[" + screenId + '/' + pageId + "] paints=" + paintCount
            + " idle=" + idleRepaintCount + " dirty=" + lastDirtyPixels + '/' + surfacePixels
            + " rasterMs=" + nanosToMillis(lastRasterNanos) + " uploadMs=" + nanosToMillis(lastUploadNanos)
            + " compositeMs=" + nanosToMillis(lastCompositeNanos) + " nativeMs=" + nanosToMillis(lastNativeOverlayNanos)
            + " native=" + nativeItemCount + "i/" + nativeEntityCount + "e"
            + " animations=" + activeAnimationCount;
    }

    private static double nanosToMillis(long nanos) { return nanos / 1_000_000D; }
}
