package io.netnotes.debug;

import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalBatchBuilder;
import io.netnotes.terminal.TerminalRenderable;


import java.util.List;

/**
 * BatchTraceAspect - Traces batch command construction and rendering flow.
 *
 * This is the critical diagnostic for the missing text bug:
 * 1. Traces batch commands as they're added
 * 2. Traces the final batch before it's sent
 * 3. Traces the renderable tree during toBatch()
 *
 * The bug manifests as borders rendering but text doesn't:
 * - Borders likely call batch.drawBorder() which adds commands
 * - Text might call batch.print() which might not add commands correctly
 * - OR text is being rendered to the wrong coordinates
 * - OR damage regions don't include text area
 */
public class BatchTraceAspect {

    /**
     * Called when print command is added to batch
     */
    public static void onPrintCommand(TerminalBatchBuilder batch, String text, int x, int y, Object style) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.BATCH_COMMAND_ADDED, "batch-builder")
            .with("command", "print")
            .with("text", text != null ? text.substring(0, Math.min(text.length(), 20)) : "null")
            .with("x", x)
            .with("y", y)
            .with("hasStyle", style != null));
    }

    /**
     * Called when draw border command is added
     */
    public static void onDrawBorderCommand(TerminalBatchBuilder batch, TerminalRectangle region, Object style) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.BATCH_COMMAND_ADDED, "batch-builder")
            .with("command", "drawBorder")
            .with("region", region != null ? region.toString() : "null")
            .with("hasStyle", style != null));
    }

    /**
     * Called when fill region command is added
     */
    public static void onFillRegionCommand(TerminalBatchBuilder batch, TerminalRectangle region, Object fill) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.BATCH_COMMAND_ADDED, "batch-builder")
            .with("command", "fillRegion")
            .with("region", region != null ? region.toString() : "null"));
    }

    /**
     * Called before toBatch starts on root renderable
     */
    public static void onToBatchStart(TerminalRenderable rootRenderable, boolean hasDamage, boolean childrenDirty) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.TO_BATCH_START, rootRenderable.getName())
            .with("hasDamage", hasDamage)
            .with("childrenDirty", childrenDirty));
    }

    /**
     * Called after toBatch completes
     */
    public static void onToBatchEnd(TerminalRenderable rootRenderable, int commandCount) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.TO_BATCH_END, rootRenderable.getName())
            .with("commandCount", commandCount));
    }

    /**
     * Called when batch command is built (final step before send)
     */
    public static void onBatchBuilt(String containerName, int commandCount, List<TerminalRectangle> damageRegions) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.BATCH_BUILT, containerName)
            .with("commandCount", commandCount)
            .with("damageRegionCount", damageRegions != null ? damageRegions.size() : 0));
    }

    /**
     * Called when batch is empty but shouldn't be (the bug!)
     */
    public static void onBatchEmptyWarning(String containerName, String reason, TerminalRenderable rootRenderable) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.BATCH_EMPTY_UNEXPECTED, containerName)
            .with("reason", reason)
            .with("renderableClass", rootRenderable.getClass().getSimpleName())
            .with("renderableName", rootRenderable.getName()));
    }

    /**
     * Called from ContainerHandle when render is dropped
     */
    public static void onRenderDropped(String containerName, String reason) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.RENDER_DROPPED, containerName)
            .with("reason", reason));
    }

    /**
     * Called when text renderable renders itself
     */
    public static void onTextRenderSelf(TerminalRenderable textRenderable, String text, boolean actuallyRendered) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.TEXT_RENDER_ATTEMPT, textRenderable.getName())
            .with("text", text != null ? text.substring(0, Math.min(text.length(), 30)) : "null")
            .with("actuallyRendered", actuallyRendered));
    }

    /**
     * Called when damage is checked
     */
    public static void onDamageCheck(String renderableName, boolean hasDamage, int damageCount) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.DAMAGE_CHECK, renderableName)
            .with("hasDamage", hasDamage)
            .with("damageCount", damageCount));
    }
}
