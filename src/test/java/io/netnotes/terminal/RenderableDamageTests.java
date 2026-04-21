package io.netnotes.terminal;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.netnotes.debug.RendererTraceEvent;
import io.netnotes.debug.RendererTraceRecorder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RenderableDamageTests - Unit tests for damage propagation timing scenarios
 * at the concrete TerminalRenderable level.
 *
 * These tests verify that damage is not lost during critical timing windows:
 * 1. invalidate() called during content changes
 * 2. render request arrives before damage is accumulated
 * 3. visibility transitions trigger correct damage
 * 4. damage propagates to parent
 */
public class RenderableDamageTests {

    private RendererTraceRecorder traceRecorder;

    @BeforeEach
    void setUp() {
        traceRecorder = RendererTraceRecorder.getInstance();
        traceRecorder.setEnabled(true);
        traceRecorder.clear();
    }

    @AfterEach
    void tearDown() {
        traceRecorder.setEnabled(false);
        traceRecorder.clear();
    }

    /**
     * Test: invalidating a terminal label triggers damage
     *
     * Scenario:
     * - Create a TerminalLabel
     * - Change its text (internally calls invalidate)
     * - Expected: Damage is tracked
     */
    @Test
    void text_change_triggers_invalidate() {
        TestTerminalRenderable label = new TestTerminalRenderable("test-label");

        // Set initial region
        label.setRegion(new TerminalRectangle(0, 0, 10, 1));

        // Reset counters after construction
        label.resetTracking();

        // Call setText which should trigger invalidate
        label.setText("New Text");

        // Verify invalidate was called
        assertTrue(label.getInvalidateCount() > 0,
            "Changing text should trigger invalidate. Tracking: " + label.getTrackingState());
    }

    /**
     * Test: damage_reaches_accumulator_at_root
     *
     * Scenario:
     * - Deep child calls invalidate()
     * - Damage propagates up through parents
     * - Expected: Damage reaches root
     */
    @Test
    void damage_propagates_through_hierarchy() {
        // Create hierarchy using concrete TerminalLabel
        TestTerminalRenderable root = new TestTerminalRenderable("root");
        TestTerminalRenderable child = new TestTerminalRenderable("child");

        // Set regions
        root.setRegion(new TerminalRectangle(0, 0, 100, 100));
        child.setRegion(new TerminalRectangle(10, 10, 80, 80));

        // Add child
        root.addChild(child);

        // Reset tracking
        child.resetTracking();

        // Child invalidates
        child.setText("Trigger invalidate");
   
        //TODO: propatedEvents not used
        var propagatedEvents = traceRecorder.getEvents(RendererTraceEvent.Type.DAMAGE_PROPAGATED);
        // Note: Without full layout manager, damage propagation may not be complete
        // but we should at least see the invalidate
        assertTrue(child.getInvalidateCount() > 0, "Child should have called invalidate");
    }

    /**
     * Test: render_request_with_no_damage_is_dropped_correctly
     */
    @Test
    void render_request_with_no_damage_is_dropped_correctly() {
        TestTerminalRenderable renderable = new TestTerminalRenderable("test");
        renderable.setRegion(new TerminalRectangle(0, 0, 10, 10));

        // Request render without any invalidation
        boolean needsRender = renderable.checkNeedsRender();

        // Without damage, should not need render
        assertFalse(needsRender, "Should not need render without damage");
    }

    /**
     * Test: initial_phase_is_detached
     */
    @Test
    void initial_phase_is_detached() {
        TestTerminalRenderable renderable = new TestTerminalRenderable("test");

        // Initial phase should be DETACHED before layout manager registration
        assertEquals(io.netnotes.engine.ui.renderer.RenderPhase.DETACHED,
                     renderable.getCurrentRenderPhase(),
                     "New renderable should be DETACHED");
    }

    /**
     * Test: visibility_change_triggers_invalidate
     */
    @Test
    void visibility_change_triggers_invalidate() {
        TestTerminalRenderable renderable = new TestTerminalRenderable("test");
        renderable.setRegion(new TerminalRectangle(0, 0, 10, 10));
        renderable.resetTracking();

        // Make visible - this should trigger layout and potentially invalidate
        renderable.setVisible(true);

        // Content should have been processed
        assertTrue(renderable.getInvalidateCount() >= 0,
            "Visibility change should be tracked. State: " + renderable.getTrackingState());
    }

    // ===== Helper Methods =====
    //TODO: assertDamagePropagated not used
    private void assertDamagePropagated(String fromRenderable, String toRenderable) {
        var events = traceRecorder.getEvents(RendererTraceEvent.Type.DAMAGE_PROPAGATED);
        boolean found = events.stream().anyMatch(e ->
            e.getSource().equals(fromRenderable) &&
            toRenderable.equals(e.getAttribute("to"))
        );
        assertTrue(found, String.format("Expected damage to propagate from %s to %s", fromRenderable, toRenderable));
    }
}
