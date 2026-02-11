package io.netnotes.terminal.components.input;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.Color;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.components.text.TerminalLabel;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.terminal.layout.TerminalLayoutData;
import io.netnotes.engine.io.input.Keyboard.KeyCodeBytes;
import io.netnotes.engine.io.input.events.keyboardEvents.KeyDownEvent;
import io.netnotes.engine.ui.TextAlignment;
import io.netnotes.noteBytes.NoteBytesEphemeral;
import io.netnotes.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.utils.LoggingHelpers.Log;
import io.netnotes.engine.utils.virtualExecutors.VirtualExecutors;

public class PasswordPrompt extends TerminalRegion {
    
    private static final int STATE_INACTIVE = 40;
    private static final int STATE_ACTIVE = 41;
    private static final int STATE_CONFIRMING = 42;

    public enum Mode {
        CREATE,
        VERIFY
    }
    
    private final Mode mode;

    private String title = "Authentication";
    private String promptText = "Enter password:";
    private String confirmPromptText = "Confirm password:";
    private int timeoutSeconds = 30;
    private int boxWidth = 50;
    private int boxHeight = 11;
    
    private Consumer<NoteBytesEphemeral> onPassword;
    private Consumer<NoteBytesEphemeral> onVerify;
    private Runnable onTimeout;
    private Runnable onCancel;
    private Runnable onMismatch;
    
    private volatile NoteBytesEphemeral firstPassword = null;
    
    private final TerminalPasswordField createField;
    private final TerminalPasswordField confirmField;
    private final TerminalPasswordField verifyField;
    private final TerminalLabel headerLabel;
    private final TerminalLabel promptLabel;
    private final TerminalLabel confirmPromptLabel;
    private final TerminalLabel statusLabel;
    private final TerminalLabel footerLabel;
    private CompletableFuture<Void> timeoutFuture;
    private NoteBytesReadOnly cancelHandlerId;
    
    private boolean showVerifyLine = false;
    private TextStyle boxBgStyle = new TextStyle().bgRgb(0x111111);
    private TextStyle lineStyle = new TextStyle().color(TextStyle.Color.WHITE).bgRgb(0x111111);

    private TextStyle passFieldBg = new TextStyle().bgColor(Color.WHITE);
    private TextStyle passFieldTextStyle = new TextStyle().color(Color.BLACK).bgColor(Color.WHITE);
    private TextStyle textLabelStyle = TextStyle.BOLD.copy().bgColor(boxBgStyle.getBackground());
    public PasswordPrompt(String name) {
        this(new Builder(name));
    }

    public PasswordPrompt(String name, Mode mode) {
        this(new Builder(name).mode(mode));
    }

    private PasswordPrompt(Builder builder) {
        super(builder.name);
        this.mode = builder.mode;
        this.title = builder.title;
        this.promptText = builder.promptText;
        this.confirmPromptText = builder.confirmPromptText;
        this.timeoutSeconds = builder.timeoutSeconds;
        this.boxWidth = builder.boxWidth;
        this.boxHeight = builder.boxHeight;
        this.onPassword = builder.onPassword;
        this.onVerify = builder.onVerify;
        this.onTimeout = builder.onTimeout;
        this.onCancel = builder.onCancel;
        this.onMismatch = builder.onMismatch;
        if (builder.boxBgStyle != null) {
            this.boxBgStyle = builder.boxBgStyle;
        }
        if (builder.lineStyle != null) {
            this.lineStyle = builder.lineStyle;
        }
        if (builder.passFieldBg != null) {
            this.passFieldBg = builder.passFieldBg;
        }
        if (builder.passFieldTextStyle != null) {
            this.passFieldTextStyle = builder.passFieldTextStyle;
        }
        this.textLabelStyle = builder.textLabelStyle != null 
            ? builder.textLabelStyle 
            : TextStyle.BOLD.copy().bgColor(boxBgStyle.getBackground());

        setFocusable(true);
        setWidthPreference(SizePreference.FILL);
        setHeightPreference(SizePreference.FILL);

        headerLabel = new TerminalLabel("header");
        promptLabel = new TerminalLabel("prompt");
        confirmPromptLabel = new TerminalLabel("confirm-prompt");
        statusLabel = new TerminalLabel("status");
        footerLabel = new TerminalLabel("footer");

        createField = new TerminalPasswordField("password-input", false)
            .withDisplayMode(TerminalPasswordField.DisplayMode.MASKED)
            .withMaskChar('*')
            .withTextStyle(passFieldTextStyle)
            .withBaseStyle(passFieldBg)
            .withOnComplete(this::handleCreateFirstEntered);

        confirmField = new TerminalPasswordField("password-confirm", false)
            .withDisplayMode(TerminalPasswordField.DisplayMode.MASKED)
            .withMaskChar('*')
            .withTextStyle(passFieldTextStyle)
            .withBaseStyle(passFieldBg)
            .withOnComplete(this::handleCreateConfirmEntered);

        verifyField = new TerminalPasswordField("password-verify", true)
            .withDisplayMode(TerminalPasswordField.DisplayMode.INVISIBLE)
            .withFixedCursor(true)
            .withTextStyle(passFieldTextStyle)
            .withBaseStyle(passFieldBg)
            .withOnComplete(this::handleVerifyEntered);

        verifyField.setOnFocusChanged((field, isFocused) -> {
            showVerifyLine = isFocused;
            invalidate();
        });

        headerLabel.setText(title);
        headerLabel.setTextStyle(textLabelStyle);
        headerLabel.setTextAlignment(TextAlignment.CENTER);
        
        promptLabel.setTextStyle(TextStyle.NORMAL.copy().bgColor(boxBgStyle.getBackground()));
        promptLabel.setTextAlignment(TextAlignment.CENTER);
        
        confirmPromptLabel.setTextStyle(TextStyle.NORMAL.copy().bgColor(boxBgStyle.getBackground()));
        confirmPromptLabel.setTextAlignment(TextAlignment.CENTER);

        statusLabel.setTextStyle(TextStyle.WARNING.copy().bgColor(boxBgStyle.getBackground()));
        statusLabel.setTextAlignment(TextAlignment.CENTER);
        
        footerLabel.setText(builder.footerText);
        footerLabel.setTextStyle(TextStyle.INFO.copy().bgColor(boxBgStyle.getBackground()));
        footerLabel.setTextAlignment(TextAlignment.CENTER);

        updateMinimumSize();
        initializeLayout();
    }

    private void updateMinimumSize() {
        int minHeight = Math.max(boxHeight, getRequiredBoxHeight()) + 4;
        setMinWidth(boxWidth + 4);
        setMinHeight(minHeight);
    }

    private int getRequiredBoxHeight() {
        return mode == Mode.CREATE ? 15 : 11;
    }

    private int getResolvedBoxHeight() {
        return Math.max(boxHeight, getRequiredBoxHeight());
    }

    private int getResolvedBoxWidth() {
        return boxWidth;
    }

    private TerminalLayoutData layoutAt(TerminalLayoutContext ctx, int xOffset, int yOffset, int width, boolean hidden) {
        int boxWidth = getResolvedBoxWidth();
        int boxHeight = getResolvedBoxHeight();
        int boxX = (ctx.getParentRegion().getWidth() - boxWidth) / 2;
        int boxY = (ctx.getParentRegion().getHeight() - boxHeight) / 2;
        return TerminalLayoutData.getBuilder()
            .setX(boxX + xOffset)
            .setY(boxY + yOffset)
            .setWidth(width)
            .setHeight(1)
            .hidden(hidden)
            .build();
    }

    private void initializeLayout() {
        addChild(headerLabel, ctx -> layoutAt(ctx, 2, 1, getResolvedBoxWidth() - 4, false));
        addChild(promptLabel, ctx -> layoutAt(ctx, 2, 3, getResolvedBoxWidth() - 4, false));

        addChild(createField, ctx -> layoutAt(ctx, 4, 5, getResolvedBoxWidth() - 8, mode != Mode.CREATE));
        addChild(confirmPromptLabel, ctx -> layoutAt(ctx, 2, 7, getResolvedBoxWidth() - 4, mode != Mode.CREATE));
        addChild(confirmField, ctx -> layoutAt(ctx, 4, 9, getResolvedBoxWidth() - 8, mode != Mode.CREATE));

        addChild(verifyField, ctx -> layoutAt(ctx, 4, 5, getResolvedBoxWidth() - 8, mode != Mode.VERIFY));

        addChild(statusLabel, ctx -> layoutAt(ctx, 2, mode == Mode.CREATE ? 11 : 7, getResolvedBoxWidth() - 4, false));
        addChild(footerLabel, ctx -> layoutAt(ctx, 2, getResolvedBoxHeight() - 2, getResolvedBoxWidth() - 4, false));
    }
    
    @Override
    protected void setupStateTransitions() {
        stateMachine.onStateAdded(STATE_INACTIVE, (old, now, bit) -> {
            cleanupResources();
        });
        
        stateMachine.onStateAdded(STATE_ACTIVE, (old, now, bit) -> {
            promptLabel.setText(promptText);
            confirmPromptLabel.setText(confirmPromptText);
            statusLabel.setText("");
            if (mode == Mode.CREATE) {
                createField.clear();
                confirmField.clear();
                createField.requestFocus();
            } else {
                verifyField.clear();
                verifyField.requestFocus();
            }
            startTimeout();
            invalidate();
        });
        
        stateMachine.onStateAdded(STATE_CONFIRMING, (old, now, bit) -> {
            statusLabel.setText("");
            confirmField.clear();
            confirmField.requestFocus();
            startTimeout();
            invalidate();
        });
        
        stateMachine.addState(STATE_INACTIVE);
    }
    
    @Override
    protected void setupEventHandlers() {
        cancelHandlerId = addKeyDownHandler(event -> {
            if (event instanceof KeyDownEvent kd && kd.getKeyCodeBytes().equals(KeyCodeBytes.ESCAPE)) {
                handleCancel();
            }
        });
    }
    


    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        if (stateMachine.hasState(STATE_INACTIVE)) return;
        
        int parentWidth = getWidth();
        int parentHeight = getHeight();
        
        int resolvedBoxWidth = getResolvedBoxWidth();
        int resolvedBoxHeight = getResolvedBoxHeight();
        int boxX = (parentWidth - resolvedBoxWidth) / 2;
        int boxY = (parentHeight - resolvedBoxHeight) / 2;
        
        fillRegion(batch, boxX, boxY, resolvedBoxWidth, resolvedBoxHeight, ' ', boxBgStyle);
        
        if (mode == Mode.VERIFY && showVerifyLine && verifyField.hasFocus()) {
            int lineY = boxY + 2;
            fillRegion(batch, boxX + 1, lineY, resolvedBoxWidth - 2, 1, '-', lineStyle);
        }
     
    }
    
    //TODO: use actual builder not just with methods, high secure password prompt needs to be created as such

    public PasswordPrompt withTitle(String title) { 
        this.title = title; 
        if (headerLabel != null) headerLabel.setText(title);
        return this; 
    }
    
    public PasswordPrompt withPrompt(String prompt) { 
        this.promptText = prompt; 
        if (promptLabel != null) promptLabel.setText(prompt);
        return this; 
    }
    
    public PasswordPrompt withConfirmPrompt(String confirmPrompt) { 
        this.confirmPromptText = confirmPrompt; 
        if (confirmPromptLabel != null) confirmPromptLabel.setText(confirmPrompt);
        return this; 
    }
    
    public PasswordPrompt withTimeout(int seconds) { 
        this.timeoutSeconds = seconds; 
        return this; 
    }
    
    public PasswordPrompt withBoxSize(int width, int height) { 
        this.boxWidth = width; 
        this.boxHeight = height; 
        updateMinimumSize();
        return this; 
    }
    
    public PasswordPrompt onPassword(Consumer<NoteBytesEphemeral> handler) { 
        this.onPassword = handler; 
        return this; 
    }

    public PasswordPrompt onVerify(Consumer<NoteBytesEphemeral> handler) {
        this.onVerify = handler;
        return this;
    }
    
    public PasswordPrompt onTimeout(Runnable handler) { 
        this.onTimeout = handler; 
        return this; 
    }
    
    public PasswordPrompt onCancel(Runnable handler) { 
        this.onCancel = handler; 
        return this; 
    }
    
    public PasswordPrompt onMismatch(Runnable handler) { 
        this.onMismatch = handler; 
        return this; 
    }
    
    public void activate() {
        if (!stateMachine.hasState(STATE_INACTIVE)) {
            Log.logError("[PasswordPrompt] Already active");
            return;
        }
        transitionTo(STATE_INACTIVE, STATE_ACTIVE);
    }
    
    public void deactivate() {
        if (stateMachine.hasState(STATE_INACTIVE)) return;
        
        int current = stateMachine.hasState(STATE_CONFIRMING) ? STATE_CONFIRMING : STATE_ACTIVE;
        transitionTo(current, STATE_INACTIVE);
    }
    
    private void handleCreateFirstEntered(NoteBytesEphemeral password) {
        firstPassword = password.copy();
        password.close();
        transitionTo(STATE_ACTIVE, STATE_CONFIRMING);
    }
    
    private void handleCreateConfirmEntered(NoteBytesEphemeral password) {
        if (firstPassword == null) {
            password.close();
            transitionTo(STATE_CONFIRMING, STATE_ACTIVE);
            return;
        }
        boolean match = firstPassword.equals(password);
        
        if (match) {
            password.close();
            NoteBytesEphemeral confirmedPassword = firstPassword;
            firstPassword = null;
            completeWithPassword(confirmedPassword);
        } else {
            firstPassword.close();
            firstPassword = null;
            password.close();
            
            if (onMismatch != null) {
                onMismatch.run();
                deactivate();
            } else {
                showMismatchError();
            }
        }
    }

    private void handleVerifyEntered(NoteBytesEphemeral password) {
        cancelTimeout();
        if (onVerify != null) {
            onVerify.accept(password);
        } else {
            completeWithPassword(password);
        }
    }
    
    private void showMismatchError() {
        statusLabel.setText("Passwords do not match");
        invalidate();
        
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(2000); } 
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, VirtualExecutors.getVirtualExecutor())
        .thenRun(() -> {
            statusLabel.setText("");
            transitionTo(STATE_CONFIRMING, STATE_ACTIVE);
        });
    }
    
    private void completeWithPassword(NoteBytesEphemeral password) {
        if (onPassword != null) {
            onPassword.accept(password);
        } else {
            password.close();
        }
        deactivate();
    }
    
    private void startTimeout() {
        cancelTimeout();
        if (timeoutSeconds <= 0) return;
        
        timeoutFuture = CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(timeoutSeconds);
                if (!stateMachine.hasState(STATE_INACTIVE)) {
                    handleTimeout();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, VirtualExecutors.getVirtualExecutor());
    }
    
    private void cancelTimeout() {
        if (timeoutFuture != null) {
            timeoutFuture.cancel(true);
            timeoutFuture = null;
        }
    }
    
    private void handleTimeout() {
        if (onTimeout != null) {
            onTimeout.run();
        }
        deactivate();
    }
    
    private void handleCancel() {
        if (onCancel != null) {
            onCancel.run();
        }
        deactivate();
    }
    
    private void cleanupResources() {
        cancelTimeout();
        
        createField.clear();
        confirmField.clear();
        verifyField.clear();
        showVerifyLine = false;
        
        if (firstPassword != null) {
            firstPassword.close();
            firstPassword = null;
        }
    }
    
    public boolean isActive() {
        return !stateMachine.hasState(STATE_INACTIVE);
    }
    
    @Override
    protected void onCleanup() {
        if (cancelHandlerId != null) {
            removeKeyDownHandler(cancelHandlerId);
        }
        deactivate();
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static Builder createBuilder(String name) {
        return new Builder(name).mode(Mode.CREATE);
    }

    public static Builder verifyBuilder(String name) {
        return new Builder(name).mode(Mode.VERIFY);
    }

    public static class Builder {
        private final String name;
        private Mode mode = Mode.VERIFY;
        private String title = "Authentication";
        private String promptText = "Enter password:";
        private String confirmPromptText = "Confirm password:";
        private int timeoutSeconds = 30;
        private int boxWidth = 50;
        private int boxHeight = 11;
        private String footerText = "ESC: Cancel";
        private Consumer<NoteBytesEphemeral> onPassword;
        private Consumer<NoteBytesEphemeral> onVerify;
        private Runnable onTimeout;
        private Runnable onCancel;
        private Runnable onMismatch;
        private TextStyle boxBgStyle;
        private TextStyle lineStyle;
        private TextStyle passFieldBg;
        private TextStyle passFieldTextStyle;
        private TextStyle textLabelStyle;

        public Builder(String name) {
            this.name = name;
        }

        public Builder mode(Mode mode) { 
            if (mode != null) this.mode = mode; 
            return this; 
        }
        public Builder title(String title) { this.title = title; return this; }
        public Builder prompt(String prompt) { this.promptText = prompt; return this; }
        public Builder confirmPrompt(String confirmPrompt) { this.confirmPromptText = confirmPrompt; return this; }
        public Builder timeoutSeconds(int seconds) { this.timeoutSeconds = seconds; return this; }
        public Builder boxSize(int width, int height) { this.boxWidth = width; this.boxHeight = height; return this; }
        public Builder footerText(String footerText) { this.footerText = footerText; return this; }
        public Builder onPassword(Consumer<NoteBytesEphemeral> handler) { this.onPassword = handler; return this; }
        public Builder onVerify(Consumer<NoteBytesEphemeral> handler) { this.onVerify = handler; return this; }
        public Builder onTimeout(Runnable handler) { this.onTimeout = handler; return this; }
        public Builder onCancel(Runnable handler) { this.onCancel = handler; return this; }
        public Builder onMismatch(Runnable handler) { this.onMismatch = handler; return this; }
        public Builder boxBackgroundStyle(TextStyle style) { this.boxBgStyle = style; return this; }
        public Builder lineStyle(TextStyle style) { this.lineStyle = style; return this; }
        public Builder fieldBaseStyle(TextStyle style) { this.passFieldBg = style; return this; }
        public Builder fieldTextStyle(TextStyle style) { this.passFieldTextStyle = style; return this; }
        public Builder headerStyle(TextStyle style) { this.textLabelStyle = style; return this; }

        public PasswordPrompt build() {
            if (mode == Mode.CREATE && (confirmPromptText == null || confirmPromptText.isEmpty())) {
                confirmPromptText = "Confirm password:";
            }
            return new PasswordPrompt(this);
        }
    }
}
