package io.netnotes.terminal.components.install;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * State-transition and mutation behavior tests for TerminalInstallWizard.
 */
public class TerminalInstallWizardStateTest {

    @Test
    void stepStateListenerReceivesExpectedTransitions() {
        TerminalInstallWizard wizard = TerminalInstallWizard.builder("test-wizard")
            .title("Test Wizard")
            .build();

        List<String> transitions = new ArrayList<>();
        wizard.withStepStateListener((step, oldStatus, newStatus) ->
            transitions.add(step.getId() + ": " + oldStatus + "->" + newStatus)
        );

        InstallStep step1 = new InstallStep("step1", "First Step");
        InstallStep step2 = new InstallStep("step2", "Second Step");
        wizard.addStep(step1);
        wizard.addStep(step2);

        wizard.beginStep("step1");
        wizard.updateProgress("step1", 0.5f, "Halfway there");
        wizard.completeStep("step1");
        wizard.beginStep("step2");
        wizard.failStep("step2", "Configuration error");

        assertTrue(transitions.contains("step1: PENDING->RUNNING"));
        assertTrue(transitions.contains("step1: RUNNING->COMPLETE"));
        assertTrue(transitions.contains("step2: PENDING->RUNNING"));
        assertTrue(transitions.contains("step2: RUNNING->ERROR"));
    }

    @Test
    void stepStateListenerIgnoresNoOpStatusChange() {
        TerminalInstallWizard wizard = TerminalInstallWizard.builder("test-wizard")
            .title("Test Wizard")
            .build();

        int[] callbackCount = {0};
        wizard.withStepStateListener((step, oldStatus, newStatus) -> callbackCount[0]++);

        wizard.addStep(InstallStep.builder("step", "Test Step").build());

        wizard.completeStep("step");
        int afterFirstComplete = callbackCount[0];
        wizard.completeStep("step");

        assertEquals(afterFirstComplete, callbackCount[0],
            "Second completeStep call should not emit an extra transition");
    }

    @Test
    void tickInvalidatesOnlyWhenVisualStateChanges() {
        TestableTerminalInstallWizard wizard = new TestableTerminalInstallWizard("tick-test");

        // First tick initializes footer cache state and may invalidate once.
        wizard.tick();
        int afterFirstTick = wizard.getInvalidationCount();

        wizard.tick();
        assertEquals(afterFirstTick, wizard.getInvalidationCount(),
            "Subsequent tick should not invalidate when nothing changes");

        wizard.addStep(InstallStep.builder("step", "Spinner Step").build());
        wizard.beginStep("step");
        int afterBegin = wizard.getInvalidationCount();
        wizard.tick();

        assertTrue(wizard.getInvalidationCount() > afterBegin,
            "Tick should invalidate while an active step spinner is advancing");
    }

    @Test
    void resetWizardResetsStepStateButKeepsStepDefinitions() {
        TerminalInstallWizard wizard = TerminalInstallWizard.builder("reset-test")
            .title("Reset Test")
            .subtitle("State validation")
            .build();

        wizard.addSteps(List.of(
            InstallStep.builder("step1", "Step One").build(),
            InstallStep.builder("step2", "Step Two").build()
        ));
        wizard.beginStep("step1");
        wizard.updateProgress("step1", 0.6f, "Applying changes");
        wizard.logLine("step1", "Created directory");
        wizard.completeStep("step1");
        wizard.beginStep("step2");
        wizard.failStep("step2", "Network timeout");

        wizard.resetWizard();

        assertEquals(2, wizard.getSteps().size(), "Reset should preserve step definitions");

        for (InstallStep step : wizard.getSteps()) {
            assertEquals(InstallStep.Status.PENDING, step.getStatus());
            assertEquals(0f, step.getProgress(), 0.0001f);
            assertNull(step.getDetail());
            assertNull(step.getErrorMessage());
            assertTrue(step.getLogLines().isEmpty());
        }

        assertEquals(0f, wizard.getTerminalWizardHeader().getOverallProgress(), 0.0001f);
    }

    private static final class TestableTerminalInstallWizard extends TerminalInstallWizard {
        private int invalidationCount = 0;

        private TestableTerminalInstallWizard(String name) {
            super(TerminalInstallWizard.builder(name));
        }

        @Override
        public void invalidate() {
            invalidationCount++;
            super.invalidate();
        }

        private int getInvalidationCount() {
            return invalidationCount;
        }
    }
}
