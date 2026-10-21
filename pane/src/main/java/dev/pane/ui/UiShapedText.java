package dev.pane.ui;

import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.util.Arrays;

/** One shaped run used for both drawing and UTF-16 caret geometry. */
final class UiShapedText {
    final TextLayout layout;
    final java.awt.font.GlyphVector glyphs;
    final float ascent;
    private final float[] carets;
    UiShapedText(TextLayout layout,String source,int start,int end,float ascent) {
        this.layout=layout;this.glyphs=null;this.ascent=ascent;
        carets=new float[end-start+1];Arrays.fill(carets,Float.NaN);
        for(int i=0;i<carets.length;i++) {
            int at=start+i;
            if(at>start&&at<end&&Character.isHighSurrogate(source.charAt(at-1))&&Character.isLowSurrogate(source.charAt(at)))continue;
            carets[i]=layout==null?0:layout.getCaretInfo(TextHitInfo.leading(i))[0];
        }
    }
    private UiShapedText(java.awt.font.GlyphVector glyphs,float ascent,float[] carets) {
        this.layout=null;this.glyphs=glyphs;this.ascent=ascent;this.carets=carets;
    }
    static UiShapedText simpleRun(String source,int start,int end,java.awt.Font font,
                                  java.awt.font.FontRenderContext context,float ascent) {
        int length=end-start;
        if(length==0)return null;
        for(int i=start;i<end;i++)if(source.charAt(i)<32||source.charAt(i)>126)return null;
        char[] chars=new char[length];source.getChars(start,end,chars,0);
        var glyphs=font.layoutGlyphVector(context,chars,0,length,java.awt.Font.LAYOUT_LEFT_TO_RIGHT);
        if(glyphs.getNumGlyphs()!=length)return null;
        int[] indices=glyphs.getGlyphCharIndices(0,length,null);
        for(int i=0;i<length;i++)if(indices[i]!=i)return null;
        float[] positions=glyphs.getGlyphPositions(0,length+1,null),carets=new float[length+1];
        for(int i=0;i<=length;i++)carets[i]=positions[i*2];
        return new UiShapedText(glyphs,ascent,carets);
    }
    float x(int index){return carets[Math.clamp(index,0,carets.length-1)];}
    float width(){return glyphs!=null?carets[carets.length-1]:layout==null?0:layout.getAdvance();}
    record Line(int start,int end,UiShapedText text) { }
}
