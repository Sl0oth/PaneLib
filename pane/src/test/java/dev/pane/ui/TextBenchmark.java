package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.util.*;

/** Explicit CPU workload, invoked only by :pane:textBenchmark; not a client FPS test. */
public final class TextBenchmark {
    public static void main(String[] args) {
        var image=new BufferedImage(420,250,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
        try {
            var canvas=new UiCanvas(graphics,image);
            for(int length:new int[]{256,1024,4096,16384}) {
                var state=new HashMap<String,Object>();state.put("text","a".repeat(length));
                long[] samples=new long[5];
                for(int sample=0;sample<7;sample++) {
                    var ui=new UiContext(canvas,UiTheme.dark(),420,250,0xFF55AAFF,new UiRect(0,0,420,250),null,null,
                        1,1,false,0,state,new ArrayList<>(),new LinkedHashMap<>(),new LinkedHashMap<>(),new HashMap<>(),
                        new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new UiTextInputState("field",length,length),
                        new ArrayList<>(),new ArrayList<>(),null,1,null);
                    long start=System.nanoTime();ui.textField("field","text","",0,0,300,30,"",length,UiInputStyle.defaults());
                    if(sample>=2)samples[sample-2]=System.nanoTime()-start;
                }
                Arrays.sort(samples);
                System.out.printf(Locale.ROOT,"TEXT_BENCH chars=%d median_ms=%.4f min_ms=%.4f max_ms=%.4f%n",length,samples[2]/1e6,samples[0]/1e6,samples[4]/1e6);
            }
            String sampleText="a".repeat(16384);
            for(int i=0;i<5;i++) {
                long started=System.nanoTime();var shaped=canvas.shapeText(sampleText,8,java.awt.Font.PLAIN,UiCanvas.SANS);
                long shapedAt=System.nanoTime();canvas.shapedText(shaped,0,0,0xFFFFFFFF);
                long drawnAt=System.nanoTime();
                System.out.printf(Locale.ROOT,"TEXT_PHASE shape_ms=%.4f draw_ms=%.4f%n",(shapedAt-started)/1e6,(drawnAt-shapedAt)/1e6);
            }
            var benchmarkFont=new java.awt.Font(UiCanvas.SANS,java.awt.Font.PLAIN,9);
            for(int i=0;i<3;i++) {
                long started=System.nanoTime();var layout=new java.awt.font.TextLayout(sampleText,benchmarkFont,graphics.getFontRenderContext());
                long layoutAt=System.nanoTime();new UiShapedText(layout,sampleText,0,sampleText.length(),9);
                System.out.printf(Locale.ROOT,"TEXT_LAYOUT layout_ms=%.4f carets_ms=%.4f%n",(layoutAt-started)/1e6,(System.nanoTime()-layoutAt)/1e6);
            }
            String token="a".repeat(10000);long[] wrapSamples=new long[5];
            for(int sample=0;sample<7;sample++) {
                long start=System.nanoTime();UiTextWrap.lines(token,10001,(from,to)->to-from);
                if(sample>=2)wrapSamples[sample-2]=System.nanoTime()-start;
            }
            Arrays.sort(wrapSamples);
            System.out.printf(Locale.ROOT,"WRAP_BENCH chars=10000 median_ms=%.4f%n",wrapSamples[2]/1e6);
        }finally{graphics.dispose();}
    }
}
