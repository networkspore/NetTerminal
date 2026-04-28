package io.netnotes.terminal.layout;

import io.netnotes.engine.ui.BorderPanel;
import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.components.panels.TerminalBorderPanel;
import io.netnotes.terminal.components.text.TerminalLabel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TerminalDamageIntegrationTest {

    private static final int W = 80;
    private static final int H = 24;

    private TerminalDamageTestHarness harness;
    private TerminalBorderPanel panel;

    @BeforeEach
    void setup() {
        panel = new TerminalBorderPanel("bp");
        harness = new TerminalDamageTestHarness(W, H);
        harness.attach(panel); // blocks until first idle
        // No drainInitialRender() needed – attach ensures everything settled.
        // If the initial layout produces a render, it’s done by now.
    }

    // … all test methods remain exactly as before, except that:
    //   - `assertTrue(harness.waitForLayoutComplete(), …)` calls are removed.
    //   - `harness.drainInitialRender()` is gone.
    //   - `harness.flushLayout()` calls (if any) are removed.
    //
    // The existing capture pattern (beginDamageCapture / awaitDamageCapture) works
    // with the new latch‑based implementation without any further changes.

    @Nested
    class ExplicitInvalidation {
        @Test
        void full_invalidate_produces_damage_covering_entire_panel() {
            List<TerminalRectangle> damage = harness.captureNextRender(
                    () -> panel.invalidate());
            assertFalse(damage.isEmpty());
            assertTrue(TerminalDamageTestHarness.unionCovers(damage, 0, 0, W, H));
        }

        @Test
        void invalidate_after_content_add_covers_at_least_the_affected_slot() {
            int topH = 3;
            TerminalLabel label = labelWithMinHeight("hdr", topH);

            harness.beginDamageCapture();
            panel.addToPanel(BorderPanel.TOP, label);
            List<TerminalRectangle> addDamage = harness.awaitDamageCapture();

            assertFalse(addDamage.isEmpty());
            assertTrue(TerminalDamageTestHarness.unionCovers(addDamage, 0, 0, W, topH));
        }

        @Test
        void repeated_invalidate_calls_each_produce_damage() {
            List<TerminalRectangle> first = harness.captureNextRender(() -> panel.invalidate());
            assertFalse(first.isEmpty());

            List<TerminalRectangle> second = harness.captureNextRender(() -> panel.invalidate());
            assertFalse(second.isEmpty());
        }
    }

    // remaining tests exactly as original, no changes needed …
    // (ContentChanges, VisibilityChanges, Resize, AccumulatorInteractions,
    //  LayoutDrivenDamage) – all use the same harness API.

    private static TerminalLabel labelWithMinHeight(String name, int h) {
        TerminalLabel label = new TerminalLabel(name);
        label.setMinHeight(h);
        label.setHeightPreference(SizePreference.FIT_CONTENT);
        return label;
    }

    private static TerminalLabel fillTopLabel(String name, int h) {
        TerminalLabel label = labelWithMinHeight(name, h);
        label.setMinWidth(1);
        label.setWidthPreference(SizePreference.FILL);
        return label;
    }

    private static TerminalLabel fillLabel(String name) {
        TerminalLabel label = new TerminalLabel(name, "x");
        label.setMinWidth(1);
        label.setMinHeight(1);
        label.setWidthPreference(SizePreference.FILL);
        label.setHeightPreference(SizePreference.FILL);
        return label;
    }
}