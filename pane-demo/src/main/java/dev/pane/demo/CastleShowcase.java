package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/** Briarkeep: an existing-API consumer example. All architectural artwork is procedural. */
public final class CastleShowcase {
    private static final int INK=0xFF111713, GOLD=0xFFDDC58B, PAPER=0xFFF1E6C8, MOSS=0xFF81955A;
    private CastleShowcase() { }
    private record Layout(float scale,float x,float y) {
        static Layout of(UiContext ui) {
            float scale=Math.min(ui.width()/1000F,ui.height()/640F);
            return new Layout(scale,(ui.width()-1000*scale)/2,(ui.height()-640*scale)/2);
        }
        float x(float value){return x+value*scale;}
        float y(float value){return y+value*scale;}
        UiRect rect(float x,float y,float w,float h){return new UiRect(x(x),y(y),w*scale,h*scale);}
    }

    public static UiScreen createScreen(Screen parent) {
        UiLayer scenery=new UiLayer();
        UiScreen[] owner=new UiScreen[1];
        var document=UiDocument.builder("Briarkeep • Castle menu")
            .theme(UiTheme.dark().withAccent(GOLD)).scaling(UiScaling.fixed())
            .page(new UiPage("gate","The gate","",GOLD,ui->paint(ui,scenery,"gate")))
            .page(new UiPage("settings","Chamber settings","",GOLD,ui->paint(ui,scenery,"settings")))
            .page(new UiPage("chronicle","Chronicle","",GOLD,ui->paint(ui,scenery,"chronicle")))
            .build();
        owner[0]=new UiScreen(document, nativeUi->nativeUi.player("adventurer",nativeUi.localPlayer()),
            (UiEventListener)(type,key,value)->{
                if(!type.equals("action"))return;
                UiScreen screen=owner[0];
                switch(key) {
                    case "enter" -> {
                        var client=Minecraft.getInstance();
                        if(client.level!=null)client.gui.setScreen(null);
                        else client.gui.setScreen(new SelectWorldScreen(screen));
                    }
                    case "settings" -> screen.showPage("settings");
                    case "chronicle" -> screen.showPage("chronicle");
                    case "gate" -> screen.showPage("gate");
                    case "ivy" -> screen.setValue("ivy",!screen.booleanValue("ivy",true));
                    case "light" -> screen.setValue("light",!screen.booleanValue("light",true));
                    case "back" -> screen.closeToParent();
                }
            },UiScreenOptions.builder().parent(parent).backdrop(UiBackdrop.NONE)
                .onClose(screen->{
                    if(!screen.pageId().equals("gate")){screen.showPage("gate");return true;}
                    return false;
                }).build()) {
                @Override public void removed(){scenery.clear();super.removed();}
            };
        return owner[0];
    }

    private static void paint(UiContext ui,UiLayer scenery,String page) {
        var g=ui.canvas();var l=Layout.of(ui);
        boolean ivy=ui.booleanValue("ivy",true),light=ui.booleanValue("light",true);
        g.fill(0xFF0D110E,0,0,ui.width(),ui.height());
        g.cachedLayer(scenery,(ivy?1:0)+(light?2:0),new UiRect(0,0,ui.width(),ui.height()),canvas->
            canvas.withTranslation(l.x,l.y,()->canvas.withScale(l.scale,l.scale,0,0,()->scenery(canvas,ivy,light))));
        text(g,l,"THE IVYBOUND REALM",108,117,10,GOLD,Font.BOLD,UiCanvas.SERIF);
        // The bespoke carved title is part of the retained scenery.
        g.line(0x997D7858,l.scale,l.x(108),l.y(207),l.x(438),l.y(207));
        diamond(g,l.x(273),l.y(207),4*l.scale,GOLD);
        if(page.equals("gate")) {
            text(g,l,"Beyond the walls, a world awaits.",109,226,13,0xFFBCBDA3,Font.ITALIC,UiCanvas.SERIF);
            stoneButton(ui,l,"enter",Minecraft.getInstance().level==null?"ENTER THE REALM":"RETURN TO THE REALM",285,true);
            stoneButton(ui,l,"chronicle","THE CHRONICLE",358,false);
            stoneButton(ui,l,"settings","CHAMBER SETTINGS",431,false);
            stoneButton(ui,l,"back","LEAVE THE GATE",504,false);
        } else if(page.equals("settings")) {
            text(g,l,"Make this chamber your own.",109,226,13,0xFFBCBDA3,Font.ITALIC,UiCanvas.SERIF);
            stoneButton(ui,l,"light","TORCHLIGHT  /  "+(light?"WARM":"SUBDUED"),285,true);
            stoneButton(ui,l,"ivy","IVY  /  "+(ivy?"OVERGROWN":"BARE STONE"),358,false);
            text(g,l,"Changes stay with this open menu.",111,445,11,0xFFBCBDA3,Font.ITALIC,UiCanvas.SERIF);
            stoneButton(ui,l,"gate","BACK TO THE GATE",504,false);
        } else {
            text(g,l,"A hall reclaimed by the green.",109,226,13,0xFFBCBDA3,Font.ITALIC,UiCanvas.SERIF);
            UiRect body=l.rect(108,280,340,200);
            g.roundRect(0xDC151D17,body.x(),body.y(),body.width(),body.height(),3*l.scale);
            g.strokeRoundRect(0xFF777250,l.scale,body.x(),body.y(),body.width(),body.height(),3*l.scale);
            g.wrappedText("Once a watchtower at the edge of the old wood, Briarkeep now opens its gates to every wandering soul. Stone remembers. Ivy returns. Your story begins beyond the arch.",
                l.x(128),l.y(303),300*l.scale,14*l.scale,23*l.scale,PAPER,Font.PLAIN,UiCanvas.SERIF);
            text(g,l,"An original castle-menu example.",128,449,10,MOSS,Font.ITALIC,UiCanvas.SERIF);
            stoneButton(ui,l,"gate","BACK TO THE GATE",504,false);
        }
        var client=Minecraft.getInstance();
        if(client.player!=null) {
            UiRect slot=l.rect(655,240,170,270);
            ui.nativeSlot("adventurer",slot.x(),slot.y(),slot.width(),slot.height());
        } else {
            g.withTranslation(l.x,l.y,()->g.withScale(l.scale,l.scale,0,0,()->g.withTranslation(0,20,()->armour(g))));
        }
        String name=client.player==null?"YOUR ADVENTURER":client.player.getName().getString();
        g.centeredText(g.ellipsize(name,210*l.scale,Math.max(11,13*l.scale),Font.BOLD,UiCanvas.SERIF),l.x(740),l.y(538),Math.max(11,13*l.scale),PAPER,Font.BOLD,UiCanvas.SERIF);
        g.centeredText(client.player==null?"Enter a world to see your player":"Ready beyond the gate",l.x(740),l.y(559),Math.max(8,9*l.scale),0xFFA6AD90,Font.ITALIC,UiCanvas.SERIF);
        text(g,l,"PANELIB  /  BRIARKEEP",45,606,8,0xFF9C9F84,Font.BOLD,UiCanvas.SERIF);
        text(g,l,"TAB  Navigate     ENTER  Select     ESC  Back",619,606,8,0xFF9C9F84,Font.PLAIN,UiCanvas.SERIF);
    }

    private static void text(UiCanvas g,Layout l,String text,float x,float y,float size,int color,int style,String font) {
        g.text(text,l.x(x),l.y(y),Math.max(8,size*l.scale),color,style,font);
    }
    private static void stoneButton(UiContext ui,Layout l,String id,String label,float y,boolean primary) {
        var g=ui.canvas();float x=108,w=340,h=58;
        boolean over=ui.hovered(id)||ui.focused(id),down=ui.pressed(id);
        UiRect b=l.rect(x,y,w,h);float scale=l.scale;
        g.withTranslation(b.x(),b.y(),()->g.withScale(scale,scale,0,0,()->{
            int face=primary?0xFF695643:0xFF424A49;
            if(over)face=UiCanvas.lighten(face,.13F);
            if(down)face=UiCanvas.darken(face,.12F);
            final int stone=face;
            // Thick split slate, chipped corners and a visibly recessed inscription bed.
            g.polygon(0xC0040808,-5,5,337,2,348,11,345,61,333,68,1,66,-7,54);
            g.polygon(0xFF131717,-3,3,9,-4,323,-3,342,5,343,51,331,62,7,60,-4,50);
            g.polygon(stone,1,5,13,0,323,1,337,8,336,49,329,56,8,55,1,47);
            g.polygon(UiCanvas.lighten(stone,.30F),1,5,13,0,323,1,337,8,326,10,15,8,7,13);
            g.polygon(UiCanvas.darken(stone,.55F),1,47,8,55,329,56,336,49,327,47,16,48,7,43);
            g.grain(0x709E9C87,.48F,8,10,320,37);
            g.grain(0x880A1111,.37F,8,10,320,37);
            Random r=new Random(992L+id.hashCode());
            for(int i=0;i<80;i++) {
                float px=9+r.nextFloat()*320,py=3+r.nextFloat()*49;
                if(py>14&&py<43&&px>52&&px<292)continue;
                g.line(i%2==0?0xFF252B26:0xFF98917B,.4F+r.nextFloat(),px,py,px+2+r.nextInt(7),py-1);
            }
            // Recessed dark name plate leaves a calm interior inside the rough frame.
            g.polygon(0xFF151D1C,49,11,286,11,299,19,294,45,47,45,40,35,43,19);
            g.polygon(0xFF28302C,50,14,284,14,293,21,289,42,50,42,45,34,47,21);
            g.line(0xFF0A1110,1.4F,52,14,282,14);
            g.line(over?GOLD:0xFF8B8061,.8F,51,44,290,44);
            for(int side:new int[]{0,1}) {
                float bx=side==0?13:308;
                // Hammered wrought-iron strap with pointed ends and two countersunk rivets.
                g.polygon(0xFF0C1212,bx-4,1,bx+20,1,bx+16,12,bx+18,48,bx+12,58,bx-5,54,bx-1,42,bx-2,13);
                g.line(0xFF646C62,1,bx-1,4,bx+15,4);
                g.line(0xFF525C53,1,bx-1,7,bx+1,48);
                g.grain(0xFF333D36,.28F,bx+2,8,10,40);
                rivet(g,bx+7,11,2.7F);rivet(g,bx+7,46,2.7F);
                diamond(g,bx+7,28,6,over?GOLD:0xFF857955);
                diamond(g,bx+7,28,3,0xFF192522);
            }
            g.path().moveTo(77,1).lineTo(72,6).lineTo(81,9).lineTo(78,13).stroke(0xFF171E1C,1.1F);
            g.path().moveTo(245,55).lineTo(251,50).lineTo(247,47).stroke(0xFF171E1C,1.2F);
            // Engraved shadow/light registration, restrained tracking, intentionally not a UI sans.
            float size=Math.max(9/scale,11.5F),tracking=.8F;
            float tw=g.trackedTextWidth(label,size,Font.BOLD,UiCanvas.SERIF,tracking);
            if(tw>244){tracking=.15F;tw=g.trackedTextWidth(label,size,Font.BOLD,UiCanvas.SERIF,tracking);}
            float tx=(340-tw)/2,ty=21+(down?1:0);
            g.trackedText(label,tx,ty+1.2F,size,0xFF000604,Font.BOLD,UiCanvas.SERIF,tracking);
            g.trackedText(label,tx,ty,size,over?0xFFFFE3A3:0xFFD1C59F,Font.BOLD,UiCanvas.SERIF,tracking);
            if(over) {
                g.path().moveTo(50,12).lineTo(285,12).lineTo(297,20).lineTo(292,44).lineTo(48,44).lineTo(43,34).close().stroke(GOLD,1);
                g.line(GOLD,1,57,49,282,49);
                diamond(g,40,29,3,PAPER);diamond(g,300,29,3,PAPER);
            }
        }));
        if(ui.focused(id))g.dashedRoundRect(GOLD,scale,3*scale,b.x()-6*scale,b.y()-6*scale,b.width()+12*scale,b.height()+12*scale,3*scale);
        ui.hit(id,id,b.x(),b.y(),b.width(),b.height());
    }
    private static void diamond(UiCanvas g,float x,float y,float r,int color){g.polygon(color,x,y-r,x+r,y,x,y+r,x-r,y);}

    private static void scenery(UiCanvas g,boolean ivy,boolean light) {
        g.fill(INK,0,0,1000,640);
        Random random=new Random(7184);
        // Large, individually fractured ashlar blocks; never a tiled Minecraft texture.
        float courseY=0;
        for(int row=0;courseY<640;row++) {
            Random course=new Random(7184L+row*997);
            float height=35+course.nextInt(27),x=-course.nextInt(65);
            for(int col=0;x<1000;col++) {
                float width=65+course.nextInt(74);
                rock(g,x+2,courseY+2,width-5,height-5,row*17011+col*919,row%4==0);
                x+=width;
            }
            courseY+=height;
        }
        // Damp mineral streaks collect under joints, not uniformly over every material.
        for(int i=0;i<65;i++) {
            float x=random.nextFloat()*1000,y=random.nextFloat()*560;
            g.line(0x1B071915,2+random.nextFloat()*4,x,y,x+2,y+15+random.nextFloat()*70);
        }
        // A recessed central lancet lets the distant landscape breathe behind the architecture.
        arch(g,475,38,108,420).fill(0xFF101C17).stroke(0xFF74765D,14);
        arch(g,484,49,90,397).fillGradient(0xFF405447,0xFF182920,90);
        g.circle(0xFFBCC2A1,532,126,21);g.circle(0xFF405447,542,118,19);
        // Cut jambs and inset leadwork give the window its own smaller scale of masonry.
        for(int i=0;i<8;i++) {
            rock(g,469,153+i*36,14,35,600+i,true);
            rock(g,575,153+i*36,14,35,700+i,true);
        }
        g.path().moveTo(491,209).lineTo(491,181).curveTo(491,165,509,158,515,151).curveTo(522,158,526,169,526,181).lineTo(526,209).stroke(0xFF6D7D69,2);
        g.path().moveTo(534,209).lineTo(534,181).curveTo(534,165,552,158,558,151).curveTo(565,158,568,169,568,181).lineTo(568,209).stroke(0xFF6D7D69,2);
        for(int i=0;i<8;i++) {
            float x=486+i*12,y=200+(i%3)*19;
            g.polygon(0xFF1F3529,x-20,360,x,y,x+20,360);
            g.line(0xFF182C21,3,x,y+40,x,421);
        }
        g.withClip(488,151,81,285,()->{
            for(int i=-12;i<14;i++) {
                g.line(0x995D6C5F,1,488,151+i*23,569,232+i*23);
                g.line(0x995D6C5F,1,569,151+i*23,488,232+i*23);
            }
        });
        g.line(0xFF797B60,5,529,55,529,438);g.line(0xFF797B60,4,483,224,574,224);
        // Hairline fractures stay attached to the leadwork rather than reading as floating shards.
        g.path().moveTo(531,290).lineTo(541,301).lineTo(538,310).stroke(0x99526050,.6F);
        // Floor slabs with converging joints, a worn moss runner, and a heavy bottom cornice.
        g.gradientRect(0xFF333F30,0xFF18241B,90,0,565,1000,75,0);
        for(int i=-5;i<12;i++)g.line(0xFF0C1710,2,500+i*48,565,500+i*100,640);
        g.line(0xFF78806A,1,0,569,1000,569);g.line(0xFF131D13,2,0,595,1000,595);
        g.fill(0xF019231B,0,591,1000,49);g.line(0xFF535F44,1,0,592,1000,592);
        // Long woven standards frame the two halves of the chamber.
        banner(g,43,59,54,242);banner(g,903,59,54,292);
        column(g,584,49,42,504);column(g,862,49,42,504);
        arch(g,622,87,240,425).fill(0xFF111E17).stroke(0xFF10170F,29);
        arch(g,622,87,240,425).stroke(0xFF79766A,22).stroke(0xFFA29B82,1);
        arch(g,631,99,222,402).fillGradient(0xFF283D2B,0xFF102018,90).stroke(0xFF444E37,2);
        archStones(g);
        g.line(0xFF38482E,1,740,127,740,461);
        g.radialRect(0x345F8650,0x00162B1D,740,330,150,635,180,210,320,0);
        g.polygon(0xFF4D5A3D,642,489,838,489,861,513,619,513);
        g.fill(0xFF283721,619,513,242,15);g.line(0xFFA5A17A,1,620,513,860,513);
        g.withScale(1,.23F,740,489,()->g.circle(0x80081009,740,489,48));
        g.polygon(0xFF0B1310,625,528,855,528,854,573,740,586,626,573);
        g.polygon(0xFF30372A,630,532,850,532,849,569,740,581,631,569);
        g.line(0xFF9B8D65,1,632,533,848,533);
        g.grain(0x444E553A,.35F,635,535,210,31);
        rivet(g,633,538,2);rivet(g,847,538,2);
        g.line(0xFF6F714E,1,633,569,739,580);g.line(0xFF6F714E,1,739,580,847,569);
        // Aged oak backboard separates typography from masonry; forged edging, no glass panel.
        g.shadow(0xCC030807,15,0,5,93,105,371,465,1);
        g.polygon(0xDD0B1110,94,111,110,100,448,102,464,115,460,572,93,572);
        for(int i=0;i<7;i++) {
            float x=99+i*51;
            g.gradientRect(i%2==0?0xFF292820:0xFF22251E,0xFF111B17,90,x,110,49,458,0);
            g.line(0xFF070F0D,2,x+50,113,x+50,568);
            Random wood=new Random(901+i);
            for(int n=0;n<17;n++) {
                float wx=x+wood.nextFloat()*47,wy=111+wood.nextFloat()*400;
                g.path().moveTo(wx,wy).curveTo(wx+3,wy+25,wx-2,wy+54).stroke(0x293F4731,.6F);
            }
        }
        g.grain(0x294F5742,.35F,99,110,355,458);
        g.line(0xFF797254,2,95,112,95,567);g.line(0xFF797254,2,461,115,461,567);
        for(int yy:new int[]{119,258,565}) {rivet(g,97,yy,3);rivet(g,459,yy,3);}
        // Thorned crest and hand-cut title share the same old gold as the metalwork.
        g.polygon(0xFF141B16,243,54,290,54,289,88,267,110,244,88);
        g.polygon(0xFF726746,247,58,286,58,284,87,267,103,249,86);
        g.polygon(0xFF283B2C,251,62,282,62,279,85,267,96,254,84);
        g.path().moveTo(267,68).lineTo(267,89).moveTo(258,77).lineTo(276,77).stroke(GOLD,2);
        for(int i=0;i<5;i++){diamond(g,236+i*15,52-(i%2)*4,2,GOLD);}
        carvedTitle(g);
        torch(g,40,402,light);torch(g,936,443,light);
        if(ivy) {
            vine(g,13,0,333,1,31);vine(g,969,0,454,-1,58);
            vine(g,599,8,235,1,13);vine(g,896,6,168,-1,24);
            vine(g,456,34,105,-1,82);
            for(int i=0;i<18;i++)leaf(g,612+i*3,512+(i%4)*3,8,i%2==0?1:-1,0xFF536A35);
        }
        g.vignette(0x66050D08,0,0,1000,640);
    }
    /** Deterministic multiscale stone detail; baked once into the retained scene. */
    private static void rock(UiCanvas g,float x,float y,float w,float h,int seed,boolean warm) {
        Random r=new Random(seed);float chip=3+r.nextFloat()*5;
        int shade=65+r.nextInt(33);
        int base=0xFF000000|((shade+(warm?12:0))<<16)|((shade+1)<<8)|(shade-5);
        g.polygon(0xFF080D0D,x,y+5,x+chip,y,x+w-7,y+1,x+w,y+7,x+w-2,y+h-3,x+w-10,y+h,x+5,y+h-1,x,y+h-7);
        g.polygon(base,x+2,y+7,x+chip+2,y+3,x+w-9,y+4,x+w-3,y+9,x+w-5,y+h-7,x+w-11,y+h-3,x+8,y+h-5,x+3,y+h-9);
        g.path().moveTo(x+4,y+9).lineTo(x+chip+3,y+4).lineTo(x+w-12,y+5).lineTo(x+w-5,y+10).stroke(UiCanvas.lighten(base,.26F),2);
        g.path().moveTo(x+5,y+h-9).lineTo(x+10,y+h-4).lineTo(x+w-12,y+h-3).lineTo(x+w-5,y+h-8).stroke(0xFF171D1B,3);
        // Low-frequency mineral inclusions, then pores; several scales prevent a smooth vector face.
        for(int i=0;i<20;i++) {
            float px=x+7+r.nextFloat()*(w-20),py=y+9+r.nextFloat()*(h-21),sz=2+r.nextFloat()*7;
            g.polygon(i%2==0?0x187D887C:0x29212B28,px,py,px+sz,py-1,px+sz+2,py+2,px+2,py+4);
        }
        g.grain(0x70303732,.45F,x+6,y+8,w-13,h-17);
        g.grain(0x509EA38F,.34F,x+6,y+8,w-13,h-17);
        for(int i=0;i<12;i++) {
            float px=x+9+r.nextFloat()*(w-22),py=y+10+r.nextFloat()*(h-22);
            g.line(0x7753584E,.6F,px,py+1,px+2,py+1);g.line(0xBB1B2420,.7F,px,py,px+2,py);
        }
        if(r.nextBoolean()) {
            float cx=x+w*(.28F+r.nextFloat()*.4F);
            g.path().moveTo(cx,y+4).lineTo(cx-4,y+12).lineTo(cx+2,y+19).lineTo(cx-1,y+26).stroke(0xCF151E1C,1);
            g.line(0x608D927B,.6F,cx+1,y+6,cx-2,y+12);
            g.line(0x8A18231D,.6F,cx+2,y+19,cx+11,y+22);
        }
        // Tiny moss colonies are clustered along the damp bottom seam.
        if(r.nextInt(3)==0)for(int i=0;i<17;i++) {
            float px=x+8+r.nextFloat()*(w*.55F),py=y+h-6-r.nextFloat()*5;
            g.circle(i%3==0?0xFF727C43:0xFF354731,px,py,.5F+r.nextFloat()*1.6F);
        }
    }
    private static void rivet(UiCanvas g,float x,float y,float r) {
        g.circle(0xFF050B0A,x,y+1,r+1);g.circle(0xFF777560,x,y,r);
        g.circle(0xFF353F36,x+.7F,y+.7F,r*.7F);g.line(0xFFB4A883,.7F,x-r*.4F,y-r*.5F,x+r*.4F,y-r*.5F);
        g.line(0xFF111C17,.6F,x-1,y+1,x+1,y-1);
    }
    // Original angular inscription: purpose-drawn letterforms rather than a perfect system font.
    // Each glyph occupies a 30x48 cell. Broad tapered strokes and cut serifs read as carved uncials.
    private static void carvedTitle(UiCanvas g) {
        String title="BRIARKEEP";
        float[] widths={34,34,18,34,34,34,31,31,33};
        float cursor=112;
        for(int i=0;i<title.length();i++) {
            char c=title.charAt(i);float x=cursor,y=146+(i%3==0?-1:0);
            g.withTranslation(x+1.5F,y+3,()->glyph(g,c,0xFF050B09));
            g.withTranslation(x,y,()->{
                glyph(g,c,0xFFBDAB76);
                // Small stone-cut facets deliberately interrupt uniform lettering.
                Random r=new Random(c*37L);
                for(int n=0;n<9;n++) {
                    float yy=5+r.nextFloat()*37;
                    g.line(0x55726948,.55F,5,yy,8,yy-.6F);
                }
                g.line(0xFFE6D39C,.7F,5,6,5,41);
            });
            cursor+=widths[i]+3;
        }
        // Fine engraved flourish, curled ends and a central thorn under the wordmark.
        g.path().moveTo(118,200).curveTo(145,194,160,201,183,200).lineTo(252,200).stroke(0xFF98865C,.8F);
        g.path().moveTo(437,200).curveTo(410,194,397,201,374,200).lineTo(297,200).stroke(0xFF98865C,.8F);
        diamond(g,274,200,5,0xFFB4A06A);
        for(int d:new int[]{-1,1})g.polygon(0xFF82714D,274+d*10,200,274+d*21,196,274+d*16,200,274+d*23,203);
    }
    private static void glyph(UiCanvas g,char c,int color) {
        // Flat polygon primitives keep this lettering fully within PaneLib's current API.
        g.polygon(color,2,5,9,0,14,4,11,9,11,39,14,43,7,48,1,43,4,38,4,10);
        switch(c) {
            case 'B' -> {g.polygon(color,10,2,23,1,31,8,29,18,21,23,31,29,30,40,22,46,10,44,10,39,23,38,24,31,18,26,10,27,10,20,22,17,23,9,10,8);}
            case 'R','P' -> {g.polygon(color,10,2,24,1,32,8,30,21,22,27,10,26,10,20,23,19,24,9,10,8);if(c=='R')g.polygon(color,18,24,25,25,26,36,33,42,27,48,20,41,19,31,13,26);}
            case 'I' -> {g.polygon(color,0,1,14,1,16,5,12,7,2,7);g.polygon(color,0,42,14,40,16,44,11,48,1,46);}
            case 'A' -> {g.polygon(color,11,3,19,0,27,6,28,37,33,43,26,48,20,43,20,27,10,27,10,21,20,21,20,10,11,8);}
            case 'K' -> {g.polygon(color,26,0,33,5,14,25,27,37,33,41,27,48,21,44,9,28,9,22,23,7,22,5);}
            case 'E' -> {g.polygon(color,10,3,26,1,31,5,27,12,24,8,11,9);g.polygon(color,10,21,24,19,27,23,22,27,10,27);g.polygon(color,10,39,25,38,30,34,29,44,23,47,10,44);}
        }
    }
    private static UiPath arch(UiCanvas g,float x,float y,float w,float h) {
        return g.path().moveTo(x,y+h).lineTo(x,y+w*.55F)
            .curveTo(x,y+w*.2F,x+w*.26F,y+w*.13F,x+w/2,y)
            .curveTo(x+w*.74F,y+w*.13F,x+w,y+w*.2F,x+w,y+w*.55F)
            .lineTo(x+w,y+h).close();
    }
    private static void column(UiCanvas g,float x,float y,float w,float h) {
        g.fill(0xFF080F0D,x-5,y,w+10,h);
        for(int i=0;i<11;i++) {
            rock(g,x+(i%3==0?-2:0),y+19+i*42,w,41,1701+i+(int)x,true);
            g.line(0x618E967D,.8F,x+10,y+28+i*42,x+10,y+53+i*42);
            g.line(0x8030392D,1.5F,x+w-9,y+28+i*42,x+w-9,y+53+i*42);
        }
        rock(g,x-8,y,w+16,20,81,true);rock(g,x-10,y+h-20,w+20,22,93,true);
        for(int i=0;i<4;i++) {
            float cx=x+5+i*10;
            g.path().moveTo(cx,y+19).curveTo(cx-6,y+12,cx+4,y+8).stroke(0xFF242D22,2);
            g.path().moveTo(cx,y+18).curveTo(cx-4,y+12,cx+5,y+9).stroke(0xFFAAA185,.7F);
        }
    }
    private static void archStones(UiCanvas g) {
        for(int side:new int[]{-1,1})for(int i=0;i<10;i++) {
            float t0=i/10F+.008F,t1=(i+1)/10F-.008F;
            float[] a=archPoint(t0),b=archPoint(t1);
            float ax=742+side*a[0],bx=742+side*b[0];
            int base=UiCanvas.mix(0xFF535850,0xFFADA187,((i*7)%11)/13F);
            g.polygon(0xFF101711,ax+side*15,a[1]-7,bx+side*15,b[1]-7,bx-side*10,b[1]+8,ax-side*10,a[1]+8);
            g.polygon(base,ax+side*13,a[1]-5,bx+side*13,b[1]-5,bx-side*8,b[1]+6,ax-side*8,a[1]+6);
            g.line(0xFFB6AA8C,.9F,ax+side*12,a[1]-5,bx+side*12,b[1]-5);
            Random r=new Random(i*81+side);
            for(int n=0;n<35;n++) {
                float t=r.nextFloat(),q=r.nextFloat();
                float xx=ax+(bx-ax)*t+side*(q*19-7),yy=a[1]+(b[1]-a[1])*t+5-q*9;
                g.fill(n%2==0?0x80606B5D:0x857C8270,xx,yy,1,.7F);
            }
            if(i==3||i==7)g.path().moveTo(ax+side*11,a[1]-3).lineTo(ax,a[1]+2).lineTo(ax+side*3,a[1]+6).stroke(0xFF303B30,1);
        }
        // Projecting keystone carries a deeply cut trefoil.
        g.polygon(0xFF1A211A,722,87,740,72,762,87,754,120,732,120);
        g.polygon(0xFF9F9375,725,87,741,77,758,88,751,115,734,115);
        g.line(0xFFD1BE96,1,726,87,741,78);g.line(0xFF4B5342,2,734,114,751,114);
        for(int i=0;i<3;i++)g.circle(0xFF394A35,737+(i==1?8:i==2?4:0),97-(i==2?6:0),4);
        g.polygon(0xFF394A35,738,100,744,100,746,106,736,106);
    }
    private static float[] archPoint(float t) {
        float u=1-t;
        return new float[]{120*u*u*u+120*3*u*u*t+58*3*u*t*t,219*u*u*u+135*3*u*u*t+118*3*u*t*t+87*t*t*t};
    }
    private static void banner(UiCanvas g,float x,float y,float w,float h) {
        boolean right=x>500;Random r=new Random((int)x);
        g.line(0xFF070D0B,7,x-9,y-6,x+w+9,y-6);g.line(0xFF9C8B63,1.5F,x-8,y-8,x+w+8,y-8);
        for(int i=0;i<4;i++){g.strokeCircle(0xFFB1A17C,1,x+6+i*(w-12)/3,y-2,4);}
        var cloth=g.path().moveTo(x,y+3).curveTo(x+w*.45F,y+9,x+w,y+1).lineTo(x+w-1,y+h-21);
        for(int i=10;i>=0;i--)cloth.lineTo(x+i*w/10,y+h-24+(i%3)*7+(i==4?13:0));
        cloth.lineTo(x+1,y+3).close().fillGradient(right?0xFF713E29:0xFF825033,0xFF301B16,0).stroke(0xFF31251A,1);
        for(int i=0;i<8;i++) {
            float xx=x+3+i*6;
            g.path().moveTo(xx,y+6).curveTo(xx+3,y+h*.4F,xx-2,y+h-26).stroke(i%2==0?0x80402219:0x807C5033,3);
        }
        // Uneven warp/weft, stitched hems, patches and loose threads.
        for(int i=0;i<h-28;i+=2)g.line(i%6==0?0x405C3525:0x368C6544,.45F,x+3,y+9+i,x+w-4,y+8+i);
        for(int i=0;i<w-5;i+=2)g.line(0x365B3E2A,.45F,x+3+i,y+8,x+2+i,y+h-25);
        g.grain(0x67502D20,.40F,x+3,y+10,w-6,h-40);
        g.dashedLine(0xFFB39862,.65F,2,x+5,y+11,x+5,y+h-28);
        g.dashedLine(0xFFB39862,.65F,2,x+w-6,y+11,x+w-6,y+h-28);
        for(int i=0;i<17;i++) {
            float xx=x+3+r.nextFloat()*(w-6),yy=y+h-23+r.nextInt(13);
            g.line(0xFF9B7951,.55F,xx,yy-5,xx-2,yy+3+r.nextInt(9));
        }
        float cx=x+w/2,cy=y+63;
        g.path().moveTo(cx,cy-23).lineTo(cx+17,cy).lineTo(cx,cy+27).lineTo(cx-17,cy).close().stroke(0xFFB79A60,1.3F);
        g.path().moveTo(cx-2,cy+17).curveTo(cx+8,cy,cx-7,cy-14).stroke(0xFFCFB780,1.6F);
        for(int i=0;i<3;i++) {
            float ly=cy-10+i*9;
            g.polygon(0xFFBDA46B,cx,ly,cx-10,ly-6,cx-7,ly+2);g.polygon(0xFFBDA46B,cx+1,ly+4,cx+10,ly,cx+7,ly+9);
        }
        g.grain(0x7A533A28,.24F,x+12,y+39,w-24,51);
        float py=y+h*.64F;
        g.polygon(0xFF4D3726,x+12,py,x+32,py+3,x+30,py+31,x+10,py+27);
        g.dashedLine(0xFFA18256,.7F,2,x+13,py+2,x+11,py+25);
        g.dashedLine(0xFFA18256,.7F,2,x+12,py+26,x+28,py+29);
        g.dashedLine(0xFFA18256,.7F,2,x+14,py+2,x+30,py+5);
        g.dashedLine(0xFFA18256,.7F,2,x+30,py+5,x+28,py+29);
        g.path().moveTo(x+34,py-12).lineTo(x+39,py-8).lineTo(x+36,py+3).lineTo(x+30,py-2).close().fill(0xFF151B13).stroke(0xFF9C7146,.7F);
    }
    private static void torch(UiCanvas g,float x,float y,boolean light) {
        if(light)g.radialRect(0x59E4A647,0x00C28438,x,y-28,100,x-100,y-128,200,200,0);
        g.roundRect(0xFF151B13,x-9,y+4,18,35,2);g.line(0xFF7F8060,4,x,y+28,x,y-4);
        g.polygon(0xFFB09B59,x-11,y-5,x+11,y-5,x+7,y+6,x-7,y+6);
        if(light) {
            g.path().moveTo(x,y-52).curveTo(x-3,y-27,x-18,y-28,x-10,y-12).curveTo(x,y,x+19,y-11,x+9,y-30)
                .curveTo(x+6,y-22,x+4,y-39,x,y-52).close().fill(0xFFD98736);
            g.path().moveTo(x+1,y-34).curveTo(x-11,y-9,x+8,y-3,x+7,y-17).close().fill(0xFFF3CC73);
        }
    }
    private static void vine(UiCanvas g,float x,float y,float length,int direction,int seed) {
        Random r=new Random(seed);float previous=x,top=y;
        int i=0;
        while(top<y+length) {
            float step=9+r.nextFloat()*15,next=previous+(r.nextFloat()-.43F)*20*direction;
            next=Math.max(x-22,Math.min(x+27,next));
            float width=3.8F-(top-y)/length*2;
            g.path().moveTo(previous+2,top+2).curveTo(next+9*direction,top+step*.4F,next+2,top+step+2).stroke(0xAA040D09,width+2);
            g.path().moveTo(previous,top).curveTo(next+7*direction,top+step*.4F,next,top+step).stroke(0xFF364B2A,width);
            g.path().moveTo(previous-1,top).curveTo(next+6*direction,top+step*.4F,next-1,top+step).stroke(0xFF89905A,.8F);
            int side=r.nextBoolean()?1:-1;
            float size=9+r.nextFloat()*12;
            leaf(g,next,top+step,size,side,i%7==0?0xFF8C7141:i%3==0?0xFF7A8545:0xFF405E34);
            if(i%3==0) {
                float ex=next-side*(13+r.nextFloat()*14),ey=top+step+8;
                g.path().moveTo(next,top+step).curveTo(ex,top+step-3,ex,ey).stroke(0xFF637342,1);
                leaf(g,ex,ey,size*.65F,-side,0xFF5D713D);
                g.path().moveTo(ex,ey).curveTo(ex+side*7,ey+9,ex-side*5,ey+14,ex-side*4,ey+6).stroke(0xFF82905B,.65F);
            }
            previous=next;top+=step;i++;
        }
    }
    private static void leaf(UiCanvas g,float x,float y,float size,int side,int color) {
        g.withTranslation(x,y,()->g.withRotation((x+y)%39-18,0,0,()->{
            float s=size,d=side;
            // Five pointed lobes, a folded centre, branched veins, a tiny eaten edge.
            float[] points={0,0, .15F*s*d,-.34F*s, -.05F*s*d,-.64F*s,.38F*s*d,-.58F*s,.55F*s*d,-s,.75F*s*d,-.56F*s,1.15F*s*d,-.65F*s,.98F*s*d,-.24F*s,1.20F*s*d,-.05F*s,.66F*s*d,.14F*s,.34F*s*d,.10F*s};
            g.withTranslation(1.5F,2,()->g.polygon(0x95050F09,points));
            g.polygon(color,points);
            g.polygon(UiCanvas.lighten(color,.13F),0,0,.55F*s*d,-s,.56F*s*d,-.40F*s,1.15F*s*d,-.65F*s,.66F*s*d,.14F*s);
            g.path().moveTo(0,0).lineTo(.55F*s*d,-.82F*s).stroke(0xFFADB178,.65F);
            for(int i=1;i<4;i++) {
                float q=i/5F;
                g.line(0x907E965A,.45F,q*s*.55F*d,-q*s*.82F,(q*.55F+.27F)*s*d,-(q*.82F+.04F)*s);
                g.line(0x907E965A,.45F,q*s*.55F*d,-q*s*.82F,(q*.55F-.15F)*s*d,-(q*.82F+.16F)*s);
            }
            g.line(0xAA283C23,.6F,.66F*s*d,.14F*s,1.20F*s*d,-.05F*s);
        }));
    }
    private static void armour(UiCanvas g) {
        // Empty, visibly worn ceremonial armour; never an invented player skin.
        g.fill(0xFF252B21,734,290,12,187);
        // A ragged faded tabard hangs behind the steel plates.
        g.polygon(0xFF623B29,716,320,764,320,770,430,759,427,754,443,746,436,736,448,728,435,714,441,710,425);
        g.grain(0x8A281D16,.35F,719,329,41,94);
        for(int i=0;i<6;i++)g.line(0x88775B3A,.7F,720+i*7,334,716+i*8,431);
        // Helmet: raised ridge, cheek plates, chased brow, narrow cut visor.
        g.path().moveTo(708,272).lineTo(710,259).lineTo(725,249).lineTo(747,247).lineTo(769,260).lineTo(772,280)
            .lineTo(763,307).lineTo(741,320).lineTo(717,307).close().fillGradient(0xFFB7B39B,0xFF3C4943,32).stroke(0xFF14221C,2);
        g.polygon(0xFF69756B,741,251,762,262,765,283,757,305,742,315);
        g.polygon(0xFFDED1A7,738,250,742,250,744,279,740,284,736,278);
        g.polygon(0xFF0C1813,714,275,737,278,741,282,746,277,767,273,764,282,745,288,739,288,716,283);
        g.line(0xFFCEBF98,1,714,274,736,277);g.line(0xFFCEBF98,1,747,276,766,272);
        for(int i=0;i<4;i++) {g.line(0xFF1A2C23,1,720+i*4,292,722+i*4,299);g.line(0xFF1A2C23,1,749+i*4,291,748+i*4,299);}
        g.path().moveTo(718,261).lineTo(724,266).lineTo(723,271).stroke(0xFF4A5547,.8F);
        rivet(g,716,267,1.4F);rivet(g,764,265,1.4F);
        // Separate overlapping pauldrons and articulated breastplate, with a warm brass rim.
        g.path().moveTo(713,324).lineTo(727,317).lineTo(750,317).lineTo(769,326).lineTo(763,373)
            .lineTo(740,393).lineTo(717,376).close().fillGradient(0xFFA9AF9C,0xFF344C3D,25).stroke(0xFF17291D,2);
        g.polygon(0xFF465C49,740,320,763,329,759,371,740,388);
        g.line(0xFFD2C697,1.3F,740,320,740,385);
        g.path().moveTo(720,330).curveTo(726,343,735,342).moveTo(747,342).curveTo(756,343,761,331).stroke(0xFF314937,2);
        for(int side:new int[]{-1,1}) {
            float x=740+side*33;
            g.path().moveTo(x-side*7,322).lineTo(x+side*15,326).lineTo(x+side*20,343).lineTo(x+side*11,361).lineTo(x-side*7,355).close()
                .fillGradient(0xFF8D9C86,0xFF344738,90).stroke(0xFF132619,1.5F);
            for(int i=0;i<3;i++) {
                g.line(0xFFBDB792,.8F,x-side*5,331+i*7,x+side*13,334+i*7);
                g.line(0xFF203A29,1.1F,x-side*5,333+i*7,x+side*13,336+i*7);
            }
            rivet(g,x+side*8,330,1.4F);
        }
        for(int i=0;i<3;i++) {
            float yy=376+i*7;
            g.polygon(i%2==0?0xFF697C63:0xFF475F47,719,yy,740,yy+9,761,yy,760,yy+5,740,yy+14,720,yy+5);
            g.path().moveTo(720,yy).lineTo(740,yy+8).lineTo(760,yy).stroke(0xFFACAA84,.7F);
        }
        for(int side:new int[]{-1,1}) {
            float x=740+side*15;
            g.polygon(0xFF687B60,x-8,403,x+8,403,x+7,456,x-7,456);
            g.polygon(0xFF9CA488,x-6,405,x-1,401,x+2,453,x-5,454);
            g.polygon(0xFF536A50,x-9,416,x,410,x+9,416,x+6,428,x-6,428);
            g.line(0xFFC1BA91,.8F,x-7,416,x,412);
            g.polygon(0xFF374E39,x-8,454,x+7,454,x+13,466,x+11,472,x-10,471);
            for(int i=0;i<3;i++)g.line(0xFF7B8C6A,.8F,x-6,459+i*4,x+8,461+i*4);
        }
        Random r=new Random(501);
        for(int i=0;i<23;i++) {
            float x=720+r.nextFloat()*39,y=330+r.nextFloat()*41;
            g.line(0x607A886A,.6F,x,y,x+2,y+3);
        }
        g.line(0xFF263F2D,1,751,353,747,359);g.line(0xFFCED0AE,.6F,752,353,748,359);
    }
}
