package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A field kit over the running world: the live player model, real item icons and Minecraft's own
 * font, laid out with PaneLib controls in a panel that leaves the world visible beside it.
 */
public final class InWorldShowcase {
    private static final int PANEL=0xEE15171B, RIM=0x26FFFFFF, SLOT=0xFF0E1013, SLOT_RIM=0x1FFFFFFF;
    private static final int INK=0xFFF2F0EA, MUTED=0xFF9EA39A, FAINT=0xFF6E736B, ACCENT=0xFF7CC66E;
    private static final String FONT=ExampleFonts.FAMILY;
    private static final List<ItemStack> KIT=List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.IRON_PICKAXE),
        new ItemStack(Items.BOW), new ItemStack(Items.SHIELD), new ItemStack(Items.TORCH,48), new ItemStack(Items.COOKED_BEEF,12),
        new ItemStack(Items.OAK_PLANKS,64), new ItemStack(Items.COMPASS), new ItemStack(Items.ARROW,32));
    private static final String[] NAMES={"Diamond Sword","Iron Pickaxe","Bow","Shield","Torch","Steak","Oak Planks","Compass","Arrow"};
    /** Places a player might mark: name, marker colour and block position. */
    private record Waypoint(String name, int color, int x, int y, int z) { }
    private static final List<Waypoint> WAYPOINTS=List.of(new Waypoint("Home",0xFF7CC66E,-112,71,240),
        new Waypoint("Iron vein",0xFFD9D4C7,-64,32,190), new Waypoint("Village",0xFFE0B65A,96,68,320),
        new Waypoint("Ravine",0xFF8FB4E8,-220,54,118), new Waypoint("Nether portal",0xFFB48AE8,-140,70,262));

    private InWorldShowcase() {}

    public static UiScreen createScreen() {
        var doc=UiDocument.builder("Field kit").shell(UiShell.NONE).theme(UiTheme.dark())
            .scaling(UiScaling.fixed().reference(1000,640).adaptivity(1).range(.25F,8)).renderSettings(UiRenderSettings.typography())
            .page(new UiPage("kit","Field kit","",ACCENT,InWorldShowcase::paint)).build();
        NativeOverlayRenderer overlay=o->{
            var player=Minecraft.getInstance().player;
            if(player!=null)o.entity("player",player);
            for(int i=0;i<KIT.size();i++){
                o.item("slot"+i,KIT.get(i));
                if(KIT.get(i).getCount()>1)o.text("count"+i,Integer.toString(KIT.get(i).getCount()),
                    UiMinecraftTextStyle.hud().alignment(UiMinecraftTextStyle.Alignment.RIGHT));
            }
        };
        return UiScreen.create(doc,overlay,(s,event)->{
            if(event.type()==UiEventType.ACTION&&event.key().startsWith("select:")){s.setValue("selected",(double)Integer.parseInt(event.key().substring(7)));s.invalidate();}
        },UiScreenOptions.builder().backdrop(UiBackdrop.NONE).build());
    }

    private static void paint(UiContext ui) {
        var g=ui.canvas();
        float w=ui.width(), h=ui.height();
        // The panel ends above the hotbar, hearts and hunger, which stay in view beneath it.
        float pw=Math.min(450,w*.45F), px=w-pw-24, py=22, ph=Math.min(512,h*.8F-py);
        // A soft fade from the right edge keeps the panel readable without hiding the world.
        g.gradientRect(0x00000000,0x8C000000,0,w*.45F,0,w*.55F,h,0);
        g.shadow(0x66000000,24,0,8,px,py,pw,ph,14);
        g.roundRect(PANEL,px,py,pw,ph,14);
        g.strokeRoundRect(RIM,1,px+.5F,py+.5F,pw-1,ph-1,14);
        float x=px+22, y=py+20, cw=pw-44;
        g.text("FIELD KIT",x,y,10,ACCENT,Font.BOLD,FONT);
        var player=Minecraft.getInstance().player;
        String name=player==null?"Explorer":player.getName().getString();
        g.text(name,x,y+15,23,INK,Font.BOLD,FONT);
        g.text(place(),x,y+46,12,MUTED,Font.PLAIN,FONT);
        String tab=ui.tabs("tabs","tab",List.of(new UiSelectOption("loadout","Loadout"),new UiSelectOption("waypoints","Waypoints"),
            new UiSelectOption("settings","Settings")),x,y+72,cw,30,"loadout");
        float top=y+118;
        switch(tab){
            case "waypoints" -> waypoints(ui,x,top,cw,py+ph-24-top);
            case "settings" -> settings(ui,x,top,cw);
            default -> loadout(ui,x,top,cw,py+ph-24-top);
        }
    }

    private static void loadout(UiContext ui, float x, float y, float w, float h) {
        var g=ui.canvas();
        float cell=Math.min(62,(w-130-18-16)/3), pw=w-3*cell-16-18, ph=3*cell+16;
        g.roundRect(SLOT,x,y,pw,ph,10);
        g.strokeRoundRect(SLOT_RIM,1,x+.5F,y+.5F,pw-1,ph-1,10);
        g.gradientRect(0x00000000,0x337CC66E,90,x,y+ph*.55F,pw,ph*.45F,10);
        ui.nativeSlot("player",x+12,y+12,pw-24,ph-20);
        float gx=x+w-3*cell-16;
        int selected=(int)Math.round(ui.numberValue("selected",0));
        for(int i=0;i<KIT.size();i++){
            float sx=gx+(i%3)*(cell+8), sy=y+(i/3)*(cell+8);
            boolean on=i==selected, hover=ui.hovered("slot-hit"+i);
            g.roundRect(on?0xFF1E2A1C:hover?0xFF171B1F:SLOT,sx,sy,cell,cell,8);
            g.strokeRoundRect(on?ACCENT:SLOT_RIM,on?1.5F:1,sx+.5F,sy+.5F,cell-1,cell-1,8);
            ui.hit("slot-hit"+i,"select:"+i,sx,sy,cell,cell);
            ui.tooltip("slot-hit"+i,NAMES[i]);
            ui.nativeSlot("slot"+i,sx+cell*.18F,sy+cell*.18F,cell*.64F,cell*.64F);
            ui.nativeSlot("count"+i,sx+4,sy+cell-15,cell-8,10);
        }
        float by=y+ph+16;
        g.text(NAMES[selected],x,by,16,INK,Font.BOLD,FONT);
        g.text(detail(selected),x,by+22,12,MUTED,Font.PLAIN,FONT);
        bar(g,"Durability",x,by+48,w,selected<4?.82F:1,ACCENT);
        bar(g,"Hunger",x,by+70,w,.9F,0xFFE0B65A);
        float ry=by+102;
        ui.toggle("autosort","autosort",x,ry+5,true);
        g.text("Auto-sort",x+52,ry+9,13,INK,Font.PLAIN,FONT);
        ui.button("equip","Equip kit","equip",x+w-112,ry,112,34,UiContext.ButtonStyle.PRIMARY,UiContext.Shape.ROUNDED);
        ui.button("reset","Reset","reset",x+w-206,ry,86,34,UiContext.ButtonStyle.SECONDARY,UiContext.Shape.ROUNDED);
    }

    private static void waypoints(UiContext ui, float x, float y, float w, float h) {
        var g=ui.canvas();
        g.text("Sorted by",x,y+9,12,MUTED,Font.PLAIN,FONT);
        String sort=ui.dropdown("sort","sort",x+72,y,170,32,"near",List.of(new UiSelectOption("near","Nearest first"),
            new UiSelectOption("name","Name"),new UiSelectOption("recent","Recently added")));
        var rows=new java.util.ArrayList<>(WAYPOINTS);
        if(sort.equals("near"))rows.sort(java.util.Comparator.comparingDouble(InWorldShowcase::distance));
        else if(sort.equals("name"))rows.sort(java.util.Comparator.comparing(Waypoint::name));
        float ry=y+46;
        for(int i=0;i<rows.size();i++){
            var p=rows.get(i);
            boolean hover=ui.hovered("wp"+i);
            g.roundRect(hover?0xFF1B2026:SLOT,x,ry,w,46,9);
            g.strokeRoundRect(SLOT_RIM,1,x+.5F,ry+.5F,w-1,45,9);
            g.roundRect(p.color(),x+15,ry+17,12,12,2);
            g.text(p.name(),x+40,ry+7,14,INK,Font.BOLD,FONT);
            g.text(p.x()+", "+p.y()+", "+p.z(),x+40,ry+26,11,FAINT,Font.PLAIN,FONT);
            String d=Math.round(distance(p))+" m";
            g.text(d,x+w-15-g.textWidth(d,14,Font.BOLD,FONT),ry+14,14,i==0?ACCENT:MUTED,Font.BOLD,FONT);
            ui.hit("wp"+i,"go",x,ry,w,46);
            ry+=52;
        }
        ui.button("mark","Mark this spot","mark",x,ry+6,w,34,UiContext.ButtonStyle.PRIMARY,UiContext.Shape.ROUNDED);
    }

    private static void settings(UiContext ui, float x, float y, float w) {
        var g=ui.canvas();
        g.text("Compass ring size",x,y,13,INK,Font.PLAIN,FONT);
        ui.slider("ring","ring",x,y+24,w,.6);
        ui.checkbox("night","night","Dim the panel at night",x,y+70,w,true);
        ui.checkbox("sounds","sounds","Click sounds",x,y+106,w,false);
    }

    private static void bar(UiCanvas g, String label, float x, float y, float w, float value, int color) {
        g.text(label,x,y,12,MUTED,Font.PLAIN,FONT);
        g.roundRect(0xFF22262B,x+90,y+5,w-90,6,3);
        g.roundRect(color,x+90,y+5,(w-90)*value,6,3);
    }

    private static String detail(int i) {
        return switch(i){
            case 0 -> "Attack damage 7  /  Attack speed 1.6";
            case 1 -> "Mines iron, gold and diamond ore";
            case 2 -> "Pull back to charge, release to fire";
            case 3 -> "Blocks attacks from the front";
            default -> KIT.get(i).getCount()+" in this kit";
        };
    }

    private static String place() {
        var player=Minecraft.getInstance().player;
        var level=Minecraft.getInstance().level;
        if(player==null||level==null)return "Not in a world";
        var pos=player.blockPosition();
        String biome=level.getBiome(pos).unwrapKey().map(k->k.identifier().getPath().replace('_',' ')).orElse("overworld");
        biome=Character.toUpperCase(biome.charAt(0))+biome.substring(1);
        return biome+"  /  "+pos.getX()+", "+pos.getY()+", "+pos.getZ()+"  /  Day "+(level.getOverworldClockTime()/24000+1);
    }

    private static double distance(Waypoint p) {
        var player=Minecraft.getInstance().player;
        if(player==null)return 0;
        double dx=player.getX()-p.x(), dz=player.getZ()-p.z();
        return Math.sqrt(dx*dx+dz*dz);
    }
}
