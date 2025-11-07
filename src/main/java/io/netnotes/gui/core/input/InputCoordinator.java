package io.netnotes.gui.fx.uiNode.input;


import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netnotes.engine.state.BitFlagStateMachine;

public final class InputCoordinator {

    private final Map<Integer, InputSource> sources = new ConcurrentHashMap<>();
    private final Map<Integer, InputEventParser> parsers = new ConcurrentHashMap<>();
    private final BitFlagStateMachine stateMachine;

    public InputCoordinator(BitFlagStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    public void addSource(InputSource source) {
        sources.put(source.getSourceId(), source);
        parsers.put(source.getSourceId(), source.getParser());
        source.start(packet -> handlePacket(source.getSourceId(), packet));
    }

    private void handlePacket(int sourceId, byte[] packet) {
        InputEvent event = parsers.get(sourceId).parse(packet);
        applyToStateMachine(event);
    }

    private void applyToStateMachine(InputEvent event) {
        // Example: Map InputEvent types to bit flags or transitions
        // long newBits = decodeEventToBitmask(event);
        //stateMachine.setBits(newBits);
    }

    public void stopAll() {
        sources.values().forEach(InputSource::stop);
    }
}

