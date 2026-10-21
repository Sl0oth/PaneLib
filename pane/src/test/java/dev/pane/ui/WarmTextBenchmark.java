package dev.pane.ui;

import java.awt.image.BufferedImage;
import java.util.*;

/** Same common API workload can run against the saved pre-optimization development JAR. */
public final class WarmTextBenchmark {
    public static void main(String[] args) {
        var image=new BufferedImage(420,250,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
        try {
            var canvas=new UiCanvas(graphics,image);
            var sizes=new ArrayList<>(List.of(256,1024,4096,16384));var random=new Random(17);
            var values=new HashMap<Integer,Map<String,Object>>();
            for(int size:sizes){var state=new HashMap<String,Object>();state.put("text","a".repeat(size));values.put(size,state);}
            for(int round=0;round<50;round++){Collections.shuffle(sizes,random);for(int size:sizes)paint(canvas,values.get(size),size,new UiInteractionState());}
            var samples=new TreeMap<Integer,List<Long>>();for(int size:sizes)samples.put(size,new ArrayList<>());
            for(int round=0;round<20;round++) {
                Collections.shuffle(sizes,random);
                for(int size:sizes){long start=System.nanoTime();paint(canvas,values.get(size),size,new UiInteractionState());samples.get(size).add(System.nanoTime()-start);}
            }
            for(var entry:samples.entrySet()){var times=entry.getValue();Collections.sort(times);System.out.printf(Locale.ROOT,"WARM_TEXT chars=%d median_ms=%.4f p95_ms=%.4f%n",entry.getKey(),times.get(10)/1e6,times.get(18)/1e6);}
            var retained=new UiInteractionState();var state=values.get(16384);paint(canvas,state,16384,retained);
            var navigation=new ArrayList<Long>();
            for(int sample=0;sample<20;sample++){long start=System.nanoTime();paint(canvas,state,16384-sample,retained);navigation.add(System.nanoTime()-start);}
            Collections.sort(navigation);System.out.printf(Locale.ROOT,"CARET_ONLY chars=16384 median_ms=%.4f p95_ms=%.4f%n",navigation.get(10)/1e6,navigation.get(18)/1e6);
        }finally{graphics.dispose();}
    }
    private static void paint(UiCanvas canvas,Map<String,Object> state,int caret,UiInteractionState shared) {
        var ui=new UiContext(canvas,UiTheme.dark(),420,250,0xFF55AAFF,new UiRect(0,0,420,250),null,null,
            1,1,false,0,state,new ArrayList<>(),new LinkedHashMap<>(),new LinkedHashMap<>(),new HashMap<>(),
            new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new UiTextInputState("field",caret,caret),
            new ArrayList<>(),new ArrayList<>(),null,1,null).withInteractions(shared);
        ui.textField("field","text","",0,0,300,30,"",20000,UiInputStyle.defaults());
    }
}
