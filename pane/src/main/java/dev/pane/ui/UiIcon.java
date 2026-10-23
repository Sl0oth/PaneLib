package dev.pane.ui;

/** Small code-drawn icon set. Consumers can still draw any custom icon with {@link UiPath}. */
public enum UiIcon implements UiSymbol {
    /** A check mark. */
    CHECK,
    /** A cross, for closing. */
    CLOSE,
    /** A plus sign. */
    PLUS,
    /** A minus sign. */
    MINUS,
    /** A magnifying glass. */
    SEARCH,
    /** A chevron pointing down. */
    CHEVRON_DOWN,
    /** An arrow pointing right. */
    ARROW_RIGHT,
    /** Three horizontal lines. */
    MENU,
    /** A house, for home. */
    HOME,
    /** An information sign. */
    INFO,
    /** A warning triangle. */
    WARNING,
    /** A play triangle. */
    PLAY,
    /** Two pause bars. */
    PAUSE,
    /** Three dots in a row. */
    MORE,
    /** Two overlapping sheets, for copying. */
    COPY,
    /** A bookmark ribbon. */
    BOOKMARK,
    /** Side-by-side columns. */
    COLUMNS,
    /** A bin, for deleting. */
    TRASH,
    /** An empty checkbox. */
    BOX,
    /** A ticked checkbox. */
    CHECKED_BOX,
    /** An outlined star. */
    STAR,
    /** An envelope. */
    MAIL,
    /** An inbox tray. */
    INBOX,
    /** An archive box. */
    ARCHIVE,
    /** A chevron pointing left. */
    CHEVRON_LEFT,
    /** A chevron pointing right. */
    CHEVRON_RIGHT,
    /** A paper plane, for sending. */
    SEND,
    /** A label tag. */
    TAG,
    /** A clock face. */
    CLOCK,
    /** A question mark in a circle. */
    HELP,
    /** A gear, for settings. */
    SETTINGS,
    /** A circular arrow, for refreshing. */
    REFRESH,
    /** A document page. */
    FILE,
    /** A pen, for editing. */
    PEN,
    /** A grid of squares. */
    APPS,
    /** Three dots in a column. */
    MORE_VERTICAL,
    /** Adjustment sliders. */
    TUNE,
    /** Two people. */
    PEOPLE,
    /** A filled star. */
    STAR_FILLED;

    /** Draws this icon inside the supplied square. */
    public void draw(UiCanvas canvas, int color, float x, float y, float size, float stroke) {
        if (canvas == null || size <= 0) return;
        float scale = size / 24F;
        canvas.withTranslation(x, y, () -> canvas.withScale(scale, scale, 0, 0,
            () -> draw24(canvas, color, Math.max(.75F, stroke / scale))));
    }

    private void draw24(UiCanvas g, int color, float s) {
        switch (this) {
            case BOX, CHECKED_BOX, STAR, MAIL, INBOX, ARCHIVE, CHEVRON_LEFT, CHEVRON_RIGHT, SEND, TAG, CLOCK, HELP, SETTINGS, REFRESH, FILE, PEN, APPS, MORE_VERTICAL, TUNE, PEOPLE, STAR_FILLED -> g.withScale(1.2F, 1.2F, 0, 0, () -> draw20(g, color, s / 1.2F));
            case CHECK -> g.path().moveTo(4, 12).lineTo(9, 17).lineTo(20, 6).stroke(color, s);
            case CLOSE -> {
                g.line(color, s, 5, 5, 19, 19);
                g.line(color, s, 19, 5, 5, 19);
            }
            case PLUS -> {
                g.line(color, s, 12, 4, 12, 20);
                g.line(color, s, 4, 12, 20, 12);
            }
            case MINUS -> g.line(color, s, 4, 12, 20, 12);
            case SEARCH -> {
                g.strokeCircle(color, s, 10, 10, 6);
                g.line(color, s, 14.5F, 14.5F, 20, 20);
            }
            case CHEVRON_DOWN -> g.path().moveTo(5, 9).lineTo(12, 16).lineTo(19, 9).stroke(color, s);
            case ARROW_RIGHT -> {
                g.line(color, s, 4, 12, 20, 12);
                g.path().moveTo(14, 6).lineTo(20, 12).lineTo(14, 18).stroke(color, s);
            }
            case MENU -> {
                g.line(color, s, 4, 6, 20, 6);
                g.line(color, s, 4, 12, 20, 12);
                g.line(color, s, 4, 18, 20, 18);
            }
            case HOME -> g.path().moveTo(3, 11).lineTo(12, 3).lineTo(21, 11)
                .moveTo(6, 9).lineTo(6, 21).lineTo(18, 21).lineTo(18, 9).stroke(color, s);
            case INFO -> {
                g.strokeCircle(color, s, 12, 12, 9);
                g.line(color, s, 12, 10, 12, 17);
                g.circle(color, 12, 7, s / 2);
            }
            case WARNING -> {
                g.path().moveTo(12, 3).lineTo(22, 21).lineTo(2, 21).close().stroke(color, s);
                g.line(color, s, 12, 9, 12, 15);
                g.circle(color, 12, 18, s / 2);
            }
            case PLAY -> g.path().moveTo(7, 4).lineTo(20, 12).lineTo(7, 20).close().fill(color);
            case PAUSE -> {
                g.roundRect(color, 6, 4, 4, 16, 1);
                g.roundRect(color, 14, 4, 4, 16, 1);
            }
            case MORE -> {
                g.circle(color, 5, 12, 1.5F);
                g.circle(color, 12, 12, 1.5F);
                g.circle(color, 19, 12, 1.5F);
            }
            case COPY -> {
                g.strokeRoundRect(color, s, 8, 8, 12, 12, 2);
                g.path().moveTo(5, 16).lineTo(4, 16).lineTo(4, 4).lineTo(16, 4).lineTo(16, 5).stroke(color, s);
            }
            case BOOKMARK -> g.path().moveTo(6, 3).lineTo(18, 3).lineTo(18, 21)
                .lineTo(12, 17).lineTo(6, 21).close().stroke(color, s);
            case COLUMNS -> {
                g.strokeRoundRect(color, s, 3, 4, 18, 16, 2);
                g.line(color, s, 11, 4, 11, 20);
                g.line(color, s, 16, 4, 16, 20);
            }
            case TRASH -> {
                g.path().moveTo(5, 7).lineTo(19, 7).moveTo(9, 7).lineTo(9, 4)
                    .lineTo(15, 4).lineTo(15, 7).moveTo(7, 7).lineTo(8, 21)
                    .lineTo(16, 21).lineTo(17, 7).stroke(color, s);
                g.line(color, s, 11, 11, 11, 17);
                g.line(color, s, 14, 11, 14, 17);
            }
        }
    }

    private void draw20(UiCanvas g, int c, float s) {
        float x = 0, y = 0;
        switch(this){
            case BOX,CHECKED_BOX->{g.strokeRoundRect(c,s,x+3,y+3,14,14,2);if(this == CHECKED_BOX){g.line(c,s,x+5,y+10,x+9,y+14);g.line(c,s,x+9,y+14,x+15,y+6);}}
            case STAR,STAR_FILLED->{float[] p=new float[20];for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?9:4;p[i*2]=x+10+(float)Math.cos(a)*r;p[i*2+1]=y+10+(float)Math.sin(a)*r;}if(this == STAR_FILLED)g.polygon(c,p);else for(int i=0;i<10;i++)g.line(c,s,p[i*2],p[i*2+1],p[(i*2+2)%20],p[(i*2+3)%20]);}
            case MAIL,INBOX,ARCHIVE->{g.strokeRoundRect(c,s,x+2,y+4,16,13,2);if(this == MAIL){g.line(c,s,x+2,y+5,x+10,y+11);g.line(c,s,x+10,y+11,x+18,y+5);}else {g.line(c,s,x+2,y+11,x+7,y+11);g.line(c,s,x+7,y+11,x+9,y+14);g.line(c,s,x+9,y+14,x+12,y+14);g.line(c,s,x+12,y+14,x+14,y+11);g.line(c,s,x+14,y+11,x+18,y+11);}}
            case CHEVRON_LEFT,CHEVRON_RIGHT->{float a=this == CHEVRON_LEFT?1:-1;g.line(c,s,x+10+a*3,y+4,x+10-a*3,y+10);g.line(c,s,x+10-a*3,y+10,x+10+a*3,y+16);}
            case SEND->{g.line(c,s,x+2,y+2,x+19,y+10);g.line(c,s,x+19,y+10,x+2,y+18);g.line(c,s,x+2,y+18,x+5,y+10);g.line(c,s,x+5,y+10,x+2,y+2);g.line(c,s,x+5,y+10,x+15,y+10);}
            case TAG->{g.line(c,s,x+2,y+4,x+12,y+4);g.line(c,s,x+12,y+4,x+19,y+10);g.line(c,s,x+19,y+10,x+12,y+16);g.line(c,s,x+12,y+16,x+2,y+16);g.line(c,s,x+2,y+16,x+2,y+4);g.circle(c,x+6,y+10,1.5F);}
            case CLOCK,HELP,SETTINGS,REFRESH->{g.strokeRoundRect(c,s,x+2,y+2,16,16,8);if(this == CLOCK){g.line(c,s,x+10,y+5,x+10,y+10);g.line(c,s,x+10,y+10,x+14,y+12);}else if(this == HELP)g.text("?",x+6,y+2,14,c,java.awt.Font.BOLD);else if(this == REFRESH)g.polygon(c,x+13,y,x+20,y+2,x+17,y+8);else {g.strokeRoundRect(c,s,x+7,y+7,6,6,3);for(int i=0;i<8;i++){double a=i*Math.PI/4;g.line(c,s,x+10+(float)Math.cos(a)*8,y+10+(float)Math.sin(a)*8,x+10+(float)Math.cos(a)*11,y+10+(float)Math.sin(a)*11);}}}
            case FILE->{g.strokeRoundRect(c,s,x+4,y+2,12,16,1);g.line(c,s,x+7,y+8,x+13,y+8);g.line(c,s,x+7,y+12,x+13,y+12);}
            case PEN->{g.line(c,s * 1.8F,x+4,y+15,x+15,y+4);g.polygon(c,x+2,y+18,x+4,y+12,x+8,y+16);g.line(c,s,x+13,y+2,x+18,y+7);}
            case APPS->{for(int a=0;a<3;a++)for(int b=0;b<3;b++)g.circle(c,x+3+a*6,y+3+b*6,1.6F);}
            case MORE_VERTICAL->{for(int i=0;i<3;i++)g.circle(c,x+10,y+4+i*6,1.5F);}
            case TUNE->{for(int i=0;i<3;i++){g.line(c,s,x+1,y+4+i*6,x+19,y+4+i*6);g.strokeRoundRect(c,s,x+(i==1?4:10),y+1+i*6,6,6,3);}}
            case PEOPLE->{g.circle(c,x+7,y+5,3.5F);g.circle(c,x+15,y+6,3);g.roundRect(c,x+1,y+11,12,7,5);g.roundRect(c,x+12,y+12,7,6,4);}
        }
    }
}
