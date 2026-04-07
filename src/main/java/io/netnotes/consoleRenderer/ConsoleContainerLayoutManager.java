package io.netnotes.consoleRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.netnotes.engine.ui.containers.Container;
import io.netnotes.engine.ui.containers.ContainerId;
import io.netnotes.engine.ui.containers.ContainerLayoutManager;
import io.netnotes.terminal.TerminalRectangle;

public class ConsoleContainerLayoutManager implements ContainerLayoutManager
<
    TerminalRectangle,
    ConsoleContainer
> {


    public static final int MIN_COL_WIDTH = 40;
    public static final int MIN_ROW_HEIGHT = 24;

    private ConsoleRenderer renderer;

    private final List<ConsoleContainer> displayOrder = new ArrayList<>();
    private int maxVisible = 1;
    private int focusedIndex = 0;
    private boolean layoutLocked = false;
    private ContainerId lockedContainerId = null;
    private int preLockMaxVisible = 1;
    private int preLockFocusedIndex = 0;
    private int termWidth = 40;
    private int termHeight = 24;

    public void init(ConsoleRenderer renderer, int termWidth, int termHeight) {
        this.renderer = renderer;
        this.termWidth = termWidth;
        this.termHeight = termHeight;
    }

    @Override
    public CompletableFuture<Void> onContainerAdded(ConsoleContainer container) {
        displayOrder.add(container);
        ensureFocusIfNeeded();
        return reflow();
    }

    @Override
    public CompletableFuture<Void> onContainerRemoved(ConsoleContainer container) {
        int idx = displayOrder.indexOf(container);
        displayOrder.remove(container);
        if (layoutLocked) {
            if (container.getId().equals(lockedContainerId)) {
                unlockContainer(false);
                return reflow();
            }
            refreshLockedFocus();
        } else {
            if (focusedIndex >= displayOrder.size()) focusedIndex = Math.max(0, displayOrder.size() - 1);
            else if (idx < focusedIndex) focusedIndex--;
        }
        ensureFocusIfNeeded();
        return reflow();
    }

    @Override
    public CompletableFuture<Void> onViewportResized(TerminalRectangle viewPort) {
        this.termWidth = viewPort.getWidth();
        this.termHeight = viewPort.getHeight();
        return reflow();
    }

    public void onContainerShown(ConsoleContainer container) {
        ensureFocusIfNeeded();
    }

    public void onContainerHidden(ConsoleContainer container) {
        int idx = displayOrder.indexOf(container);
        if (idx < 0) return;
        // if focused container hidden, cycle to next visible
        if (!layoutLocked && idx == focusedIndex) {
            focusNextFocusable(idx, 1);
        }
        ensureFocusIfNeeded();
        reflow();
    }


    public void focusSlot(int slot) {
        if (layoutLocked) return;
        List<Integer> focusable = getFocusableIndices();
        if (slot < 0 || slot >= focusable.size()) return;
        requestFocusIndex(focusable.get(slot));
    }



    // Ctrl+[
    public void cycleForward() {
        if (layoutLocked) return;
        int size = displayOrder.size();
        if (size < 2) return;
        
        focusNextFocusable(focusedIndex, 1);
    }

    public void cycleBackward() {
        if (layoutLocked) return;
        int size = displayOrder.size();
        if (size < 2) return;

        focusNextFocusable(focusedIndex, -1);
    }

    // Ctrl+Alt+Left/Right
    public void moveContainer(int delta) {
        if (layoutLocked) return;
        int target = focusedIndex + delta;
        if (target < 0 || target >= displayOrder.size()) return;
        Collections.swap(displayOrder, focusedIndex, target);
        focusedIndex = target;
        reflow();
    }

    // Ctrl+Alt+=/−
    public void adjustMaxVisible(int delta) {
        if (layoutLocked) return;
        maxVisible = Math.max(1, maxVisible + delta);
        reflow();
    }

    public void lockToContainer(ContainerId id) {
        if (id == null) return;
        int idx = indexOfContainerId(id);
        if (idx < 0) return;
        if (!layoutLocked) {
            preLockMaxVisible = maxVisible;
            preLockFocusedIndex = focusedIndex;
        }
        layoutLocked = true;
        lockedContainerId = id;
        maxVisible = 1;
        focusedIndex = idx;
        requestFocus(id);
    }

    public void unlockContainer() {
        unlockContainer(true);
    }

    private void unlockContainer(boolean reflow) {
        if (!layoutLocked) return;
        layoutLocked = false;
        lockedContainerId = null;
        maxVisible = Math.max(1, preLockMaxVisible);
        focusedIndex = clampFocusedIndex(preLockFocusedIndex);
        if (!displayOrder.isEmpty()) {
            requestFocus(displayOrder.get(focusedIndex).getId());
        }
        if (reflow) reflow();
    }

    public boolean canGrantFocus(ContainerId id) {
        if (id == null) return false;
        if (layoutLocked && lockedContainerId != null && !lockedContainerId.equals(id)) return false;
        int idx = indexOfContainerId(id);
        if (idx < 0) return false;
        return isFocusable(displayOrder.get(idx));
    }

    public CompletableFuture<Void> requestFocus(ContainerId id) {
        if (id == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("ContainerId required"));
        }
        if (layoutLocked && lockedContainerId != null && !lockedContainerId.equals(id)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Focus locked"));
        }
        int idx = indexOfContainerId(id);
        if (idx < 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Container not found"));
        }
        if (!isFocusable(displayOrder.get(idx))) {
            return CompletableFuture.failedFuture(new IllegalStateException("Container not focusable"));
        }
        return displayOrder.get(idx).requestFocus();
    }

    /**
     * Called by RenderManager AFTER grantFocus() and renderer.onFocusGranted() have completed.
     * Only responsible for updating the layout manager's own focusedIndex and triggering reflow.
     * Must NOT call renderer.onFocusGranted() or requestFocus() — that would re-enter the pipeline.
     */
    public void onFocusGranted(ContainerId id) {
        if (id == null) return;
        if (layoutLocked && lockedContainerId != null && !lockedContainerId.equals(id)) return;
        int idx = indexOfContainerId(id);
        if (idx < 0) return;
        focusedIndex = idx;
        reflow();
    }

    public int visibleCount() {
        return Math.min(getFocusableIndices().size(), maxVisible);
    }

    private void requestFocusIndex(int index) {
        if (index < 0 || index >= displayOrder.size()) return;
        requestFocus(displayOrder.get(index).getId());
    }

    private boolean isFocusable(ConsoleContainer container) {
        return container.getStateMachine().hasState(Container.STATE_VISIBLE) &&
            !container.getStateMachine().hasState(Container.STATE_HIDDEN);
    }

    private List<Integer> getFocusableIndices() {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < displayOrder.size(); i++) {
            if (isFocusable(displayOrder.get(i))) indices.add(i);
        }
        return indices;
    }

    private void focusNextFocusable(int startIndex, int direction) {
        int target = findNextFocusableIndex(startIndex, direction);
        if (target >= 0) {
            requestFocusIndex(target);
        }
    }

    private int findNextFocusableIndex(int startIndex, int direction) {
        int size = displayOrder.size();
        if (size == 0) return -1;
        int step = direction >= 0 ? 1 : -1;
        for (int i = 1; i <= size; i++) {
            int idx = (startIndex + (i * step) + size) % size;
            if (isFocusable(displayOrder.get(idx))) return idx;
        }
        return -1;
    }

    private void ensureFocusIfNeeded() {
        boolean anyFocused = displayOrder.stream()
            .anyMatch(c -> c.getStateMachine().hasState(Container.STATE_FOCUSED) && isFocusable(c));
        if (anyFocused) return;

        int firstFocusable = findFirstFocusableIndex();
        if (firstFocusable >= 0) {
            requestFocusIndex(firstFocusable);
            return;
        }

        // No focusable containers; revoke any stale focus.
        // revokeFocus() clears STATE_FOCUSED on the container and emits the focus-lost event.
        // renderer.onFocusRevoked() clears Renderer.focusedContainerId.
        displayOrder.stream()
            .filter(c -> c.getStateMachine().hasState(Container.STATE_FOCUSED))
            .forEach(c -> {
                c.revokeFocus();
                renderer.onFocusRevoked(c);
            });
    }

    private int findFirstFocusableIndex() {
        for (int i = 0; i < displayOrder.size(); i++) {
            if (isFocusable(displayOrder.get(i))) return i;
        }
        return -1;
    }

    private int indexOfContainerId(ContainerId id) {
        for (int i = 0; i < displayOrder.size(); i++) {
            if (displayOrder.get(i).getId().equals(id)) return i;
        }
        return -1;
    }

    private void refreshLockedFocus() {
        if (!layoutLocked || lockedContainerId == null) return;
        int idx = indexOfContainerId(lockedContainerId);
        if (idx >= 0) focusedIndex = idx;
    }

    private int clampFocusedIndex(int value) {
        if (displayOrder.isEmpty()) return 0;
        return Math.min(Math.max(0, value), displayOrder.size() - 1);
    }

    public List<ConsoleContainer> getVisibleContainers() {
        List<Integer> focusable = getFocusableIndices();
        int visible = Math.min(focusable.size(), maxVisible);
        if (visible == 0) return Collections.emptyList();

        int windowStart = 0;
        int focusableSize = focusable.size();
        if (focusableSize > visible) {
            int focusedFocusableIndex = indexOfFocusable(focusable, focusedIndex);
            if (focusedFocusableIndex < 0) focusedFocusableIndex = 0;
            int desiredStart = focusedFocusableIndex - (visible / 2);
            desiredStart = Math.max(0, Math.min(desiredStart, focusableSize - visible));
            windowStart = desiredStart;
        }

        List<ConsoleContainer> result = new ArrayList<>(visible);
        for (int i = 0; i < visible; i++) {
            result.add(displayOrder.get(focusable.get(windowStart + i)));
        }
        return result;
    }


    private CompletableFuture<Void> reflow() {
        List<Integer> focusable = getFocusableIndices();
        int visible = Math.min(focusable.size(), maxVisible);
        boolean isManaged = visible > 1;

        List<CompletableFuture<Void>> futures = new ArrayList<>(displayOrder.size());
        List<Integer> visibleIndices = new ArrayList<>(visible);

        int windowStart = 0;
        int focusableSize = focusable.size();
        if (focusableSize > visible) {
            int focusedFocusableIndex = indexOfFocusable(focusable, focusedIndex);
            if (focusedFocusableIndex < 0) focusedFocusableIndex = 0;
            int desiredStart = focusedFocusableIndex - (visible / 2);
            if (desiredStart < 0) desiredStart = 0;
            int maxStart = focusableSize - visible;
            if (desiredStart > maxStart) desiredStart = maxStart;
            windowStart = desiredStart;
        }

        if (visible > 0) {
            int colWidth = Math.max(termWidth / visible, MIN_COL_WIDTH);
            int remainder = termWidth - (colWidth * visible);

            // Containers in visible window get allocated bounds (on-screen)
            for (int i = 0; i < visible; i++) {
                int containerIndex = focusable.get(windowStart + i);
                visibleIndices.add(containerIndex);
                int x = i * colWidth;
                int w = (containerIndex == focusedIndex) ? colWidth + Math.max(0, remainder) : colWidth;
                // Sets allocated bounds on ConsoleContainer (clip rect for renderer)
                CompletableFuture<Void> future = displayOrder.get(containerIndex).setAllocatedBounds(
                    x, 0, w, termHeight, isManaged, false);
                futures.add(future);
            }
        }

        // Non-visible containers get off-screen bounds + flag
        for (int i = 0; i < displayOrder.size(); i++) {
            if (!visibleIndices.contains(i)) {
                ConsoleContainer container = displayOrder.get(i);
                futures.add(container.setAllocatedBoundsOffScreen(isManaged, true));
            }
        }
    
      
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> renderer.getRenderManager().markDirtyForNewGeneration());
    }

    private int indexOfFocusable(List<Integer> focusable, int containerIndex) {
        for (int i = 0; i < focusable.size(); i++) {
            if (focusable.get(i) == containerIndex) return i;
        }
        return -1;
    }

    
}