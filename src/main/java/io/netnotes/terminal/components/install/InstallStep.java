package io.netnotes.terminal.components.install;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * InstallStep - Data model for a single installation wizard step
 *
 * <p>Holds all mutable state for one step: its identity, current {@link Status},
 * a short display name, an optional detail/subtitle line, a 0–1 progress value
 * for sub-tasks, and an ordered list of log lines emitted during execution.
 *
 * <p>This class is purely a data carrier; visual rendering is handled by
 * {@link TerminalInstallStepRow}.
 *
 * EXAMPLE:
 * <pre>
 *   InstallStep step = new InstallStep("download-deps", "Download Dependencies");
 *   step.setDetail("Fetching maven artifacts…");
 *   step.setStatus(InstallStep.Status.RUNNING);
 *   step.setProgress(0.42f);
 *   step.addLogLine("Resolved: commons-lang3:3.12.0");
 * </pre>
 */
public class InstallStep {

    // ===== STATUS =====

    /**
     * Lifecycle states for a single installation step.
     */
    public enum Status {
        /**
         * Not yet started. Rendered with a hollow circle: ○
         */
        PENDING,

        /**
         * Currently executing. Rendered with a spinner or pulsing indicator: ◉
         */
        RUNNING,

        /**
         * Completed successfully. Rendered with a check mark: ✓
         */
        COMPLETE,

        /**
         * Failed with an error. Rendered with a cross: ✗
         */
        ERROR,

        /**
         * Intentionally skipped (e.g. optional component not selected). Rendered: ─
         */
        SKIPPED;

        /**
         * Return the single-character icon that represents this status.
         * All icons are in the Basic Multilingual Plane for broad terminal compat.
         */
        public char icon() {
            switch (this) {
                case PENDING:  return '○';
                case RUNNING:  return '◉';
                case COMPLETE: return '✓';
                case ERROR:    return '✗';
                case SKIPPED:  return '─';
                default:       return '?';
            }
        }

        /** Return true if this status represents a terminal (non-transient) state. */
        public boolean isTerminal() {
            return this == COMPLETE || this == ERROR || this == SKIPPED;
        }

        /** Return true if any work is currently happening. */
        public boolean isActive() {
            return this == RUNNING;
        }
    }

    // ===== SPINNER FRAMES =====

    /** Braille-spinner frames cycled while a step is {@link Status#RUNNING}. */
    public static final char[] SPINNER_FRAMES = {
        '⠋', '⠙', '⠸', '⠴', '⠦', '⠇'
    };

    // ===== FIELDS =====

    private final String id;
    private final String displayName;

    private Status  status        = Status.PENDING;
    private String  detail        = null;       // subtitle / current sub-task description
    private float   progress      = 0f;         // 0.0 – 1.0, used when status == RUNNING
    private boolean showProgress  = false;      // whether to render the sub-progress bar
    private String  errorMessage  = null;       // set on failure
    private int     stepNumber    = 0;          // 1-based ordinal set by the wizard
    private boolean optional      = false;

    private final List<String> logLines = new ArrayList<>();
    private int maxLogLines = 100;              // rolling buffer cap

    // ===== CONSTRUCTION =====

    /**
     * @param id          unique machine-readable identifier
     * @param displayName human-readable step name shown in the wizard
     */
    public InstallStep(String id, String displayName) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("InstallStep id must not be blank");
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("InstallStep displayName must not be blank");
        this.id          = id;
        this.displayName = displayName;
    }

    // ===== IDENTITY =====

    public String getId()          { return id; }
    public String getDisplayName() { return displayName; }

    // ===== STATUS =====

    public Status getStatus()                { return status; }
    public void   setStatus(Status status)   {
        if (status != null) this.status = status;
    }

    // ===== DETAIL =====

    public String getDetail()             { return detail; }
    public void   setDetail(String detail){ this.detail = detail; }

    // ===== PROGRESS =====

    /**
     * Set sub-task progress (clamped to [0, 1]).
     * Automatically enables the sub-progress bar.
     */
    public void setProgress(float progress) {
        this.progress     = Math.max(0f, Math.min(1f, progress));
        this.showProgress = true;
    }

    public float   getProgress()               { return progress; }
    public boolean isShowProgress()            { return showProgress; }
    public void    setShowProgress(boolean v)  { this.showProgress = v; }

    // ===== ERROR =====

    public String getErrorMessage()                  { return errorMessage; }
    public void   setErrorMessage(String msg)        { this.errorMessage = msg; }

    // ===== STEP NUMBER =====

    /** 1-based ordinal — set automatically by {@link TerminalInstallWizard}. */
    public int  getStepNumber()          { return stepNumber; }
    public void setStepNumber(int n)     { this.stepNumber = n; }

    // ===== OPTIONAL =====

    public boolean isOptional()           { return optional; }
    public void    setOptional(boolean v) { this.optional = v; }

    // ===== LOG LINES =====

    /**
     * Append a log line emitted by this step.
     * Oldest lines are discarded when {@link #maxLogLines} is exceeded.
     */
    public void addLogLine(String line) {
        if (line == null) return;
        if (logLines.size() >= maxLogLines) {
            logLines.remove(0);
        }
        logLines.add(line);
    }

    /** Unmodifiable view of all retained log lines. */
    public List<String> getLogLines() {
        return Collections.unmodifiableList(logLines);
    }

    public void clearLogLines() { logLines.clear(); }

    public int  getMaxLogLines()        { return maxLogLines; }
    public void setMaxLogLines(int max) { this.maxLogLines = Math.max(1, max); }

    // ===== UTILITIES =====

    /**
     * Reset the step to its initial PENDING state, clearing progress, detail,
     * error, and log lines. Useful for re-running the wizard.
     */
    public void reset() {
        status       = Status.PENDING;
        detail       = null;
        progress     = 0f;
        showProgress = false;
        errorMessage = null;
        logLines.clear();
    }

    @Override
    public String toString() {
        return "InstallStep[" + id + ", " + status + ", " + (int)(progress * 100) + "%]";
    }
}