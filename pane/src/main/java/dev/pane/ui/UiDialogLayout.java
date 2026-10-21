package dev.pane.ui;

import java.util.ArrayList;
import java.util.List;

/** Measured once per dialog/viewport/style change; scrolling does not rewrap text. */
record UiDialogLayout(UiRect frame, UiRect content, UiRect cancel, UiRect confirm,
                      List<Line> lines, float contentHeight, String cancelLabel, String confirmLabel,
                      Typography titleText, Typography messageText, Typography buttonText) {
    private static final UiDialogStyle DEFAULT_STYLE=UiDialogStyle.builder().build();
    /** The exact resolved typography used for both measurement and rendering. */
    record Typography(String family, float size, int fontStyle, float lineSpacing) {
        float height(UiCanvas canvas){return canvas.textHeight(size,fontStyle,family);}
        float width(UiCanvas canvas,String text){return canvas.textWidth(text,size,fontStyle,family);}
    }
    private static String family(String override,String shared,String theme){
        return override!=null?override:shared!=null?shared:theme;
    }
    record Line(String text, float top, float height, boolean title) { }
    int firstVisible(float offset) {
        int low=0,high=lines.size();
        while(low<high){int mid=(low+high)/2;Line line=lines.get(mid);if(line.top()+line.height()<=offset)low=mid+1;else high=mid;}
        return low;
    }
    float scrollStep(float offset) {
        return lines.isEmpty()?1:Math.max(1,lines.get(Math.min(firstVisible(offset),lines.size()-1)).height());
    }
    float maxOffset() { return Math.max(0, contentHeight-content.height()); }
    float clamp(float offset) { return Math.clamp(offset,0,maxOffset()); }
    static UiDialogLayout measure(UiCanvas canvas, UiDialog dialog, String family, float screenWidth, float screenHeight) {
        UiDialogStyle style=dialog.style()==null?DEFAULT_STYLE:dialog.style();
        var titleText=new Typography(family(style.titleFontFamily(),style.fontFamily(),family),style.titleSize(),style.titleFontStyle(),style.titleLineSpacing());
        var messageText=new Typography(family(style.messageFontFamily(),style.fontFamily(),family),style.messageSize(),style.messageFontStyle(),style.messageLineSpacing());
        var buttonText=new Typography(family(style.buttonFontFamily(),style.fontFamily(),family),style.buttonSize(),style.buttonFontStyle(),0);
        float margin=Math.min(12,Math.min(screenWidth,screenHeight)/8);
        float width=Math.max(.01F,Math.min(style.maxWidth(),screenWidth-2*margin));
        float maxHeight=Math.max(.01F,Math.min(screenHeight-2*margin,screenHeight*(style.maxHeightFraction())));
        float padding=Math.min(style.padding(),Math.min(width/4,maxHeight/8));
        float inner=Math.max(.01F,width-2*padding), gap=Math.min(7,maxHeight/20);
        float cancelWidth=Math.max(70,buttonText.width(canvas,dialog.cancelLabel())+22);
        float confirmWidth=Math.max(76,buttonText.width(canvas,dialog.confirmLabel())+22);
        boolean stacked=cancelWidth+gap+confirmWidth>inner;
        float copyMinimum=Math.min(12,maxHeight/8);
        float actionAvailable=maxHeight-2*padding-gap-copyMinimum;
        float buttonHeight=Math.min(Math.max(28,buttonText.height(canvas)+12),Math.max(.01F,(actionAvailable-(stacked?gap:0))/(stacked?2:1)));
        float actionHeight=stacked?buttonHeight*2+gap:buttonHeight;
        float textWidth=Math.max(.01F,inner-Math.min(6,inner/4));
        var lines=new ArrayList<Line>();
        float contentHeight=append(canvas,lines,dialog.title(),textWidth,titleText,0,true);
        contentHeight+=Math.min(10,maxHeight/20);
        contentHeight=append(canvas,lines,dialog.message(),textWidth,messageText,contentHeight,false);
        float height=Math.min(maxHeight,Math.max(Math.min(150,maxHeight),2*padding+contentHeight+gap+actionHeight));
        float x=(screenWidth-width)/2, y=(screenHeight-height)/2;
        UiRect frame=new UiRect(x,y,width,height);
        UiRect content=new UiRect(x+padding,y+padding,inner,Math.max(.01F,height-2*padding-gap-actionHeight));
        float actionY=y+height-padding-actionHeight;
        UiRect cancel,confirm;
        if(stacked) {
            cancel=new UiRect(x+padding,actionY,inner,buttonHeight);
            confirm=new UiRect(x+padding,actionY+buttonHeight+gap,inner,buttonHeight);
        } else {
            confirm=new UiRect(x+width-padding-confirmWidth,actionY,confirmWidth,buttonHeight);
            cancel=new UiRect(confirm.x()-gap-cancelWidth,actionY,cancelWidth,buttonHeight);
        }
        return new UiDialogLayout(frame,content,cancel,confirm,List.copyOf(lines),contentHeight,
            label(canvas,dialog.cancelLabel(),cancel.width()-12,buttonText),label(canvas,dialog.confirmLabel(),confirm.width()-12,buttonText),
            titleText,messageText,buttonText);
    }
    private static float append(UiCanvas canvas,List<Line> lines,String text,float width,Typography typography,float top,boolean title) {
        text=UiTextBoundary.normalizeLines(text);
        final String value=text;
        float height=typography.height(canvas)+typography.lineSpacing();
        for(var line:UiTextWrap.lines(text,width,(start,end)->typography.width(canvas,value.substring(start,end)))) {
            lines.add(new Line(text.substring(line.start(),line.end()),top,height,title));top+=height;
        }
        return top;
    }
    private static String label(UiCanvas canvas,String text,float width,Typography typography) {
        if(width<=0)return "";
        if(typography.width(canvas,text)<=width)return text;
        if(typography.width(canvas,"…")>width)return "";
        int low=0,high=text.codePointCount(0,text.length());
        while(low<high) {
            int mid=(low+high+1)/2,end=text.offsetByCodePoints(0,mid);
            if(typography.width(canvas,text.substring(0,end)+"…")<=width)low=mid;else high=mid-1;
        }
        return text.substring(0,text.offsetByCodePoints(0,low))+"…";
    }
}
