package dev.pane.ui;

import dev.pane.demo.VanillaHudExample;
import dev.pane.ui.internal.NativeOpacity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Isolated real-font/resource/HUD/screen fixture. The private copied world is owned by its launcher. */
public final class NativeTextAuditTest {
    private static final UiMinecraftTextStyle PLAIN=UiMinecraftTextStyle.hud().shadow(false);
    private static final Component TITLE=Component.literal("Vanilla ").withStyle(ChatFormatting.GOLD)
        .append(Component.literal("Minecraft text").withStyle(ChatFormatting.WHITE,ChatFormatting.BOLD));
    private static final Component TRANSLATED=Component.translatable("container.inventory");
    private static final Component PACK_TRANSLATION=Component.translatable("pane.font.audit");
    private static final Component ALT=Component.literal("RESOURCE FONT").withStyle(s->s.withFont(
        new FontDescription.Resource(Identifier.withDefaultNamespace("alt"))));
    private static final Component PACK=Component.literal("AAAA").withStyle(s->s.withFont(
        new FontDescription.Resource(Identifier.fromNamespaceAndPath("pane_font_test","width"))));
    private final Runnable disableShowcase;
    private UiHud example,probe;
    private UiScreen screen;
    private int stage,checks;
    private long next,start,layouts,paints,uploads,revision;
    private boolean failed,omit;
    private String label="A long vanilla label that must end with dots";
    private CompletableFuture<Void> reload;
    private final NativeTextRenderer layoutProbe=new NativeTextRenderer();
    public NativeTextAuditTest(Runnable disableShowcase){this.disableShowcase=disableShowcase;}

    public void tick(Minecraft c){try{
        if(start==0)start=System.nanoTime();
        if(System.nanoTime()-start>180_000_000_000L)throw new IllegalStateException("native text timeout at "+stage);
        if(System.nanoTime()<next)return;
        switch(stage){
            case 0->{disableShowcase.run();c.getWindow().setWindowed(1200,800);writePack(c,5);
                c.getResourcePackRepository().reload();c.getResourcePackRepository().addPack("file/pane-font-audit");
                reload=c.reloadResourcePacks();advance(200);}
            case 1->{if(!loaded(c))return;
                example=VanillaHudExample.create().attach(id("example"));example.setMotionScale(0);
                example.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.TOP_LEFT,24,36,1.5F));
                probe=createProbe().attach(id("probe"));probe.setMotionScale(0);
                probe.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.TOP_RIGHT,24,36,1.5F));advance(750);}
            case 2->{
                check(example.nativeTextCount()==3&&example.nativeItemCount()==4,"copyable vanilla example draws native labels and items");
                check(probe.nativeTextCount()==10&&probe.nativeItemCount()==1,"text styles submit with native item pose restored");
                check(c.font.width(PACK)==20,"selected custom resource font uses its declared advances");
                check(PACK_TRANSLATION.getString().equals("Before reload"),"resource-pack translation loads into the retained component");
                var line=layoutProbe.resolve("ellipsis",TITLE,c.font,36,PLAIN);
                check(line.width()<=36&&plain(line.text()).endsWith("..."),"ellipsis fits using Minecraft glyph metrics");
                boolean[] gold={false};line.text().accept((i,s,cp)->{gold[0]|=s.getColor()!=null&&s.getColor().getValue()==0xFFAA00;return true;});
                check(gold[0],"truncated component preserves its styled spans");
                var translation=layoutProbe.resolve("translated",TRANSLATED,c.font,200,PLAIN);
                check(plain(translation.text()).equals(TRANSLATED.getString())&&!TRANSLATED.getString().equals("container.inventory"),"translation resolves through the live Minecraft language");
                var unicode=Component.literal("A\uD83D\uDE80B אבג");
                var unicodeLine=layoutProbe.resolve("unicode",unicode,c.font,300,PLAIN);
                check(plain(unicodeLine.text()).codePoints().anyMatch(n->n==0x1F680),"visual-order path preserves supplementary codepoints");
                var tiny=layoutProbe.resolve("tiny","very long",c.font,2,PLAIN);
                check(tiny.width()<=2,"tiny ellipsis budget never overflows");
                var clip=layoutProbe.resolve("clip",TITLE,c.font,20,PLAIN.overflow(UiMinecraftTextStyle.Overflow.CLIP));
                check(plain(clip.text()).equals(plain(TITLE.getVisualOrderText())),"clip mode retains the full styled visual sequence");
                var same=layoutProbe.resolve("ellipsis",TITLE,c.font,36,PLAIN);
                check(same==line,"stable component layout is reused");
                var str=layoutProbe.resolve("literal",new String("repeat"),c.font,100,PLAIN);
                check(str==layoutProbe.resolve("literal",new String("repeat"),c.font,100,PLAIN),"equal newly supplied strings reuse cached component and layout");
                var child=Component.literal("child");var mutable=Component.literal("Parent ").append(child);
                var beforeEdit=layoutProbe.resolve("mutable",mutable,c.font,300,PLAIN);
                child.append(" updated");mutable.withStyle(ChatFormatting.RED);
                var afterEdit=layoutProbe.resolve("mutable",mutable,c.font,300,PLAIN);
                check(afterEdit!=beforeEdit&&plain(afterEdit.text()).equals("Parent child updated"),"in-place child and root-style edits refresh cached text");
                check(afterEdit.component().getStyle().getColor().getValue()==0xFF5555,"mutable style update is present in the rendered snapshot");
                check(afterEdit==layoutProbe.resolve("mutable",mutable.copy(),c.font,300,PLAIN),"equivalent component values reuse measured layout");
                var arg=Component.literal("First");var translatedArg=Component.translatableWithFallback("pane.test.fallback","Value %s",arg);
                layoutProbe.resolve("args",translatedArg,c.font,300,PLAIN);arg.append(" changed");
                check(plain(layoutProbe.resolve("args",translatedArg,c.font,300,PLAIN).text()).equals("Value First changed"),"mutable component translation arguments refresh correctly");
                for(int i=0;i<300;i++)layoutProbe.resolve("bounded"+i,"bounded",c.font,100,PLAIN);
                check(layoutProbe.cachedLines()==256,"changing slot IDs cannot grow the line cache without bound");
                capture(c,"vanilla-font-hud");baseline();advance(600);}
            case 3->{idle("stable native text");label="Updated label";advance(400);}
            case 4->{check(probe.nativeTextLayoutCount()==layouts+1,"only changed native label remeasures");
                check(probe.snapshot().paints()==paints&&probe.snapshot().uploadedPixels()==uploads,"native label update needs no CPU panel repaint or texture upload");
                baseline();c.options.guiScale().set(3);c.resizeGui();advance(500);}
            case 5->{check(probe.nativeTextCount()==10,"native text survives GUI scale change");idle("GUI scale with unchanged physical placement");
                capture(c,"vanilla-font-gui-scale");probe.setPlacement(new UiHudPlacement(UiHudPlacement.Anchor.CENTER_RIGHT,24,0,.75F));advance(500);}
            case 6->{check(probe.nativeTextCount()==10,"scaled and moved HUD retains all labels");capture(c,"vanilla-font-scaled");
                revision=UiCanvas.minecraftTextureRevision();layouts=probe.nativeTextLayoutCount();writePack(c,10);reload=c.reloadResourcePacks();advance(200);}
            case 7->{if(!loaded(c))return;advance(600);}
            case 8->{check(UiCanvas.minecraftTextureRevision()>revision&&c.font.width(PACK)==40,"actual resource-pack reload replaces font metrics");
                check(PACK_TRANSLATION.getString().equals("After reload"),"same translatable component refreshes after language resources reload");
                check(probe.nativeTextLayoutCount()>layouts,"font reload invalidates retained native line layout");
                check(layoutProbe.resolve("reloaded",PACK,c.font,100,PLAIN).width()==40,"layout uses the replacement font advances");
                baseline();advance(400);}
            case 9->{idle("settled resource reload");omit=true;probe.invalidate();advance(400);}
            case 10->{check(probe.nativeTextCount()==9,"removed native slot immediately stops drawing its text");
                omit=false;probe.invalidate();probe.setMotionScale(1);probe.setVisible(false);advance(350);}
            case 11->{check(probe.nativeTextCount()==0&&!probe.snapshot().hasSurface(),"exit fade releases text and backing surface");probe.setVisible(true);advance(450);}
            case 12->{check(probe.nativeTextCount()==10,"show restores native labels");probe.setMotionScale(0);c.gui.hud.toggle();advance(250);}
            case 13->{check(probe.nativeTextCount()==0&&example.nativeTextCount()==0,"F1 suppresses all native font draws");c.gui.hud.toggle();c.gui.setScreen(new PauseScreen(false));advance(300);}
            case 14->{check(probe.nativeTextCount()==0,"ordinary screens suppress HUD font draws");probe.setShowOnScreens(true);advance(300);}
            case 15->{check(probe.nativeTextCount()==10,"showOnScreens restores native text under a screen");c.gui.setScreen(null);probe.setEnabled(false);
                check(probe.nativeTextCount()==0&&!probe.snapshot().hasSurface(),"disable clears native text immediately");probe.setEnabled(true);advance(400);}
            case 16->{check(probe.nativeTextCount()==10,"enable rebuilds native text after cache release");probe.close();example.close();
                check(probe.nativeTextCount()==0&&!probe.snapshot().hasSurface(),"close clears text diagnostics and surfaces");
                c.disconnect(new TitleScreen(),false);advance(500);}
            case 17->{if(c.level!=null||!(c.gui.screen() instanceof TitleScreen)||c.gui.overlay()!=null)return;
                screen=new UiScreen(UiDocument.builder("Native font screen").scaling(UiScaling.fixed())
                    .page(new UiPage("main","Main","",0xFFCCAA33,ui->{
                        ui.canvas().fill(0xFF303030,0,0,ui.width(),ui.height());
                        ui.nativeSlot("screen",30,30,420,24);
                        ui.scroll("clip",new UiRect(30,90,220,40),100,body->{body.nativeSlot("partial",0,32,160,12);});
                        ui.canvas().fill(0xFF1D5C39,30,160,420,44);ui.nativeSlot("covered",35,170,400,20);
                        ui.nativeOverlayLayer("cover",30,160,420,44);
                        ui.nativeSlot("modal-under",ui.width()/2-100,ui.height()/2-10,200,20);
                        ui.dialogButton("dialog","Open dialog",30,230,140,28,UiContext.ButtonStyle.PRIMARY,UiContext.Shape.ROUNDED,
                            new UiDialog("Native text ordering","The dialog covers the red native label below it.","Done","done","Cancel","cancel"));
                    })).build(),o->{o.text("screen",TITLE);o.text("partial","must not escape");o.text("covered","COVERED NATIVE TEXT");
                        o.text("modal-under","NATIVE UNDER MODAL",PLAIN.color(0xFFFF0000));},UiEventListener.NONE);
                c.gui.setScreen(screen);advance(600);}
            case 18->{check(screen.nativeTextCount()==3,"world-free UiScreen draws text and hides partially clipped native slots");
                check(c.level==null&&c.player==null,"font screen needs no world or player");capture(c,"vanilla-font-screen");
                layouts=screen.nativeTextLayoutCount();paints=screen.paintCount();advance(500);}
            case 19->{check(screen.nativeTextLayoutCount()==layouts&&screen.paintCount()==paints,"world-free stable labels do not remeasure or repaint");
                screen.focus("dialog");screen.keyPressed(new KeyEvent(257,0,0));advance(400);}
            case 20->{check(screen.openDialogId().isPresent()&&screen.nativeTextCount()==3,"native text continues submitting beneath an open dialog");
                capture(c,"vanilla-font-modal");var b=screen.regionBounds("dialog-body").orElseThrow();
                System.out.println("[NativeTextModal] x="+b.x()+" y="+b.y()+" w="+b.width()+" h="+b.height());
                screen.keyPressed(new KeyEvent(256,0,0));advance(300);}
            case 21->{check(screen.openDialogId().isEmpty()&&screen.nativeTextCount()==3,"closing dialog restores unobstructed native labels");
                c.gui.setScreen(new TitleScreen());check(screen.nativeTextCount()==0,"screen removal clears text diagnostics");
                c.getResourcePackRepository().removePack("file/pane-font-audit");
                System.out.println("[NativeTextTest] complete "+(failed?"FAIL":"PASS")+" checks="+checks);stage=99;c.stop();}
        }
    }catch(Throwable e){e.printStackTrace();System.out.println("[NativeTextTest] complete FAIL "+e);stage=99;c.stop();}}

    private UiHud createProbe(){return new UiHud(360,240,UiTheme.dark(),ui->{
        var g=ui.canvas();g.fill(0xFF000000,0,0,360,240);g.strokeRoundRect(0xFFAAAAAA,1,0,0,360,240,0);
        ui.nativeSlot("title",12,12,336,18);ui.nativeSlot("translated",12,40,336,12);
        if(!omit)ui.nativeSlot("ellipsis",12,66,130,12);ui.nativeSlot("right",180,66,168,12);
        ui.nativeSlot("center",12,90,336,12);ui.nativeSlot("clip",12,114,130,12);
        ui.nativeSlot("alt",12,138,336,12);ui.nativeSlot("full",12,162,150,12);
        ui.nativeSlot("half",12,182,150,12);ui.nativeSlot("zero",12,202,150,12);
        ui.nativeSlot("pack",180,162,150,12);ui.nativeSlot("item",280,196,32,32);
    },o->{
        o.text("title",TITLE);o.text("translated",TRANSLATED);o.text("ellipsis",label,PLAIN);
        o.text("right",PACK_TRANSLATION,PLAIN.alignment(UiMinecraftTextStyle.Alignment.RIGHT));
        o.text("center","Centered label",PLAIN.alignment(UiMinecraftTextStyle.Alignment.CENTER));
        o.text("clip","CLIPPED TEXT CANNOT ESCAPE ITS SLOT",PLAIN.overflow(UiMinecraftTextStyle.Overflow.CLIP));
        o.text("alt",ALT,PLAIN);o.text("full","FADE CHECK",PLAIN);
        NativeOpacity.with(.5F,()->o.text("half","FADE CHECK",PLAIN));
        NativeOpacity.with(0,()->o.text("zero","FADE CHECK",PLAIN));
        o.text("pack",PACK,PLAIN);o.item("item",new ItemStack(Items.DIAMOND));
        o.text("missing","must not draw");
    });}
    private boolean loaded(Minecraft c){if(!reload.isDone()||c.gui.overlay()!=null)return false;reload.join();return true;}
    private void writePack(Minecraft c,int width)throws Exception{
        Path root=c.gameDirectory.toPath().resolve("resourcepacks/pane-font-audit");
        Path font=root.resolve("assets/pane_font_test/font/width.json");Files.createDirectories(font.getParent());
        int version=SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).major();
        Files.writeString(root.resolve("pack.mcmeta"),"{\"pack\":{\"description\":\"Isolated font audit\",\"min_format\":"+version+",\"max_format\":"+version+"}}");
        Files.writeString(font,"{\"providers\":[{\"type\":\"space\",\"advances\":{\"A\":"+width+"}},{\"type\":\"reference\",\"id\":\"minecraft:default\"}]}");
        Path language=root.resolve("assets/pane_font_test/lang/en_us.json");Files.createDirectories(language.getParent());
        Files.writeString(language,"{\"pane.font.audit\":\""+(width==5?"Before reload":"After reload")+"\"}");
    }
    private static String plain(net.minecraft.util.FormattedCharSequence text){var out=new StringBuilder();text.accept((i,s,cp)->{out.appendCodePoint(cp);return true;});return out.toString();}
    private void baseline(){layouts=probe.nativeTextLayoutCount();paints=probe.snapshot().paints();uploads=probe.snapshot().uploadedPixels();}
    private void idle(String name){check(layouts==probe.nativeTextLayoutCount()&&paints==probe.snapshot().paints()&&uploads==probe.snapshot().uploadedPixels(),name+" reuses layout and retained pixels");}
    private void advance(int ms){stage++;next=System.nanoTime()+ms*1_000_000L;}
    private void check(boolean ok,String name){checks++;failed|=!ok;System.out.println("[NativeTextTest] "+name+": "+(ok?"PASS":"FAIL"));}
    private static Identifier id(String path){return Identifier.fromNamespaceAndPath("pane_demo","font_"+path);}
    private void capture(Minecraft c,String name){
        Screenshot.grab(c.gameDirectory,name+".png",c.gameRenderer.mainRenderTarget(),1,m->{});
        if(probe!=null&&!name.endsWith("screen")){var b=probe.snapshot().bounds();double scale=c.getWindow().getGuiScale();
            System.out.println("[NativeTextPixels] "+name+" x="+b.x()*scale+" y="+b.y()*scale+" w="+b.width()*scale+" h="+b.height()*scale);}
    }
}
