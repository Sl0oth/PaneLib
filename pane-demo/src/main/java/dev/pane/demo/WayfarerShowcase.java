package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/** Original game-interface showcase. The expedition is a local UI flow, not a game mode. */
public final class WayfarerShowcase {
    private static final String FONT=ExampleFonts.FAMILY;
    private static final int WHITE=0xFFF5F6F0, MUTED=0xFFACBDCA, GOLD=0xFFFFD357, INK=0xFF081725, BLUE=0xFF75DBFF;
    private static final Identifier ART=Identifier.fromNamespaceAndPath("pane_demo","textures/wayfarer/mountain-lookout.png");
    private static final String[] MISSIONS={"THE CLOUDLINE","HOLLOW REACH","AFTER THE STORM"};
    private static final String[] TYPES={"EXPLORATION / 01","SALVAGE / 02","SURVEY / 03"};
    private static final String[] DESC={"Chart the islands beyond the old beacons.\nFind your own way through the open sky.","Recover a lost survey team's field records.\nSome stories are worth bringing home.","Follow the quiet edge of a passing storm.\nEvery new horizon starts with a first step."};
    private static final String[] GEAR={"PATHFINDER","SIGNAL KIT","GLIDER RIG"};
    private static final UiSymbol COMPASS=UiSymbol.vector(24,24,(g,c,s)->{g.strokeCircle(c,s,12,12,10);g.polygon(c,15,5,14,14,5,19,10,10);});
    private static final UiSymbol GLIDER=UiSymbol.vector(24,24,(g,c,s)->{g.path().moveTo(1,9).lineTo(12,2).lineTo(23,9).lineTo(12,7).close().stroke(c,s);g.line(c,s,2,9,12,19);g.line(c,s,22,9,12,19);g.line(c,s,12,7,12,22);});
    private static final UiSymbol SIGNAL=UiSymbol.vector(24,24,(g,c,s)->{g.circle(c,12,12,2);g.path().moveTo(7,6).curveTo(2,9,2,15,7,18).stroke(c,s);g.path().moveTo(17,6).curveTo(22,9,22,15,17,18).stroke(c,s);g.line(c,s,12,14,12,23);});
    private static final UiSymbol[] SYMBOLS={COMPASS,SIGNAL,GLIDER};
    private WayfarerShowcase(){}
    public static UiScreen createScreen(Screen parent){
        var theme=new UiTheme(INK,INK,0xFF152D40,0xFF152D40,WHITE,MUTED,GOLD,INK,3,8,13,1,0,FONT);
        var doc=UiDocument.builder("WAYFARER / PaneLib").theme(theme).shell(UiShell.NONE).scaling(UiScaling.fixed().reference(1280,720).adaptivity(1).range(.25F,8)).renderSettings(UiRenderSettings.typography())
            .page(new UiPage("lobby","Wayfarer","",GOLD,WayfarerShowcase::paint)).build();
        return UiScreen.create(doc,WayfarerPlayerPreview::render,(s,event)->{
            var type=event.type(); var key=event.key(); var value=event.value();
                if(type==UiEventType.VALUE&&key.equals("reduce-motion")){s.setMotionScale(Boolean.parseBoolean(value)?0:1);return;}
            if(type!=UiEventType.ACTION)return;
            if(key.startsWith("tab:"))s.setValue("tab",key.substring(4));
            else if(key.startsWith("mission:"))s.setValue("mission",Integer.parseInt(key.substring(8)));
            else if(key.startsWith("gear:"))s.setValue("gear",Integer.parseInt(key.substring(5)));
            else if(key.equals("claim")&&!s.booleanValue("claimed",false)){s.setValue("claimed",true);s.setValue("xp",1670);}
            else if(key.equals("launch")){s.setValue("launched",true);s.setValue("revision",s.numberValue("revision",0)+1);}
            else if(key.equals("return"))s.setValue("launched",false);
            else if(key.equals("party"))s.setValue("party",!s.booleanValue("party",false));
            s.invalidate();
        },UiScreenOptions.builder().parent(parent).backdrop(UiBackdrop.NONE).onKey((s,e)->{
            if(e.key()==GLFW.GLFW_KEY_ESCAPE&&(s.booleanValue("launched",false)||!s.stringValue("tab","PLAY").equals("PLAY"))){s.setValue("launched",false);s.setValue("tab","PLAY");return true;}return false;
        }).pauseGame(true).build());
    }
    private static final class D {
        final UiContext ui;final UiCanvas g;final float w,h;
        D(UiContext ui){this.ui=ui;g=ui.canvas();w=ui.width();h=ui.height();}
        void fill(int c,float x,float y,float w,float h){g.fill(c,x,y,w,h);}
        void text(String t,float x,float y,float z,int c,boolean b){g.text(t,x,y,z,c,b?Font.BOLD:Font.PLAIN,FONT);}
        void label(String t,float x,float y,int c){g.trackedText(t,x,y,9,c,Font.BOLD,FONT,1.6F);}
        void line(int c,float x,float y,float xx,float yy){g.line(c,1,x,y,xx,yy);}
        void icon(UiSymbol i,float x,float y,float z,int c){i.draw(g,c,x,y,z,1.5F);}
        void panel(float x,float y,float w,float h){fill(0xD80A1B2A,x,y,w,h);g.strokeRoundRect(0x407DABC5,.6F,x,y,w,h,3);}
        void button(String id,String label,float x,float y,float w,float h,boolean active,boolean enabled,java.util.function.Consumer<UiButtonState> painter){
            ui.paintedButton(id,label,id,x,y,w,h,2,enabled,(canvas,state)->{
                if(active||state.hovered())fill(active?0x3528A9DD:0x2348BBEC,x,y,w,h);
                painter.accept(state);
            });
        }
        void solid(String id,String label,float x,float y,float w,float h,boolean enabled){button(id,label,x,y,w,h,false,enabled,state->{fill(state.hovered()?0xFFFFE597:GOLD,x,y,w,h);text(label,x+19,y+h/2-8,15,INK,true);icon(UiIcon.ARROW_RIGHT,x+w-36,y+h/2-9,18,INK);});}
    }
    private static void paint(UiContext ui){
        D d=new D(ui);var g=d.g;String tab=ui.stringValue("tab","PLAY");
        var background=g.minecraftImage(ART,new UiRect(0,0,ui.width(),ui.height()),UiImageOptions.photo());
        g.gradientRect(0xC8071628,0x00071628,0,0,0,720,ui.height(),0);
        g.gradientRect(0x00101F2B,0xF0071624,90,0,(d.h-230),ui.width(),230,0);
        g.gradientRect(0xE0061322,0x00061322,90,0,0,ui.width(),125,0);
        d.icon(GLIDER,39,25,28,WHITE);d.text("WAYFARER",78,24,24,WHITE,true);d.label("THE SKY IS NOT THE LIMIT",79,53,MUTED);
        String[] tabs={"PLAY","LOADOUT","JOURNAL"};for(int i=0;i<tabs.length;i++){String id=tabs[i];float x=390+i*115;
            d.button("tab:"+id,id,x,23,102,43,false,true,state->d.text(id,x+13,37,12,tab.equals(id)?WHITE:MUTED,true));}
        if(!tab.equals("OPTIONS")){int index=tab.equals("PLAY")?0:tab.equals("LOADOUT")?1:2;float xx=ui.animate("nav",390+index*115,260,UiEasing.EASE_OUT,new UiRect(380,60,360,6));d.fill(GOLD,xx,64,102,3);}
        d.label("01 / FIRST LIGHT",d.w-350,27,GOLD);d.text("SEASON FIELD PASS",d.w-350,45,9,MUTED,false);
        d.panel(d.w-193,24,102,35);d.icon(UiIcon.STAR, d.w-181,33,16,GOLD);d.text("2,450",d.w-158,34,13,WHITE,true);
        d.button("tab:OPTIONS","Options",d.w-67,24,30,35,false,true,state->d.icon(UiIcon.SETTINGS,d.w-60,32,19,WHITE));
        d.line(0x457DABC5,40,83,d.w-40,83);
        if(ui.booleanValue("launched",false)){briefing(d);footer(d);return;}
        if(tab.equals("PLAY")){lobby(d);party(d);}else if(tab.equals("LOADOUT"))loadout(d);else if(tab.equals("JOURNAL"))journal(d);else options(d);
        if(tab.equals("PLAY")||tab.equals("LOADOUT")){
            background.ifPresent(mapping->WayfarerPlayerPreview.reserve(ui,mapping));
        }
        footer(d);
    }
    private static void lobby(D d){
        int m=(int)d.ui.numberValue("mission",0);float enter=d.ui.animateFrom("entrance",0,1,600,0,UiEasing.EASE_OUT,new UiRect(0,0,d.ui.width(),d.ui.height()));
        float y=128+(1-enter)*14;
        d.fill(GOLD,40,y,3,30);d.label("EXPEDITION "+String.format("%02d",m+1)+"   /   OPEN SKY",54,y+2,GOLD);d.text("A new horizon is waiting.",54,y+18,10,MUTED,false);
        d.text(m==0?"BEYOND":m==1?"INTO":"CHASE",38,y+51,57,WHITE,true);
        d.text(m==0?"THE CLOUDLINE":m==1?"HOLLOW REACH":"THE CLEARING",38,y+109,m==1?43:44,WHITE,true);
        String[] lines=DESC[m].split("\n");for(int i=0;i<lines.length;i++)d.text(lines[i],42,y+176+i*18,12,0xFFD4E4EF,false);
        d.label(TYPES[m],42,y+239,MUTED);d.icon(UiIcon.PEOPLE,211,y+237,14,MUTED);d.text("1–3 explorers",231,y+239,10,MUTED,false);
        float by=y+268;var style=UiDialogStyle.builder().surface(INK).border(0xFF487B97).confirmBackground(GOLD).confirmText(INK).titleColor(WHITE).messageColor(MUTED).maxWidth(460).padding(20).radius(3).fontFamily(FONT)
            .titleSize(20).messageSize(12).buttonSize(11).titleLineSpacing(3).messageLineSpacing(4).build();
        d.ui.paintedDialogButton("deploy","Ready to explore",40,by,315,56,2,true,
            new UiDialog("Ready for "+MISSIONS[m]+"?","Loadout: "+GEAR[(int)d.ui.numberValue("gear",0)]+". This local showcase opens an expedition briefing; it does not start gameplay or matchmaking.","Begin briefing","launch","Keep exploring","cancel",style),(canvas,state)->{
            float hover=d.ui.animate("deploy-glow",state.hovered(),140,UiEasing.EASE_OUT,new UiRect(40,by,315,56));
            d.fill(state.pressed()?0xFFE7BD32:UiCanvas.mix(GOLD,0xFFFFE48A,hover),40,by,315,56);
            d.text("READY TO EXPLORE",61,by+18,17,INK,true);d.icon(UiIcon.ARROW_RIGHT,315,by+17,22,INK);
        });
        d.text("PRIVATE EXPEDITION",42,by+66,8,MUTED,true);d.text("Change your route below",190,by+66,8,MUTED,false);
        float cy=d.h-171;d.label("CHOOSE YOUR NEXT HORIZON",40,cy-21,WHITE);
        for(int i=0;i<3;i++){int n=i;float x=40+i*237;boolean selected=m==i;
            d.button("mission:"+i,MISSIONS[i],x,cy,224,116,selected,true,state->{
                d.g.withClip(x,cy,224,116,()->{d.g.minecraftImage(ART,new UiRect(x,cy,224,116),new UiImageOptions(UiImageOptions.Fit.COVER,UiImageOptions.Sampling.BILINEAR,n*.5F,.5F));d.g.gradientRect(0x1007192A,0xF807192A,90,x,cy,224,116,0);});
                d.g.strokeRoundRect(selected?GOLD:0x7084A8BE,(selected?2:1),x,cy,224,116,0);
                d.label("0"+(n+1),x+13,cy+12,selected?GOLD:WHITE);if(selected)d.icon(UiIcon.CHECK,x+193,cy+12,16,GOLD);
                d.text(MISSIONS[n],x+13,cy+69,15,WHITE,true);d.text(TYPES[n]+"     +"+(400+n*150)+" XP",x+13,cy+93,8,MUTED,false);
            });
        }
        d.panel(d.w-250,d.h-171,210,116);d.label("FIELD PASS",d.w-234,d.h-157,MUTED);d.text("07",d.w-234,d.h-137,31,WHITE,true);d.text("WAYFINDER",d.w-177,d.h-127,10,GOLD,true);
        float xp=(float)d.ui.numberValue("xp",1420);d.fill(0xFF263F52,d.w-234,d.h-87,178,4);d.fill(GOLD,d.w-234,d.h-87,178*xp/2000,4);d.text((int)xp+" / 2,000 XP",d.w-234,d.h-73,8,MUTED,false);
    }
    private static void party(D d){
        float x=d.w-250;d.panel(x,109,210,135);d.label("YOUR CREW",x+16,125,WHITE);d.text("01 / 03",x+151,125,9,MUTED,false);
        d.fill(0xFF27485E,x+16,151,31,31);d.icon(COMPASS,x+22,157,19,GOLD);d.text(d.ui.stringValue("callsign","Rowan"),x+58,151,12,WHITE,true);d.text("Party leader",x+58,169,9,MUTED,false);d.g.circle(0xFF82E1B4,(x+183),160,3);
        d.button("party","Toggle crew privacy",x+16,199,178,29,false,true,state->{d.line(0x456A91AB,x+16,193,x+194,193);d.icon(d.ui.booleanValue("party",false)?UiIcon.CHECK:UiIcon.PLUS,x+19,204,14,MUTED);d.text(d.ui.booleanValue("party",false)?"CREW OPEN · LOCAL":"OPEN CREW SLOT",x+44,206,9,MUTED,true);});
        d.panel(x,259,210,81);d.label("DAILY FIELD NOTE",x+16,273,GOLD);d.text("Leave it better than you found it.",x+16,294,10,WHITE,false);d.text("Pack light. Look up. Keep going.",x+16,312,9,MUTED,false);
    }
    private static void loadout(D d){
        d.label("YOUR EXPEDITION / YOUR WAY",42,121,GOLD);d.text("BUILT FOR THE",39,151,43,WHITE,true);d.text("UNKNOWN.",39,201,55,WHITE,true);d.text("Choose the tool that fits your next horizon.",42,275,12,MUTED,false);
        String[] desc={"Read the terrain. Find a better route.","Track faint signals across the islands.","Turn a long fall into a new beginning."};
        int chosen=(int)d.ui.numberValue("gear",0);
        for(int i=0;i<3;i++){int n=i;float x=42+i*217,y=334;boolean selected=i==chosen;
            d.button("gear:"+i,"Equip "+GEAR[i],x,y,202,233,selected,true,state->{d.panel(x,y,202,233);d.fill(selected?GOLD:BLUE,x,y,202,3);d.label("FIELD TOOL / 0"+(n+1),x+17,y+20,MUTED);
                float a=d.ui.animate("gear-lift-"+n,selected?1:0,240,UiEasing.SPRING,new UiRect(x,y,202,233));d.icon(SYMBOLS[n],x+67,y+54-a*5,66,selected?GOLD:BLUE);
                d.text(GEAR[n],x+17,y+139,17,WHITE,true);d.g.wrappedText(desc[n],(x+17),(y+168),172,10,15,MUTED,Font.PLAIN,FONT);
                d.text(selected?"EQUIPPED":"SELECT EQUIPMENT",x+17,y+207,9,selected?GOLD:MUTED,true);if(selected)d.icon(UiIcon.CHECK,x+168,y+204,15,GOLD);
            });}
        d.text("Equipment selection carries into your expedition briefing.",42,593,11,MUTED,false);
    }
    private static void journal(D d){
        d.panel(32,108,727,543);d.label("SMALL STEPS. BIG HORIZONS.",53,130,GOLD);d.text("FIELD JOURNAL",50,159,39,WHITE,true);
        String[] title={"A proper send-off","Find your bearings","No island left behind"};String[] description={"Finish your first equipment check.","Survey three landmarks beyond the cloudline.","Recover a lost field recorder."};
        for(int i=0;i<3;i++){float y=232+i*111;d.line(0x405E829B,53,y-12,736,y-12);d.icon(i==0?UiIcon.CHECK:COMPASS,57,y+13,29,i==0?GOLD:MUTED);d.text(title[i],107,y+6,20,WHITE,true);d.text(description[i],107,y+37,11,MUTED,false);d.text(i==0?"COMPLETE":i==1?"1 / 3 LANDMARKS":"NOT STARTED",107,y+65,9,i==0?GOLD:MUTED,true);d.text("+"+(250+i*150)+" XP",648,y+9,13,GOLD,true);}
        boolean claimed=d.ui.booleanValue("claimed",false);d.solid("claim",claimed?"REWARD CLAIMED":"CLAIM 250 XP",53,586,276,43,!claimed);d.text("Rewards and progress are local to this demo.",347,601,10,MUTED,false);
    }
    private static void options(D d){
        d.panel(32,108,650,510);d.label("MAKE YOURSELF AT HOME",55,132,GOLD);d.text("FLIGHT SETTINGS",52,160,39,WHITE,true);
        d.text("Callsign",56,237,13,WHITE,true);
        var style=new UiInputStyle(14,Font.PLAIN,FONT,12,7,20).appearance(UiInputAppearance.defaults().background(0xFF152C3E).border(0xFF46677B).text(WHITE).placeholder(MUTED).focus(BLUE).radius(3));
        d.ui.textField("callsign","callsign","Your callsign",56,263,420,40,"Rowan",24,style);
        d.text("Interface volume",56,337,13,WHITE,true);d.ui.slider("volume","volume",56,372,420,0,100,70);d.text((int)d.ui.numberValue("volume",70)+"%",499,366,13,GOLD,true);
        d.text("Reduce motion",56,435,13,WHITE,true);d.text("Keep the view still. All controls remain available.",56,460,11,MUTED,false);d.ui.toggle("reduce-motion","reduce-motion",472,435,false);
        d.text("Volume is a UI setting preview; this demo contains no audio.",56,535,10,MUTED,false);
    }
    private static void briefing(D d){
        d.fill(0xD9071626,0,84,d.w,d.h-84);int m=(int)d.ui.numberValue("mission",0);
        d.label("EXPEDITION BRIEFING",58,133,GOLD);d.text("YOUR HORIZON",54,178,54,WHITE,true);d.text("IS READY.",54,239,54,WHITE,true);
        d.text(MISSIONS[m],58,324,24,BLUE,true);d.text("PILOT  /  "+d.ui.stringValue("callsign","Rowan"),58,376,12,MUTED,true);d.text("LOADOUT  /  "+GEAR[(int)d.ui.numberValue("gear",0)],58,403,12,MUTED,true);
        float p=d.ui.animateFrom("briefing-"+(int)d.ui.numberValue("revision",0),0,1,1200,0,UiEasing.EASE_IN_OUT,new UiRect(58,455,480,8));d.fill(0xFF2A4254,58,455,480,4);d.fill(GOLD,58,455,480*p,4);
        d.text("UI demonstration complete. No world or matchmaking session is started.",58,487,11,MUTED,false);d.solid("return","BACK TO SKYPORT",58,537,292,52,true);
        d.icon(COMPASS,d.w-351,208,186,GOLD);d.label("GO SOMEWHERE NEW.",d.w-366,426,WHITE);
    }
    private static void footer(D d){
        d.fill(0xED071624,0,d.h-39,d.w,39);
        d.line(0x6088A8BA,40,d.h-39,d.w-40,d.h-39);
        d.text("PANELIB",40,d.h-25,11,WHITE,true);
        d.text("MINECRAFT UI SHOWCASE ONLY — NOT A REAL GAME",111,d.h-25,11,WHITE,true);
    }
}
