package io.netnotes.terminal.components.install;

import io.netnotes.engine.ui.SizePreference;
import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TextStyle;
import io.netnotes.terminal.TextStyle.LineStyle;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout-driven wizard measurement tests.
 *
 * These tests intentionally follow the same attach/wait pattern as the shared
 * layout tests so visual refactors can rely on committed layout state.
 */
public class TerminalInstallWizardCacheTest {
    private static final int ROOT_W = 120;
    private static final int ROOT_H = 40;
    private static final TerminalLayoutContext[] NO_CONTEXT = new TerminalLayoutContext[0];

    @Test
    void fitContentWidthGrowsWhenWiderStepIsAdded() {
        TerminalInstallWizard wizard = createFitContentWizard();
        TerminalInstallWizardTestHarness harness = attachWizard(wizard);

        wizard.addStep(InstallStep.builder("s1", "Short step").build());
        wizard.addStep(InstallStep.builder("s2", "Another short step").build());
        assertTrue(harness.waitForLayoutComplete(), "Layout after initial step add must complete");

        int initialWidth = measureWidth(wizard);

        wizard.addStep(InstallStep.builder("s3",
            "A much wider step label used to verify fit-content width recomputation").build());
        assertTrue(harness.waitForLayoutComplete(), "Layout after adding wide step must complete");

        int newWidth = measureWidth(wizard);
        assertTrue(newWidth > initialWidth,
            "Expected fit-content width to increase after adding wider step");
    }

    @Test
    void expandedStepWithLongLogsIncreasesFitContentWidth() {
        TerminalInstallWizard wizard = createFitContentWizard();
        TerminalInstallWizardTestHarness harness = attachWizard(wizard);

        wizard.addStep(InstallStep.builder("step1", "Test Step").build());
        assertTrue(harness.waitForLayoutComplete(), "Layout after adding step must complete");

        int collapsedWidth = measureWidth(wizard);

        wizard.setStepExpanded("step1", true);
        for (int i = 0; i < 4; i++) {
            wizard.logLine("step1",
                "Log line " + i + " - extracting very-long-resource-path-component-" + "x".repeat(50));
        }
        assertTrue(harness.waitForLayoutComplete(), "Layout after expand/log updates must complete");

        int expandedWidth = measureWidth(wizard);
        assertTrue(expandedWidth > collapsedWidth,
            "Expanded long log content should increase fit-content width");
    }

    @Test
    void manyStepsRemainMeasurableAfterLayoutSettles() {
        TerminalInstallWizard wizard = createFitContentWizard();
        TerminalInstallWizardTestHarness harness = attachWizard(wizard);

        for (int i = 0; i < 40; i++) {
            wizard.addStep(InstallStep.builder("step" + i, "Step " + i).build());
        }
        assertTrue(harness.waitForLayoutComplete(), "Layout after adding many steps must complete");

        assertDoesNotThrow(() -> {
            int width = measureWidth(wizard);
            int height = measureHeight(wizard);
            assertTrue(width > 0, "Measured width should be positive");
            assertTrue(height > 0, "Measured height should be positive");
        }, "Wizard should remain measurable with many rows");
    }

    @Test
    void expandedDetailChangeTriggersWidthRecompute() {
        TerminalInstallWizard wizard = createFitContentWizard();
        TerminalInstallWizardTestHarness harness = attachWizard(wizard);

        wizard.addStep(InstallStep.builder("cfg", "Configure system").build());
        wizard.beginStep("cfg");
        wizard.setStepExpanded("cfg", true);
        assertTrue(harness.waitForLayoutComplete(), "Layout after begin/expand must complete");

        int beforeDetail = measureWidth(wizard);

        wizard.updateProgress("cfg", 0.5f,
            "Progress: 50% - Writing configuration files into very-long-target-directory-name");
        assertTrue(harness.waitForLayoutComplete(), "Layout after detail update must complete");

        int afterDetail = measureWidth(wizard);
        assertTrue(afterDetail > beforeDetail,
            "Expanded detail text should increase fit-content width");
    }

    private TerminalInstallWizardTestHarness attachWizard(TerminalInstallWizard wizard) {
        TerminalInstallWizardTestHarness harness = new TerminalInstallWizardTestHarness(ROOT_W, ROOT_H);
        harness.attach(wizard);
        assertTrue(harness.waitForLayoutComplete(), "Initial wizard layout must complete");
        return harness;
    }

    private TerminalInstallWizard createFitContentWizard() {
        TerminalInstallWizard wizard = TerminalInstallWizard.builder("wizard-under-test")
            .title("Wizard")
            .subtitle("Layout testing")
            .showFooter(true)
            .showElapsed(true)
            .borderStyle(LineStyle.DOUBLE)
            .uniformStyle(TextStyle.NORMAL, TextStyle.BOLD)
            .build();
        wizard.setWidthPreference(SizePreference.FIT_CONTENT);
        wizard.setHeightPreference(SizePreference.FIT_CONTENT);
        return wizard;
    }

    private int measureWidth(TerminalInstallWizard wizard) {
        TerminalRectangle measured = wizard.measureContent(NO_CONTEXT);
        int width = measured.getWidth();
        wizard.getRegionPool().recycle(measured);
        return width;
    }

    private int measureHeight(TerminalInstallWizard wizard) {
        TerminalRectangle measured = wizard.measureContent(NO_CONTEXT);
        int height = measured.getHeight();
        wizard.getRegionPool().recycle(measured);
        return height;
    }
}
