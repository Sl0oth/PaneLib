package dev.pane.ui;

import java.lang.management.ManagementFactory;
import java.nio.*;
import java.security.MessageDigest;
import java.util.*;

/** Explicit native-buffer CPU workload. No timing gate and no GPU/FPS claim. */
public final class PixelTransferBenchmark {
    private static final int ROUNDS = 80, BATCH = 8;
    public static void main(String[] args) throws Exception {
        var memory = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        boolean allocations = memory.isThreadAllocatedMemorySupported();
        if (allocations && !memory.isThreadAllocatedMemoryEnabled()) memory.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        System.out.printf("PIXEL_ENV java=%s os=%s arch=%s warmup=%d samples=%d batch=%d%n",
            System.getProperty("java.runtime.version"),System.getProperty("os.name"),System.getProperty("os.arch"),ROUNDS,ROUNDS,BATCH);
        var random = new Random(25);
        for (int[] region : new int[][]{{1708,960,0,0,1708,960}, {1708,960,300,300,800,350},
                {1708,960,700,20,150,30}, {380,220,0,0,380,220}}) {
            int stride=region[0], rows=region[1], x=region[2], y=region[3], width=region[4], height=region[5];
            int[] source = new int[stride*rows];
            for (int i=0;i<source.length;i++) source[i]=random.nextInt();
            int[] original = source.clone();
            IntBuffer target = ByteBuffer.allocateDirect(source.length*4).order(ByteOrder.nativeOrder()).asIntBuffer();
            long[] times = new long[ROUNDS], allocated = new long[ROUNDS];
            for (int sample=-ROUNDS;sample<ROUNDS;sample++) {
                long bytes=allocations?memory.getThreadAllocatedBytes(thread):0, start=System.nanoTime();
                for (int repeat=0;repeat<BATCH;repeat++) UiPixelTransfer.copy(source,target,stride,x,y,width,height);
                long duration=System.nanoTime()-start;
                if (sample>=0) {
                    times[sample]=duration/BATCH;
                    allocated[sample]=allocations?(memory.getThreadAllocatedBytes(thread)-bytes)/BATCH:-1;
                }
            }
            if (!Arrays.equals(original,source)) throw new AssertionError("Modified ARGB backing");
            var digest=MessageDigest.getInstance("SHA-256");
            for (int row=0;row<rows;row++) for(int col=0;col<stride;col++) {
                int index=row*stride+col, pixel=source[index];
                int expected=row>=y&&row<y+height&&col>=x&&col<x+width
                    ? pixel&0xFF00FF00 | pixel>>>16&0xFF | (pixel&0xFF)<<16 : 0;
                int actual=target.get(index);
                if (actual!=expected) throw new AssertionError("Unexpected native pixel at "+index);
                digest.update((byte)(actual>>>24));digest.update((byte)(actual>>>16));
                digest.update((byte)(actual>>>8));digest.update((byte)actual);
            }
            Arrays.sort(times);Arrays.sort(allocated);
            System.out.printf(Locale.ROOT,
                "{\"name\":\"%dx%d_%d_%d_%dx%d\",\"median_us\":%.3f,\"p95_us\":%.3f,\"allocated_bytes\":%d,\"sha256\":\"%s\"}%n",
                stride,rows,x,y,width,height,times[ROUNDS/2]/1e3,times[(int)Math.ceil(ROUNDS*.95)-1]/1e3,
                allocated[ROUNDS/2],HexFormat.of().formatHex(digest.digest()));
        }
    }
}
