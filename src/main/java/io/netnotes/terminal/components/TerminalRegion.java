package io.netnotes.terminal.components;

import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.layout.TerminalInsets;
import io.netnotes.terminal.layout.TerminalSizeable;

public class TerminalRegion extends TerminalRenderable implements TerminalSizeable {
    protected final TerminalInsets insets;
    private SizePreference widthPreference = SizePreference.STATIC;
    private SizePreference heightPreference = SizePreference.STATIC;
    private float percentWidth = 0f;
    private float percentHeight = 0f;
    private int minWidth = 1;
    private int minHeight = 1;
    private boolean isHiddenManaged = true;

    public TerminalRegion(String regionName){
        super(regionName);
        insets = new TerminalInsets();
        insets.setOnChanged(this::handleInsetsChanged);
    }

    private void handleInsetsChanged(TerminalInsets insets){
        onInsetsChanged(insets);
        requestLayoutUpdate();
    }

    protected void onInsetsChanged(TerminalInsets insets){}

    public void setMinWidth(int minWidth){
        this.minWidth = Math.max(1, minWidth);
        requestLayoutUpdate();
    }

    public void setMinHeight(int minHeight){
        this.minHeight = Math.max(1, minHeight);
        requestLayoutUpdate();
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
        requestLayoutUpdate();
    }

    public void setHeightPreference(SizePreference heightPreference) {
        this.heightPreference = heightPreference != null ? heightPreference : SizePreference.FIT_CONTENT;
        requestLayoutUpdate();
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
    public boolean isHiddenManaged(){
        return isHiddenManaged;
    }

    public void setIsHiddenManaged(boolean isHiddenManaged){
        if(this.isHiddenManaged != isHiddenManaged ){
            this.isHiddenManaged = isHiddenManaged;
            requestLayoutUpdate();
        }
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

    public void setInsets(int all) {
        this.insets.setAll(all);
    }
    public void setInsets(TerminalInsets padding) {
        if (padding == null) {
            if (!this.insets.isZero()) {
                this.insets.clear();
            }
            return;
        }

        if (!this.insets.equals(padding)) {
            this.insets.copyFrom(padding);
        }
    }

    @Override
    public float getPercentWidth() {
        return percentWidth;
    }

    @Override
    public void setPercentWidth(float percent) {
        this.percentWidth = percent;
        requestLayoutUpdate();
    }

    @Override
    public float getPercentHeight() {
        return percentHeight;
    }

    @Override
    public void setPercentHeight(float percent) {
        this.percentHeight = percent;
        requestLayoutUpdate();
    }

    @Override
    public int getMinSize(int axis) {
        switch(axis){
            case 0:
                return getMinWidth();
            case 1:
                return getMinHeight();
        }
        throw new IllegalArgumentException("getMinSize TerminalRegion does not have: " + axis + " axis");
    }

    @Override
    public int getPreferredSize(int axis) {
        switch(axis){
            case 0:
                return getPreferredWidth();
            case 1:
                return getPreferredHeight();
        }
        throw new IllegalArgumentException("getPreferredSize TerminalRegion does not have: " + axis + " axis");
    }

    @Override
    public SizePreference getSizePreference(int axis) {
        switch(axis){
            case 0:
                return getWidthPreference();
            case 1:
                return getHeightPreference();
        }
        throw new IllegalArgumentException("getSizePreference TerminalRegion does not have: " + axis + " axis");
    }

    @Override
    public boolean isSizedByContent() {
        return widthPreference == SizePreference.FIT_CONTENT 
            || heightPreference == SizePreference.FIT_CONTENT;
    }

    @Override
    public boolean isSizedByParent() {
        return widthPreference == SizePreference.FILL
            || widthPreference == SizePreference.PERCENT
            || heightPreference == SizePreference.FILL
            || heightPreference == SizePreference.PERCENT;
    }

    public static int resolveContentDimension(
        TerminalRenderable child,
        int viewport,
        float percent,
        SizePreference pref,
        boolean isWidth
    ) {

        return switch (pref) {
            case FILL    -> viewport;
            case PERCENT -> Math.round(viewport * (percent / 100f));
            case STATIC  -> isWidth ? child.getRegion().getWidth() 
                                    : child.getRegion().getHeight();
            case FIT_CONTENT -> child instanceof TerminalSizeable s 
                                    ? ( isWidth 
                                            ? Math.max(s.getMinWidth(),  s.getPreferredWidth())
                                            : Math.max(s.getMinHeight(), s.getPreferredHeight())) 
                                    : viewport;
            default -> viewport;
        };
    }


}
