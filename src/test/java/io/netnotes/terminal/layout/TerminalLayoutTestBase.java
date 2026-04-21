package io.netnotes.terminal.layout;

import io.netnotes.terminal.TerminalRegion;
import org.junit.jupiter.api.BeforeEach;

public class TerminalLayoutTestBase {
    protected TerminalRegion rootRegion;
    protected int testWidth = 80;
    protected int testHeight = 24;

    @BeforeEach
    void setupTestBase() {
        rootRegion = new TerminalRegion(0, 0, testWidth, testHeight);
    }
}