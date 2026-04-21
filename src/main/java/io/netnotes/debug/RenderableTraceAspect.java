package io.netnotes.debug;

import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;

/**
 * RenderableTraceAspect - Static hooks for tracing TerminalRenderable behavior.
 *
 * This class provides static entry points that can be called from TerminalRenderable
 * to emit trace events without polluting the core codebase.
 *
 * Uses concrete TerminalRenderable and TerminalRectangle types.
 */
public class RenderableTraceAspect{

    /**
     * Called when invalidate() is invoked
     */
    public static void onInvalidateRequested(TerminalRenderable renderable, TerminalRectangle localRegion) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.INVALIDATE_REQUESTED, renderable.getName())
            .with("localRegion", localRegion != null ? localRegion.toString() : "null")
            .with("threadId", Thread.currentThread().threadId()));
    }

    /**
     * Called when invalidate() is deferred due to layout executing
     */
    public static void onInvalidateDeferred(TerminalRenderable renderable, String reason) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.INVALIDATE_DEFERRED, renderable.getName())
            .with("reason", reason));

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.PENDING_INVALIDATE_SET, renderable.getName())
            .with("triggeredBy", "invalidateDeferred"));
    }

    /**
     * Called when pendingInvalidate is set because renderable has no layout manager
     */
    public static void onPendingInvalidateSet(TerminalRenderable renderable, String reason) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.PENDING_INVALIDATE_SET, renderable.getName())
            .with("reason", reason));
    }

    /**
     * Called when invalidateImmediate() executes
     */
    public static void onInvalidateImmediate(TerminalRenderable renderable, TerminalRectangle absoluteDamage) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.INVALIDATE_IMMEDIATE, renderable.getName())
            .with("damage", absoluteDamage != null ? absoluteDamage.toString() : "full")
            .with("hasDamage", renderable.getRegion() != null));
    }

    /**
     * Called when propagateDamageUp executes
     */
    public static void onDamagePropagated(TerminalRenderable fromRenderable, TerminalRenderable toRenderable, TerminalRectangle damage) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.recordDamagePropagation(
            fromRenderable.getName(),
            toRenderable != null ? toRenderable.getName() : "null",
            damage);
    }

    /**
     * Called when damage reaches root and reportDamage is invoked
     */
    public static void onDamageReported(TerminalRenderable rootRenderable, TerminalRectangle absoluteDamage) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.DAMAGE_REPORTED, rootRenderable.getName())
            .with("damageRegion", absoluteDamage != null ? absoluteDamage.toString() : "null"));
    }

    /**
     * Called when applyLayoutData is called on a renderable
     */
    public static void onApplyLayoutData(TerminalRenderable renderable, boolean hadRegion, boolean hadStateChanges, boolean hadPendingInvalidate) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.LAYOUT_NODE_COMMIT, renderable.getName())
            .with("hadRegion", hadRegion)
            .with("hadStateChanges", hadStateChanges)
            .with("hadPendingInvalidate", hadPendingInvalidate));

        if (hadPendingInvalidate) {
            rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.PENDING_INVALIDATE_CLEARED, renderable.getName())
                .with("by", "applyLayoutData"));
        }
    }

    /**
     * Called when advanceRenderPhase is called
     */
    public static void onPhaseAdvance(TerminalRenderable renderable, String fromPhase, String toPhase) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.PHASE_ADVANCE, renderable.getName())
            .with("from", fromPhase)
            .with("to", toPhase));
    }

    /**
     * Called when toBatch starts
     */
    public static void onToBatchStart(TerminalRenderable renderable) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.TO_BATCH_START, renderable.getName()));
    }

    /**
     * Called when toBatch completes
     */
    public static void onToBatchEnd(TerminalRenderable renderable, boolean hadDamage) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.TO_BATCH_END, renderable.getName())
            .with("hadDamage", hadDamage));
    }

    /**
     * Called when damage would be dropped/lost
     */
    public static void onDamageDropped(TerminalRenderable renderable, String reason) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.DAMAGE_DROPPED, renderable.getName())
            .with("reason", reason));
    }

    /**
     * Called from DamageAccumulator.add()
     */
    public static void onDamageAccumulated(String accumulatorName, String regionInfo) {
        RendererTraceRecorder rec = RendererTraceRecorder.getInstance();
        if (!rec.isEnabled()) return;

        rec.record(RendererTraceEvent.builder(RendererTraceEvent.Type.DAMAGE_ACCUMULATED, accumulatorName)
            .with("region", regionInfo));
    }
}
