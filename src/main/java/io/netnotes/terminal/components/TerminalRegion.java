package io.netnotes.terminal.components;

import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalSizeable;

public class TerminalRegion extends TerminalRenderable implements TerminalSizeable {
    private final TerminalInsets insets = new TerminalInsets();
    private SizePreference widthPreference = SizePreference.FIT_CONTENT;
    private SizePreference heightPreference = SizePreference.FIT_CONTENT;
    private float percentWidth = 0f;
    private float percentHeight = 0f;
    private int minWidth = 0;
    private int minHeight = 0;

    public TerminalRegion(String regionName){
        super(regionName);
    }

    public void setMinWidth(int minWidth){
        this.minWidth = Math.max(0, minWidth);
        invalidate();
    }

    public void setMinHeight(int minHeight){
        this.minHeight = Math.max(0, minHeight);
        invalidate();
    }

    public int getMinWidth(){
        return minWidth + getInsets().getHorizontal();
    }

    public int getMinHeight(){
        return minHeight + getInsets().getVertical();
    }

    @Override
    public SizePreference getWidthPreference() {
        return this.widthPreference;
    }

    @Override
    public SizePreference getHeightPreference() {
        return this.heightPreference;
    }

    public void setWidthPreference(SizePreference widthPreference) {
        this.widthPreference = widthPreference != null ? widthPreference : SizePreference.FIT_CONTENT;
        invalidate();
    }

    public void setHeightPreference(SizePreference heightPreference) {
        this.heightPreference = heightPreference != null ? heightPreference : SizePreference.FIT_CONTENT;
        invalidate();
    }
 
    @Override
    public int getPreferredWidth() {
        SizePreference pref = getWidthPreference();
        if (pref == SizePreference.STATIC) {
            return region.getWidth();
        }
        if (pref == SizePreference.PERCENT || pref == SizePreference.FILL) {
            return getMinWidth();
        }

        int maxPrefWidth = 0;
        for (TerminalRenderable child : getChildren()) {
            if (child.isHidden()) continue;
            
            if (child instanceof TerminalSizeable) {
                maxPrefWidth = Math.max(maxPrefWidth, ((TerminalSizeable) child).getPreferredWidth());
            } else if (child.getRequestedRegion() != null) {
                maxPrefWidth = Math.max(maxPrefWidth, child.getRequestedRegion().getWidth());
            } else {
                maxPrefWidth = Math.max(maxPrefWidth, child.getRegion().getWidth());
            }
        }
        return Math.max(getMinWidth(), maxPrefWidth + getInsets().getHorizontal());
    
    }

    @Override
    public int getPreferredHeight() {
        SizePreference pref = getHeightPreference();
        if (pref == SizePreference.STATIC) {
            return region.getHeight();
        }
        if (pref == SizePreference.PERCENT || pref == SizePreference.FILL) {
            return getMinHeight();
        }

        int maxPrefHeight = 0;
        for (TerminalRenderable child : getChildren()) {
            if (child.isHidden()) continue;
            
            if (child instanceof TerminalSizeable) {
                maxPrefHeight = Math.max(maxPrefHeight, ((TerminalSizeable) child).getPreferredHeight());
            } else if (child.getRequestedRegion() != null) {
                maxPrefHeight = Math.max(maxPrefHeight, child.getRequestedRegion().getHeight());
            } else {
                maxPrefHeight = Math.max(maxPrefHeight, child.getRegion().getHeight());
            }
        }
        return Math.max(getMinHeight(), maxPrefHeight + getInsets().getVertical());
    }


    @Override
    public TerminalInsets getInsets() {
        return insets;
    }

    @Override
    public float getPercentWidth() {
        return percentWidth;
    }

    @Override
    public void setPercentWidth(float percent) {
        this.percentWidth = percent;
        invalidate();
    }

    @Override
    public float getPercentHeight() {
        return percentHeight;
    }

    @Override
    public void setPercentHeight(float percent) {
        this.percentHeight = percent;
        invalidate();
    }
}
