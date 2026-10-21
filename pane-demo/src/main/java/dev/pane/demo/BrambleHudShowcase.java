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
import net.minecraft.world.level.levelgen.Heightmap;

/** Original, material-rich HUD composition. Opt-in, isolated screenshot showcase only. */
final class BrambleHudShowcase {
    static final int INK=0xFF30251E, PAPER=0xFFF1DEAD, LIGHT=0xFFFFF0C8,
        GOLD=0xFFD6AB58, EDGE=0xFF79512F, GREEN=0xFF49744B, RED=0xFFB93E3E;
    static final String SERIF="Serif", FONT="bramble-roboto";
    static final Identifier PAPER_TEXTURE=Identifier.fromNamespaceAndPath("pane_demo","textures/bramble/parchment.png"),
        WOOD_TEXTURE=Identifier.fromNamespaceAndPath("pane_demo","textures/bramble/wood.jpg"),
        LEATHER_TEXTURE=Identifier.fromNamespaceAndPath("pane_demo","textures/bramble/leather.jpg");
    private UiHud location, journal, vitals, belt, compass, badge, crosshair;
    private int stage,ticks;private long deadline,started;private boolean done;
    private volatile boolean prepared;
    void init(){
        var font=getClass().getResourceAsStream("/assets/pane_demo/fonts/Roboto-Variable.ttf");
        if(font!=null)UiCanvas.registerFont(FONT,font);
        FieldHudDemo.setEnabled(false);
        var theme=UiTheme.dark().withAccent(GOLD);
        location=hud("location",292,106,theme,this::location);
        journal=hud("journal",272,228,theme,this::journal);
        vitals=hud("vitals",326,152,theme,this::vitals);
        belt=hud("belt",580,142,theme,this::belt);
        compass=hud("compass",350,64,theme,this::compass);
        badge=hud("badge",204,104,theme,this::badge);
        crosshair=hud("crosshair",24,24,theme,ui->{var g=ui.canvas();g.strokeCircle(0xAA30251E,3,12,12,4);g.strokeCircle(LIGHT,1,12,12,4);g.circle(LIGHT,12,12,1);});
        crosshair.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.CENTER,0,0,1));
        // Replace only the vanilla components represented by this opt-in showcase.
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
        } : NativeOverlayRenderer.NONE).attach(Identifier.fromNamespaceAndPath("pane_demo","bramble_"+id));hud.setMotionScale(0);return hud;}
    void tick(Minecraft c){
        if(done){if(Boolean.getBoolean("pane.demo.brambleKeepOpen")&&c.player!=null){sample(c);place(c);}return;}
        try{
            if(started==0)started=System.nanoTime();
            if(System.nanoTime()-started>180_000_000_000L)throw new IllegalStateException("HUD capture timeout");
            if(stage==0){
                if(!(c.gui.screen() instanceof TitleScreen)||c.gui.overlay()!=null)return;
                c.options.renderDistance().set(10);c.options.simulationDistance().set(5);c.options.fov().set(75);
                c.options.pauseOnLostFocus=false;c.getWindow().setWindowed(1440,900);
                if(!c.getLevelSource().levelExists("bramble_preview"))throw new IllegalStateException("Missing copied Bramble demo save");
                c.createWorldOpenFlows().openWorld("bramble_preview",()->{});stage=1;return;
            }
            if(c.player==null||c.level==null||c.gui.overlay()!=null)return;
            if(stage==1){
                c.gui.setScreen(null);var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
                server.execute(()->{
                    var p=server.getPlayerList().getPlayer(uuid);if(p==null)return;
                    var world=server.overworld();int x=80,z=80,y=world.getMinY();
                    // Find a natural canopy overlook in the isolated save, without changing terrain.
                    for(int sx=16;sx<=144;sx+=32)for(int sz=16;sz<=144;sz+=32){
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
                    System.out.println("[BrambleCapture] staged viewpoint "+x+" / "+y+" / "+z);
                    p.teleportTo(world,x+.5,y,z+.5,Set.of(),yaw,13,false);
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
            sample(c);place(c);
            if(stage==2&&prepared&&System.nanoTime()>deadline&&belt.snapshot().frames()>30 && belt.nativeItemCount()==9){
                if(c.gui.screen()!=null)throw new IllegalStateException("Capture must be in gameplay, not a screen");
                Screenshot.grab(c.gameDirectory,"bramble-hud.png",c.gameRenderer.mainRenderTarget(),1,m->{});
                System.out.println("[BrambleCapture] actual world="+c.level.dimension()+" screen=null; native item count="+belt.nativeItemCount()+" HUD surfaces="+belt.snapshot().hasSurface());
                stage=3;deadline=System.nanoTime()+1_000_000_000L;
            }
            if(stage==3&&System.nanoTime()>deadline){done=true;System.out.println("[BrambleCapture] complete; single-frame showcase, not a full gameplay audit");if(!Boolean.getBoolean("pane.demo.brambleKeepOpen"))c.stop();}
        }catch(Throwable e){done=true;e.printStackTrace();System.out.println("[BrambleCapture] FAIL "+e);c.stop();}
    }
    private void place(Minecraft c){
        float width=c.getWindow().getScreenWidth();
        location.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.TOP_LEFT,24,24,1));
        journal.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.TOP_RIGHT,24,24,1));
        vitals.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.BOTTOM_LEFT,24,25,1));
        belt.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.BOTTOM_LEFT,(width-580)/2,22,1));
        badge.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.BOTTOM_RIGHT,24,28,1));
        compass.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.TOP_LEFT,(width-350)/2,22,1));
    }
    private void sample(Minecraft c){
        if(ticks++%5!=0)return;var p=c.player;var at=p.blockPosition();
        String biome=c.level.getBiome(at).unwrapKey().map(k->k.identifier().getPath().replace('_',' ')).orElse("Wilderness");
        List<Integer> counts=new ArrayList<>();for(int i=0;i<9;i++)counts.add(p.getInventory().getItem(i).getCount());
        var live=new HudShowcaseState.Live(Math.round(p.getHealth()),Math.round(p.getMaxHealth()),p.getFoodData().getFoodLevel(),p.getArmorValue(),p.experienceLevel,p.getInventory().getSelectedSlot(),p.getMainHandItem().getHoverName().getString(),biome,at.getX(),at.getY(),at.getZ(),Math.floorMod(Math.round(p.getYRot()+180),360),List.copyOf(counts));
        HudShowcaseState.apply(huds(),live,true);
    }
    private void text(UiCanvas g,String s,float x,float y,float size,int color,boolean bold){g.text(s,x,y,size,color,bold?Font.BOLD:Font.PLAIN,FONT);}
    private void serif(UiCanvas g,String s,float x,float y,float size,int color){g.text(s,x,y,size,color,Font.BOLD,SERIF);}
    private void paper(UiCanvas g,float x,float y,float w,float h){
        // Keep the entire scanned sheet, including its transparent torn edges and stained folds.
        g.minecraftTextureRegion(PAPER_TEXTURE,0,0,640,480,x,y,w,h);
    }
    private void rivet(UiCanvas g,float x,float y){
        g.circle(INK,x,y+1,3.8F);g.circle(GOLD,x,y,3.2F);
        g.withCircleClip(x,y,2.8F,()->{
            g.minecraftTextureRegion(WOOD_TEXTURE,700,900,70,70,x-3,y-3,6,6);
            g.circle(0x70D6AB58,x,y,3);
        });
        g.line(0xFFC6AF76,.8F,x-1.6F,y-1.5F,x+1,y-2);g.line(INK,.8F,x-1,y+1,x+1,y-1);
    }
    private void wood(UiCanvas g,float x,float y,float w,float h,int offset){
        g.roundRect(INK,x,y+2,w,h,2);
        g.withRoundedClip(x+1,y,w-2,h-2,2,()->g.minecraftTextureRegion(WOOD_TEXTURE,offset,240,Math.round(w*3),Math.round(h*3),x+1,y,w-2,h-2));
        g.line(0xAAE5BB78,1,x+2,y+1,x+w-3,y+1);g.line(0xCC231A12,2,x+2,y+h-3,x+w-2,y+h-3);
    }
    private void leather(UiCanvas g,float x,float y,float w,float h){
        g.roundRect(0x8030251E,x+2,y+5,w,h,5);g.roundRect(INK,x,y,w,h,5);
        g.withRoundedClip(x+2,y+2,w-4,h-4,3,()->g.minecraftTextureRegion(LEATHER_TEXTURE,50,220,Math.round(w*3),Math.round(h*3),x+2,y+2,w-4,h-4));
        for(float a=x+9;a<x+w-7;a+=7){
            g.line(0xCC271D14,2,a,y+7,a+3,y+9);g.line(0xFFD6BD8B,.9F,a,y+6,a+3,y+8);
            g.line(0xCC271D14,2,a,y+h-7,a+3,y+h-5);g.line(0xFFD6BD8B,.9F,a,y+h-8,a+3,y+h-6);
        }
        for(float a=y+14;a<y+h-11;a+=7){g.line(0xFFD6BD8B,.9F,x+6,a,x+8,a+3);g.line(0xFFD6BD8B,.9F,x+w-8,a,x+w-6,a+3);}
    }
    private void leaf(UiCanvas g,float x,float y,float size,float angle){
        g.withTransform(UiTransform.identity().rotate(angle),x,y,()->{
            g.path().moveTo(x,y).curveTo(x-size,y-size,x-size/2,y-size*2,x,y-size*2.4F).curveTo(x+size,y-size*2,x+size,y-size,x,y).close().fill(GREEN);
            g.line(0xFFB3C37A,1,x,y,x,y-size*2.1F);
        });
    }
    private void location(UiContext ui){var g=ui.canvas();var v=HudShowcaseState.location(ui);
        paper(g,42,15,244,77);g.fill(RED,57,12,3,72);
        text(g,"BRAMBLE  /  FIELD ATLAS",73,27,9,EDGE,true);
        String biome=v.biome().substring(0,1).toUpperCase()+v.biome().substring(1);
        serif(g,biome,72,42,23,INK);text(g,v.x()+"  /  "+v.y()+"  /  "+v.z()+"     OVERWORLD",73,72,9,EDGE,false);
        g.circle(INK,35,52,31);
        g.withCircleClip(35,51,29,()->g.minecraftTextureRegion(WOOD_TEXTURE,200,500,220,220,6,22,58,58));
        g.strokeCircle(GOLD,1,35,51,29);g.circle(INK,35,51,24);
        g.withCircleClip(35,51,23,()->g.minecraftTextureRegion(PAPER_TEXTURE,90,60,440,340,12,28,46,46));
        for(int i=0;i<16;i++){double a=i*Math.PI/8;g.line(EDGE,1,35+(float)Math.sin(a)*20,51+(float)Math.cos(a)*20,35+(float)Math.sin(a)*23,51+(float)Math.cos(a)*23);}
        g.polygon(RED,35,30,42,57,35,52,28,57);g.polygon(INK,35,72,42,48,35,52,28,48);g.circle(GOLD,35,51,3);
        leaf(g,17,93,9,-40);leaf(g,15,88,8,-75);
    }
    private void journal(UiContext ui){var g=ui.canvas();
        paper(g,17,26,244,192);leather(g,34,12,211,34);rivet(g,45,28);rivet(g,233,28);
        text(g,"THE FIELD JOURNAL",63,23,12,LIGHT,true);
        g.polygon(RED,218,14,236,14,236,77,227,68,218,77);
        text(g,"01   /   A FRESH START",36,62,9,EDGE,true);serif(g,"Into the wild",35,83,25,INK);
        text(g,"Every good trail starts somewhere.",36,116,11,EDGE,false);
        UiIcon.CHECKED_BOX.draw(g,GREEN,34,139,15,1.4F);text(g,"Pack your tools",57,140,12,INK,false);
        UiIcon.BOX.draw(g,EDGE,34,165,15,1.4F);text(g,"Find a place to call home",57,166,12,INK,true);
        g.line(0x556E5435,1,36,190,241,190);text(g,"EXAMPLE OBJECTIVE",36,198,8,EDGE,true);
        leaf(g,258,207,11,30);leaf(g,250,207,8,-20);
    }
    private void vitals(UiContext ui){var g=ui.canvas();var v=HudShowcaseState.vitals(ui);
        paper(g,62,40,253,88);leather(g,73,25,211,34);rivet(g,275,41);
        text(g,"TRAILKEEPER",112,35,12,LIGHT,true);
        text(g,"HEALTH",113,67,8,EDGE,true);serif(g,v.health()+" / "+v.max(),112,80,24,INK);
        g.fill(EDGE,205,74,1,32);text(g,"ARMOR",223,67,8,EDGE,true);
        for(int i=0;i<5;i++){float x=219+i*15;g.polygon(i*4<v.armor()?0xFF6C7E81:0xFFBBA77E,x,86,x+11,86,x+10,96,x+5.5F,100,x+1,96);g.line(LIGHT,.7F,x+2,88,x+9,88);}
        // A corked glass flask, with a visible liquid meniscus and reflected light.
        g.roundRect(INK,40,15,37,27,4);wood(g,44,15,29,17,1100);
        for(int i=0;i<4;i++)g.line(EDGE,1,48+i*6,18,48+i*6,28);
        leather(g,38,30,40,16);g.circle(INK,58,82,46);
        g.withCircleClip(58,80,43,()->g.minecraftTextureRegion(WOOD_TEXTURE,400,800,300,300,15,37,86,86));
        g.strokeCircle(GOLD,1,58,80,42);g.circle(0xFF465B4D,58,80,37);
        rivet(g,25,52);rivet(g,91,52);rivet(g,28,111);rivet(g,88,111);
        g.withCircleClip(58,80,34,()->{
            float liquidY=48+64*(1-Math.min(1,v.health()/(float)Math.max(1,v.max())));
            g.gradientRect(0xFFED6C58,0xFF841F36,90,23,liquidY,70,70,0);
            g.path().moveTo(21,liquidY+2).curveTo(45,liquidY-4,67,liquidY+7,95,liquidY).stroke(0xFFFFAF88,2);
            g.circle(0x30FFF2BF,43,66,11);g.line(0xAAFFF9D7,4,35,65,32,83);
        });
        serif(g,Integer.toString(v.health()),45,82,26,LIGHT);g.strokeCircle(0xFFB39152,1,58,80,39);
        leaf(g,29,127,10,-65);leaf(g,37,135,11,-35);
        text(g,"PROVISIONS",112,119,8,LIGHT,true);
        for(int i=0;i<10;i++){float x=181+i*12;g.line(EDGE,3,x,126,x+5,117);g.line(i*2<v.food()?GOLD:0xFF716751,2,x,125,x+4,117);g.line(i*2<v.food()?LIGHT:EDGE,2,x+1,121,x-2,117);}
    }
    private void belt(UiContext ui){var g=ui.canvas();var v=HudShowcaseState.equipment(ui);
        // Nine separate stitched pouches on a continuous leather belt, not a rectangular panel.
        leather(g,4,46,570,63);g.roundRect(GOLD,0,62,17,29,3);g.roundRect(EDGE,4,67,9,19,2);
        g.roundRect(GOLD,561,62,17,29,3);g.roundRect(EDGE,565,67,9,19,2);
        for(int i=0;i<9;i++){float x=13+i*60;boolean selected=i==v.selected();
            wood(g,x,36,54,72,80+i*170);
            leather(g,x+6,44,42,51);g.roundRect(0x55302316,x+8,48,38,43,2);
            if(selected)g.strokeRoundRect(GOLD,2,x,36,54,72,3);
            rivet(g,x+5,41);rivet(g,x+49,41);
            g.polygon(EDGE,x+3,38,x+15,38,x+3,50);g.polygon(GOLD,x+51,38,x+41,38,x+51,48);
            paper(g,x+18,99,21,21);text(g,Integer.toString(i+1),x+25,103,10,INK,true);
            ui.nativeSlot("equipment-"+i,20+i*60,55,36,36);
            int count=v.counts().get(i);if(count>1)text(g,""+count,x+31,83,10,LIGHT,true);
            if(selected){g.polygon(GOLD,x+17,26,x+27,32,x+37,26);g.line(LIGHT,2,x+5,111,x+48,111);}
        }
        float tw=g.textWidth(v.item(),12,Font.BOLD,FONT);paper(g,(580-tw-36)/2,0,tw+36,25);text(g,v.item(),(580-tw)/2,6,12,INK,true);
        g.line(INK,5,34,130,548,130);g.line(GREEN,3,34,130,362,130);
        g.circle(INK,290,130,13);g.circle(GOLD,290,129,11);text(g,""+v.level(),283,123,11,INK,true);
    }
    private void compass(UiContext ui){var g=ui.canvas();var v=HudShowcaseState.navigation(ui);
        paper(g,10,6,330,47);g.line(EDGE,1,27,36,325,36);
        for(int offset=-60;offset<=60;offset+=15){float x=175+offset*2.2F;int deg=Math.floorMod(v.heading()+offset,360);
            g.line(EDGE,1,x,32,x,offset%30==0?23:28);
            if(offset%30==0)text(g,""+deg,x-8,12,9,EDGE,true);
        }
        g.polygon(RED,169,39,181,39,175,47);g.circle(GOLD,175,52,3);
        rivet(g,20,15);rivet(g,330,15);
    }
    private void badge(UiContext ui){var g=ui.canvas();
        paper(g,10,30,185,60);wood(g,4,6,190,31,900);rivet(g,15,21);rivet(g,184,21);
        serif(g,"BRAMBLE",39,8,22,LIGHT);text(g,"A PANELIB HUD EXAMPLE",28,44,9,EDGE,true);
        g.line(0x446D4F2A,1,25,60,179,60);text(g,"E   INVENTORY",27,70,10,INK,true);leaf(g,193,93,9,45);
    }
}
