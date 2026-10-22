package dev.pane.demo;

import dev.pane.ui.*;
import java.awt.Font;
import java.util.*;
import net.minecraft.client.gui.screens.Screen;

/** A native PaneLib consumer: Gmail's desktop visual language, entirely local sample mail. */
public final class GmailShowcase {
    static final int BG=0xFFF6F8FC, WHITE=0xFFFFFFFF, INK=0xFF202124, MUTED=0xFF5F6368,
        BLUE=0xFF0B57D0, SEARCH=0xFFEAF1FB, SELECT=0xFFD3E3FD, LINE=0xFFE1E5EA;
    static final String FONT=ExampleFonts.FAMILY;
    private static final UiInputStyle INPUT=new UiInputStyle(14,Font.PLAIN,FONT,12,6,22);
    private static final UiInputStyle COMPOSER_INPUT=INPUT.appearance(UiInputAppearance.defaults()
        .background(WHITE).borderWidth(0).radius(0).focus(BLUE).focusWidth(1)
        .text(INK).placeholder(MUTED).caret(BLUE));
    static final class Mail {
        final int id; final String sender,subject,preview,time,category,label;
        boolean unread,star,selected,archived,trash,sent;
        Mail(int id,String sender,String subject,String preview,String time,String category,String label,boolean unread){
            this.id=id;this.sender=sender;this.subject=subject;this.preview=preview;this.time=time;this.category=category;this.label=label;this.unread=unread;
        }
    }
    static final class Model {
        final List<Mail> mail=new ArrayList<>(); String folder="Inbox",category="Primary"; int opened=-1; boolean compact,compose;
        Model(){
            String[][] rows={
                {"Alex Morgan","A few ideas for the weekend","Hey! I found a great little spot for Saturday. Are you free around 10?","10:42 AM","",""},
                {"GitHub","[PaneLib] Your build passed","All checks have passed for the latest changes. View the workflow run.","10:18 AM","Projects",""},
                {"Maya Chen","Re: The new inbox design","This is looking really good. I left a few notes on spacing and typography.","9:56 AM","Projects",""},
                {"Google Calendar","Reminder: Design catch-up @ 11:00 AM","You have an upcoming event. Join with Google Meet.","9:30 AM","",""},
                {"Sam, me 3","Photos from the trip","Sam: Finally got these off my camera! That last evening was something else.","8:47 AM","Personal",""},
                {"Notion","Your weekly workspace digest","Here's what happened in your workspace this week. 4 pages were updated.","8:12 AM","",""},
                {"Jamie Rivera","Coffee tomorrow?","There's a new place on the corner that looks pretty good. My treat.","Oct 19","Personal",""},
                {"Figma","Your files are ready for review","Maya invited you to review Inbox exploration in the Design team.","Oct 19","Projects",""},
                {"Daniel Brooks","Re: Friday's playlist","That second track is so good. Adding it to the rotation immediately.","Oct 19","",""},
                {"Linear","Your team’s weekly summary","12 issues completed, 3 projects moved forward. Here's the full picture.","Oct 19","Projects",""},
                {"me, Alex 2","Dinner plans","Me: Sounds perfect. I'll book a table for seven. See you there!","Oct 18","Personal",""},
                {"Google Drive","A document was shared with you","Maya Chen shared the document “Fall project notes” with you.","Oct 18","",""},
                {"Sl0oth’s reading list","Something worth saving","A handful of interesting reads for a quiet Sunday morning.","Oct 18","",""},
                {"Casey Lee","Quick question about the project","Hey Sl0oth, do you have a minute to look at the latest version?","Oct 17","Projects",""},
                {"The Neighborhood","October community update","A few dates for your calendar, plus news from around the neighborhood.","Oct 17","",""},
                {"Jordan Park","Re: Let’s catch up","It's been way too long. Next Tuesday works for me if you're around.","Oct 16","Personal",""},
                {"Dropbox","Your files have synced","Everything is up to date. You can access your files from any device.","Oct 16","",""},
                {"Taylor Wilson","Notes from our meeting","Thanks for your time earlier. Here are the next steps we talked about.","Oct 15","Projects",""},
                {"Patagonia","A little more time outside","Meet the layers made for crisp mornings and longer trails.","9:15 AM","","Promotions"},
                {"Spotify","Your next favorite is waiting","Fresh releases and a few recommendations just for you.","Oct 19","","Promotions"},
                {"Discord","Your friends are talking","Catch up on the latest conversations from your communities.","8:30 AM","","Social"}
            };
            for(int i=0;i<rows.length;i++){var r=rows[i];mail.add(new Mail(i,r[0],r[1],r[2],r[3],r[5].isEmpty()?"Primary":r[5],r[4],i<4||i==7));}
            mail.get(2).star=true;mail.get(6).star=true;
        }
        List<Mail> visible(String query){String q=query.toLowerCase(Locale.ROOT).trim();return mail.stream().filter(m->
            (folder.equals("Trash")?m.trash:!m.trash)&&switch(folder){
                case "Starred"->m.star;case "Sent"->m.sent;case "All mail"->true;case "Trash"->true;
                case "Projects","Personal"->m.label.equals(folder);case "Drafts","Snoozed"->false;
                default->!m.archived&&!m.sent&&(q.isEmpty()?m.category.equals(category):true);
            }).filter(m->(m.sender+" "+m.subject+" "+m.preview).toLowerCase(Locale.ROOT).contains(q)).toList();}
    }
    public static UiScreen createScreen(Screen parent){return createScreen(parent,new Model());}
    static UiScreen createScreen(Screen parent,Model model){
        var theme=new UiTheme(BG,WHITE,SEARCH,SEARCH,INK,MUTED,BLUE,WHITE,8,16,24,1,0,FONT);
        var doc=UiDocument.builder("Pane Mail • PaneLib sample inbox").theme(theme).shell(UiShell.NONE).scaling(UiScaling.fixed()).renderSettings(UiRenderSettings.typography())
            .page(new UiPage("mail","Inbox","",BLUE,ui->paint(ui,model))).build();
        return UiScreen.create(doc,NativeOverlayRenderer.NONE,(s,event)->{
            var type=event.type(); var key=event.key(); var value=event.value();
            if(type!=UiEventType.ACTION)return;
            if(key.startsWith("folder:")){model.folder=key.substring(7);model.opened=-1;s.scrollTo("messages",0);}
            else if(key.startsWith("tab:")){model.category=key.substring(4);model.opened=-1;s.scrollTo("messages",0);}
            else if(key.startsWith("open:")){model.opened=Integer.parseInt(key.substring(5));model.mail.get(model.opened).unread=false;}
            else if(key.startsWith("star:")){var m=model.mail.get(Integer.parseInt(key.substring(5)));m.star=!m.star;}
            else if(key.startsWith("select:")){var m=model.mail.get(Integer.parseInt(key.substring(7)));m.selected=!m.selected;}
            else switch(key){
                case "menu"->model.compact=!model.compact;
                case "back"->model.opened=-1;
                case "compose"->model.compose=true;
                case "close-compose"->model.compose=false;
                case "discard"->{model.compose=false;s.setValue("to","");s.setValue("subject","");s.setValue("body","");}
                case "select-all"->{var rows=model.visible(s.stringValue("query",""));boolean all=rows.stream().allMatch(m->m.selected);rows.forEach(m->m.selected=!all);}
                case "archive","delete","read"->{for(var m:model.mail)if(m.selected||m.id==model.opened){if(key.equals("archive"))m.archived=true;else if(key.equals("delete"))m.trash=true;else m.unread=false;m.selected=false;}model.opened=-1;}
                case "send"->{
                    if(s.stringValue("to","").isBlank()){s.setValue("notice","Add a recipient to send this sample message.");break;}
                    var m=new Mail(model.mail.size(),"me",s.stringValue("subject","").isBlank()?"(no subject)":s.stringValue("subject",""),s.stringValue("body",""),"Now","Primary","",false);
                    m.sent=true;model.mail.add(m);model.compose=false;s.setValue("to","");s.setValue("subject","");s.setValue("body","");s.setValue("notice","Message saved in Sent · local demo only");
                }
                case "dismiss"->s.setValue("notice","");
                case "refresh"->s.setValue("notice","You're up to date · sample inbox");
            }
            s.invalidate();
        },UiScreenOptions.builder().parent(parent).backdrop(UiBackdrop.NONE).onClose(s->{
            if(model.compose){model.compose=false;s.invalidate();return true;}
            if(model.opened>=0){model.opened=-1;s.invalidate();return true;}return false;
        }).build());
    }
    private static void text(UiCanvas g,String t,float x,float y,float size,int color,boolean bold){g.text(t,x,y,size,color,bold?Font.BOLD:Font.PLAIN,FONT);}
    private static void hit(UiContext ui,String id,float x,float y,float w,float h){ui.paintedButton(id,id.replace(':', ' '),id,x,y,w,h,4,true,(g,state)->{});}
    private static void control(UiContext ui,String id,String icon,float x,float y){
        String label=switch(id){case "close-compose" -> "Close composer";case "discard" -> "Discard draft";default -> id.replace('-', ' ');};
        ui.paintedButton(id,label,id,x,y,32,32,16,true,(g,state)->{
            if(state.hovered()||state.pressed())g.circle(state.pressed()?0xFFD3E3FD:0xFFE8EAED,x+16,y+16,16);
            icon(g,icon,x+6,y+6,MUTED);
        });
    }
    private static void paint(UiContext ui,Model m){
        var g=ui.canvas();float w=ui.width(),h=ui.height();boolean narrow=w<900||m.compact;float left=narrow?72:256,right=w-56;
        g.fill(BG,0,0,w,h);
        control(ui,"menu","menu",14,16);
        // Original Pane Mail badge, drawn with the library's envelope icon.
        g.roundRect(BLUE,65,14,36,36,10);
        UiIcon.MAIL.draw(g,WHITE,71,20,24,1.7F);
        if(!narrow)text(g,"Pane Mail",111,18,23,INK,false);
        float sx=Math.max(left,140),sw=Math.max(140,Math.min(720,w-sx-200));
        g.roundRect(SEARCH,sx,8,sw,48,24);icon(g,"search",sx+17,22,MUTED);
        ui.textField("search","query","Search mail",sx+46,12,sw-92,40,"",256,INPUT.font(FONT,16,Font.PLAIN));
        icon(g,"tune",sx+sw-34,22,MUTED);
        icon(g,"help",w-166,23,MUTED);icon(g,"settings",w-128,23,MUTED);icon(g,"apps",w-88,23,MUTED);
        g.circle(0xFF38675A,w-30,32,17);text(g,"S",w-36,21,19,WHITE,false);
        ui.paintedButton("compose","Compose message","compose",12,80,narrow?48:142,56,16,true,(canvas,state)->{canvas.roundRect(0xFFC2E7FF,12,80,narrow?48:142,56,16);icon(canvas,"pen",27,97,INK);if(!narrow)text(canvas,"Compose",62,99,14,INK,true);});
        String[] folders={"Inbox","Starred","Snoozed","Sent","Drafts","All mail","Trash"};String[] icons={"inbox","star","clock","send","file","mail","trash"};
        for(int i=0;i<folders.length;i++){
            float y=151+i*32;boolean active=m.folder.equals(folders[i]);String id="folder:"+folders[i];
            final int index=i;
            ui.paintedButton(id,folders[i],id,12,y,narrow?48:232,32,16,true,(canvas,state)->{
                if(active||state.hovered())canvas.roundRect(active?SELECT:0xFFE8EDF5,12,y,narrow?48:232,32,16);
                icon(canvas,icons[index],28,y+6,active?INK:MUTED);
                if(!narrow){text(canvas,folders[index],64,y+8,14,INK,active);if(index==0)text(canvas,""+m.mail.stream().filter(a->a.unread&&!a.archived&&!a.trash&&!a.sent).count(),214,y+9,12,INK,true);}
            });
        }
        if(!narrow){text(g,"Labels",28,405,16,INK,true);icon(g,"plus",218,405,MUTED);
            for(int i=0;i<2;i++){String label=i==0?"Projects":"Personal";float y=442+i*32;icon(g,"tag",28,y,i==0?0xFF5F8CCC:0xFF60A78E);text(g,label,64,y+2,14,INK,false);hit(ui,"folder:"+label,12,y-5,232,32);}
            text(g,"PaneLib · sample inbox",24,h-28,11,MUTED,false);
        }
        g.roundRect(WHITE,left,64,right-left,h-80,16);
        for(int i=0;i<4;i++){
            float y=91+i*54,x=w-36;
            String tool=i==0?"file":i==1?"pen":i==2?"checked":"people";
            icon(g,tool,x,y,MUTED);
        }
        g.line(LINE,1,w-40,317,w-20,317);icon(g,"plus",w-36,342,MUTED);
        if(m.opened<0)inbox(ui,m,left,right,h);else message(ui,m,left,right,h);
        String notice=ui.stringValue("notice","");if(!notice.isEmpty()){g.roundRect(0xFF323232,left+20,h-65,Math.min(470,right-left-40),40,5);text(g,notice,left+34,h-53,12,WHITE,false);hit(ui,"dismiss",left+20,h-65,Math.min(470,right-left-40),40);}
        if(m.compose)composer(ui,m,left,right,h);
    }
    private static void inbox(UiContext ui,Model m,float left,float right,float h){
        var g=ui.canvas();var rows=m.visible(ui.stringValue("query",""));
        control(ui,"select-all","box",left+8,72);control(ui,"refresh","refresh",left+54,72);
        boolean selected=rows.stream().anyMatch(a->a.selected);
        if(selected){control(ui,"archive","archive",left+94,72);control(ui,"delete","trash",left+132,72);control(ui,"read","mail",left+170,72);}
        else icon(g,"more",left+106,78,MUTED);
        text(g,rows.isEmpty()?"0 of 0":"1–"+rows.size()+" of "+rows.size(),right-171,83,12,MUTED,false);
        icon(g,"left",right-79,79,0xFFB7BCC3);icon(g,"right",right-43,79,0xFFB7BCC3);
        float top=112;boolean tabs=m.folder.equals("Inbox")&&ui.stringValue("query","").isEmpty();
        if(tabs){float tw=Math.min(250,(right-left)/3);String[] names={"Primary","Promotions","Social"};String[] icons={"inbox","tag","people"};
            for(int i=0;i<3;i++){float x=left+i*tw;boolean active=m.category.equals(names[i]);String id="tab:"+names[i];
                if(ui.hovered(id))g.fill(0xFFF2F5FC,x,112,tw,56);icon(g,icons[i],x+20,130,active?BLUE:MUTED);text(g,names[i],x+54,132,14,active?BLUE:MUTED,active);
                if(active)g.roundRect(BLUE,x+8,165,tw-16,3,2);hit(ui,id,x,112,tw,56);
            }top=168;
        }
        final float y0=top;ui.scroll("messages",new UiRect(left,y0,right-left,Math.max(32,h-y0-66)),rows.size()*40,sc->{
            for(int i=0;i<rows.size();i++)row(sc,rows.get(i),left,y0+i*40,right-left);
        });
        if(rows.isEmpty())text(g,"No conversations found.",left+30,top+45,14,MUTED,false);
        text(g,"0.2 GB of 15 GB used",left+16,h-46,11,MUTED,false);
        text(g,"Terms · Privacy · Program Policies",left+(right-left)/2-85,h-46,10,MUTED,false);
        if(right-left>700)text(g,"Last account activity: 2 minutes ago",right-192,h-46,10,MUTED,false);
    }
    private static void row(UiContext ui,Mail m,float x,float y,float w){
        var g=ui.canvas();String id="open:"+m.id;boolean hover=ui.hovered(id);int bg=m.selected?0xFFC2DBFF:m.unread?WHITE:0xFFF2F6FC;
        g.fill(hover?0xFFE9EFF8:bg,x,y,w,40);g.line(LINE,.6F,x,y+39.5F,x+w,y+39.5F);
        ui.paintedButton(id,"Open message from "+m.sender+": "+m.subject,id,x+72,y,w-72,40,0,true,(canvas,state)->{});icon(g,m.selected?"checked":"box",x+15,y+10,m.selected?BLUE:0xFF8B9096);ui.paintedButton("select:"+m.id,"Select message from "+m.sender,"select:"+m.id,x+8,y+4,32,32,4,true,(canvas,state)->{});
        icon(g,"star",x+45,y+10,m.star?0xFFF4B400:0xFF9AA0A6);ui.paintedButton("star:"+m.id,(m.star?"Unstar":"Star")+" message from "+m.sender,"star:"+m.id,x+40,y+4,28,32,4,true,(canvas,state)->{});
        float senderW=Math.min(176,w*.23F),subjectX=x+80+senderW;
        g.withClip(x+80,y,senderW-10,40,()->text(g,m.sender,x+80,y+12,14,INK,m.unread));
        g.withClip(subjectX,y,Math.max(20,x+w-84-subjectX),40,()->{
            float pos=subjectX;
            if(!m.label.isEmpty()){float lw=g.textWidth(m.label,11,Font.PLAIN,FONT)+12;g.roundRect(0xFFE8EAED,pos,y+12,lw,17,3);text(g,m.label,pos+6,y+14,11,MUTED,false);pos+=lw+8;}
            text(g,m.subject,pos,y+12,14,INK,m.unread);pos+=g.textWidth(m.subject,14,m.unread?Font.BOLD:Font.PLAIN,FONT)+6;
            text(g,"– "+m.preview,pos,y+12,14,MUTED,false);
        });
        float tw=g.textWidth(m.time,12,m.unread?Font.BOLD:Font.PLAIN,FONT);text(g,m.time,x+w-tw-20,y+13,12,INK,m.unread);
    }
    private static void message(UiContext ui,Model model,float left,float right,float h){
        var g=ui.canvas();Mail m=model.mail.get(model.opened);control(ui,"back","left",left+10,72);control(ui,"archive","archive",left+54,72);control(ui,"delete","trash",left+96,72);
        text(g,m.subject,left+65,137,24,INK,false);g.roundRect(0xFFE8EAED,left+66,176,43,19,3);text(g,"Inbox",left+73,178,11,MUTED,false);
        g.circle(0xFF7E57C2,left+30,234,19);text(g,m.sender.substring(0,1).toUpperCase(),left+24,223,19,WHITE,false);
        text(g,m.sender,left+65,218,14,INK,true);text(g,"<hello@example.com>",left+65+g.textWidth(m.sender,14,Font.BOLD,FONT)+10,219,12,MUTED,false);
        text(g,"to me",left+65,240,12,MUTED,false);text(g,m.time,right-90,220,12,MUTED,false);
        g.wrappedText("Hi Sl0oth,\n\n"+m.preview+"\n\nThis is a local sample conversation for the PaneLib inbox.\n\nThanks,\n"+m.sender,left+65,290,right-left-110,14,24,INK,Font.PLAIN,FONT);
        g.strokeRoundRect(0xFF747775,1,left+65,h-100,110,36,18);icon(g,"left",left+78,h-92,MUTED);text(g,"Reply",left+107,h-90,14,MUTED,false);hit(ui,"compose",left+65,h-100,110,36);
    }
    private static void composer(UiContext ui,Model m,float left,float right,float h){
        var g=ui.canvas();float cw=Math.min(520,right-left-24),x=right-cw-14,y=Math.max(120,h-530),ch=h-y-16;
        g.shadow(0x35000000,18,0,2,x,y,cw,ch,12);
        ui.hit("compose-surface","compose-surface",x,y,cw,ch);g.roundRect(WHITE,x,y,cw,ch,10);g.roundRect(SEARCH,x,y,cw,40,10);g.fill(SEARCH,x,y+20,cw,20);
        text(g,"New Message",x+16,y+13,14,INK,true);control(ui,"close-compose","close",x+cw-36,y+4);
        ui.textField("to","to","Recipients",x+8,y+48,cw-16,35,"",256,COMPOSER_INPUT);
        ui.textField("subject","subject","Subject",x+8,y+89,cw-16,35,"",256,COMPOSER_INPUT);
        ui.textArea("body","body","Write your message…",x+8,y+131,cw-16,Math.max(40,ch-199),"",8000,COMPOSER_INPUT);
        g.line(LINE,1,x+16,y+85,x+cw-16,y+85);
        g.line(LINE,1,x+16,y+127,x+cw-16,y+127);
        ui.paintedButton("send","Send local sample","send",x+16,h-67,98,36,18,true,(canvas,state)->{canvas.roundRect(BLUE,x+16,h-67,98,36,18);text(canvas,"Send",x+41,h-57,14,WHITE,true);});
        text(g,"Local demo",x+128,h-55,12,MUTED,false);control(ui,"discard","trash",x+cw-46,h-65);
    }
    private static void icon(UiCanvas g,String name,float x,float y,int c){
        UiIcon symbol = switch (name) {
            case "menu" -> UiIcon.MENU;
            case "box" -> UiIcon.BOX;
            case "checked" -> UiIcon.CHECKED_BOX;
            case "star" -> c == 0xFFF4B400 ? UiIcon.STAR_FILLED : UiIcon.STAR;
            case "mail" -> UiIcon.MAIL;
            case "inbox" -> UiIcon.INBOX;
            case "archive" -> UiIcon.ARCHIVE;
            case "search" -> UiIcon.SEARCH;
            case "left" -> UiIcon.CHEVRON_LEFT;
            case "right" -> UiIcon.CHEVRON_RIGHT;
            case "send" -> UiIcon.SEND;
            case "tag" -> UiIcon.TAG;
            case "clock" -> UiIcon.CLOCK;
            case "help" -> UiIcon.HELP;
            case "settings" -> UiIcon.SETTINGS;
            case "refresh" -> UiIcon.REFRESH;
            case "plus" -> UiIcon.PLUS;
            case "close" -> UiIcon.CLOSE;
            case "trash" -> UiIcon.TRASH;
            case "file" -> UiIcon.FILE;
            case "pen" -> UiIcon.PEN;
            case "apps" -> UiIcon.APPS;
            case "more" -> UiIcon.MORE_VERTICAL;
            case "tune" -> UiIcon.TUNE;
            case "people" -> UiIcon.PEOPLE;
            default -> throw new IllegalArgumentException("Unknown icon: " + name);
        };
        symbol.draw(g,c,x,y,20,1.6F);
    }
}
