package io.netnotes.terminal.components.install;

import io.netnotes.terminal.layout.TerminalLayoutTestHarness;

/**
 * Install-wizard specific harness wrapper.
 *
 * Keeps wizard tests aligned with the shared layout harness behavior used by
 * layout integration tests (attach + waitForLayoutComplete).
 */
public final class TerminalInstallWizardTestHarness extends TerminalLayoutTestHarness {

    public TerminalInstallWizardTestHarness(int width, int height) {
        super(width, height);
    }

    public void attach(TerminalInstallWizard wizard) {
        super.attach(wizard);
    }
}
