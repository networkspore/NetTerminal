package io.netnotes.gui.fx.app;

import javafx.application.HostServices;
import javafx.application.Application.Parameters;

public interface FxApplicationInterface{
    void shutdownNow();
    HostServices getHostServices();
    Parameters getParameters();
}