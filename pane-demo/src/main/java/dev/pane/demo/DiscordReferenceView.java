package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.util.*;


/** Native chat showcase with original branding, vector avatars and landscape artwork. */
final class DiscordReferenceView {
    static final String[] NAMES={"Rowan","Mira","Theo","Jules","Sage","Finn"};
    private static final int RAIL=0xFF000000,SIDE=0xFF000000,CHAT=0xFF000000,INPUT=0xFF101014,
        TEXT=0xFFD8D8DC,MUTED=0xFF909098,WHITE=0xFFF3F3F4,BLUE=0xFF5865F2,GREEN=0xFF43B581;
    private static final int[] NAME_COLORS={0xFFE9E9ED,0xFFE9E9ED,0xFFE9E9ED,0xFFE9E9ED,0xFFE9E9ED,0xFF96989D};
    private static final String FONT=ExampleFonts.FAMILY;
    private static final UiSymbol MIC=UiSymbol.vector(20,20,(g,c,s)->{g.strokeRoundRect(c,s,7,1,6,11,3);g.path().moveTo(4,8).lineTo(4,12).lineTo(10,16).lineTo(16,12).lineTo(16,8).stroke(c,s);g.line(c,s,10,16,10,20);});
    private static final UiSymbol HEADSET=UiSymbol.vector(20,20,(g,c,s)->{g.path().moveTo(2,16).lineTo(2,8).curveTo(2,0,18,0,18,8).lineTo(18,16).stroke(c,s);g.roundRect(c,1,11,5,7,2);g.roundRect(c,14,11,5,7,2);});
    private static final UiSymbol BELL=UiSymbol.vector(20,20,(g,c,s)->{g.path().moveTo(3,14).lineTo(5,11).lineTo(5,6).curveTo(5,0,15,0,15,6).lineTo(15,11).lineTo(17,14).close().fill(c);g.circle(c,10,17,2);});
    private static final UiSymbol HASH=UiSymbol.vector(20,20,(g,c,s)->{g.line(c,s,7,1,4,19);g.line(c,s,15,1,12,19);g.line(c,s,1,7,19,7);g.line(c,s,0,13,18,13);});
    private static final UiSymbol GIFT=UiSymbol.vector(20,20,(g,c,s)->{g.strokeRoundRect(c,s,3,8,14,11,1);g.line(c,s,1,8,19,8);g.line(c,s,10,8,10,19);g.path().moveTo(10,8).curveTo(-2,6,5,-4,10,8).curveTo(22,6,15,-4,10,8).stroke(c,s);});
    private static final UiSymbol SMILE=UiSymbol.vector(20,20,(g,c,s)->{g.circle(c,10,10,9);g.circle(INPUT,7,8,1);g.circle(INPUT,13,8,1);g.path().moveTo(5,12).curveTo(8,16,12,16,15,12).stroke(INPUT,s);});
    private static final UiSymbol PIN=UiSymbol.vector(20,20,(g,c,s)->{g.path().moveTo(6,2).lineTo(16,7).lineTo(12,10).lineTo(13,14).lineTo(3,9).lineTo(8,8).close().fill(c);g.line(c,s,8,11,4,19);});

    private static final class D {
        final UiContext ui;final UiCanvas g;final float w,h;
        D(UiContext ui){this.ui=ui;g=ui.canvas();w=ui.width();h=ui.height();}
        void fill(int c,float x,float y,float w,float h){g.fill(c,x,y,w,h);}
        void round(int c,float x,float y,float w,float h,float r){g.roundRect(c,x,y,w,h,r);}
        void line(int c,float x,float y,float xx,float yy){g.line(c,.6F,x,y,xx,yy);}
        void text(String v,float x,float y,float size,int c,boolean bold){g.text(v,x,y,size,c,bold?Font.BOLD:Font.PLAIN,FONT);}
        void icon(UiSymbol symbol,float x,float y,float size,int c){symbol.draw(g,c,x,y,size,1.5F);}
        void avatar(int who,float x,float y,float size){GatherAvatars.draw(g,who,x,y,size);}
        void landscape(float x,float y,float width,float height){
            g.withClip(x,y,width,height,()->{
                fill(0xFF153E52,x,y,width,height);
                g.circle(0xFFFFD69C,(x+width*.78F),(y+height*.27F),height*.13F);
                for(int i=0;i<8;i++)g.circle(0xFFBED9DC,(x+width*(.07F+i*.105F)),(y+height*(.12F+(i%3)*.06F)),.65F);
                g.polygon(0xFF2F6573,x,(y+height*.77F),(x+width*.25F),(y+height*.28F),(x+width*.55F),(y+height));
                g.polygon(0xFF427F84,(x+width*.27F),(y+height),(x+width*.66F),(y+height*.39F),(x+width),(y+height*.84F),(x+width),(y+height));
                fill(0xFF122F3C,x,y+height*.79F,width,height*.21F);
                for(int i=0;i<4;i++)round(0xFF46747B,x+width*(.48F+i*.03F),y+height*(.82F+i*.035F),width*(.18F-i*.035F),1,0);
                text("NIGHTFALL / FIELD NOTES",x+12,y+height-14,5.5F,0xFFB7D3DB,true);
            });
        }
        void button(String id,String label,float x,float y,float width,float height,boolean active,java.util.function.Consumer<D> paint){ui.paintedButton(id,label,id,x,y,width,height,3,true,(g,state)->{if(active||state.hovered())round(active?0xFF252528:0xFF161619,x,y,width,height,3);paint.accept(this);});}
        void action(String id,String label,UiSymbol icon,float x,float y,float size){button(id,label,x-3,y-3,size+6,size+6,false,d->d.icon(icon,x,y,size,MUTED));}
        void field(String id,String key,String placeholder,float x,float y,float w,float h,float size,int bg){
            var appearance=UiInputAppearance.defaults().background(bg).borderWidth(0).focusWidth(0).text(TEXT).placeholder(MUTED).radius(2);
            ui.textField(id,key,placeholder,x,y,w,h,"",2000,new UiInputStyle(size,Font.PLAIN,FONT,5,2,12).appearance(appearance));
        }
        void label(String text,float y){icon(UiIcon.CHEVRON_DOWN,46,y+1,5,MUTED);text(text,53,y,6.1F,MUTED,true);}
    }
    static String displayDm(String id){return switch(id){case "Maya"->"Mira";case "Alex"->"Theo";case "Jun"->"Jules";default->id;};}
    static String displayChannel(String id){return switch(id){case "general"->"lounge";case "showcase"->"creations";case "ideas-and-feedback"->"ideas";case "welcome"->"introductions";default->id;};}
    static void paint(UiContext ui,DiscordShowcase.Model m){
        D d=new D(ui);float w=d.w,h=d.h;
        d.fill(CHAT,0,0,w,h);d.fill(RAIL,0,0,w,22);
        d.g.circle(0xFFFF6058,9,11,3.8F);d.g.circle(0xFFFFBD2E,22,11,3.8F);d.g.circle(0xFF28C840,35,11,3.8F);
        d.text("‹",57,4,13,MUTED,false);d.text("›",72,4,13,0xFF48484D,false);
        d.text("Gather  /  "+m.serverName,w/2-43,7,7.5F,TEXT,true);
        d.icon(UiIcon.INBOX,w-37,7,10,MUTED);d.icon(UiIcon.HELP,w-17,7,10,MUTED);
        d.fill(RAIL,0,22,44,h-22);float side=174,left=44+side;
        d.fill(SIDE,44,22,side,h-22);d.line(0xFF242427,44,22,w,22);d.line(0xFF242427,left,22,left,h);
        d.button("home","Friends and direct messages",10,27,25,25,false,a->{a.round(0xFF202024,11,28,23,23,7);a.icon(UiIcon.PEOPLE,16,33,13,WHITE);});
        d.button("server",m.serverName,10,58,25,25,false,a->{a.round(BLUE,11,59,23,23,7);a.text("TS",16,65,10,WHITE,true);});
        d.round(WHITE,0,m.dm?32:63,2,14,1);
        d.g.circle(0xFF15191A,23,102,12);d.icon(UiIcon.PLUS,17,96,12,GREEN);
        if(m.dm)dmSidebar(d,m,left);else serverSidebar(d,m,left);
        if(m.dm&&m.friends){friends(d,m,left);profile(d,m,left);return;}
        float member=!m.dm&&m.members?155:0,right=w-member;
        d.fill(CHAT,left,22,w-left,28);d.line(0xFF242427,44,50,w,50);
        d.icon(m.dm?UiIcon.PEOPLE:HASH,left+12,31,11,MUTED);d.text(m.dm?displayDm(m.conversation):displayChannel(m.conversation),left+28,32,8.5F,WHITE,true);
        float tools=w-239;d.action("threads","Threads preview",HASH,tools,31,11);d.action("notifications","Notification settings",BELL,tools+22,31,11);d.action("pins","Pinned messages",PIN,tools+44,30,12);d.action("members","Toggle member list",UiIcon.PEOPLE,tools+66,31,11);
        d.g.strokeRoundRect(0xFF353539,.6F,(w-144),28,138,17,5);
        d.field("search","search","Search "+m.serverName,w-142,29,133,15,7.5F,CHAT);d.icon(UiIcon.SEARCH,w-19,32,10,MUTED);
        if(member>0)memberList(d,m,right);
        history(d,m,left,right);
        composer(d,m,left,right);
        profile(d,m,left);
    }
    private static void serverSidebar(D d,DiscordShowcase.Model m,float left){
        d.ui.paintedMenuButton("server-menu",m.serverName+" server menu",49,25,(left-55),23,3,true,List.of(new UiMenuItem("settings","Server Settings","settings"),new UiMenuItem("notifications","Notification Settings","notifications")),
            UiMenuStyle.builder().fontFamily(FONT).textSize(8.8F).rowHeight(23).padding(9,4)
                .background(0xFF111114).text(TEXT).highlight(BLUE).borderWidth(0).radius(4).rowRadius(3).rowInset(3).build(),(g,state)->{
            d.fill(state.pressed()?0xFF29292D:state.hovered()?0xFF161619:SIDE,49,25,left-55,23);
            d.text(g.ellipsize(m.serverName,Math.max(0,left-101),9,Font.BOLD,FONT),57,32,9,WHITE,true);
            d.icon(UiIcon.CHEVRON_DOWN,left-38,34,6,MUTED);d.icon(UiIcon.PEOPLE,left-20,32,11,MUTED);
        });
        d.icon(UiIcon.INBOX,58,65,10,MUTED);d.text("Events",75,66,8.5F,MUTED,false);
        d.icon(UiIcon.PLUS,58,88,10,MUTED);d.text("Community perks",75,89,8.5F,MUTED,false);
        d.line(0xFF242427,57,110,left-12,110);
        d.text("Text Channels",57,124,7.5F,MUTED,false);d.icon(UiIcon.CHEVRON_DOWN,112,126,5,MUTED);d.icon(UiIcon.PLUS,left-19,124,8,MUTED);
        channel(d,m,"general",136,left);channel(d,m,"announcements",158,left);channel(d,m,"showcase",180,left);channel(d,m,"ideas-and-feedback",202,left);channel(d,m,"welcome",224,left);channel(d,m,"shows",246,left);
        d.text("Voice Channels",57,282,7.5F,MUTED,false);d.icon(UiIcon.PLUS,left-19,282,8,MUTED);
        for(int i=0;i<2;i++){final int index=i;d.button(i==0?"voice":"voice-gaming","Preview voice channel",51,297+i*22,left-57,21,m.voice&&m.voiceChannel==i,a->{a.icon(HEADSET,58,302+index*22,10,MUTED);a.text(index==0?"General":"Workshop",75,302+index*22,8.5F,m.voice&&m.voiceChannel==index?GREEN:TEXT,false);});}
        if(m.voice){d.fill(0xFF0C1512,48,d.h-77,left-52,32);d.text("Voice preview · local only",57,d.h-67,7,GREEN,true);d.text("No microphone or connection",57,d.h-56,6,MUTED,false);}
    }
    private static void channel(D d,DiscordShowcase.Model m,String id,float y,float left){d.button("channel:"+id,"Open #"+displayChannel(id),52,y,left-58,21,m.conversation.equals(id),a->{a.icon(HASH,58,y+5,10,MUTED);a.text(displayChannel(id),75,y+5,8.5F,m.conversation.equals(id)?WHITE:MUTED,m.conversation.equals(id));if(m.conversation.equals(id)){a.icon(UiIcon.PEOPLE,left-29,y+5,8,MUTED);a.icon(UiIcon.SETTINGS,left-16,y+5,8,MUTED);}});}
    private static void profile(D d,DiscordShowcase.Model m,float left){
        d.round(CHAT,5,d.h-39,left-11,33,6);d.g.strokeRoundRect(0xFF242427,.6F,5,(d.h-39),(left-11),33,6);
        d.avatar(0,14,d.h-31,18);d.text("Rowan",37,d.h-31,8.5F,WHITE,true);d.text("Online",37,d.h-20,6.5F,MUTED,false);d.g.circle(GREEN,29,(d.h-15),3);
        d.action("mute","Toggle local mute",MIC,left-77,d.h-28,11);d.action("deaf","Toggle local deafen",HEADSET,left-47,d.h-28,11);d.action("appearance","User Settings",UiIcon.SETTINGS,left-24,d.h-28,11);
        if(m.mute)d.line(0xFFF04747,left-78,d.h-30,left-65,d.h-14);if(m.deaf)d.line(0xFFF04747,left-48,d.h-30,left-35,d.h-14);
    }
    private static void memberList(D d,DiscordShowcase.Model m,float x){
        d.fill(SIDE,x,50,d.w-x,d.h-50);d.line(0xFF242427,x,50,x,d.h);d.text("Host — 1",x+16,66,7,MUTED,true);d.avatar(0,x+12,81,19);d.text("Rowan",x+37,87,8,WHITE,false);d.g.polygon(0xFFF3BB45,(x+71),87,(x+73),90,(x+76),86,(x+78),90,(x+81),87,(x+80),93,(x+72),93);
        d.text("Members — 4",x+16,123,7,MUTED,true);
        int[] people={2,3,4,1};String[] status={"","Sketching ideas","","Building a cabin"};
        for(int i=0;i<people.length;i++){int who=people[i];float y=139+i*31;d.avatar(who,x+16,y,20);d.text(NAMES[who],x+42,y+4,8,NAME_COLORS[who],false);if(!status[i].isEmpty())d.text(status[i],x+42,y+14,6,MUTED,false);
            if(who<4){String dmKey=who==1?"Maya":who==2?"Alex":"Jun";d.ui.paintedButton("dm:"+dmKey,"Message "+NAMES[who],"dm:"+dmKey,(x+12),(y-1),(d.w-x-20),24,3,true,(g,state)->{});}
        }
        d.text("Offline — 1",x+16,281,7,MUTED,true);d.g.withOpacity(.42F,()->{d.avatar(5,x+16,298,20);d.text("Finn",x+42,304,8,MUTED,false);});
    }
    private static float height(D d,DiscordShowcase.Model m,DiscordShowcase.Message msg,float width){
        float size=m.textSize*.6F;int lines=msg.body.isEmpty()?0:d.g.wrapText(msg.body,width,size,Font.PLAIN,FONT).size();
        return (m.compact?26:38)+Math.max(0,lines-1)*12+(msg.attachment?171:0)+(msg.likes>0?18:0);
    }
    private static void history(D d,DiscordShowcase.Model m,float left,float right){
        var all=m.current();String q=d.ui.stringValue("search","").toLowerCase(Locale.ROOT);var visible=all.stream().filter(msg->(NAMES[msg.author]+" "+msg.body).toLowerCase(Locale.ROOT).contains(q)).toList();float tw=right-left-55,total=15;
        for(var msg:visible)total+=height(d,m,msg,tw);float hh=d.h-99;
        d.ui.scroll(m.historyId(),new UiRect(left,50,(right-left),hh),total,UiScrollFollow.WHEN_AT_END,sc->{D a=new D(sc);float y=68;
            for(var msg:visible){int index=all.indexOf(msg);float rowH=height(a,m,msg,tw),tx=left+(m.compact?12:46);
                if(!m.compact)a.avatar(msg.author,left+13,y-1,20);
                a.text(NAMES[msg.author],tx,y,8F,NAME_COLORS[msg.author],msg.author==0);float nameW=a.g.textWidth(NAMES[msg.author],8F,msg.author==0?Font.BOLD:Font.PLAIN,FONT);
                a.text("Today at "+msg.time,tx+nameW+4,y+2,6F,MUTED,false);
                if(m.mentions&&msg.body.startsWith("@members")){
                    float size=m.textSize*.6F,mw=a.g.textWidth("@members",size,Font.BOLD,FONT);
                    a.round(0xFF454350,tx,y+9,mw+2,12,2);a.text("@members",tx+1,y+10,size,0xFFD78B3B,true);
                    a.text(msg.body.substring(8).stripLeading(),tx+mw+4,y+10,size,TEXT,false);
                }else if(!msg.body.isEmpty())a.g.wrappedText(msg.body,tx,(y+10),tw,m.textSize*.6F,12,TEXT,Font.PLAIN,FONT);
                if(msg.attachment){
                    a.round(0xFF0B0B0D,tx,y+12,250,184,3);a.g.strokeRoundRect(0xFF29292D,.6F,tx,(y+12),250,184,3);
                    a.fill(0xFF38383D,tx,y+14,2,180);a.text("Trailside Studio",tx+10,y+22,6.5F,MUTED,false);
                    a.text("Nightfall — a palette for the next build",tx+10,y+37,8.5F,0xFF369CFF,true);
                    a.landscape(tx+10,y+55,230,130);
                }
                if(msg.likes>0){float ry=y+rowH-18;String id="react:"+index;a.button(id,"Toggle reaction",tx,ry,30,15,msg.reacted,b->{b.icon(UiIcon.PLUS,tx+4,ry+3,8,0xFFE5B456);b.text(""+msg.likes,tx+17,ry+3,7,TEXT,false);});}
                y+=rowH;
            }
            if(visible.isEmpty())a.text("No messages match your search.",left+20,70,9,MUTED,false);
        });d.ui.setValue("visible-message-count",visible.size());
    }
    private static void composer(D d,DiscordShowcase.Model m,float left,float right){
        d.round(INPUT,left+7,d.h-39,right-left-14,33,5);d.g.strokeRoundRect(0xFF2B2B31,.6F,(left+7),(d.h-39),(right-left-14),33,5);
        d.icon(UiIcon.PLUS,left+18,d.h-28,11,MUTED);
        String label=m.dm?displayDm(m.conversation):displayChannel(m.conversation);
        d.field("composer","draft:"+m.key(),"Message "+(m.dm?"@":"#")+label,left+39,d.h-34,Math.max(80,right-left-143),24,8.5F,INPUT);
        d.icon(GIFT,right-88,d.h-28,11,MUTED);d.round(MUTED,right-68,d.h-26,11,8,1);d.text("GIF",right-67,d.h-25,5,INPUT,true);
        d.action("send","Send local message",UiIcon.SEND,right-49,d.h-28,11);d.icon(SMILE,right-29,d.h-28,11,MUTED);
    }
    private static void dmSidebar(D d,DiscordShowcase.Model m,float left){
        d.field("find-dm","find-dm","Find or start a conversation",51,21,left-60,19,7.5F,0xFF101014);d.line(0xFF101014,44,46,left,46);
        d.button("friends","Friends",50,53,left-58,27,m.friends,a->{a.icon(UiIcon.PEOPLE,64,61,12,TEXT);a.text("Friends",91,63,9,TEXT,true);});d.text("DIRECT MESSAGES",62,95,6.5F,MUTED,true);d.icon(UiIcon.PLUS,left-22,94,8,MUTED);
        String[] keys={"Maya","Alex","Jun"};for(int i=0;i<3;i++){int who=i+1;String key=keys[i];float y=115+i*33;if(!NAMES[who].toLowerCase(Locale.ROOT).contains(d.ui.stringValue("find-dm","").toLowerCase(Locale.ROOT)))continue;
            d.button("dm:"+key,"Message "+NAMES[who],51,y,left-59,30,!m.friends&&m.conversation.equals(key),a->{a.avatar(who,61,y+4,22);a.text(NAMES[who],93,y+11,9,TEXT,false);});}
    }
    private static void friends(D d,DiscordShowcase.Model m,float left){
        d.fill(0xFF000000,left,47,d.w-left,d.h-47);
        float right=d.w-240;d.icon(UiIcon.PEOPLE,left+13,24,12,MUTED);d.text("Friends",left+30,25,9,WHITE,true);d.line(0xFF4D4E55,left+77,23,left+77,37);
        String[] tabs={"Online","All","Pending","Blocked","Add Friend"};float px=left+89;for(String tab:tabs){float width=tab.equals("Add Friend")?61:tab.equals("Pending")?45:37;final float tx=px;d.button("friend-tab:"+tab,tab,px,22,width,17,tab.equals(m.friendTab)&&!tab.equals("Add Friend"),a->{if(tab.equals("Add Friend"))a.round(0xFF248046,tx,23,width,15,3);a.text(tab,tx+5,26,8,tab.equals("Add Friend")?WHITE:MUTED,false);});px+=width+7;}
        d.line(0xFF202024,left,46,d.w,46);d.fill(0xFF000000,right,47,d.w-right,d.h-47);d.text("ACTIVE NOW",right+12,60,7,MUTED,true);d.text("It's quiet for now…",right+78,88,8,WHITE,true);d.text("When a friend starts an activity, we'll show it here.",right+14,106,7,MUTED,false);
        if(m.friendTab.equals("Add Friend")){
            d.text("ADD FRIEND",left+20,60,9,WHITE,true);d.text("You can add friends with their Gather username.",left+20,81,7.8F,MUTED,false);
            d.round(0xFF202024,left+20,102,right-left-40,36,3);d.field("friend-name","friend-name","Enter a username",left+26,109,right-left-163,23,9,0xFF202024);
            d.button("add-friend","Send local friend request",right-136,109,108,22,false,a->{a.round(BLUE,right-136,109,108,22,3);a.text("Send Friend Request",right-125,116,7,WHITE,false);});
            d.landscape(left+65,232,281,130);d.text("Good conversations start with a hello.",left+80,385,8,MUTED,false);
            String status=d.ui.stringValue("friend-status","");if(!status.isEmpty())d.text(status,left+21,151,8,GREEN,false);
        }else{
            d.text(m.friendTab.toUpperCase(Locale.ROOT),left+20,65,7,MUTED,true);if(m.friendTab.equals("Blocked"))d.text("You haven't blocked anyone.",left+20,100,9,MUTED,false);
            else if(m.friendTab.equals("Pending"))d.text(d.ui.stringValue("friend-status","").isEmpty()?"No pending friend requests.":d.ui.stringValue("friend-status",""),left+20,100,9,MUTED,false);
            else for(int i=1;i<=3;i++){int who=i;float y=89+(i-1)*43;String key=new String[]{"Maya","Alex","Jun"}[i-1];d.button("dm:"+key,"Open direct message",left+15,y,right-left-30,39,false,a->{a.avatar(who,left+24,y+6,25);a.text(NAMES[who],left+60,y+8,9,WHITE,true);a.text("Online",left+60,y+23,7,MUTED,false);a.icon(UiIcon.MAIL,right-45,y+13,13,MUTED);});d.line(0xFF45464C,left+20,y+41,right-20,y+41);}
        }
    }
}
