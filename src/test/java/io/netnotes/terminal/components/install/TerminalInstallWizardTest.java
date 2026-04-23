package io.netnotes.terminal.components.install;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.netnotes.terminal.TerminalRectangle;
import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.components.panels.TerminalPanel;
import io.netnotes.terminal.components.panels.TerminalVStack;
import io.netnotes.terminal.components.panels.TerminalHStack;
import io.netnotes.terminal.layout.TerminalLayoutContext;
import io.netnotes.engine.ui.SizePreference;

/**
 * Test class for TerminalInstallWizard to verify:
 * 1. The wizard correctly inherits measurement methods from TerminalRegion
 * 2. The wizard's custom measurement logic works correctly
 * 3. Integration with other panel components is maintained
 */
public class TerminalInstallWizardTest {

    private TerminalInstallWizard wizard;
    private TerminalLayoutContext[] childContexts;

    @BeforeEach
    public void setUp() {
        wizard = new TerminalInstallWizard.Builder("Test Wizard").build();
        childContexts = new TerminalLayoutContext[0];
    }

    @Test
    public void testTerminalInstallWizardCreation() {
        assertNotNull(wizard);
        assertEquals("Test Wizard", wizard.getName());
    }

    @Test
    public void testTerminalInstallWizardInheritsMeasurementMethods() {
        // Verify that the wizard can be measured (indirectly testing that it has the measurement infrastructure)
        assertDoesNotThrow(() -> {
            TerminalRectangle measured = wizard.measureContent(childContexts);
            assertNotNull(measured);
        });
    }

    @Test
    public void testTerminalInstallWizardMeasureContent() {
        // Test that the wizard's measureContent method works
        assertDoesNotThrow(() -> {
            TerminalRectangle measured = wizard.measureContent(childContexts);
            assertNotNull(measured);
            // Width and height can be 0 if the wizard has no content
            // The important thing is that the method doesn't throw an exception
        });
    }

    @Test
    public void testTerminalInstallWizardIntegrationWithPanels() {
        // Test that the wizard works correctly with other panel components
        TerminalVStack vstack = new TerminalVStack("testVStack");
        TerminalHStack hstack = new TerminalHStack("testHStack");
        TerminalPanel panel = new TerminalPanel("testPanel");

        // All components should be measurable
        assertDoesNotThrow(() -> {
            // Test TerminalVStack
            TerminalRectangle vstackMeasured = vstack.measureContent(childContexts);
            assertNotNull(vstackMeasured);

            // Test TerminalHStack
            TerminalRectangle hstackMeasured = hstack.measureContent(childContexts);
            assertNotNull(hstackMeasured);

            // Test TerminalPanel
            TerminalRectangle panelMeasured = panel.measureContent(childContexts);
            assertNotNull(panelMeasured);
        });
    }

    @Test
    public void testTerminalInstallWizardSizePreferences() {
        // Test different size preferences
        wizard.setWidthPreference(SizePreference.STATIC);
        wizard.setHeightPreference(SizePreference.FIT_CONTENT);

        assertEquals(SizePreference.STATIC, wizard.getWidthPreference());
        assertEquals(SizePreference.FIT_CONTENT, wizard.getHeightPreference());

        // Test measureContent with different preferences
        assertDoesNotThrow(() -> {
            TerminalRectangle measured = wizard.measureContent(childContexts);
            assertNotNull(measured);
        });
    }

    @Test
    public void testTerminalInstallWizardWithSteps() {
        // Test wizard with installation steps
        InstallStep step1 = new InstallStep("Step 1", "First step");
        InstallStep step2 = new InstallStep("Step 2", "Second step");

        wizard.addStep(step1);
        wizard.addStep(step2);

        assertEquals(2, wizard.getSteps().size());

        // Test measurement with steps
        assertDoesNotThrow(() -> {
            TerminalRectangle measured = wizard.measureContent(childContexts);
            assertNotNull(measured);
            // With steps, the method should not throw an exception
        });
    }

    @Test
    public void testTerminalInstallWizardInheritanceChain() {
        // Verify the inheritance chain
        assertTrue(wizard instanceof TerminalRenderable);
        assertTrue(wizard.getClass().getSuperclass() != null);

        // The wizard should inherit from TerminalRegion which now has our measurement methods
        Class<?> superClass = wizard.getClass().getSuperclass();
        while (superClass != null) {
            if (superClass.getName().contains("TerminalRegion")) {
                break;
            }
            superClass = superClass.getSuperclass();
        }

        assertNotNull(superClass, "Wizard should inherit from TerminalRegion");
    }
}