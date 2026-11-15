package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * Original car-sports main menu: a keyboard-driven side menu, cut-corner panels, a car drawn in
 * code that the garage repaints, playlist cards and a settings form. A local UI flow, not a game.
 */
public final class OverdriveShowcase {
    private static final String FONT=ExampleFonts.FAMILY;
    private static final int WHITE=0xFFFFFFFF, BG=0xFF070A12, PANEL=0xD4101826, LINE=0xFF2A3854, MUTED=0xFF8FA3C4, BLUE=0xFF2F8BFF, ORANGE=0xFFFF8A1F, GREEN=0xFF5BE38A;
    static final String[] MENU={"PLAY","GARAGE","PROFILE","SETTINGS","QUIT"};
    private static final String[] PARTS={"PAINT","WHEELS","DECAL","BOOST"};
    private static final String[] PLAYLIST={"CASUAL","COMPETITIVE","EXTRA MODES"};
    private static final String[] PLAYLIST_NOTE={"Jump in. No rank on the line.","Climb the ladder. Every goal counts.","Hoops, snow day and rumble."};
    private static final String[] DECALS={"NONE","STRIPES","CHEVRONS","SPLIT","DOTS","WAVE","BLOCK","EDGE","TWIN","RALLY","FADE","GRID"};
    private static final int[] BOOSTS={0xFFFFC53D,0xFF5BE3FF,0xFFFF5A8A,0xFF9BFF5A,0xFFFFFFFF,0xFFB58CFF,0xFFFF7A1F,0xFF4DF0C0,0xFFFFE066,0xFF7AA7FF,0xFFFF4B55,0xFFE6F0FF};
    private static final List<UiSelectOption> REGIONS=List.of(new UiSelectOption("EU","EUROPE"),new UiSelectOption("USE","US EAST"),new UiSelectOption("USW","US WEST"),new UiSelectOption("OCE","OCEANIA"));
    private static final UiMenuStyle MENU_STYLE=UiMenuStyle.builder().background(0xF0101826).text(WHITE).border(LINE).highlight(0xFF1E3A66).radius(2).build();
    private OverdriveShowcase(){}

    public static UiScreen createScreen(Screen parent){
        var theme=new UiTheme(BG,BG,0xFF101826,0xFF18243A,WHITE,MUTED,BLUE,BG,2,4,6,1,1,FONT);
        var doc=UiDocument.builder("Overdrive Arena / PaneLib").theme(theme).shell(UiShell.NONE).labelTooltips(false)
            .scaling(UiScaling.fixed().reference(1280,720).adaptivity(1).range(.25F,8)).renderSettings(UiRenderSettings.typography())
            .buttonStyle(UiButtonStyle.builder().background(0xFF18243A).hoverBackground(0xFF24406E).border(LINE).text(WHITE).radius(2).textSize(12).fontFamily(FONT).build())
            .menuStyle(MENU_STYLE)
            .page(new UiPage("menu","Overdrive Arena","",BLUE,OverdriveShowcase::paint)).build();
        return UiScreen.create(doc,NativeOverlayRenderer.NONE,(s,event)->{
            String key=event.key();if(event.type()!=UiEventType.ACTION)return;
            if(key.startsWith("menu:")){String target=key.substring(5);if(target.equals("QUIT")){s.onClose();return;}s.setValue("menu",target);s.setValue("view",target);}
            else if(key.equals("menu-back")){s.setValue("view","MENU");s.setValue("searching",false);}
            else if(key.startsWith("playlist:"))s.setValue("playlist",Integer.parseInt(key.substring(9)));
            else if(key.equals("queue")){s.setValue("searching",!s.booleanValue("searching",false));s.setValue("search-start",(double)System.currentTimeMillis());}
            s.invalidate();
        },UiScreenOptions.builder().parent(parent).backdrop(UiBackdrop.NONE).onKey((s,e)->{
            String view=s.stringValue("view","MENU");
            if(e.key()==GLFW.GLFW_KEY_ESCAPE&&!view.equals("MENU")){s.setValue("view","MENU");s.setValue("searching",false);s.invalidate();return true;}
            if(!view.equals("MENU"))return false;
            int index=java.util.Arrays.asList(MENU).indexOf(s.stringValue("menu","PLAY"));
            if(e.key()==GLFW.GLFW_KEY_DOWN||e.key()==GLFW.GLFW_KEY_UP){s.setValue("menu",MENU[Math.floorMod(index+(e.key()==GLFW.GLFW_KEY_DOWN?1:-1),MENU.length)]);s.invalidate();return true;}
            if(e.key()==GLFW.GLFW_KEY_ENTER||e.key()==GLFW.GLFW_KEY_KP_ENTER){if(MENU[index].equals("QUIT"))s.onClose();else{s.setValue("view",MENU[index]);s.invalidate();}return true;}
            return false;
        }).pauseGame(true).build());
    }

    private static final class D {
        final UiContext ui;final UiCanvas g;final float w,h;final int team,paint;
        D(UiContext ui){this.ui=ui;g=ui.canvas();w=ui.width();h=ui.height();team=ui.stringValue("team","BLUE").equals("BLUE")?BLUE:ORANGE;paint=ui.colorValue("paint",team);}
        void text(String t,float x,float y,float z,int c){g.text(t,x,y,z,c,Font.BOLD,FONT);}
        void plain(String t,float x,float y,float z,int c){g.text(t,x,y,z,c,Font.PLAIN,FONT);}
        void lean(String t,float x,float y,float z,int c){g.text(t,x,y,z,c,Font.BOLD,FONT,UiTextEffect.lean(12));}
        void label(String t,float x,float y,int c){g.trackedText(t,x,y,10,c,Font.BOLD,FONT,1.8F);}
        void panel(float x,float y,float w,float h){g.chamferRect(PANEL,x,y,w,h,12,0,12,0);g.chamferPath(x,y,w,h,12,0,12,0).stroke(LINE,1,false);g.fill(team,x+12,y,46,2);}
        void keycap(String key,float x,float y,float w){g.chamferRect(0xFF18243A,x,y,w,22,4);g.chamferPath(x,y,w,22,4,4,4,4).stroke(LINE,1,false);g.centeredText(key,x+w/2,y+11,10,WHITE,Font.BOLD,FONT);}
        void back(String title){
            ui.shapedButton("menu-back","Back to the main menu","menu-back",g.chamferPath(40,34,44,44,10,0,10,0),true,(c,state)->{c.chamferRect(state.hovered()?team:0xFF18243A,40,34,44,44,10,0,10,0);UiIcon.ARROW_LEFT.draw(c,WHITE,50,44,24,2.4F);});
            g.withSkew(12,100,78,()->g.text(title,100,30,44,WHITE,Font.BOLD,FONT,UiTextEffect.gradient(0xFFB9C9E6).withShadow(UiCanvas.alpha(team,150),3,3)));
        }
    }

    private static void paint(UiContext ui){
        D d=new D(ui);var g=d.g;String view=ui.stringValue("view","MENU");
        g.fill(BG,0,0,d.w,d.h);
        g.radialRect(UiCanvas.alpha(d.team,90),UiCanvas.alpha(d.team,0),820,640,620,0,0,d.w,d.h,0);
        // An arena floor: lines converging on a vanishing point, closer together toward the horizon.
        g.withClip(0,430,d.w,290,()->{for(int i=-14;i<=14;i++)g.line(0x30FFFFFF&UiCanvas.alpha(d.team,60)|0x18000000,1,820+i*26,430,820+i*260,d.h);
            for(int i=0;i<9;i++){float y=430+(float)Math.pow(i/8F,2.2F)*290;g.line(UiCanvas.alpha(d.team,30+i*6),1,0,y,d.w,y);}});
        g.gradientRect(BG,0x00070A12,90,0,300,d.w,180,0);
        for(int beam=0;beam<3;beam++){float x=520+beam*300;g.path().moveTo(x-12,0).lineTo(x+12,0).lineTo(x+190,470).lineTo(x-190,470).close().fillGradient(0x16FFFFFF,0x00FFFFFF,90);}
        g.slantRect(0xE60B101C,-140,0,560,d.h,150);g.slantRect(UiCanvas.alpha(d.team,200),414,0,10,d.h,150*10/560F+2);
        switch(view){case "PLAY"->play(d);case "GARAGE"->garage(d);case "PROFILE"->profile(d);case "SETTINGS"->settings(d);default->menu(d);}
        g.fill(0xE6070A12,0,d.h-40,d.w,40);g.fill(LINE,0,d.h-40,d.w,1);
        float x=40;
        if(view.equals("MENU")){d.keycap(" ",x,d.h-31,24);d.keycap(" ",x+28,d.h-31,24);UiIcon.ARROW_DOWN.draw(g,WHITE,x+33,d.h-27,14,1.8F);float up=x+12;g.withRotation(180,up,d.h-20,()->UiIcon.ARROW_DOWN.draw(g,WHITE,up-7,d.h-27,14,1.8F));d.plain("Navigate",x+60,d.h-27,11,MUTED);d.keycap("ENTER",x+130,d.h-31,52);d.plain("Select",x+190,d.h-27,11,MUTED);x+=240;}
        d.keycap("ESC",x,d.h-31,36);d.plain(view.equals("MENU")?"Close":"Back",x+44,d.h-27,11,MUTED);
        d.plain("PANELIB UI SHOWCASE — NOT A REAL GAME",d.w-290,d.h-27,11,MUTED);
    }

    /** The car, drawn in a 400 by 150 box at {@code scale}, standing on y = 128. */
    private static void car(D d,float x,float y,float scale){
        var g=d.g;var ui=d.ui;int paint=d.paint;int wheels=(int)ui.numberValue("pick:WHEELS",0),decal=(int)ui.numberValue("pick:DECAL",1),boost=BOOSTS[(int)ui.numberValue("pick:BOOST",0)%BOOSTS.length];
        g.withTranslation(x,y,()->g.withScale(scale,scale,0,0,()->{
            g.withScale(1,.16F,200,128,()->{for(int i=6;i>=1;i--)g.circle(0x16000000,200,128,150+i*14);});
            for(int i=0;i<3;i++)g.path().moveTo(8,70+i*9).lineTo(-70-i*26,66+i*11).lineTo(8,76+i*9).close().fillGradient(UiCanvas.alpha(boost,0),UiCanvas.alpha(boost,150-i*30),0);
            UiPath shell=body(g);shell.fillGradient(UiCanvas.lighten(paint,.22F),UiCanvas.darken(paint,.38F),90);
            g.withClip(body(g),()->{
                int ink=UiCanvas.alpha(WHITE,210);
                switch(decal%DECALS.length){case 1->g.stripes(ink,10,14,70,150,40,120,70);case 2->{for(int i=0;i<4;i++)g.polygon(ink,150+i*26,96,166+i*26,72,150+i*26,48,160+i*26,48,176+i*26,72,160+i*26,96);}
                    case 3->g.slantRect(UiCanvas.darken(paint,.55F),190,0,260,150,60);case 4->g.dots(ink,3,12,120,56,200,40);case 5->g.path().moveTo(30,84).curveTo(120,58,200,104,380,74).stroke(ink,7);
                    case 6->g.fill(ink,150,62,90,30);case 7->g.fill(ink,0,88,400,5);case 8->{g.fill(ink,0,70,400,4);g.fill(ink,0,80,400,4);}
                    case 9->{g.circle(ink,196,76,20);g.centeredText("7",196,76,24,BG,Font.BOLD,FONT);}case 10->g.gradientRect(0x00FFFFFF,ink,0,200,0,200,150,0);case 11->g.grid(ink,1.5F,12,130,50,170,50);default->{}}
                g.gradientRect(0x50FFFFFF,0x00FFFFFF,90,0,20,400,46,0);g.gradientRect(0x00000000,0x70000000,90,0,84,400,22,0);
            });
            body(g).stroke(0xFF05070C,2.5F,false);
            g.path().moveTo(120,54).curveTo(142,31,202,30,236,54).close().fillGradient(0xFF9FD8FF,0xFF16263E,100);g.line(0xFF05070C,3,176,33,180,54);
            g.polygon(0xFF05070C,4,54,48,44,50,52,12,62);g.fill(0xFF05070C,24,54,5,10);
            g.polygon(0xFFFFF2B0,372,72,388,80,388,86,366,80);g.fill(0xFFFF3B3B,10,72,5,12);
            for(float cx:new float[]{92,310}){g.circle(0xFF05070C,cx,98,36);g.circle(0xFF11151E,cx,98,30);g.circle(wheels%3==0?0xFFC9D3E3:wheels%3==1?d.team:0xFF2A3344,cx,98,20);
                int spokes=5+wheels%4*2;for(int i=0;i<spokes;i++){double a=Math.PI*2*i/spokes;g.line(0xFF11151E,wheels%2==0?3:5,cx,98,cx+(float)Math.cos(a)*19,98+(float)Math.sin(a)*19);}
                g.circle(0xFF11151E,cx,98,5);g.strokeCircle(0xFF3A465C,1.5F,cx,98,30);}
        }));
    }
    private static UiPath body(UiCanvas g){
        return g.path().moveTo(14,100).lineTo(10,74).curveTo(12,62,42,58).lineTo(98,54).curveTo(132,20,206,20,248,52).lineTo(330,62).curveTo(374,66,388,86).lineTo(390,100).close();
    }

    private static void banner(D d,float x,float y){
        var g=d.g;d.panel(x,y,330,74);g.chamferRect(d.team,x+12,y+13,48,48,10);g.centeredText("R",x+36,y+37,26,BG,Font.BOLD,FONT);
        String name=d.ui.stringValue("driver","Rowan");d.text(name,x+72,y+14,g.fitTextSize(name,150,17,Font.BOLD,FONT),WHITE);d.label("DIAMOND II  ·  DIV 3",x+72,y+38,d.team);
        g.fill(0xFF18243A,x+72,y+56,170,5);g.fill(d.team,x+72,y+56,170*.64F,5);d.text("LVL 87",x+262,y+16,14,WHITE);d.plain("6,400 XP",x+262,y+38,10,MUTED);
    }

    private static void menu(D d){
        var g=d.g;var ui=d.ui;String chosen=ui.stringValue("menu","PLAY");
        g.withSkew(12,48,120,()->g.text("OVERDRIVE",48,52,62,WHITE,Font.BOLD,FONT,UiTextEffect.gradient(0xFFB9C9E6).withOutline(BG,3).withShadow(UiCanvas.alpha(d.team,220),5,5)));
        d.label("A R E N A   ·   S E A S O N   9",54,128,d.team);
        for(int i=0;i<MENU.length;i++){String id=MENU[i];float y=206+i*66;boolean selected=chosen.equals(id);
            UiRect area=new UiRect(36,y-4,360,62);
            float slide=ui.animate("menu-slide:"+id,selected||ui.hovered("menu:"+id)?1:0,150,UiEasing.EASE_OUT,area);
            float x=48+slide*18;
            ui.shapedButton("menu:"+id,id,"menu:"+id,g.chamferPath(48,y,320,54,0,14,0,14),true,(c,state)->{
                if(slide>0){c.chamferPath(x,y,320,54,0,14,0,14).fillGradient(UiCanvas.alpha(d.team,(int)(230*slide)),UiCanvas.alpha(d.team,(int)(40*slide)),0);c.fill(WHITE,x,y,5,54);}
                else{c.chamferRect(0x12FFFFFF,x,y,320,54,0,14,0,14);c.fill(0x40FFFFFF,x,y,2,54);}
                c.withSkew(12,x+26,y+42,()->c.text(id,x+26,y+11,28,id.equals("QUIT")&&slide==0?MUTED:WHITE,Font.BOLD,FONT));
                if(slide>.5F)UiIcon.CHEVRON_RIGHT.draw(c,WHITE,x+280,y+15,24,2.6F);
            });}
        banner(d,d.w-370,34);
        car(d,560,300,1.55F);
        d.panel(d.w-370,124,330,150);d.label("WEEKLY CHALLENGES",d.w-352,140,WHITE);
        String[] challenge={"Score 10 goals","Win 3 matches","Make 15 saves"};float[] done={.7F,.33F,1};
        for(int i=0;i<3;i++){float y=166+i*34;d.plain(challenge[i],d.w-352,y,12,WHITE);g.fill(0xFF18243A,d.w-200,y+5,120,6);g.fill(done[i]>=1?GREEN:d.team,d.w-200,y+5,120*done[i],6);
            if(done[i]>=1)UiIcon.CHECK.draw(g,GREEN,d.w-68,y-2,16,2.4F);else d.plain(Math.round(done[i]*100)+"%",d.w-70,y,11,MUTED);}
    }

    private static void play(D d){
        var g=d.g;var ui=d.ui;d.back("PLAY");int chosen=(int)ui.numberValue("playlist",0);boolean searching=ui.booleanValue("searching",false);
        for(int i=0;i<3;i++){int n=i;float x=40+i*404,y=120;boolean selected=chosen==i;int tint=i==0?BLUE:i==1?ORANGE:0xFFB58CFF;
            float lift=ui.animate("playlist:"+i,selected||ui.hovered("playlist:"+i)?1:0,160,UiEasing.EASE_OUT,new UiRect(x-6,y-14,400,330));
            ui.shapedButton("playlist:"+i,PLAYLIST[i],"playlist:"+i,g.slantPath(x,y,392,300,40),!searching,(c,state)->c.withTranslation(0,-8*lift,()->{
                c.withClip(c.slantPath(x,y,392,300,40),()->{c.gradientRect(UiCanvas.darken(tint,.55F),0xFF0B101C,90,x,y,392,300,0);c.rays(UiCanvas.alpha(tint,40),16,n*9,x+250,y+110,x,y,392,300);
                    c.stripes(0x0CFFFFFF,6,10,135,x,y+200,392,100);c.gradientRect(0x00000000,0xE0070A12,90,x,y+150,392,150,0);});
                c.slantPath(x,y,392,300,40).stroke(selected?WHITE:UiCanvas.alpha(tint,150),selected?3:1.5F,false);
                c.withSkew(12,x+70,y+70,()->c.text(String.valueOf(n+1),x+66,y+16,88,UiCanvas.alpha(WHITE,40),Font.BOLD,FONT));
                c.withSkew(12,x+30,y+250,()->c.text(PLAYLIST[n],x+30,y+204,30,WHITE,Font.BOLD,FONT,UiTextEffect.shadow(UiCanvas.alpha(tint,220),3,3)));
                c.text(PLAYLIST_NOTE[n],x+26,y+250,13,MUTED,Font.PLAIN,FONT);if(selected){c.slantRect(WHITE,x+290,y+16,84,24,8);c.centeredText("SELECTED",x+332,y+28,10,BG,Font.BOLD,FONT);}
            }));}
        d.label("TEAM SIZE",40,452,MUTED);
        String size=ui.stringValue("size","3v3");String[] sizes={"1v1","2v2","3v3","4v4"};
        for(int i=0;i<sizes.length;i++){float x=40+i*86;boolean selected=size.equals(sizes[i]);g.chamferRect(selected?d.team:0xFF18243A,x,472,78,40,8,0,8,0);g.centeredText(sizes[i],x+39,492,15,WHITE,Font.BOLD,FONT);ui.selectable("size:"+sizes[i],"size",sizes[i],x,472,78,40);}
        d.label("REGION",420,452,MUTED);ui.dropdown("region",new UiRect(420,472,220,40),"EU",REGIONS);
        UiRect queue=new UiRect(d.w-420,452,380,150);
        ui.shapedButton("queue",searching?"Cancel search":"Find match","queue",g.chamferPath(d.w-400,462,360,70,18,0,18,0),true,(c,state)->{
            int base=searching?0xFFD9364A:d.team;c.chamferPath(d.w-400,462,360,70,18,0,18,0).fillGradient(UiCanvas.lighten(base,state.hovered()?.35F:.15F),UiCanvas.darken(base,.2F),90);
            c.withSkew(12,d.w-220,497,()->c.centeredText(searching?"CANCEL":"FIND MATCH",d.w-220,497,30,WHITE,Font.BOLD,FONT,UiTextEffect.outline(BG,2)));});
        if(searching){long seconds=(System.currentTimeMillis()-(long)ui.numberValue("search-start",0))/1000;
            d.text(String.format("SEARCHING  %d:%02d",seconds/60,seconds%60),d.w-400,548,16,WHITE);d.plain("Estimated wait 0:45  ·  "+PLAYLIST[chosen]+" "+size,d.w-400,572,12,MUTED);
            float sweep=ui.loop(1400,queue);g.fill(0xFF18243A,d.w-400,594,360,4);g.withClip(d.w-400,594,360,4,()->g.gradientRect(UiCanvas.alpha(d.team,0),d.team,0,d.w-520+sweep*480,594,120,4,0));
        }else d.plain("Party of 1  ·  "+PLAYLIST[chosen]+" "+size,d.w-400,548,12,MUTED);
        banner(d,d.w-370,34);
    }

    private static void garage(D d){
        var g=d.g;var ui=d.ui;d.back("GARAGE");String part=ui.stringValue("part","PAINT");
        car(d,60,250,1.75F);
        float x=d.w-470;d.panel(x,110,430,500);d.label("TEAM",x+20,128,MUTED);
        for(int i=0;i<2;i++){String id=i==0?"BLUE":"ORANGE";int color=i==0?BLUE:ORANGE;float tx=x+20+i*200;boolean selected=ui.stringValue("team","BLUE").equals(id);
            g.chamferRect(selected?color:UiCanvas.darken(color,.6F),tx,148,190,36,8,0,8,0);if(selected)g.chamferPath(tx,148,190,36,8,0,8,0).stroke(WHITE,2,false);g.centeredText(id,tx+95,166,14,WHITE,Font.BOLD,FONT);ui.selectable("team:"+id,"team",id,tx,148,190,36);}
        for(int i=0;i<PARTS.length;i++){String id=PARTS[i];float tx=x+20+i*98;boolean selected=part.equals(id);
            g.withSkew(12,tx,236,()->g.text(id,tx,208,15,selected?WHITE:MUTED,Font.BOLD,FONT));if(selected)g.slantRect(d.team,tx-2,230,g.textWidth(id,15,Font.BOLD,FONT)+8,4,2);ui.selectable("part:"+id,"part",id,tx-6,200,94,36);}
        g.fill(LINE,x+20,244,390,1);
        if(part.equals("PAINT")){d.plain("Body colour",x+20,262,13,WHITE);ui.colorPicker("paint",x+20,288,390,d.team,false);
            d.plain("The car on the left repaints as you drag.",x+20,296+UiContext.colorPickerHeight(false),11,MUTED);}
        else{int chosen=(int)ui.numberValue("pick:"+part,part.equals("DECAL")?1:0);
            d.plain(part.equals("DECAL")?DECALS[chosen%DECALS.length]:part.equals("BOOST")?"Trail colour "+(chosen+1):"Rim "+(chosen+1),x+20,262,13,WHITE);
            ui.scrollHorizontal("parts",new UiRect(x+20,292,390,150),12*112+4,strip->{var c=strip.canvas();
                for(int i=0;i<12;i++){int n=i;float tx=x+22+i*112,ty=296;boolean selected=chosen==i;
                    c.chamferRect(selected?d.team:0xFF18243A,tx,ty,104,128,10,0,10,0);c.chamferRect(0xFF0B101C,tx+3,ty+3,98,122,8,0,8,0);
                    if(part.equals("WHEELS")){c.circle(0xFF11151E,tx+52,ty+54,34);c.circle(n%3==0?0xFFC9D3E3:n%3==1?d.team:0xFF2A3344,tx+52,ty+54,24);int spokes=5+n%4*2;
                        for(int k=0;k<spokes;k++){double a=Math.PI*2*k/spokes;c.line(0xFF11151E,n%2==0?3:5,tx+52,ty+54,tx+52+(float)Math.cos(a)*23,ty+54+(float)Math.sin(a)*23);}}
                    else if(part.equals("BOOST")){for(int k=0;k<3;k++)c.path().moveTo(tx+84,ty+36+k*14).lineTo(tx+16,ty+32+k*16).lineTo(tx+84,ty+44+k*14).close().fillGradient(UiCanvas.alpha(BOOSTS[n],40),BOOSTS[n],0);}
                    else c.centeredText(DECALS[n].substring(0,1),tx+52,ty+54,44,UiCanvas.alpha(WHITE,selected?255:120),Font.BOLD,FONT);
                    c.centeredText(part.equals("DECAL")?DECALS[n]:String.valueOf(n+1),tx+52,ty+110,10,selected?WHITE:MUTED,Font.BOLD,FONT);
                    strip.selectable("pick:"+part+":"+i,"pick:"+part,String.valueOf(i),tx,ty,104,128);}
            });
            d.plain("Scroll sideways for more.",x+20,456,11,MUTED);}
    }

    private static void profile(D d){
        var g=d.g;var ui=d.ui;d.back("PROFILE");banner(d,d.w-370,34);
        float count=ui.animateFrom("profile-count",0,1,900,0,UiEasing.EASE_OUT,new UiRect(40,110,820,120));
        String[] label={"WINS","GOALS","ASSISTS","SAVES","MVPS"};int[] total={1284,4310,2177,3050,402};
        for(int i=0;i<5;i++){float x=40+i*164;d.panel(x,116,152,104);g.text(String.format("%,d",Math.round(total[i]*count)),x+16,134,30,WHITE,Font.BOLD,FONT,UiTextEffect.lean(12));d.label(label[i],x+16,186,i==0?d.team:MUTED);}
        d.panel(40,238,400,372);d.label("COMPETITIVE RANK",58,256,WHITE);float cx=240,cy=420;
        float sweep=ui.animateFrom("rank-ring",0,.72F*300,1100,150,UiEasing.EASE_OUT,new UiRect(cx-130,cy-130,260,260));
        g.path().arc(cx,cy,104,120,300).stroke(0xFF18243A,16,true);g.path().arc(cx,cy,104,120,sweep).stroke(d.team,16,true);
        g.withSkew(12,cx,cy,()->g.centeredText("DIAMOND II",cx,cy-8,26,WHITE,Font.BOLD,FONT));g.centeredText("1,184 MMR  ·  DIV 3",cx,cy+22,12,MUTED,Font.BOLD,FONT);
        d.plain("72% of the way to Diamond III",58,576,12,MUTED);
        d.panel(460,238,d.w-500,372);d.label("LAST TEN MATCHES",478,256,WHITE);
        for(int i=0;i<10;i++){int blue=(i*5+2)%6,orange=(i*3+1)%5;if(blue==orange)blue++;boolean won=blue>orange;float y=284+i*31;
            float grow=ui.animateFrom("match-bar"+i,0,1,500,i*45,UiEasing.EASE_OUT,new UiRect(476,y,d.w-530,26));
            g.slantRect(0xFF18243A,478,y,d.w-536,24,8);g.slantRect(BLUE,560,y+4,blue*44*grow+8,16,6);g.slantRect(ORANGE,560+blue*44*grow+12,y+4,orange*44*grow+8,16,6);
            d.text(won?"WIN":"LOSS",494,y+5,11,won?GREEN:0xFFFF6B77);d.text(blue+" – "+orange,d.w-130,y+5,12,WHITE);}
    }

    private static void settings(D d){
        var ui=d.ui;d.back("SETTINGS");
        d.panel(40,110,560,500);d.panel(620,110,d.w-660,500);
        ui.form("settings-game",new UiRect(56,126,528,468),form->form
            .section("Driver").text("Display name","driver","Your name","Rowan")
            .section("Camera").slider("Field of view","fov",60,110,100).stepper("Camera distance","distance",200,400,10,270).toggle("Camera shake","shake",false).toggle("Ball camera by default","ballcam",true)
            .section("Interface").dropdown("Nameplates","nameplates","ALL",List.of(new UiSelectOption("ALL","Everyone"),new UiSelectOption("TEAM","Teammates"),new UiSelectOption("NONE","Hidden")))
            .toggle("Colour-blind team colours","colorblind",false));
        ui.form("settings-keys",new UiRect(636,126,d.w-692,468),form->form
            .section("Controls").keybind("Boost","key-boost",UiKeyNames.mouse(0),true).keybind("Jump","key-jump",UiKeyNames.mouse(1),true)
            .keybind("Powerslide","key-slide",GLFW.GLFW_KEY_LEFT_SHIFT).keybind("Ball camera","key-ballcam",GLFW.GLFW_KEY_SPACE).keybind("Scoreboard","key-score",GLFW.GLFW_KEY_TAB)
            .section("Audio").slider("Master","master",0,100,80).slider("Crowd","crowd",0,100,55).slider("Music","music",0,100,30)
            .note("Settings here only change this showcase."));
    }
}
