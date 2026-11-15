package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * Original battle-royale lobby in the bold arcade style: slanted tabs, outlined display type,
 * rarity cards, a season track and a pulsing Play button. A local UI flow, not a game mode.
 */
public final class SkydropShowcase {
    private static final String FONT=ExampleFonts.FAMILY;
    static final int WHITE=0xFFFFFFFF, INK=0xFF0B1030, PANEL=0xC40B1030, MUTED=0xFFB9C4F2, GOLD=0xFFFFD21F, RED=0xFFFF4B55, GREEN=0xFF7BE06A;
    static final String[] TABS={"LOBBY","PASS","LOCKER","SHOP","CAREER"};
    private static final int[] RARITY={0xFF8E98A8,0xFF52C23A,0xFF2F9BFF,0xFFB14DFF,0xFFFF9A2E};
    private static final String[] RARITY_NAME={"COMMON","UNCOMMON","RARE","EPIC","LEGENDARY"};
    private static final long SEASON_END=System.currentTimeMillis()+((12L*24+4)*3600+11*60+32)*1000;
    private static final UiSymbol BOLT=UiSymbol.vector(24,24,(g,c,s)->g.polygon(c,14,1,4,14,11,14,9,23,20,9,13,9));
    private static final UiSymbol GLIDER=UiSymbol.vector(24,24,(g,c,s)->{g.path().moveTo(1,11).curveTo(12,-3,23,11).lineTo(12,8).close().fill(c);g.line(c,s,3,11,12,21);g.line(c,s,21,11,12,21);g.circle(c,12,21,2);});
    private static final UiSymbol PICK=UiSymbol.vector(24,24,(g,c,s)->{g.path().moveTo(3,9).curveTo(12,0,21,9).lineTo(19,11).curveTo(12,5,5,11).close().fill(c);g.line(c,s*2,12,6,12,23);});
    private static final UiSymbol SUIT=UiSymbol.vector(24,24,(g,c,s)->{g.circle(c,12,5,4);g.polygon(c,5,11,19,11,17,23,13,23,12,17,11,23,7,23);});
    private static final UiSymbol PACK=UiSymbol.vector(24,24,(g,c,s)->{g.roundRect(c,5,7,14,15,4);g.path().moveTo(8,7).curveTo(8,1,16,1,16,7).stroke(c,s*1.4F);});
    private static final UiSymbol TRAIL=UiSymbol.vector(24,24,(g,c,s)->{for(int i=0;i<3;i++)g.path().moveTo(2,6+i*6).curveTo(8,1+i*6,14,11+i*6,22,6+i*6).stroke(c,s*1.6F);});
    private static final UiSymbol BURST=UiSymbol.vector(24,24,(g,c,s)->g.polygon(c,12,1,14.6F,8,22,6,17,12,22,18,14.6F,16,12,23,9.4F,16,2,18,7,12,2,6,9.4F,8));
    private record Item(String id,String name,int rarity,int price,UiSymbol art){}
    private static final Item[] SHOP={new Item("aurora","AURORA DRIFTER",4,2000,SUIT),new Item("nimbus","NIMBUS WING",3,1200,GLIDER),
        new Item("ember","EMBER PICK",2,800,PICK),new Item("ripple","RIPPLE TRAIL",1,300,TRAIL),new Item("satchel","SCOUT SATCHEL",1,200,PACK),
        new Item("flare","VICTORY FLARE",2,500,BURST),new Item("frost","FROSTLINE SUIT",3,1500,SUIT),new Item("kite","PAPER KITE",0,100,GLIDER)};
    private static final String[] SLOTS={"OUTFIT","BACK BLING","TOOL","GLIDER","TRAIL","EMOTE 1","EMOTE 2","BANNER"};
    private static final UiSymbol[] SLOT_ART={SUIT,PACK,PICK,GLIDER,TRAIL,BURST,BURST,BOLT};
    private static final String[] QUESTS={"Land at three named places","Open seven supply crates","Travel 2,000 m by glider"};
    private static final int[] QUEST_DONE={3,7,1240}, QUEST_GOAL={3,7,2000};
    private static final List<UiSelectOption> MODES=List.of(new UiSelectOption("SOLO","SOLO"),new UiSelectOption("DUOS","DUOS"),new UiSelectOption("SQUADS","SQUADS"),new UiSelectOption("SURGE","STORM SURGE"));
    private static final UiMenuStyle MENU=UiMenuStyle.builder().background(0xF00B1030).text(WHITE).border(0xFF3D4AA8).highlight(0xFF2A3BB0).radius(4).build();
    private static final UiDialogStyle DIALOG=UiDialogStyle.builder().surface(0xFF101848).border(0xFF3D4AA8).confirmBackground(GOLD).confirmText(INK).titleColor(WHITE).messageColor(MUTED)
        .maxWidth(440).padding(22).radius(4).fontFamily(FONT).titleSize(22).messageSize(13).buttonSize(12).build();
    private SkydropShowcase(){}

    public static UiScreen createScreen(Screen parent){
        var theme=new UiTheme(INK,INK,0xFF161F5C,0xFF1D287A,WHITE,MUTED,GOLD,INK,4,8,12,1,1,FONT);
        var doc=UiDocument.builder("Skydrop / PaneLib").theme(theme).shell(UiShell.NONE).labelTooltips(false)
            .scaling(UiScaling.fixed().reference(1280,720).adaptivity(1).range(.25F,8)).renderSettings(UiRenderSettings.typography())
            .page(new UiPage("lobby","Skydrop","",GOLD,SkydropShowcase::paint)).build();
        return UiScreen.create(doc,ui->ui.playerPreview("hero",WayfarerPlayerPreview.EXPLORER_SKIN,UiPlayerPreview.standing().bodyYaw(200).headRotation(-8,0).cameraPitch(-6)),(s,event)->{
            String key=event.key();
            if(event.type()==UiEventType.VALUE){if(key.equals("reduce-motion"))s.setMotionScale(Boolean.parseBoolean(event.value())?0:1);return;}
            if(event.type()!=UiEventType.ACTION)return;
            if(key.startsWith("tab:")){s.setValue("tab",key.substring(4));s.setValue("settings",false);}
            else if(key.equals("play")){boolean queued=!s.booleanValue("queued",false);s.setValue("queued",queued);s.setValue("queue-start",(double)System.currentTimeMillis());}
            else if(key.startsWith("invite:"))s.setValue("party"+key.substring(7),!s.booleanValue("party"+key.substring(7),false));
            else if(key.startsWith("claim:")&&!s.booleanValue("claimed"+key.substring(6),false)){
                s.setValue("claimed"+key.substring(6),true);double xp=s.numberValue("xp",3400)+900;
                if(xp>=5000){xp-=5000;s.setValue("tier",s.numberValue("tier",24)+1);s.toast(new UiToast("tier","Tier up","You reached tier "+(int)s.numberValue("tier",24)+"."));}
                s.setValue("xp",xp);
            }
            else if(key.startsWith("buy:")){Item item=item(key.substring(4));double bolts=s.numberValue("bolts",2750);
                if(bolts>=item.price()){s.setValue("bolts",bolts-item.price());s.setValue("owned:"+item.id(),true);s.toast(new UiToast("buy","Purchased",item.name()+" is in your locker."));}
                else s.toast(new UiToast("buy","Not enough bolts","Press + beside your balance; this demo hands them out free."));}
            else if(key.equals("topup"))s.setValue("bolts",s.numberValue("bolts",2750)+1000);
            else if(key.equals("upgrade"))s.setValue("premium",true);
            else if(key.equals("settings"))s.setValue("settings",!s.booleanValue("settings",false));
            else if(key.equals("back"))s.onClose();
            s.invalidate();
        },UiScreenOptions.builder().parent(parent).backdrop(UiBackdrop.NONE).onKey((s,e)->{
            if(e.key()==GLFW.GLFW_KEY_ESCAPE&&s.booleanValue("settings",false)){s.setValue("settings",false);s.invalidate();return true;}
            if(s.focusedId().filter(id->id.equals("callsign")||id.contains("keybind")).isPresent()||s.booleanValue("settings",false))return false;
            int step=e.key()==GLFW.GLFW_KEY_Q?-1:e.key()==GLFW.GLFW_KEY_E?1:0;if(step==0)return false;
            int index=java.util.Arrays.asList(TABS).indexOf(s.stringValue("tab","LOBBY"));
            s.setValue("tab",TABS[Math.floorMod(index+step,TABS.length)]);s.invalidate();return true;
        }).pauseGame(true).build());
    }
    private static Item item(String id){for(Item item:SHOP)if(item.id().equals(id))return item;throw new IllegalArgumentException(id);}

    private static final class D {
        final UiContext ui;final UiCanvas g;final float w,h;final int accent;
        D(UiContext ui){this.ui=ui;g=ui.canvas();w=ui.width();h=ui.height();accent=ui.colorValue("accent",GOLD);}
        void text(String t,float x,float y,float z,int c){g.text(t,x,y,z,c,Font.BOLD,FONT);}
        void plain(String t,float x,float y,float z,int c){g.text(t,x,y,z,c,Font.PLAIN,FONT);}
        /** Heavy leaning display type with a dark rim and a hard shadow. */
        void title(String t,float x,float y,float z,int c){g.withSkew(10,x,y+z,()->g.text(t,x,y,z,c,Font.BOLD,FONT,UiTextEffect.outline(INK,Math.max(1.5F,z/11)).withShadow(0x90040824,0,Math.max(1.5F,z/12))));}
        void centeredTitle(String t,float cx,float cy,float z,int c){g.withSkew(10,cx,cy,()->g.centeredText(t,cx,cy,z,c,Font.BOLD,FONT,UiTextEffect.outline(INK,Math.max(1.5F,z/11)).withShadow(0x90040824,0,Math.max(1.5F,z/12))));}
        void panel(float x,float y,float w,float h){g.chamferRect(PANEL,x,y,w,h,0,14,0,14);g.chamferPath(x,y,w,h,0,14,0,14).stroke(0x553D4AA8,1,false);}
        void keycap(String key,float x,float y){float w=Math.max(24,g.textWidth(key,11,Font.BOLD,FONT)+12);g.roundRect(0xFF0B1030,x,y,w,24,5);g.strokeRoundRect(0xFF6976D8,1,x,y,w,24,5);g.centeredText(key,x+w/2,y+12,11,WHITE,Font.BOLD,FONT);}
        void bar(float x,float y,float w,float h,float progress,int color){g.slantRect(0xFF263079,x,y,w,h,h*.5F);if(progress>0)g.slantRect(color,x,y,Math.max(h,w*Math.min(1,progress)),h,h*.5F);}
        boolean on(String key){return ui.booleanValue(key,false);}
    }

    private static void paint(UiContext ui){
        D d=new D(ui);var g=d.g;String tab=ui.stringValue("tab","LOBBY");
        g.gradientRect(0xFF2459E0,0xFF5B2BC4,55,0,0,d.w,d.h,0);
        g.stripes(0x0BFFFFFF,30,46,115,0,0,d.w,d.h);
        g.radialRect(0x66BFE0FF,0x00BFE0FF,640,330,520,0,0,d.w,d.h,0);
        g.vignette(0xA0070B2A,0,0,d.w,d.h);
        g.gradientRect(0xD0080D33,0x00080D33,90,0,0,d.w,104,0);
        d.title("SKYDROP",30,14,34,WHITE);
        d.keycap("Q",226,24);
        for(int i=0;i<TABS.length;i++){String id=TABS[i];float x=262+i*122;boolean selected=tab.equals(id);
            ui.shapedButton("tab:"+id,id,"tab:"+id,g.slantPath(x,16,118,40,14),true,(c,state)->{
                if(selected)c.slantPath(x,16,118,40,14).fillGradient(UiCanvas.lighten(d.accent,.25F),d.accent,90);
                else c.slantRect(state.hovered()?0x70FFFFFF:0x30FFFFFF,x,16,118,40,14);
                c.withSkew(10,x+59,36,()->c.centeredText(id,x+59,36,15,selected?INK:WHITE,Font.BOLD,FONT));
            });}
        d.keycap("E",262+TABS.length*122+2,24);
        float chip=d.w-306;g.chamferRect(0xC00B1030,chip,18,142,36,8);BOLT.draw(g,GOLD,chip+10,25,22,1);
        d.text(String.format("%,d",(long)ui.numberValue("bolts",2750)),chip+38,27,15,WHITE);
        ui.paintedButton("topup","Add bolts","topup",chip+106,22,28,28,6,true,(c,state)->{c.roundRect(state.hovered()?UiCanvas.lighten(d.accent,.2F):d.accent,chip+106,22,28,28,6);UiIcon.PLUS.draw(c,INK,chip+112,28,16,2.4F);});
        UiIcon.PEOPLE.draw(g,WHITE,d.w-150,26,20,1.8F);d.text("3",d.w-124,27,15,WHITE);
        ui.paintedButton("settings","Settings","settings",d.w-92,18,36,36,8,true,(c,state)->{c.chamferRect(state.hovered()||d.on("settings")?0xFF2A3BB0:0xC00B1030,d.w-92,18,36,36,8);UiIcon.SETTINGS.draw(c,WHITE,d.w-84,26,20,1.8F);});
        ui.paintedButton("back","Back","back",d.w-48,18,36,36,8,true,(c,state)->{c.chamferRect(state.hovered()?0xFFB5303A:0xC00B1030,d.w-48,18,36,36,8);UiIcon.CLOSE.draw(c,WHITE,d.w-40,26,20,2);});
        switch(tab){case "LOBBY"->lobby(d);case "PASS"->pass(d);case "LOCKER"->locker(d);case "SHOP"->shop(d);default->career(d);}
        g.gradientRect(0x00080D33,0xD0080D33,90,0,d.h-46,d.w,46,0);
        d.keycap("Q",32,d.h-32);d.keycap("E",60,d.h-32);d.plain("Switch tab",92,d.h-28,12,MUTED);d.keycap("ESC",176,d.h-32);d.plain("Back",220,d.h-28,12,MUTED);
        d.plain("PANELIB UI SHOWCASE — NOT A REAL GAME, NOTHING IS BOUGHT OR SOLD",d.w-470,d.h-28,12,MUTED);
        if(d.on("settings"))settings(d);
    }

    private static void hero(D d,float cx,float feet,float height){
        var g=d.g;
        if(d.ui.booleanValue("animated",true)){float turn=d.ui.loop(120_000,new UiRect(cx-290,feet-height-40,580,height+110))*360;
            g.rays(0x14FFFFFF,20,turn,cx,feet-height*.45F,cx-290,feet-height-40,580,height+110);}
        g.withScale(1,.26F,cx,feet,()->{g.circle(0x70060A2A,cx,feet+30,210);g.circle(0xFF2B3A9E,cx,feet,180);g.circle(0xFF3E51C9,cx,feet-14,180);g.strokeCircle(d.accent,10,cx,feet-14,150);});
        d.ui.nativeSlot("hero",cx-height*.3F,feet-height,height*.6F,height);
    }

    private static void lobby(D d){
        var g=d.g;var ui=d.ui;
        d.panel(32,104,300,150);g.slantRect(d.accent,32,104,150,26,10);d.text("SEASON 3",44,110,13,INK);
        int tier=(int)ui.numberValue("tier",24);d.plain("TIER",48,146,12,MUTED);d.title(String.valueOf(tier),46,158,44,WHITE);
        float xp=(float)ui.numberValue("xp",3400),shown=ui.animate("xp-bar",xp/5000F,500,UiEasing.EASE_OUT,new UiRect(124,176,200,20));
        d.bar(128,178,188,14,shown,d.accent);d.plain(String.format("%,d / 5,000 XP",(int)xp),128,156,12,WHITE);
        long left=Math.max(0,SEASON_END-System.currentTimeMillis())/1000;
        d.text(String.format("ENDS IN %dD %02d:%02d:%02d",left/86400,left/3600%24,left/60%60,left%60),48,222,12,MUTED);
        ui.refreshAfter(1000-System.currentTimeMillis()%1000,new UiRect(40,216,280,24));
        d.panel(32,268,300,236);d.text("DAILY QUESTS",48,282,14,WHITE);
        for(int i=0;i<3;i++){int n=i;float y=312+i*62;boolean complete=QUEST_DONE[i]>=QUEST_GOAL[i],claimed=d.on("claimed"+i);
            d.plain(QUESTS[i],48,y,12,WHITE);d.bar(48,y+22,170,10,QUEST_DONE[i]/(float)QUEST_GOAL[i],complete?GREEN:0xFF58A6FF);
            d.plain(String.format("%,d / %,d",QUEST_DONE[i],QUEST_GOAL[i]),48,y+36,10,MUTED);
            if(claimed){UiIcon.CHECK.draw(g,GREEN,270,y+10,22,2.6F);}
            else ui.shapedButton("claim:"+i,complete?"Claim 900 XP":"900 XP when complete","claim:"+i,g.chamferPath(232,y+8,84,28,6,6,6,6),complete,(c,state)->{
                c.chamferRect(complete?(state.hovered()?UiCanvas.lighten(d.accent,.2F):d.accent):0xFF263079,232,y+8,84,28,6);c.centeredText(complete?"CLAIM":"+900 XP",274,y+22,11,complete?INK:MUTED,Font.BOLD,FONT);});
        }
        g.withClip(g.chamferPath(32,518,300,112,0,14,0,14),()->{g.gradientRect(0xFF7A2BD0,0xFF2B2D9E,20,32,518,300,112,0);g.stripes(0x18FFFFFF,10,14,45,32,518,300,112);});
        g.withRotation(-6,60,540,()->{g.roundRect(RED,44,530,52,22,4);g.centeredText("NEW",70,541,12,WHITE,Font.BOLD,FONT);});
        d.title("STORM SURGE",46,562,24,WHITE);d.plain("The circle closes twice as fast. This week only.",48,600,11,0xFFE4DAFF);

        hero(d,640,600,440);
        String name=ui.stringValue("callsign","Rowan");g.slantRect(0xE00B1030,530,622,220,36,12);
        g.centeredText(name,640,640,g.fitTextSize(name,180,17,Font.BOLD,FONT),WHITE,Font.BOLD,FONT);
        for(int side=0;side<2;side++){int n=side;float cx=side==0?420:860,cy=470;boolean joined=d.on("party"+side);String friend=side==0?"Nova_77":"Tidewalker";
            ui.shapedButton("invite:"+side,joined?"Remove "+friend:"Invite a friend","invite:"+side,g.path().arc(cx,cy,36,0,360).close(),true,(c,state)->{
                float pop=ui.animate("party-pop"+n,joined?1:0,260,UiEasing.SPRING,new UiRect(cx-70,cy-46,140,110));
                c.circle(state.hovered()?0xE01B2670:0xC00B1030,cx,cy,36);
                if(joined){c.strokeCircle(GREEN,3,cx,cy,34);UiIcon.PEOPLE.draw(c,WHITE,cx-13*pop,cy-13*pop,26*pop,2);}
                else{c.path().arc(cx,cy,34,0,360).close().stroke(0xFF8E9BF0,2);UiIcon.PLUS.draw(c,WHITE,cx-12,cy-12,24,2.6F);}
            });
            if(joined){g.centeredText(friend,cx,cy+50,12,WHITE,Font.BOLD,FONT);g.centeredText("READY",cx,cy+66,10,GREEN,Font.BOLD,FONT);}
            else g.centeredText("INVITE",cx,cy+50,10,MUTED,Font.BOLD,FONT);
        }

        float rx=928;d.panel(rx,104,320,150);d.text("MATCH RECAP",rx+16,118,14,WHITE);
        String[] recap={"PLACED","ELIMINATIONS","SURVIVED"},value={"#4","6","18:42"};
        for(int i=0;i<3;i++){float x=rx+16+i*100;d.title(value[i],x,150,26,i==0?d.accent:WHITE);d.plain(recap[i],x,190,10,MUTED);}
        d.plain("Best finish this week: #2 in Squads",rx+16,222,11,MUTED);
        boolean queued=d.on("queued");
        d.plain("GAME MODE",rx,452,11,MUTED);
        ui.paintedDropdown("mode","mode",rx,470,320,54,"SQUADS",MODES,6,!queued,MENU,(c,state)->{
            c.chamferRect(state.hovered()?0xE01B2670:PANEL,rx,470,320,54,0,12,0,12);c.chamferPath(rx,470,320,54,0,12,0,12).stroke(0x803D4AA8,1,false);
            c.withSkew(10,rx+16,510,()->c.text(state.label(),rx+16,484,22,WHITE,Font.BOLD,FONT));UiIcon.CHEVRON_DOWN.draw(c,d.accent,rx+282,486,22,2.4F);});
        ui.checkbox("fill","fill","Fill my squad",rx,536,200,true);
        UiRect play=new UiRect(rx-14,566,348,96);
        float glow=queued?0:ui.pulse(1600,play);
        ui.shapedButton("play",queued?"Cancel matchmaking":"Play","play",g.slantPath(rx,576,320,76,18),true,(c,state)->{
            int base=queued?RED:d.accent;
            c.withSkew(13,rx+160,614,()->c.glow(UiCanvas.alpha(base,(int)(70+110*glow)),10+8*glow,rx+10,580,300,68,4));
            c.slantPath(rx,576,320,76,18).fillGradient(UiCanvas.lighten(base,state.hovered()?.45F:.28F),state.pressed()?UiCanvas.darken(base,.2F):base,90);
            c.slantPath(rx,576,320,76,18).stroke(INK,3,false);
            c.withSkew(10,rx+160,614,()->c.centeredText(queued?"CANCEL":"PLAY",rx+160,queued?606:614,queued?30:42,queued?WHITE:INK,Font.BOLD,FONT,
                queued?UiTextEffect.outline(INK,2.5F):UiTextEffect.shadow(0x50FFFFFF,0,2)));
            if(queued){long seconds=(System.currentTimeMillis()-(long)ui.numberValue("queue-start",0))/1000;
                c.centeredText(String.format("FINDING MATCH  %d:%02d",seconds/60,seconds%60),rx+160,634,12,WHITE,Font.BOLD,FONT);}
        });
        if(queued)ui.refreshAfter(1000,play);
    }

    private static void pass(D d){
        var g=d.g;var ui=d.ui;int tier=(int)ui.numberValue("tier",24);boolean premium=d.on("premium");
        d.title("SEASON 3 PASS",32,100,40,WHITE);d.plain("Play to climb 60 tiers. Free rewards on top, pass rewards below.",36,150,13,MUTED);
        if(premium){g.slantRect(GREEN,960,108,288,44,12);g.centeredText("PASS UNLOCKED",1104,130,16,INK,Font.BOLD,FONT);}
        else ui.shapedButton("upgrade","Unlock the pass","upgrade",g.slantPath(960,108,288,44,12),true,(c,state)->{c.slantPath(960,108,288,44,12).fillGradient(UiCanvas.lighten(d.accent,state.hovered()?.4F:.2F),d.accent,90);c.withSkew(10,1104,130,()->c.centeredText("UNLOCK THE PASS",1104,130,17,INK,Font.BOLD,FONT));});
        d.plain("FREE",36,236,12,MUTED);d.plain("PASS",36,426,12,d.accent);
        ui.scrollHorizontal("pass-track",new UiRect(84,182,d.w-116,420),60*132+8,track->{
            var c=track.canvas();
            c.fill(0xFF263079,84,372,60*132,8);c.fill(d.accent,84,372,Math.min(60,tier)*132-66,8);
            for(int i=0;i<60;i++){int level=i+1;float x=84+i*132;boolean reached=level<=tier;
                int rarity=level%10==0?4:level%5==0?3:level%3==0?2:level%2;UiSymbol art=SLOT_ART[i%SLOT_ART.length];
                for(int row=0;row<2;row++){float y=row==0?190:398;boolean locked=row==1&&!premium;if(row==0&&level%2==0)continue;
                    c.chamferRect(0xFF0B1030,x+6,y,120,160,10);c.withClip(c.chamferPath(x+8,y+2,116,156,9,9,9,9),()->c.radialRect(UiCanvas.alpha(RARITY[rarity],reached&&!locked?255:110),UiCanvas.alpha(UiCanvas.darken(RARITY[rarity],.6F),reached&&!locked?255:110),x+66,y+70,110,x+8,y+2,116,156,0));
                    art.draw(c,reached&&!locked?WHITE:0x90FFFFFF,x+38,y+42,56,2);
                    if(locked)UiIcon.SHIELD.draw(c,WHITE,x+98,y+10,18,1.8F);else if(reached)UiIcon.CHECK.draw(c,WHITE,x+98,y+10,18,2.6F);
                    c.centeredText(RARITY_NAME[rarity],x+66,y+142,9,WHITE,Font.BOLD,FONT);}
                c.chamferRect(reached?d.accent:0xFF263079,x+44,360,44,32,8);c.centeredText(String.valueOf(level),x+66,376,14,reached?INK:WHITE,Font.BOLD,FONT);
            }
        });
        d.plain("Scroll sideways, drag the bar, or hold Shift and use the wheel.",84,616,12,MUTED);
    }

    private static void locker(D d){
        var g=d.g;var ui=d.ui;String slot=ui.stringValue("slot","OUTFIT");
        d.title("LOCKER",32,100,40,WHITE);
        for(int i=0;i<SLOTS.length;i++){String id=SLOTS[i];float x=36+i%2*128,y=166+i/2*112;boolean selected=slot.equals(id);UiSymbol art=SLOT_ART[i];
            int equipped=(int)ui.numberValue("equip:"+id,i%4);
            g.chamferRect(selected?d.accent:0xFF0B1030,x,y,116,100,10);g.withClip(g.chamferPath(x+3,y+3,110,94,8,8,8,8),()->g.radialRect(RARITY[equipped%5],UiCanvas.darken(RARITY[equipped%5],.6F),x+58,y+44,90,x+3,y+3,110,94,0));
            art.draw(g,WHITE,x+36,y+16,44,2);g.centeredText(id,x+58,y+82,10,WHITE,Font.BOLD,FONT);
            ui.selectable("slot:"+id,"slot",id,x,y,116,100);ui.tooltip("slot:"+id,"Choose your "+id.toLowerCase());
        }
        hero(d,470,600,410);
        int index=java.util.Arrays.asList(SLOTS).indexOf(slot);UiSymbol art=SLOT_ART[Math.max(0,index)];
        d.panel(690,166,558,446);d.text(slot,708,180,16,WHITE);int chosen=(int)ui.numberValue("equip:"+slot,Math.max(0,index)%4);
        d.plain(RARITY_NAME[chosen%5]+" · STYLE "+(chosen+1)+" OF 24",708,204,11,MUTED);
        ui.scroll("locker-options",new UiRect(700,226,538,376),6*128+8,list->{var c=list.canvas();
            for(int i=0;i<24;i++){int n=i;float x=708+i%4*130,y=230+i/4*128;boolean selected=chosen==i;
                c.chamferRect(selected?d.accent:0xFF182160,x,y,120,118,10);c.withClip(c.chamferPath(x+3,y+3,114,112,8,8,8,8),()->c.radialRect(RARITY[n%5],UiCanvas.darken(RARITY[n%5],.6F),x+60,y+50,100,x+3,y+3,114,112,0));
                c.withRotation(n*15,x+60,y+52,()->art.draw(c,WHITE,x+34,y+26,52,2));if(selected)UiIcon.CHECK.draw(c,WHITE,x+94,y+8,18,2.8F);
                list.selectable("equip:"+slot+":"+i,"equip:"+slot,String.valueOf(i),x,y,120,118);}
        });
    }

    private static void shop(D d){
        var g=d.g;var ui=d.ui;
        d.title("ITEM SHOP",32,100,40,WHITE);long left=86400-System.currentTimeMillis()/1000%86400;
        d.text(String.format("NEW ITEMS IN %02d:%02d:%02d",left/3600,left/60%60,left%60),d.w-262,118,14,WHITE);ui.refreshAfter(1000-System.currentTimeMillis()%1000,new UiRect(d.w-270,110,250,30));
        d.text("FEATURED",36,166,13,d.accent);d.text("DAILY",640,166,13,d.accent);
        for(int i=0;i<SHOP.length;i++){
            if(i<2)card(d,SHOP[i],36+i*296,190,280,420);else card(d,SHOP[i],640+(i-2)%3*204,190+(i-2)/3*214,192,204);
        }
    }
    private static void card(D d,Item item,float x,float y,float w,float h){
        var ui=d.ui;boolean owned=d.on("owned:"+item.id());int color=RARITY[item.rarity()];boolean big=w>240;
        float lift=ui.animate("card:"+item.id(),ui.hovered("buy:"+item.id()),140,UiEasing.EASE_OUT,new UiRect(x-10,y-10,w+20,h+20));
        ui.withTransform(UiTransform.identity().scale(1+lift*.035F),x+w/2,y+h/2,group->{var c=group.canvas();
            c.shadow(0x70040824,14,0,6+lift*4,x,y,w,h,4);c.chamferRect(lift>0?UiCanvas.mix(INK,WHITE,lift):INK,x-3,y-3,w+6,h+6,16,0,16,0);
            c.withClip(c.chamferPath(x,y,w,h,14,0,14,0),()->{c.radialRect(UiCanvas.lighten(color,.25F),UiCanvas.darken(color,.62F),x+w/2,y+h*.4F,Math.max(w,h)*.7F,x,y,w,h,0);
                c.rays(0x16FFFFFF,14,item.rarity()*11,x+w/2,y+h*.42F,x,y,w,h);c.gradientRect(0x00000000,0xD0040824,90,x,y+h*.62F,w,h*.38F,0);});
            float art=big?170:84;item.art().draw(c,WHITE,x+(w-art)/2,y+h*.42F-art/2,art,2);
            c.slantRect(color,x+10,y+10,big?112:86,big?24:20,8);c.centeredText(RARITY_NAME[item.rarity()],x+10+(big?56:43),y+10+(big?12:10),big?11:9,INK,Font.BOLD,FONT);
            float size=c.fitTextSize(item.name(),w-28,big?26:16,Font.BOLD,FONT);
            c.withSkew(10,x+14,y+h-48,()->c.text(item.name(),x+14,y+h-(big?76:58),size,WHITE,Font.BOLD,FONT,UiTextEffect.outline(INK,2)));
            if(owned){c.withRotation(-10,x+w/2,y+h/2,()->{c.roundRect(0xE0101848,x+w/2-70,y+h/2-20,140,40,6);c.strokeRoundRect(GREEN,2,x+w/2-70,y+h/2-20,140,40,6);c.centeredText("OWNED",x+w/2,y+h/2,22,GREEN,Font.BOLD,FONT);});}
            else{BOLT.draw(c,GOLD,x+14,y+h-34,22,1);c.text(String.format("%,d",item.price()),x+40,y+h-32,big?18:15,WHITE,Font.BOLD,FONT);}
            if(owned)group.tooltip("owned:"+item.id(),"In your locker",x,y,w,h);
            else group.paintedDialogButton("buy:"+item.id(),"Buy "+item.name(),x,y,w,h,4,true,new UiDialog("Buy "+item.name()+"?",
                String.format("%s item for %,d bolts. This is a UI demonstration: nothing is charged.",RARITY_NAME[item.rarity()].charAt(0)+RARITY_NAME[item.rarity()].substring(1).toLowerCase(),item.price()),"Buy","buy:"+item.id(),"Cancel","cancel",DIALOG),(canvas,state)->{});
        });
    }

    private static void career(D d){
        var g=d.g;var ui=d.ui;
        d.title("CAREER",32,100,40,WHITE);
        String[] label={"MATCHES","VICTORIES","ELIMINATIONS","TOP TEN"};int[] total={412,31,1873,146};
        float count=ui.animateFrom("career-count",0,1,900,0,UiEasing.EASE_OUT,new UiRect(32,166,620,130));
        for(int i=0;i<4;i++){float x=36+i*154;d.panel(x,170,142,116);d.title(String.format("%,d",Math.round(total[i]*count)),x+14,188,32,i==1?d.accent:WHITE);d.plain(label[i],x+14,246,11,MUTED);}
        d.panel(36,304,604,300);d.text("WIN RATE BY MODE",52,318,14,WHITE);
        String[] mode={"SOLO","DUOS","SQUADS"};float[] rate={.06F,.09F,.12F};
        for(int i=0;i<3;i++){float cx=140+i*198,cy=450;float sweep=ui.animateFrom("ring"+i,0,rate[i]*360*4,1100,i*120,UiEasing.EASE_OUT,new UiRect(cx-90,cy-90,180,180));
            g.strokeCircle(0xFF263079,14,cx,cy,70);g.path().arc(cx,cy,70,-90,sweep).stroke(i==2?d.accent:0xFF58A6FF,14,true);
            d.centeredTitle(Math.round(rate[i]*100)+"%",cx,cy,30,WHITE);g.centeredText(mode[i],cx,cy+100,12,MUTED,Font.BOLD,FONT);}
        d.panel(660,170,588,434);d.text("RECENT MATCHES",676,184,14,WHITE);
        ui.variableList("matches",new UiRect(668,212,572,382),40,i->i%4==0?74:46,6,(row,item)->{var c=row.canvas();UiRect b=item.bounds();int n=item.index();int place=1+(n*7+3)%40;boolean win=place==1||n%13==0;
            c.slantRect(win?0xFF3A3410:0xFF182160,b.x()+4,b.y(),b.width()-12,b.height(),10);if(win)c.slantRect(d.accent,b.x()+4,b.y(),10,b.height(),6);
            c.text(win?"VICTORY":"#"+place,b.x()+34,b.y()+(b.height()>60?12:14),b.height()>60?24:15,win?d.accent:WHITE,Font.BOLD,FONT);
            c.text(MODES.get(n%4).label()+"  ·  "+(n%9)+" eliminations  ·  "+(4+n%17)+":"+String.format("%02d",n*37%60),b.x()+150,b.y()+b.height()/2-7,12,MUTED,Font.PLAIN,FONT);
            if(b.height()>60)c.text("Squad MVP · +"+(300+n*10)+" XP",b.x()+34,b.y()+46,11,GREEN,Font.BOLD,FONT);
        });
    }

    private static void settings(D d){
        var g=d.g;var ui=d.ui;float x=d.w-470;
        g.fill(0x80040824,0,0,d.w,d.h);ui.hit("settings-scrim","settings",0,0,x,d.h);
        g.shadow(0xA0040824,30,-8,0,x,0,470,d.h,0);g.fill(0xFF101848,x,0,470,d.h);g.fill(d.accent,x,0,4,d.h);
        d.title("SETTINGS",x+28,24,30,WHITE);
        ui.form("settings-form",new UiRect(x+24,84,422,d.h-150),form->form
            .section("Player").text("Callsign","callsign","Your name","Rowan")
            .section("Display").toggle("Animated background","animated",true).toggle("Reduce motion","reduce-motion",false).color("Accent colour","accent",GOLD,false)
            .section("Audio").slider("Music","music",0,100,60).slider("Effects","effects",0,100,80)
            .section("Controls").keybind("Ready up","key-ready",GLFW.GLFW_KEY_SPACE).keybind("Emote","key-emote",GLFW.GLFW_KEY_B).keybind("Ping","key-ping",UiKeyNames.mouse(2),true)
            .note("Settings here only change this showcase."));
        ui.button("settings-done","Done","settings",x+24,d.h-52,422,36);
    }
}
