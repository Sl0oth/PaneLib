package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.util.*;
import net.minecraft.client.gui.screens.Screen;

/** Discord desktop visual/interaction study. All people, conversations and settings are local fixtures. */
public final class DiscordShowcase {
    private static final int RAIL=0xFF1E1F22,SIDE=0xFF2B2D31,CHAT=0xFF313338,INPUT_BG=0xFF383A40,
        TEXT=0xFFF2F3F5,MUTED=0xFFB5BAC1,FAINT=0xFF949BA4,BLUE=0xFF5865F2,GREEN=0xFF23A55A,LINE=0xFF3F4147;
    private static final String FONT=ExampleFonts.FAMILY;
    private static final UiInputStyle INPUT=new UiInputStyle(15,Font.PLAIN,FONT,12,7,22);
    private static final String[] PEOPLE={"Rowan","Mira","Theo","Jules","Sage","Finn"};
    private static final int[] COLORS={0xFF65C5A0,0xFFE4A6B8,0xFF6C9CEF,0xFFE4BF68,0xFFA49ADD,0xFF86B5A9};
    private static final String[] CHANNELS={"welcome","announcements","general","showcase","ideas-and-feedback"};
    private static final String[] SETTING_TABS={"Overview","Roles","Members","Notifications","Appearance"};
    static final class Message {
        final int author;final String body,time;final boolean attachment;int likes;boolean reacted;
        Message(int author,String body,String time,boolean attachment,int likes){this.author=author;this.body=body;this.time=time;this.attachment=attachment;this.likes=likes;}
    }
    static final class Model {
        String serverName="Trailside",description="A little place to build things, hang out, and share what we're making.",conversation="general",setting="Overview";
        boolean friends;String friendTab="Add Friend";int voiceChannel;
        boolean dm,settings,members=true,voice,mute,deaf,compact,notify=true,mentions=true,links=true;
        String role="Member";int textSize=15;
        final Map<String,List<Message>> messages=new LinkedHashMap<>();
        Model(){
            messages.put("general",new ArrayList<>(List.of(
                msg(2,"Anyone up for a little build night?","2:13 PM"),
                msg(1,"@members bring one tiny idea and a snack.","2:13 PM"),
                msg(0,"I'll open the workshop at seven.","2:14 PM"),
                msg(2,"Perfect. I've been sketching a lakeside cabin.","2:14 PM"),
                msg(0,"Love that. Here's a palette for the evening:","2:15 PM"),
                new Message(2,"","2:15 PM",true,0),
                msg(3,"Those colors are lovely. I'll handle the lanterns.","2:15 PM"),
                msg(0,"Meet you in the lounge. Bring your unfinished projects.","2:16 PM"),
                msg(3,"On my way. No promises about the roof though.","2:17 PM")
            )));
            messages.put("shows",new ArrayList<>(List.of(msg(2,"What should we watch next?","2:18 PM"))));
            messages.put("welcome",new ArrayList<>(List.of(msg(0,"Welcome to Trailside! Make yourself at home. Say hello in #general and share your work in #showcase.","Yesterday"),msg(1,"Good people, small projects, and far too many late-night ideas. Glad you're here.","Yesterday"))));
            messages.put("announcements",new ArrayList<>(List.of(msg(0,"Build night this Friday at 7. Bring a project or just come hang out. Everyone is welcome!","9:00 AM"))));
            messages.put("showcase",new ArrayList<>(List.of(new Message(2,"The latest version of the courtyard. Finally happy with the silhouette.","Yesterday",true,6))));
            messages.put("ideas-and-feedback",new ArrayList<>(List.of(msg(3,"What if we added a little greenhouse next to spawn?","Yesterday"),msg(1,"Yes please. With a reading corner.","9:10 AM"))));
            messages.put("dm:Maya",new ArrayList<>(List.of(msg(1,"hey! got a second to look at something?","10:12 AM"),msg(0,"Yeah, what's up?","10:13 AM"),msg(1,"I was thinking we could make the next build night a cozy village theme. Small houses, warm lighting, lots of plants.","10:13 AM"),msg(0,"That sounds great. We can put it in announcements.","10:14 AM"),msg(1,"perfect! I'll put together a few references tonight","10:15 AM"))));
            messages.put("dm:Alex",new ArrayList<>(List.of(msg(2,"The courtyard is nearly done. Want a tour later?","9:42 AM"),msg(0,"Absolutely. Send me a message when you're ready.","9:45 AM"))));
            messages.put("dm:Jun",new ArrayList<>(List.of(msg(3,"important question: is a greenhouse just a house for plants?","Yesterday"),msg(0,"Technically yes.","Yesterday"),msg(3,"excellent. my architectural career begins today.","Yesterday"))));
        }
        static Message msg(int a,String s,String t){return new Message(a,s,t,false,0);}
        String key(){return dm?"dm:"+conversation:conversation;}
        String historyId(){return "history:"+key();}
        List<Message> current(){return messages.get(key());}
        boolean dirty(UiScreen s){return !serverName.equals(s.stringValue("server-name",serverName))||!description.equals(s.stringValue("description",description))||notify!=s.booleanValue("notify",notify)||mentions!=s.booleanValue("mentions",mentions)||links!=s.booleanValue("links",links)||compact!=s.booleanValue("compact",compact)||textSize!=(int)s.numberValue("text-size",textSize)||!role.equals(s.stringValue("role",role));}
        void reset(UiScreen s){s.setValue("server-name",serverName);s.setValue("description",description);s.setValue("notify",notify);s.setValue("mentions",mentions);s.setValue("links",links);s.setValue("compact",compact);s.setValue("text-size",textSize);s.setValue("role",role);s.setValue("settings-error","");}
        void save(UiScreen s){String name=s.stringValue("server-name",serverName).trim();if(name.isEmpty()){s.setValue("settings-error","Give your server a name before saving.");return;}serverName=name;description=s.stringValue("description",description);notify=s.booleanValue("notify",notify);mentions=s.booleanValue("mentions",mentions);links=s.booleanValue("links",links);compact=s.booleanValue("compact",compact);textSize=(int)s.numberValue("text-size",textSize);role=s.stringValue("role",role);reset(s);}
    }
    public static UiScreen createScreen(Screen parent){return createScreen(parent,new Model());}
    static UiScreen createScreen(Screen parent,Model m){
        var theme=new UiTheme(CHAT,SIDE,INPUT_BG,INPUT_BG,TEXT,MUTED,BLUE,TEXT,6,8,12,1,0,FONT);
        var doc=UiDocument.builder("Gather • PaneLib local demo").theme(theme).shell(UiShell.NONE).scaling(UiScaling.fixed().reference(1000,630).adaptivity(1).range(.25F,8)).renderSettings(UiRenderSettings.typography())
            .page(new UiPage("discord","Gather","",BLUE,ui->{if(m.settings)settings(ui,m);else DiscordReferenceView.paint(ui,m);})).build();
        UiScreen screen=UiScreen.create(doc,NativeOverlayRenderer.NONE,(s,event)->{
            var type=event.type(); var key=event.key(); var value=event.value();
            if(type==UiEventType.SUBMIT&&key.startsWith("draft:")){send(s,m);return;}if(type!=UiEventType.ACTION)return;
            if(key.startsWith("channel:")){m.dm=false;m.friends=false;m.conversation=key.substring(8);s.setValue("search","");s.scrollTo(m.historyId(),0);}
            else if(key.startsWith("dm:")){m.dm=true;m.friends=false;m.conversation=key.substring(3);s.setValue("search","");s.scrollTo(m.historyId(),0);}
            else if(key.startsWith("friend-tab:")){m.friendTab=key.substring(11);}
            else if(key.startsWith("setting:")){m.setting=key.substring(8);s.scrollTo("settings-body",0);}
            else if(key.startsWith("react:")){var msg=m.current().get(Integer.parseInt(key.substring(6)));msg.reacted=!msg.reacted;msg.likes+=msg.reacted?1:-1;}
            else switch(key){
                case "home","friends"->{m.dm=true;m.friends=true;m.conversation="Maya";s.setValue("search","");s.scrollTo(m.historyId(),0);}
                case "server"->{m.dm=false;m.friends=false;m.conversation="general";s.setValue("search","");s.scrollTo(m.historyId(),0);}
                case "settings","appearance"->{m.settings=true;m.setting=key.equals("appearance")?"Appearance":"Overview";m.reset(s);}
                case "close-settings"->{if(!m.dirty(s))m.settings=false;else s.setValue("settings-error","Save or reset your changes before closing.");}
                case "save"->m.save(s);case "reset"->m.reset(s);
                case "send"->send(s,m);case "members"->m.members=!m.members;
                case "voice","voice-gaming"->{int selected=key.equals("voice")?0:1;m.voice=!m.voice||m.voiceChannel!=selected;m.voiceChannel=selected;}
                case "add-friend"->{String name=s.stringValue("friend-name","").trim();s.setValue("friend-status",name.isEmpty()?"Enter a username first.":"Local request pending: "+name);}
                case "pins","threads"->s.toast(new UiToast("local-preview","Not connected", "This reference demo does not implement "+key+".",2500));case "mute"->m.mute=!m.mute;case "deaf"->m.deaf=!m.deaf;
                case "notifications"->{m.settings=true;m.setting="Notifications";m.reset(s);}
            }s.invalidate();
        },UiScreenOptions.builder().parent(parent).backdrop(UiBackdrop.NONE).onClose(s->{if(m.settings){if(m.dirty(s))s.setValue("settings-error","Save or reset your changes before closing.");else m.settings=false;s.invalidate();return true;}return false;}).build());m.reset(screen);return screen;
    }
    private static void send(UiScreen s,Model m){String key="draft:"+m.key(),body=s.stringValue(key,"").trim();if(body.isEmpty())return;m.current().add(Model.msg(0,body,"Now"));s.setValue(key,"");s.scrollToEnd(m.historyId());s.invalidate();}
    private static void t(UiCanvas g,String text,float x,float y,float size,int c,boolean bold){g.text(text,x,y,size,c,bold?Font.BOLD:Font.PLAIN,FONT);}
    private static void clipped(UiCanvas g,String text,float x,float y,float width,float size,int c,boolean bold){g.withClip(x,y,width,size+8,()->t(g,text,x,y,size,c,bold));}
    private static UiButtonStyle style(int bg,int fg){return UiButtonStyle.builder().background(bg).hoverBackground(UiCanvas.lighten(bg,.08F)).text(fg).border(0).borderWidth(0).radius(4).textSize(14).fontFamily(FONT).build();}
    private static void button(UiContext ui,String id,String label,float x,float y,float w,float h,int bg,int fg){ui.button(id,label,id,x,y,w,h,style(bg,fg));}
    private static void iconButton(UiContext ui,String id,UiIcon icon,float x,float y,String tip){ui.iconButton(id,icon,id,x,y,30,UiContext.ButtonStyle.GHOST,UiContext.Shape.ROUNDED);ui.tooltip(id,tip);}
    private static void avatar(UiCanvas g,int person,float x,float y,float size,boolean online){
        GatherAvatars.draw(g,person,x,y,size);
        if(online){g.circle(SIDE,x+size-4,y+size-4,7);g.circle(GREEN,x+size-4,y+size-4,4.5F);}
    }
    private static void hash(UiCanvas g,float x,float y,int c){g.line(c,2,x+7,y,x+3,y+20);g.line(c,2,x+16,y,x+12,y+20);g.line(c,2,x,y+6,x+20,y+6);g.line(c,2,x-1,y+14,x+19,y+14);}
    private static void gear(UiCanvas g,float x,float y,int c){g.strokeCircle(c,1.8F,x+10,y+10,6);g.strokeCircle(c,1.5F,x+10,y+10,2.5F);for(int i=0;i<8;i++){double a=i*Math.PI/4;g.line(c,2.5F,x+10+(float)Math.cos(a)*6,y+10+(float)Math.sin(a)*6,x+10+(float)Math.cos(a)*9,y+10+(float)Math.sin(a)*9);}}
    private static void landscape(UiCanvas g,float x,float y,float w,float h){
        g.withClip(x,y,w,h,()->{g.fill(0xFF172A35,x,y,w,h);g.circle(0xFFE9D8A1,x+w*.79F,y+h*.22F,h*.085F);
            g.polygon(0xFF254352,x,y+h*.7F,x+w*.2F,y+h*.2F,x+w*.45F,y+h*.66F,x+w*.69F,y+h*.29F,x+w,y+h*.62F,x+w,y+h,x,y+h);
            g.polygon(0xFF355A51,x,y+h*.72F,x+w*.2F,y+h*.6F,x+w*.48F,y+h*.81F,x+w*.8F,y+h*.57F,x+w,y+h*.65F,x+w,y+h,x,y+h);
            for(int i=0;i<7;i++){float tx=x+w*(.04F+i*.15F),ty=y+h*(i%2==0?.57F:.67F);g.fill(0xFF182F30,tx+5,ty,3,h*.3F);g.polygon(0xFF193B37,tx-6,ty+h*.2F,tx+6,ty-h*.14F,tx+18,ty+h*.2F);}
            float bx=x+w*.39F,by=y+h*.57F;g.fill(0xFF87694C,bx,by,w*.25F,h*.29F);g.polygon(0xFF3D3536,bx-w*.035F,by+2,bx+w*.125F,by-h*.19F,bx+w*.285F,by+2);
            for(int i=0;i<3;i++){g.fill(0xFFF4CD84,bx+w*(.035F+i*.075F),by+h*.075F,w*.035F,h*.08F);}
            g.polygon(0xFF91816A,bx+w*.10F,by+h*.29F,bx+w*.17F,by+h*.29F,bx+w*.24F,y+h,bx-w*.03F,y+h);
        });
    }
    private static void settings(UiContext ui,Model m){
        var g=ui.canvas();float w=ui.width(),h=ui.height(),nav=Math.max(205,(w-1020)/2+220),x=nav+40,cw=Math.min(660,w-x-85);
        g.fill(CHAT,0,0,w,h);g.fill(SIDE,0,0,nav,h);float nx=Math.max(16,nav-200);clipped(g,m.serverName.toUpperCase(Locale.ROOT),nx,65,180,11,FAINT,true);
        for(int i=0;i<SETTING_TABS.length;i++){
            String name=SETTING_TABS[i];float rowY=94+i*38;boolean selected=m.setting.equals(name);
            ui.paintedButton("setting:"+name,name,"setting:"+name,nx,rowY,184,32,4,true,(canvas,state)->{
                int background=selected?0xFF404249:SIDE;
                if(state.hovered())background=UiCanvas.lighten(background,.08F);
                if(state.pressed())background=UiCanvas.darken(background,.08F);
                canvas.roundRect(background,nx,rowY,184,32,4);
                clipped(canvas,name,nx+10,rowY+8,164,14,selected?TEXT:MUTED,false);
            });
        }
        g.line(LINE,1,nx,300,nav-16,300);t(g,"LOCAL DEMO SERVER",nx,320,10,FAINT,true);t(g,"Changes stay in this screen.",nx,340,11,FAINT,false);
        iconButton(ui,"close-settings",UiIcon.CLOSE,x+cw+14,57,"Close settings");g.strokeCircle(FAINT,1,x+cw+29,72,18);t(g,"ESC",x+cw+19,99,10,FAINT,true);
        t(g,m.setting.equals("Overview")?"Server Overview":m.setting,x,62,23,TEXT,true);
        ui.scroll("settings-body",new UiRect(x,111,cw,Math.max(100,h-220)),m.setting.equals("Overview")?570:540,sc->{var c=sc.canvas();float y=126;
            switch(m.setting){
                case "Overview"->{
                    c.circle(BLUE,x+59,y+49,49);t(c,m.serverName.substring(0,1),x+45,y+28,34,TEXT,true);t(c,"SERVER NAME",x+145,y+7,11,MUTED,true);
                    sc.textField("server-name","server-name","Server name",x+145,y+31,Math.max(120,cw-145),40,m.serverName,80,INPUT);
                    t(c,"One example server. Make it yours.",x+145,y+83,12,FAINT,false);
                    t(c,"SERVER DESCRIPTION",x,y+141,11,MUTED,true);sc.textArea("description","description","What's your server about?",x,y+166,cw,90,m.description,500,INPUT);
                    c.line(LINE,1,x,y+284,x+cw,y+284);t(c,"Server preview",x,y+309,17,TEXT,true);
                    landscape(c,x,y+342,Math.min(cw,360),150);t(c,"Your community, your corner of the internet.",x,y+507,12,MUTED,false);
                }
                case "Roles"->{
                    t(c,"Use roles to organize your community.",x,y,14,MUTED,false);t(c,"ROLE",x,y+49,11,FAINT,true);t(c,"MEMBERS",x+cw-80,y+49,11,FAINT,true);
                    String[] roles={"Owner","Moderator","Member"};for(int i=0;i<3;i++){float ry=y+76+i*55;c.roundRect(SIDE,x,ry,cw,46,5);c.circle(COLORS[i],x+20,ry+23,6);t(c,roles[i],x+39,ry+14,15,TEXT,true);t(c,""+(i==2?4:1),x+cw-43,ry+14,14,MUTED,false);}
                    t(c,"MEMBER PERMISSIONS",x,y+269,11,MUTED,true);t(c,"Allow members to share links",x,y+309,15,TEXT,true);sc.toggle("links","links",x+cw-35,y+310,m.links);
                    t(c,"A sample permission setting; no external server is changed.",x,y+339,12,FAINT,false);
                }
                case "Members"->{
                    sc.textField("member-search","member-search","Search members",x,y,cw,38,"",80,INPUT);
                    String q=sc.stringValue("member-search","").toLowerCase(Locale.ROOT);float ry=y+68;
                    for(int i=0;i<6;i++){if(!PEOPLE[i].toLowerCase(Locale.ROOT).contains(q))continue;avatar(c,i,x+8,ry+3,34,i<4);t(c,PEOPLE[i],x+57,ry+3,15,TEXT,true);t(c,i==0?"Owner":i==1?"Moderator":"Member",x+57,ry+24,11,FAINT,false);
                        if(i==2)sc.dropdown("role","role",x+cw-165,ry,160,34,m.role,List.of(new UiSelectOption("Member","Member"),new UiSelectOption("Moderator","Moderator")),UiButtonStyle.builder().fontFamily(FONT).textSize(14).fontStyle(Font.PLAIN).build(),UiMenuStyle.builder().fontFamily(FONT).textSize(14).rowHeight(34).padding(12,5).build());
                        c.line(LINE,1,x,ry+52,x+cw,ry+52);ry+=65;
                    }
                }
                case "Notifications"->{t(c,"Decide what gets your attention in this demo.",x,y,14,MUTED,false);
                    t(c,"Server notifications",x,y+69,17,TEXT,true);sc.toggle("notify","notify",x+cw-35,y+70,m.notify);t(c,"Show the unread indicator in this local server.",x,y+101,13,FAINT,false);
                    c.line(LINE,1,x,y+140,x+cw,y+140);t(c,"Mention highlights",x,y+169,17,TEXT,true);sc.toggle("mentions","mentions",x+cw-35,y+170,m.mentions);t(c,"Highlight messages that mention @Sl0oth.",x,y+201,13,FAINT,false);
                }
                case "Appearance"->{t(c,"MESSAGE DISPLAY",x,y,11,MUTED,true);t(c,"Compact messages",x,y+37,16,TEXT,true);sc.toggle("compact","compact",x+cw-35,y+38,m.compact);t(c,"Hide avatars and tighten message groups.",x,y+67,13,FAINT,false);
                    t(c,"CHAT FONT SIZE",x,y+133,11,MUTED,true);sc.slider("text-size","text-size",x,y+177,cw-10,12,20,m.textSize);t(c,(int)sc.numberValue("text-size",m.textSize)+" px",x,y+203,12,MUTED,false);
                    c.roundRect(SIDE,x,y+254,cw,110,6);avatar(c,1,x+16,y+274,36,false);t(c,"Maya",x+66,y+273,15,COLORS[1],true);c.wrappedText("A little space to make something great.",x+66,y+302,cw-85,(float)sc.numberValue("text-size",m.textSize),24,TEXT,Font.PLAIN,FONT);
                }
            }
        });
        // Save controls stay outside the scrolling settings body.
        g.roundRect(RAIL,x-10,h-83,cw+20,58,6);t(g,ui.stringValue("settings-error","").isEmpty()?"Changes apply to this local demo.":ui.stringValue("settings-error",""),x+6,h-65,12,ui.stringValue("settings-error","").isEmpty()?MUTED:0xFFF08080,false);
        button(ui,"reset","Reset",x+cw-201,h-71,75,34,RAIL,TEXT);button(ui,"save","Save Changes",x+cw-117,h-71,117,34,0xFF248046,TEXT);
    }
}
