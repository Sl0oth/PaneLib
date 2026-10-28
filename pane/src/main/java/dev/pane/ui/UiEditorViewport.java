package dev.pane.ui;

/** Retained editor browsing position, independent of consumer values and enclosing scrolls. */
final class UiEditorViewport {
    float offset, maximum, step;
    UiRect viewport;
    boolean reveal, stale, pointerPlacement, readOnly;
    private boolean wasFocused;
    private int cursor=-1;
    private boolean upstream;
    private String value;
    private float width=-1, height=-1;
    private float previousCaretTop, previousCaretHeight, previousLineHeight;

    float layout(String nextValue,int nextCursor,boolean nextUpstream,boolean focused,
                 float nextWidth,float nextHeight,float contentHeight,float caretTop,float caretHeight,float lineHeight) {
        maximum=Math.max(0,contentHeight-nextHeight);
        step=lineHeight*3;
        boolean changed=!nextValue.equals(value)||nextWidth!=width||nextHeight!=height
            ||caretTop!=previousCaretTop||caretHeight!=previousCaretHeight||lineHeight!=previousLineHeight;
        // A read-only view changes under the reader, as a log does; only moving the caret reveals it.
        if(focused && !pointerPlacement && (reveal||!wasFocused||changed&&!readOnly||cursor!=nextCursor||upstream!=nextUpstream)) {
            if(caretTop<offset)offset=caretTop;
            else if(caretTop+caretHeight>offset+nextHeight)offset=caretTop+caretHeight-nextHeight;
        }
        offset=Math.clamp(offset,0,maximum);
        value=nextValue;cursor=nextCursor;upstream=nextUpstream;wasFocused=focused;
        width=nextWidth;height=nextHeight;reveal=false;stale=false;pointerPlacement=false;
        previousCaretTop=caretTop;previousCaretHeight=caretHeight;previousLineHeight=lineHeight;
        return offset;
    }
    boolean scroll(double amount) {
        float next=(float)Math.clamp(offset-amount*step,0,maximum);
        if(next==offset)return false;
        offset=next;stale=true;return true;
    }
}
