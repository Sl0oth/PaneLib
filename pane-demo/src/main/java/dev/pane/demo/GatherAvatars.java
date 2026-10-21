package dev.pane.demo;

import dev.pane.ui.*;

/** Small personal emblems: a pine, ginger cat, moon, flower, mountains and coffee. */
final class GatherAvatars {
    private static final int[] BACKGROUNDS={0xFF244C3D,0xFFDAA36D,0xFF262C48,0xFFE8D5A2,0xFF9CBCC0,0xFF76604C};
    private static final UiSymbol[] EMBLEMS={
        UiSymbol.vector(24,24,(g,c,s)->{g.fill(0xFFA88356,11,15,2,6);g.polygon(c,12,3,5,14,19,14);g.polygon(c,12,8,3,19,21,19);g.line(0xFF244C3D,1,12,9,12,18);}),
        UiSymbol.vector(24,24,(g,c,s)->{g.polygon(0xFF8E4C2B,5,12,4,3,10,7,15,7,20,3,19,14);g.roundRect(0xFFAE6336,4,7,16,13,6);g.fill(0xFFEED6AF,11,8,2,4);g.circle(0xFF252820,8,13,1);g.circle(0xFF252820,16,13,1);g.polygon(0xFF573B36,10,16,14,16,12,18);}),
        UiSymbol.vector(24,24,(g,c,s)->{g.circle(0xFFEBD9A1,11,12,8);g.circle(0xFF262C48,15,8,7);g.fill(c,18,17,2,2);g.fill(c,5,3,1,1);}),
        UiSymbol.vector(24,24,(g,c,s)->{g.line(0xFF476345,2,12,11,12,22);g.polygon(0xFF476345,12,19,4,15,7,20);g.circle(0xFFB85C44,8,7,4);g.circle(0xFFB85C44,16,7,4);g.circle(0xFFB85C44,12,4,4);g.circle(0xFFB85C44,12,11,4);g.circle(0xFFE9B94F,12,8,3);}),
        UiSymbol.vector(24,24,(g,c,s)->{g.circle(0xFFF1D195,18,6,3);g.polygon(0xFF416D72,1,21,9,5,17,21);g.polygon(0xFFEDE7D4,6,11,9,5,12,11,9,10);g.polygon(0xFF29474B,10,21,17,11,24,21);}),
        UiSymbol.vector(24,24,(g,c,s)->{g.strokeCircle(c,2,18,12,4);g.roundRect(c,4,7,12,13,2);g.fill(0xFF3E3027,5,8,10,3);g.line(0xFFD8C4A8,1,7,5,8,2);g.line(0xFFD8C4A8,1,12,5,13,2);})
    };
    static void draw(UiCanvas g,int person,float x,float y,float size) {
        int i=Math.floorMod(person,EMBLEMS.length);
        g.withCircleClip(x+size/2,y+size/2,size/2,()->{
            g.fill(BACKGROUNDS[i],x,y,size,size);
            EMBLEMS[i].draw(g,0xFFE9E2C9,x+size*.08F,y+size*.08F,size*.84F,1);
        });
    }
}
