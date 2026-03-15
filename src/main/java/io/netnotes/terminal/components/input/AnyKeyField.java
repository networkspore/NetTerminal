package io.netnotes.terminal.components.input;

import io.netnotes.engine.ui.SizePreference;

public class AnyKeyField extends TerminalTextField {
    
 public AnyKeyField(String name) {
        super(name);
        setFocusable(true);
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.STATIC);
        setBounds(0, 0, 1, 1);  // height=1, width fills
    }

    @Override
    protected void setupKeyHandlers() {
        // Don't register the default keyRunTable bindings —
        // every keypress here is "proceed", including Escape
        addKeyDownHandler(e -> {
            if (getOnComplete() != null) {
                getOnComplete().accept(getText());
            }
        });
        addKeyCharHandler(e -> {
            if (getOnComplete() != null) {
                getOnComplete().accept(getText());
            }
        });
    }
}
