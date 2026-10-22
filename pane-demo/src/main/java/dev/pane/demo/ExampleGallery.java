package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/** The one showcase launcher: three curated examples plus the example HUDs. Esc returns here from each. */
public final class ExampleGallery {
    private static final int PAPER=0xFFF1EBDD, INK=0xFF252820, MUTED=0xFF686B61, RULE=0xFFCCC8B9, GOLD=0xFFFFD357;
    private static final String FONT=ExampleFonts.FAMILY;
    private static final Identifier LANDSCAPE=Identifier.fromNamespaceAndPath("pane_demo","textures/wayfarer/mountain-lookout.png");

    public static UiScreen createScreen(Screen parent) {
        var preview=new UiLayer();
        var doc=UiDocument.builder("PaneLib / Examples").shell(UiShell.NONE)
            .theme(UiTheme.light()).scaling(UiScaling.fixed().reference(1000,700).adaptivity(1).range(.25F,8)).renderSettings(UiRenderSettings.typography())
            .page(new UiPage("examples","Examples","",GOLD,ui->paint(ui,preview))).build();
        return UiScreen.create(doc,NativeOverlayRenderer.NONE,(s,event)->{
            var type=event.type(); var key=event.key(); var value=event.value();
            if(type!=UiEventType.ACTION)return;
            var client=Minecraft.getInstance();
            switch(key){
                case "paneos" -> client.gui.setScreen(PaneOsShowcase.createScreen(s));
                case "wayfarer" -> client.gui.setScreen(WayfarerShowcase.createScreen(s));
                case "mail" -> client.gui.setScreen(GmailShowcase.createScreen(s));
                case "wayfarer-hud", "vanilla-hud" -> {
                    ExampleHudController.select(key);
                    if(client.level!=null) client.gui.setScreen(null);
                    else { s.setValue("hud-note",key.equals("wayfarer-hud")?"Wayfarer HUD selected":"Vanilla HUD selected"); s.invalidate(); }
                }
                case "disable-hud" -> { ExampleHudController.select("none"); s.setValue("hud-note","Example HUD removed"); s.invalidate(); }
                case "back" -> s.onClose();
            }
        },UiScreenOptions.builder().parent(parent).backdrop(UiBackdrop.NONE).build());
    }

    private static void paint(UiContext ui, UiLayer preview) {
        var g=ui.canvas();
        float w=ui.width(), h=ui.height(), m=Math.max(18,Math.min(w,h)*.045F);
        // Fine print-stock rules and a strong editorial hierarchy, rather than a generic dashboard.
        g.fill(PAPER,0,0,w,h);
        for(float y=0;y<h;y+=4)g.fill(0x04726A52,0,y,w,1);
        text(g,"PaneLib",m,m,30,INK,true);
        text(g,"THE EXAMPLE SHELF",w-m-175,m+8,11,MUTED,true);
        float top=m+64;
        g.fill(INK,m,top-13,w-2*m,2);
        text(g,"Different interfaces. One native library.",m,top,27,INK,true);
        text(g,"Open a working example. Read the recipe. Make it your own.",m,top+37,13,MUTED,false);
        float y=top+77, bottom=h-m-102;
        float heroW=(w-2*m)*.55F, gap=20, right=m+heroW+gap, rightW=w-m-right;
        float heroH=bottom-y;
        ui.paintedButton("paneos","Open PaneOS","paneos",m,y,heroW,heroH,0,true,(c,state)->{
            c.withClip(m,y,heroW,heroH,()->PaneOsShowcase.paintPreview(c,preview,new UiRect(m,y,heroW,heroH)));
            c.gradientRect(0x00061433,0xD9061433,90,m,y+heroH*.42F,heroW,heroH*.58F,0);
            text(c,"01 / PANEOS",m+20,y+42,12,0xFFFFFFFF,true);
            text(c,"A whole desktop.",m+20,y+heroH-150,32,0xFFFFFFFF,true);
            text(c,"Drawn in code.",m+20,y+heroH-114,32,0xFFFFFFFF,true);
            text(c,"LIQUID GLASS  /  WINDOWS  /  WIDGETS",m+20,y+heroH-65,10,0xFFBFDDF8,true);
            if(state.hovered()||state.focused())c.strokeRoundRect(0xFFFFFFFF,3,m,y,heroW,heroH,0);
        });
        float rowH=(heroH-gap)/2;
        ui.paintedButton("wayfarer","Open Wayfarer menu","wayfarer",right,y,rightW,rowH,0,true,(c,state)->{
            c.minecraftImage(LANDSCAPE,new UiRect(right,y,rightW,rowH),UiImageOptions.photo());
            c.gradientRect(0x18081924,0xF0081924,90,right,y,rightW,rowH,0);
            text(c,"02 / WAYFARER",right+18,y+18,11,GOLD,true);
            text(c,"Beyond the cloudline.",right+18,y+rowH-53,18,0xFFF5F6F0,true);
            text(c,"Player preview & game menu",right+18,y+rowH-25,10,0xFFB5B8B1,false);
            if(state.hovered()||state.focused())c.strokeRoundRect(GOLD,2,right,y,rightW,rowH,0);
        });
        card(ui,"mail","03 / PANE MAIL","A desk for your day.","Search, compose & organise",right,y+rowH+gap,rightW,rowH,0xFFFEFFFC,0xFF365A4B);
        float foot=bottom+18;
        text(g,"IN THE WORLD",m,foot,10,MUTED,true);
        button(ui,"wayfarer-hud","Wayfarer HUD",m,foot+22,160,30);
        button(ui,"vanilla-hud","Vanilla HUD",m+170,foot+22,145,30);
        button(ui,"disable-hud","HUD off",m+325,foot+22,95,30);
        text(g,ui.stringValue("hud-note", "Choose a HUD, then open a world. H: this launcher · J: toggle HUD · Esc: back here."),m,foot+62,11,MUTED,false);
        button(ui,"back","Back to Minecraft",w-m-170,foot+22,170,30);
    }

    private static void card(UiContext ui,String id,String label,String title,String detail,float x,float y,float w,float h,int bg,int accent){
        ui.paintedButton(id,"Open "+label.substring(5),id,x,y,w,h,0,true,(g,state)->{
            g.fill(bg,x,y,w,h);g.fill(accent,x,y,4,h);
            int ink=id.equals("mail")?INK:0xFFF3F1EA;
            text(g,label,x+18,y+18,11,accent,true);
            var preview=Identifier.fromNamespaceAndPath("pane_demo","textures/examples/"+id+".png");
            g.minecraftImage(preview,new UiRect(x+18,y+41,w-36,h-101),
                new UiImageOptions(UiImageOptions.Fit.COVER,UiImageOptions.Sampling.BILINEAR,.5F,.25F));
            text(g,title,x+18,y+h-53,18,ink,true);
            text(g,detail,x+18,y+h-25,10,id.equals("mail")?MUTED:0xFFB5B8B1,false);
            if(state.hovered()||state.focused())g.strokeRoundRect(accent,2,x,y,w,h,0);
        });
    }
    private static void button(UiContext ui,String id,String label,float x,float y,float w,float h){
        ui.paintedButton(id,label,id,x,y,w,h,0,true,(g,s)->{
            g.fill(s.hovered()||s.focused()?INK:PAPER,x,y,w,h);g.strokeRoundRect(INK,1,x,y,w,h,0);
            text(g,label,x+10,y+8,11,s.hovered()||s.focused()?PAPER:INK,true);
        });
    }
    private static void text(UiCanvas g,String text,float x,float y,float size,int color,boolean bold){g.text(text,x,y,size,color,bold?Font.BOLD:Font.PLAIN,FONT);}
}
