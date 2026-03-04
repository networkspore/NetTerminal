package io.netnotes.terminal.layout;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * TerminalInsets - padding/margins for terminal layout
 */
public class TerminalInsets {
    private int top;
    private int right;
    private int bottom;
    private int left;

    private Consumer<TerminalInsets> onChanged = null;

    /**
     * Default constructor - all zero
     */
    public TerminalInsets() {
        this(0, 0, 0, 0);
    }

    /**
     * Uniform constructor - all sides same
     */
    public TerminalInsets(int all) {
        this(all, all, all, all);
    }

    /**
     * Vertical/Horizontal constructor
     */
    public TerminalInsets(int vertical, int horizontal) {
        this(vertical, horizontal, vertical, horizontal);
    }

    /**
     * Full constructor - top, right, bottom, left
     */
    public TerminalInsets(int top, int right, int bottom, int left) {
        set(top, right, bottom, left);
    }

    public void setOnChanged(Consumer<TerminalInsets> onChanged){
        this.onChanged = onChanged;
    }

    protected void thisChanged(){
        if(this.onChanged != null){
            onChanged.accept(this);
        }
    }

    public int getTop() {
        return top;
    }

    public int getRight() {
        return right;
    }

    public int getBottom() {
        return bottom;
    }

    public int getLeft() {
        return left;
    }

    protected void updateChanged(){

    }

    public void setTop(int top) {
        int old = this.top;
        
        this.top = Math.max(0, top);
        if(old != this.top){
            thisChanged();
        }
    }

    public void setRight(int right) {
        int old = this.right;
       
        this.right = Math.max(0, right);
        if(old != this.right){
            thisChanged();
        }
    }

    public void setBottom(int bottom) {
        int old = this.bottom;
        this.bottom = Math.max(0,bottom);
        if(this.bottom != old){
            thisChanged();
        }
    }

    public void setLeft(int left) {
        int old = this.left;
        this.left = Math.max(0,left);
        if(old != this.left){
            thisChanged();;
        }
    }

    public void set(int top, int right, int bottom, int left) {
        int oldTop = this.top;
        int oldRight = this.right;
        int oldBottom = this.bottom;
        int oldLeft = this.left;
        this.top = Math.max(0, top);
        this.right = Math.max(0, right);
        this.bottom = Math.max(0,bottom);
        this.left = Math.max(0,left);
        if(oldTop != top || oldRight != right || oldBottom != bottom || oldLeft != left){
            thisChanged();
        }
    }

    public void setAll(int all) {
        set(all, all, all, all);
    }

    public void setVerticalHorizontal(int vertical, int horizontal) {
        set(vertical, horizontal, vertical, horizontal);
    }

    public int getHorizontal() {
        return left + right;
    }

    public int getVertical() {
        return top + bottom;
    }

    public boolean isZero() {
        return top == 0 && right == 0 && bottom == 0 && left == 0;
    }

    public void clear() {
        boolean changed = top != 0 || right != 0 || bottom != 0 || left != 0;
        top = 0;
        right = 0;
        bottom = 0;
        left = 0;
        if(changed){
            thisChanged();
        }
    }

    public TerminalInsets copy() {
        return new TerminalInsets(top, right, bottom, left);
    }

    public void copyFrom(TerminalInsets other) {
        if (other == null) return;
        set(other.top, other.right, other.bottom, other.left);
    }

    public boolean equals(int all) {
        return top == all &&
               right == all &&
               bottom == all &&
               left == all;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TerminalInsets)) return false;
        TerminalInsets other = (TerminalInsets) obj;
        return top == other.top &&
               right == other.right &&
               bottom == other.bottom &&
               left == other.left;
    }

    @Override
    public int hashCode() {
        return Objects.hash(top, right, bottom, left);
    }

    @Override
    public String toString() {
        return String.format("TerminalInsets[top=%d, right=%d, bottom=%d, left=%d]",
            top, right, bottom, left);
    }
}
