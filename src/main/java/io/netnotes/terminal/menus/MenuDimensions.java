package io.netnotes.terminal.menus;

public class MenuDimensions {
    private final int boxWidth;
    private final int boxXOffset;
    private final int itemContentWidth;
    
    
	public MenuDimensions(int boxWidth, int boxXOffset, int itemContentWidth) {
        this.boxWidth = boxWidth;
        this.boxXOffset = boxXOffset;
        this.itemContentWidth = itemContentWidth;
    }

    public int getBoxWidth() {
		return boxWidth;
	}

	public int getBoxXOffset() {
		return boxXOffset;
	}

	public int getItemContentWidth() {
		return itemContentWidth;
	}

    @Override
    public String toString() {
        return String.format("MenuDimensions[width=%d, col=%d, contentWidth=%d]",
            boxWidth, boxXOffset, itemContentWidth);
    }
}