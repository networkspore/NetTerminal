package io.netnotes.debug;

import io.netnotes.terminal.TerminalRectangle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * RendererTraceRecorder - Captures chronological events in the render/layout lifecycle.
 *
 * This recorder is designed to diagnose the specific bug where:
 * 1. invalidate() is called during layout
 * 2. Damage is deferred via pendingInvalidate flag
 * 3. applyLayoutData() consumes pendingInvalidate
 * 4. But something between layout commit and render causes damage to be lost
 *
 * USAGE:
 *   RendererTraceRecorder recorder = RendererTraceRecorder.getInstance();
 *   recorder.setEnabled(true);
 *   recorder.addEventListener(event -> {
 *       if (event.getType() == RendererTraceEvent.Type.DAMAGE_DROPPED) {
 *           // Found the bug!
 *       }
 *   });
 *
 *   // Run test scenario
 *   recorder.dumpTimeline(System.out);
 *   recorder.assertNoDroppedDamage();
 *
 * THREAD SAFETY:
 *   - Events are recorded via CopyOnWriteArrayList (lock-free reads)
 *   - Recording can be called from any thread
 *   - Timeline analysis should run single-threaded
 */
public class RendererTraceRecorder {
    private static final RendererTraceRecorder INSTANCE = new RendererTraceRecorder();

    // Events stored chronologically
    private final List<RendererTraceEvent> events = new CopyOnWriteArrayList<>();
    private final List<Consumer<RendererTraceEvent>> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean enabled = false;
    private volatile boolean captureStackTraces = false;

    private RendererTraceRecorder() {}

    public static RendererTraceRecorder getInstance() {
        return INSTANCE;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setCaptureStackTraces(boolean capture) {
        this.captureStackTraces = capture;
    }

    /**
     * Record an event. Does nothing if recording is disabled.
     */
    public void record(RendererTraceEvent event) {
        if (!enabled) {
            return;
        }
        events.add(event);
        for (Consumer<RendererTraceEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                // Prevent listener exceptions from affecting recording
            }
        }
    }

    /**
     * Convenience method for recording with builder
     */
    public void record(RendererTraceEvent.Builder builder) {
        if (captureStackTraces) {
            builder.withStackTrace();
        }
        record(builder.build());
    }

    /**
     * Add listener for real-time analysis
     */
    public void addEventListener(Consumer<RendererTraceEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Remove listener
     */
    public void removeEventListener(Consumer<RendererTraceEvent> listener) {
        listeners.remove(listener);
    }

    /**
     * Get all events as immutable list
     */
    public List<RendererTraceEvent> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    /**
     * Get events filtered by type
     */
    public List<RendererTraceEvent> getEvents(RendererTraceEvent.Type type) {
        return events.stream()
            .filter(e -> e.getType() == type)
            .collect(Collectors.toList());
    }

    /**
     * Get events for a specific source (e.g., renderable name)
     */
    public List<RendererTraceEvent> getEventsForSource(String source) {
        return events.stream()
            .filter(e -> e.getSource().equals(source))
            .collect(Collectors.toList());
    }

    /**
     * Get events within a time window (nanoseconds relative to first event)
     */
    public List<RendererTraceEvent> getEventsInWindow(long startNs, long endNs) {
        long baseTime = events.isEmpty() ? 0 : events.get(0).getTimestampNs();
        return events.stream()
            .filter(e -> {
                long relative = e.getTimestampNs() - baseTime;
                return relative >= startNs && relative <= endNs;
            })
            .collect(Collectors.toList());
    }

    /**
     * Clear all recorded events
     */
    public void clear() {
        events.clear();
    }

    /**
     * Dump timeline to output
     */
    public void dumpTimeline(Appendable out) {
        dumpTimeline(out, null);
    }

    /**
     * Dump timeline filtered by types
     */
    public void dumpTimeline(Appendable out, List<RendererTraceEvent.Type> filterTypes) {
        try {
            if (events.isEmpty()) {
                out.append("[No events recorded]\n");
                return;
            }

            long baseTime = events.get(0).getTimestampNs();
            RendererTraceEvent.Type lastType = null;

            out.append("=== Renderer Trace Timeline ===\n");
            out.append(String.format("Total events: %d\n", events.size()));
            out.append(String.format("Time span: %d microseconds\n\n",
                TimeUnit.NANOSECONDS.toMicros(events.get(events.size() - 1).getTimestampNs() - baseTime)));

            for (RendererTraceEvent event : events) {
                if (filterTypes != null && !filterTypes.contains(event.getType())) {
                    continue;
                }

                long relativeUs = TimeUnit.NANOSECONDS.toMicros(event.getTimestampNs() - baseTime);

                // Group consecutive events of same type visually
                String prefix = (event.getType() == lastType) ? "   " : "\n";
                lastType = event.getType();

                out.append(String.format("%s%6dus %s\n", prefix, relativeUs, event.toString()));

                // Include stack trace if present
                if (event.getStackTrace() != null) {
                    StackTraceElement[] stack = event.getStackTrace().getStackTrace();
                    for (int i = 1; i < Math.min(stack.length, 8); i++) { // Skip first two internal frames
                        out.append(String.format("           at %s\n", stack[i]));
                    }
                }
            }
            out.append("\n=== End Timeline ===\n");
        } catch (Exception e) {
            // Ignore output errors
        }
    }

    /**
     * ASSERTION: Verify no damage was dropped
     *
     * Damage is considered "dropped" if:
     * - INVALIDATE_REQUESTED was called but never reached DAMAGE_REPORTED
     * - INVALIDATE_DEFERRED was set but PENDING_INVALIDATE was never cleared by applyLayoutData
     * - DAMAGE_PROPAGATED was called but never reached DAMAGE_ACCUMULATED
     */
    public boolean hasDroppedDamage() {
        return !getDroppedDamageEvents().isEmpty();
    }

    /**
     * Find events where damage might have been lost
     */
    public List<DroppedDamageReport> getDroppedDamageReports() {
        List<DroppedDamageReport> reports = new ArrayList<>();

        // Check for pendingInvalidates that were set but not cleared
        // by LAYOUT_NODE_COMMIT
        List<RendererTraceEvent> pendingSets = new ArrayList<>();
        for (RendererTraceEvent event : events) {
            if (event.getType() == RendererTraceEvent.Type.PENDING_INVALIDATE_SET) {
                pendingSets.add(event);
            } else if (event.getType() == RendererTraceEvent.Type.PENDING_INVALIDATE_CLEARED) {
                // Match by source
                String source = event.getSource();
                pendingSets.removeIf(e -> e.getSource().equals(source));
            }
        }

        // Any remaining pending sets = dropped damage
        for (RendererTraceEvent dropped : pendingSets) {
            reports.add(new DroppedDamageReport(
                DroppedDamageReport.Reason.PENDING_NEVER_CLEARED,
                dropped,
                "pendingInvalidate set but never cleared by applyLayoutData",
                null
            ));
        }

        return reports;
    }

    /**
     * Assert no damage was dropped
     */
    public void assertNoDroppedDamage() throws AssertionError {
        List<DroppedDamageReport> dropped = getDroppedDamageReports();
        if (!dropped.isEmpty()) {
            StringBuilder msg = new StringBuilder("Damage was dropped:\n");
            for (DroppedDamageReport report : dropped) {
                msg.append("  - ").append(report.describe()).append("\n");
            }
            throw new AssertionError(msg.toString());
        }
    }

    /**
     * Get events indicating dropped damage
     */
    private List<RendererTraceEvent> getDroppedDamageEvents() {
        return events.stream()
            .filter(e -> e.getType() == RendererTraceEvent.Type.DAMAGE_DROPPED)
            .collect(Collectors.toList());
    }

    /**
     * Record render request being dropped
     */
    public void recordRenderDropped(String source, String reason) {
        record(RendererTraceEvent.builder(RendererTraceEvent.Type.RENDER_DROPPED, source)
            .with("reason", reason));
    }

    /**
     * Record damage propagation
     */
    public void recordDamagePropagation(String fromRenderable, String toRenderable,
                                         TerminalRectangle damageRegion) {
        record(RendererTraceEvent.builder(RendererTraceEvent.Type.DAMAGE_PROPAGATED, fromRenderable)
            .with("to", toRenderable)
            .with("damage", damageRegion.toString()));
    }

    /**
     * DroppedDamageReport - Details about lost damage
     */
    public static class DroppedDamageReport {
        public enum Reason {
            PENDING_NEVER_CLEARED,    // pendingInvalidate was never consumed
            DAMAGE_NEVER_REPORTED,    // invalidate called but never reached root
            INVALIDATE_LOST_TIMING,   // invalidate lost due to race condition
            UNKNOWN
        }

        public final Reason reason;
        public final RendererTraceEvent triggerEvent;
        public final String description;
        public final RendererTraceEvent relatedEvent;

        public DroppedDamageReport(Reason reason, RendererTraceEvent triggerEvent,
                                   String description, RendererTraceEvent relatedEvent) {
            this.reason = reason;
            this.triggerEvent = triggerEvent;
            this.description = description;
            this.relatedEvent = relatedEvent;
        }

        public String describe() {
            return String.format("[%s] %s (trigger: %s)",
                reason, description, triggerEvent);
        }

        @Override
        public String toString() {
            return describe();
        }
    }
}
