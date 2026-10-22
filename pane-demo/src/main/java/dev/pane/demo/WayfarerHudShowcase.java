package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.Heightmap;

/** Original, material-rich HUD composition. Live recipe with separate opt-in capture fixtures. */
final class WayfarerHudShowcase {
    static final int INK=0xFF081725, PAPER=0xFFE8D9B4, LIGHT=0xFFF5F6F0,
        GOLD=0xFFFFD357, EDGE=0xFF314550, GREEN=0xFF84B59A, RED=0xFFE19B4C;
    static final String FONT=ExampleFonts.FAMILY;
    static final Identifier PAPER_TEXTURE=Identifier.fromNamespaceAndPath("pane_demo","textures/bramble/parchment.png"),
        METAL_TEXTURE=Identifier.fromNamespaceAndPath("pane_demo","textures/wayfarer-hud/metal.jpg"),
        FABRIC_TEXTURE=Identifier.fromNamespaceAndPath("pane_demo","textures/wayfarer-hud/fabric.jpg");
    private UiHud location, journal, vitals, belt, compass, badge, crosshair;
    private int stage,ticks;private long deadline,started;private boolean done;
    private volatile boolean prepared; private volatile float captureYaw;
    float displayedHeading;
    private HudNativeAuditTest audit;
    private HudRestorationAuditTest restoration;
    private HudPlacementAuditTest placementAudit;
    private HudQualityAuditTest qualityAudit;
    private NativeTextAuditTest fontAudit;
    private HudEfficiencyAuditTest efficiencyAudit;
    UiHud belt(){return belt;}
    UiHud compassHud(){return compass;}
    void init(){
        if(Boolean.getBoolean("pane.demo.nativeTextTest"))fontAudit=new NativeTextAuditTest(()->setEnabled(false));
        if(Boolean.getBoolean("pane.demo.hudRestorationTest")){restoration=new HudRestorationAuditTest(this);restoration.installBefore();}
        if(Boolean.getBoolean("pane.demo.hudNativeTest"))audit=new HudNativeAuditTest(this);
        if(Boolean.getBoolean("pane.demo.hudEfficiencyTest"))efficiencyAudit=new HudEfficiencyAuditTest(this);
        if(Boolean.getBoolean("pane.demo.hudQualityTest"))qualityAudit=new HudQualityAuditTest();
        if(Boolean.getBoolean("pane.demo.hudPlacementTest"))placementAudit=new HudPlacementAuditTest(this);
        var theme=UiTheme.dark().withAccent(GOLD);
        location=hud("location",310,106,theme,this::location);
        journal=hud("journal",290,228,theme,this::journal);
        vitals=hud("vitals",350,160,theme,this::vitals);
        belt=hud("belt",580,152,theme,this::belt);
        compass=hud("compass",400,80,theme,this::compass);
        compass.setMotionScale(1);
        badge=hud("badge",226,140,theme,this::badge);
        crosshair=hud("crosshair",24,24,theme,ui->{var g=ui.canvas();g.strokeCircle(0xAA30251E,3,12,12,4);g.strokeCircle(LIGHT,1,12,12,4);g.circle(LIGHT,12,12,1);});
        setHudScale(1);
        belt.replaceVanilla(VanillaHudElements.HOTBAR, VanillaHudElements.INFO_BAR,
            VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP);
        vitals.replaceVanilla(VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR,
            VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR);
        crosshair.replaceVanilla(VanillaHudElements.CROSSHAIR);

    }
    List<UiHud> huds(){return List.of(location,journal,vitals,belt,compass,badge,crosshair);}
    void setEnabled(boolean enabled){for(var hud:huds())hud.setEnabled(enabled);}
    void toggle(){setEnabled(!belt.enabled());}
    void close(){for(var hud:huds())hud.close();}
    private UiHud hud(String id,int w,int h,UiTheme t,UiPainter p){var hud=new UiHud(w,h,t,p, "belt".equals(id) ? overlay -> {
            var player=Minecraft.getInstance().player;
            if(player!=null)for(int i=0;i<9;i++)overlay.item("equipment-"+i,player.getInventory().getItem(i));
        } : NativeOverlayRenderer.NONE).attach(Identifier.fromNamespaceAndPath("pane_demo","wayfarer_hud_"+id));hud.setMotionScale(0);hud.setRenderSettings(UiRenderSettings.highQuality());return hud;}
    /** Live recipe: observes the chosen world; never stages a scene or modifies player data. */
    void tickLive(Minecraft client) {
        if (client.player != null && client.level != null) sample(client);
    }

    void tick(Minecraft c){
        if(stage==9){fontAudit.tick(c);return;}
        if(stage==8){efficiencyAudit.tick(c);return;}
        if(stage==7){if(c.player!=null&&c.level!=null)sample(c);qualityAudit.tick(c);return;}
        if(stage==6){if(c.player!=null&&c.level!=null)sample(c);placementAudit.tick(c);return;}
        if(stage==5){if(c.player!=null&&c.level!=null){sample(c);}restoration.tick(c);return;}
        if(stage==4){if(c.player!=null&&c.level!=null){sample(c);}audit.tick(c);return;}
        if(done){if(Boolean.getBoolean("pane.demo.wayfarerHudKeepOpen")&&c.player!=null){sample(c);}return;}
        try{
            if(started==0)started=System.nanoTime();
            if(System.nanoTime()-started>180_000_000_000L)throw new IllegalStateException("HUD capture timeout");
            if(stage==0){
                if(!(c.gui.screen() instanceof TitleScreen)||c.gui.overlay()!=null)return;
                c.options.renderDistance().set(10);c.options.simulationDistance().set(5);c.options.fov().set(75);
                c.options.pauseOnLostFocus=false;c.getWindow().setWindowed(1440,900);
                if(c.getLevelSource().levelExists("wayfarerHud_preview")) c.createWorldOpenFlows().openWorld("wayfarerHud_preview",()->{});
                else c.createWorldOpenFlows().createFreshLevel("wayfarerHud_preview",
                    new LevelSettings("Wayfarer HUD fixture", GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false), true, WorldDataConfiguration.DEFAULT),
                    new WorldOptions(2784L,false,false), WorldPresets::createNormalWorldDimensions,null);stage=1;return;
            }
            if(c.player==null||c.level==null||c.gui.overlay()!=null)return;
            if(stage==1){
                c.gui.setScreen(null);var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
                server.execute(()->{
                    var p=server.getPlayerList().getPlayer(uuid);if(p==null)return;
                    var world=server.overworld();int x=80,z=80,y=world.getMinY();
                    // Find a natural canopy overlook in the isolated save, without changing terrain.
                    for(int sx=16;sx<=144;sx+=2)for(int sz=16;sz<=144;sz+=2){
                        world.getChunk(sx>>4,sz>>4);
                        int top=world.getHeight(Heightmap.Types.MOTION_BLOCKING,sx,sz);
                        if(top>y){x=sx;z=sz;y=top;}
                    }
                    int lowest=Integer.MAX_VALUE;float yaw=135;
                    for(int angle=0;angle<360;angle+=45){
                        double rad=Math.toRadians(angle);int tx=x-(int)(Math.sin(rad)*48),tz=z+(int)(Math.cos(rad)*48);
                        world.getChunk(tx>>4,tz>>4);int top=world.getHeight(Heightmap.Types.MOTION_BLOCKING,tx,tz);
                        if(top<lowest){lowest=top;yaw=angle;}
                    }
                    if(y<world.getMinY()+8)throw new IllegalStateException("Scenic chunk has no valid surface: "+y);
                    System.out.println("[WayfarerHudCapture] staged viewpoint "+x+" / "+y+" / "+z);
                    captureYaw=yaw;p.teleportTo(world,x+.5,y,z+.5,Set.of(),yaw,8,false);
                    p.setGameMode(GameType.SURVIVAL);p.setHealth(18);p.getFoodData().setFoodLevel(16);
                    p.setExperienceLevels(12);p.setExperiencePoints(30);
                    p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
                    p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.IRON_LEGGINGS));
                    p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.LEATHER_BOOTS));
                    Item[] items={Items.IRON_SWORD,Items.IRON_PICKAXE,Items.IRON_AXE,Items.BOW,Items.BREAD,Items.TORCH,Items.OAK_LOG,Items.COMPASS,Items.SPYGLASS};
                    int[] counts={1,1,1,1,12,48,32,1,1};
                    for(int i=0;i<9;i++)p.getInventory().setItem(i,new ItemStack(items[i],counts[i]));
                    p.getInventory().setSelectedSlot(0);p.inventoryMenu.broadcastChanges();
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set 1000");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"weather clear");
                    prepared=true;
                });stage=2;deadline=System.nanoTime()+18_000_000_000L;
            }
            if(stage==2&&prepared){
                c.player.setYRot(captureYaw);c.player.setXRot(8);
                c.options.keyUp.setDown(false);c.options.keyDown.setDown(false);c.options.keyLeft.setDown(false);c.options.keyRight.setDown(false);c.options.keyJump.setDown(false);
            }
            sample(c);
            if(stage==2&&prepared&&System.nanoTime()>deadline&&belt.snapshot().frames()>30 && belt.nativeItemCount()==9){
                if(fontAudit!=null){stage=9;return;}
                if(efficiencyAudit!=null){stage=8;return;}
                if(qualityAudit!=null){stage=7;return;}
                if(placementAudit!=null){stage=6;return;}
                if(restoration!=null){stage=5;return;}
                if(audit!=null){stage=4;return;}
                if(c.gui.screen()!=null)throw new IllegalStateException("Capture must be in gameplay, not a screen");
                Screenshot.grab(c.gameDirectory,"wayfarerHud-hud.png",c.gameRenderer.mainRenderTarget(),1,m->{});
                System.out.println("[WayfarerHudCapture] actual world="+c.level.dimension()+" screen=null; native item count="+belt.nativeItemCount()+" HUD surfaces="+belt.snapshot().hasSurface());
                stage=3;deadline=System.nanoTime()+1_000_000_000L;
            }
            if(stage==3&&System.nanoTime()>deadline){done=true;System.out.println("[WayfarerHudCapture] complete; single-frame showcase, not a full gameplay audit");if(!Boolean.getBoolean("pane.demo.wayfarerHudKeepOpen"))c.stop();}
        }catch(Throwable e){done=true;e.printStackTrace();System.out.println("[WayfarerHudCapture] FAIL "+e);c.stop();}
    }
    void setHudScale(float value){
        if(!Float.isFinite(value)||value<.5F||value>2)throw new IllegalArgumentException("HUD scale must be .5..2");
        String[] names={"location","journal","vitals","belt","compass","badge","crosshair"};
        var widgets=huds();
        for(int i=0;i<names.length;i++){
            String name=names[i];
            widgets.get(i).setPlacementPolicy(viewport->WayfarerHudLayout.placement(name,viewport,value));
        }
    }
    private void sample(Minecraft c){
        compass.setValue("heading", (double)c.player.getYRot()+180);
        if(ticks++%5!=0)return;var p=c.player;var at=p.blockPosition();
        String biome=c.level.getBiome(at).unwrapKey().map(k->k.identifier().getPath().replace('_',' ')).orElse("Wilderness");
        List<Integer> counts=new ArrayList<>();for(int i=0;i<9;i++)counts.add(p.getInventory().getItem(i).getCount());
        var live=new HudShowcaseState.Live(Math.round(p.getHealth()),Math.round(p.getMaxHealth()),p.getFoodData().getFoodLevel(),p.getArmorValue(),p.experienceLevel,p.getInventory().getSelectedSlot(),p.getMainHandItem().getHoverName().getString(),biome,at.getX(),at.getY(),at.getZ(),0,List.copyOf(counts));
        HudShowcaseState.apply(huds(),live,false);
    }
    private void text(UiCanvas g,String s,float x,float y,float size,int color,boolean bold){g.text(s,x,y,size,color,bold?Font.BOLD:Font.PLAIN,FONT);}
    private void label(UiCanvas g,String s,float x,float y,float size,int color){g.trackedText(s,x,y,size,color,Font.BOLD,FONT,1.3F);}
    private void paper(UiCanvas g,float x,float y,float w,float h){g.minecraftTextureRegion(PAPER_TEXTURE,0,0,640,480,x,y,w,h);}
    private void metal(UiCanvas g,float x,float y,float w,float h){
        g.roundRect(0xAA050F17,x+2,y+4,w,h,3);g.roundRect(INK,x,y,w,h,3);
        g.withRoundedClip(x+1,y+1,w-2,h-2,2,()->g.minecraftTextureRegion(METAL_TEXTURE,280,100,1400,Math.min(1900,Math.round(1400*h/w)),x+1,y+1,w-2,h-2));
        g.strokeRoundRect(0xFF8B8C75,1,x,y,w,h,3);
        g.line(0x80DFD9B8,1,x+5,y+2,x+w-6,y+2);g.line(0xC005121C,2,x+3,y+h-3,x+w-4,y+h-3);
    }
    private void canvas(UiCanvas g,float x,float y,float w,float h){
        g.roundRect(INK,x,y,w,h,3);
        g.withRoundedClip(x+2,y+2,w-4,h-4,2,()->{
            g.minecraftTextureRegion(FABRIC_TEXTURE,100,200,Math.round(w*5),Math.round(h*5),x+2,y+2,w-4,h-4);
            g.fill(0x48081725,x+2,y+2,w-4,h-4);
        });
        for(float xx=x+7;xx<x+w-7;xx+=7){g.line(0xAAE5D5AB,.8F,xx,y+5,xx+3,y+5);g.line(0xAAE5D5AB,.8F,xx,y+h-5,xx+3,y+h-5);}
    }
    private void screw(UiCanvas g,float x,float y){g.circle(INK,x,y+1,3);g.circle(0xFFAEAC91,x,y,2.5F);g.line(0xFF263239,1,x-1.5F,y+1,x+1.5F,y-1);g.line(LIGHT,.5F,x-1.4F,y-1.7F,x+.6F,y-2);}
    private void logo(UiCanvas g,float x,float y,float z,int c){
        g.path().moveTo(x,y+z*.3F).lineTo(x+z*.5F,y).lineTo(x+z,y+z*.3F).lineTo(x+z*.5F,y+z*.25F).close().stroke(c,1.4F);
        g.line(c,1.4F,x,y+z*.3F,x+z*.5F,y+z*.8F);g.line(c,1.4F,x+z,y+z*.3F,x+z*.5F,y+z*.8F);g.line(c,1.4F,x+z*.5F,y+z*.25F,x+z*.5F,y+z);
    }
    private void location(UiContext ui){var g=ui.canvas();var v=HudShowcaseState.location(ui);
        canvas(g,3,13,293,70);metal(g,10,7,284,68);screw(g,17,15);screw(g,287,15);
        logo(g,24,25,30,GOLD);label(g,"WAYFARER",68,21,20,LIGHT);label(g,"01 / FIRST LIGHT",69,49,9,GOLD);
        paper(g,44,75,250,28);text(g,"FIELD SECTOR  /  "+v.biome().toUpperCase(Locale.ROOT),58,83,9,INK,true);
    }
    private void journal(UiContext ui){var g=ui.canvas();
        canvas(g,24,17,250,198);paper(g,12,34,264,188);
        metal(g,18,8,258,40);g.fill(GOLD,27,18,3,20);label(g,"EXPEDITION 01",40,22,10,LIGHT);screw(g,265,28);
        label(g,"THE CLOUDLINE",34,62,16,INK);text(g,"Follow the old survey trail.",34,90,12,EDGE,false);
        g.line(0x88725A35,1,34,115,251,115);
        g.polygon(EDGE,41,128,46,135,41,142,36,135);g.polygon(GOLD,41,130,44,135,41,140,38,135);
        text(g,"Reach the ridge beacon",57,128,12,INK,true);
        text(g,"Survey landmarks",34,159,11,EDGE,false);text(g,"1 / 3",225,159,11,INK,true);
        for(int i=0;i<3;i++){g.fill(0xFF9D916E,34+i*74,179,66,4);if(i==0)g.fill(INK,34,179,66,4);}
        label(g,"FIELD NOTES",35,196,8,EDGE);text(g,"+400 XP",211,195,9,INK,true);
        // The expedition text is authored showcase content, not a gameplay objective system.
    }
    private void vitals(UiContext ui){var g=ui.canvas();var v=HudShowcaseState.vitals(ui);
        canvas(g,44,36,296,109);metal(g,73,33,265,103);screw(g,328,42);screw(g,328,127);
        label(g,"ROWAN",121,45,15,LIGHT);text(g,"PATHFINDER  /  MK. 01",122,68,8,0xFFD3D1B9,true);
        g.line(0x557F9AAB,1,121,84,317,84);
        label(g,"VITALS",122,95,8,LIGHT);label(g,"ARMOR",233,95,8,LIGHT);
        for(int i=0;i<10;i++)g.fill(i<v.health()/2?GOLD:0xFF13222B,122+i*9,111,6,9);
        text(g,Integer.toString(v.armor()),233,108,18,LIGHT,true);text(g,"/ 20",258,115,9,0xFFBBC7CC,false);
        // A mechanical dial with a paper face, etched tick marks and a physical needle.
        g.circle(INK,62,91,53);g.circle(0xFFA99D75,62,88,51);
        g.withCircleClip(62,88,48,()->g.minecraftTextureRegion(METAL_TEXTURE,420,250,700,700,14,40,96,96));
        g.strokeCircle(GOLD,1.2F,62,88,46);g.circle(INK,62,88,40);
        g.withCircleClip(62,88,38,()->g.minecraftTextureRegion(PAPER_TEXTURE,100,60,440,350,24,50,76,76));
        for(int i=0;i<=20;i++){double a=Math.toRadians(-135+i*13.5);float r=i%5==0?29:32;g.line(i<4?RED:EDGE,i%5==0?1.4F:.7F,62+(float)Math.sin(a)*r,88-(float)Math.cos(a)*r,62+(float)Math.sin(a)*36,88-(float)Math.cos(a)*36);}
        int pct=Math.round(100F*v.health()/Math.max(1,v.max()));double a=Math.toRadians(-135+270*pct/100F);
        g.line(INK,2,62,88,62+(float)Math.sin(a)*27,88-(float)Math.cos(a)*27);g.circle(INK,62,88,3);
        text(g,Integer.toString(pct),45,92,19,INK,true);text(g,"SUIT",51,73,8,EDGE,true);
        screw(g,28,53);screw(g,96,53);screw(g,28,123);screw(g,96,123);
        paper(g,116,135,219,24);label(g,"SUPPLIES",127,142,7,INK);
        for(int i=0;i<10;i++)g.fill(i*2<v.food()?EDGE:0x887D7562,209+i*10,142,7,7);
    }
    private void belt(UiContext ui){var g=ui.canvas();var v=HudShowcaseState.equipment(ui);
        canvas(g,5,48,570,62);metal(g,0,62,15,29);metal(g,565,62,15,29);
        for(int i=0;i<9;i++){float x=13+i*60;boolean selected=i==v.selected();
            metal(g,x,36,54,72);canvas(g,x+5,44,44,49);
            if(selected){g.strokeRoundRect(GOLD,2,x,36,54,72,2);g.fill(GOLD,x+3,38,48,3);g.polygon(GOLD,x+21,26,x+33,26,x+27,32);}
            screw(g,x+5,41);screw(g,x+49,103);
            paper(g,x+18,100,21,20);text(g,Integer.toString(i+1),x+25,104,10,INK,true);
            ui.nativeSlot("equipment-"+i,20+i*60,55,36,36);
            int count=v.counts().get(i);if(count>1)text(g,""+count,x+31,83,10,LIGHT,true);
        }
        String name=v.item().toUpperCase(Locale.ROOT);float tw=g.textWidth(name,11,Font.BOLD,FONT);
        metal(g,(580-tw-36)/2,0,tw+36,24);text(g,name,(580-tw)/2,6,11,LIGHT,true);
        g.line(INK,4,40,131,540,131);g.line(GOLD,2,40,131,320,131);
        g.circle(INK,290,130,13);g.circle(GOLD,290,130,11);text(g,""+v.level(),283,124,11,INK,true);
        text(g,"FIELD EQUIPMENT",38,137,7,LIGHT,true);text(g,"1 — 9   SELECT",476,137,7,LIGHT,true);
    }
    private void compass(UiContext ui){var g=ui.canvas();var v=HudShowcaseState.navigation(ui);
        float heading=ui.animateAngle("bearing",(float)ui.numberValue("heading",0),90,UiEasing.EASE_OUT);
        displayedHeading=heading;
        if(audit!=null)audit.heading(heading);
        canvas(g,36,4,328,55);metal(g,11,11,378,47);screw(g,20,20);screw(g,380,20);
        g.withRoundedClip(30,17,340,35,1,()->{
            g.minecraftTextureRegion(PAPER_TEXTURE,50,100,540,230,30,17,340,35);
            int first=(int)Math.floor((heading-90)/5)*5;
            for(int tick=first;tick<=heading+90;tick+=5){
                float x=200+(tick-heading)*2;int degree=Math.floorMod(tick,360);
                boolean major=degree%30==0;
                g.line(EDGE,major?1:.7F,x,48,x,major?37:43);
                if(major){String label=switch(degree){case 0->"N";case 90->"E";case 180->"S";case 270->"W";default->""+degree;};
                    text(g,label,x-g.textWidth(label,10,Font.BOLD,FONT)/2,21,10,INK,true);}
            }
        });
        g.polygon(GOLD,194,7,206,7,200,15);g.line(RED,1,200,17,200,51);
        paper(g,115,58,170,22);label(g,"ALT  "+v.y()+" m   /   OVERWORLD",128,65,7,INK);
    }
    private void badge(UiContext ui){var g=ui.canvas();
        canvas(g,10,20,206,86);metal(g,5,14,216,84);screw(g,13,22);screw(g,212,22);
        logo(g,20,34,34,GOLD);label(g,"GLIDER RIG",68,32,12,LIGHT);text(g,"STOWED / READY",69,52,9,GOLD,true);
        g.line(0x657F9AAB,1,20,73,204,73);text(g,"SPACE",20,81,8,LIGHT,true);text(g,"DEPLOY",141,81,8,LIGHT,true);
        paper(g,9,101,211,34);text(g,"PANELIB / WAYFARER HUD CONCEPT",22,110,8,INK,true);text(g,"UI EXAMPLE — NOT A REAL GAME",22,121,7,EDGE,false);
    }
}
