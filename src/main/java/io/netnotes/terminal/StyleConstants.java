package io.netnotes.terminal;

import io.netnotes.engine.noteBytes.NoteBytesReadOnly;

public class StyleConstants {
    public final static NoteBytesReadOnly FG_MODE = new NoteBytesReadOnly("fg_mode");
    public final static NoteBytesReadOnly FG_INDEXED = new NoteBytesReadOnly("fg_indexed");
    public final static NoteBytesReadOnly FG_RGB = new NoteBytesReadOnly("fg_rgb");

    public final static NoteBytesReadOnly BG_MODE = new NoteBytesReadOnly("bg_mode");
    public final static NoteBytesReadOnly BG_INDEXED = new NoteBytesReadOnly("bg_indexed");
    public final static NoteBytesReadOnly BG_RGB = new NoteBytesReadOnly("bg_rgb");
    public final static NoteBytesReadOnly BG_STYLE = new NoteBytesReadOnly("bg_style");

    public final static NoteBytesReadOnly EMPTY_STYLE = new NoteBytesReadOnly("empty_style");
    public final static NoteBytesReadOnly BORDER_STYLE = new NoteBytesReadOnly("border_style");

    public final static NoteBytesReadOnly FAINT = new NoteBytesReadOnly("faint");
    public final static NoteBytesReadOnly ITALIC = new NoteBytesReadOnly("italic");
    public final static NoteBytesReadOnly BLINK = new NoteBytesReadOnly("blink");
    public final static NoteBytesReadOnly HIDDEN = new NoteBytesReadOnly("hidden");
    public final static NoteBytesReadOnly STRIKETHROUGH = new NoteBytesReadOnly("strikethrough");
    public final static NoteBytesReadOnly UNDERLINE = new NoteBytesReadOnly("underline");
}
