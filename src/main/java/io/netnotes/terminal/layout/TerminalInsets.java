package io.netnotes.terminal.layout;

import java.util.Objects;

/**
 * TerminalInsets - padding/margins for terminal layout
 */
public class TerminalInsets {
    private int top;
    private int right;
    private int bottom;
    private int left;

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
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
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

    public void setTop(int top) {
        this.top = top;
    }

    public void setRight(int right) {
        this.right = right;
    }

    public void setBottom(int bottom) {
        this.bottom = bottom;
    }

    public void setLeft(int left) {
        this.left = left;
    }

    public void set(int top, int right, int bottom, int left) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
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
        top = 0;
        right = 0;
        bottom = 0;
        left = 0;
    }

    public TerminalInsets copy() {
        return new TerminalInsets(top, right, bottom, left);
    }

    public void copyFrom(TerminalInsets other) {
        if (other == null) return;
        this.top = other.top;
        this.right = other.right;
        this.bottom = other.bottom;
        this.left = other.left;
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
