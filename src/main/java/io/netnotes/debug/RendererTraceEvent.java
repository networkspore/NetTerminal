package io.netnotes.debug;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RendererTraceEvent - Immutable event in the renderer trace timeline.
 *
 * Captures a single moment in the render/layout/damage lifecycle with:
 * - Timestamp (nanoseconds for precise ordering)
 * - Event type (INVALIDATE, APPLY_LAYOUT, RENDER_REQUEST, etc.)
 * - Source (renderable name, layout manager, container, etc.)
 * - Thread ID for detecting cross-thread issues
 * - Key-value attributes for context
 */
public final class RendererTraceEvent {
    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    public enum Type {
        // Damage tracking
        INVALIDATE_REQUESTED,      // invalidate() called
        INVALIDATE_DEFERRED,       // invalidate() deferred during layout
        INVALIDATE_IMMEDIATE,      // invalidateImmediate() executed
        DAMAGE_PROPAGATED,         // propagateDamageUp called
        DAMAGE_REPORTED,           // reportDamage called (reached root)
        DAMAGE_ACCUMULATED,        // DamageAccumulator.add() called
        DAMAGE_DROPPED,            // Damage was lost/discarded

        // Layout lifecycle
        LAYOUT_PASS_START,         // performUpdate started
        LAYOUT_PASS_END,           // performUpdate completed
        LAYOUT_NODE_COMMIT,        // applyLayoutData called on node
        LAYOUT_DIRTY_MARK,         // markLayoutDirty called

        // Render lifecycle
        RENDER_REQUESTED,          // render requested
        RENDER_DROPPED,            // render dropped (no damage/dirty)
        RENDER_STARTED,            // renderInternal started
        RENDER_COMPLETED,          // renderInternal completed
        TO_BATCH_START,            // toBatch called
        TO_BATCH_END,              // toBatch completed

        // State transitions
        PHASE_ADVANCE,             // advanceRenderPhase called
        STATE_CHANGE,              // State machine transition

        // Timing markers
        PENDING_INVALIDATE_SET,    // pendingInvalidate = true
        PENDING_INVALIDATE_CLEARED, // pendingInvalidate = false (consumed)

        // Batch building (for text rendering bug diagnostics)
        BATCH_COMMAND_ADDED,       // Command added to batch builder
        BATCH_BUILT,               // Final batch command built
        BATCH_EMPTY_UNEXPECTED,    // Batch is unexpectedly empty
        TEXT_RENDER_ATTEMPT,       // Text component tried to render
        DAMAGE_CHECK               // Damage status checked during render
    }

    private final long sequence;
    private final long timestampNs;
    private final Type type;
    private final String source;
    private final long threadId;
    private final Map<String, String> attributes;
    private final Throwable stackTrace; // Captured for certain events

    private RendererTraceEvent(Builder builder) {
        this.sequence = SEQUENCE.incrementAndGet();
        this.timestampNs = System.nanoTime();
        this.type = builder.type;
        this.source = builder.source;
        this.threadId = Thread.currentThread().threadId();
        this.attributes = Collections.unmodifiableMap(new HashMap<>(builder.attributes));
        this.stackTrace = builder.captureStack ? new Throwable("Stack capture") : null;
    }

    public long getSequence() { return sequence; }
    public long getTimestampNs() { return timestampNs; }
    public Type getType() { return type; }
    public String getSource() { return source; }
    public long getThreadId() { return threadId; }
    public Map<String, String> getAttributes() { return attributes; }
    public Throwable getStackTrace() { return stackTrace; }

    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    public String getAttribute(String key) {
        return attributes.get(key);
    }

    public String getAttribute(String key, String defaultValue) {
        return attributes.getOrDefault(key, defaultValue);
    }

    public long getDurationSince(RendererTraceEvent other) {
        return this.timestampNs - other.timestampNs;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%d] @%dns [%s] %s (tid=%d)",
            sequence, timestampNs, type, source, threadId));
        if (!attributes.isEmpty()) {
            sb.append(" {")
              .append(attributes.toString().replace('{', ' ').replace('}', ' ').trim())
              .append('}');
        }
        return sb.toString();
    }

    public static Builder builder(Type type, String source) {
        return new Builder(type, source);
    }

    public static class Builder {
        private final Type type;
        private final String source;
        private final Map<String, String> attributes = new HashMap<>();
        private boolean captureStack = false;

        private Builder(Type type, String source) {
            this.type = type;
            this.source = source;
        }

        public Builder with(String key, String value) {
            if (value != null) {
                attributes.put(key, value);
            }
            return this;
        }

        public Builder with(String key, int value) {
            attributes.put(key, String.valueOf(value));
            return this;
        }

        public Builder with(String key, long value) {
            attributes.put(key, String.valueOf(value));
            return this;
        }

        public Builder with(String key, boolean value) {
            attributes.put(key, String.valueOf(value));
            return this;
        }

        public Builder with(String key, Object value) {
            if (value != null) {
                attributes.put(key, value.toString());
            }
            return this;
        }

        public Builder withStackTrace() {
            this.captureStack = true;
            return this;
        }

        public RendererTraceEvent build() {
            return new RendererTraceEvent(this);
        }
    }
}
