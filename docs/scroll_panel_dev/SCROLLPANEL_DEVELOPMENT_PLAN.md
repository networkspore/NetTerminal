# TerminalScrollPanel Development Plan

## Document Purpose

A comprehensive implementation guide for enhancing TerminalScrollPanel. This document contains step-by-step instructions, code patterns, and decision rationale. Each phase builds on previous work and includes verification steps.

---

## Phase Priority Summary

| Phase | Feature | Complexity | Impact | Prerequisite |
|-------|---------|------------|--------|--------------|
| 1 | Focus-Aware Scrolling | Medium | High | None |
| 2 | Mouse-Interactive Scrollbars | Low-Medium | Medium | None |
| 3 | Scroll Event System | Low | High | None |
| 4 | Focus Visibility Auto-Scroll | Medium | High | Phase 1 |
| 5 | Full Terminal Mode | High | High | TerminalRenderer support |

---

## Phase 1: Focus-Aware Scrolling

### Purpose
Enable TerminalScrollPanel to track focus changes within its content and respond appropriately.

### Current Gap
The scroll panel has no mechanism to:
- Detect when a child component gains focus
- Know the spatial position of focused elements
- Auto-scroll to keep focused content visible

### Implementation Overview

```
┌─────────────────────────────────────┐
│ TerminalScrollPanel                 │
│ ┌───────────────────────────────┐   │
│ │ TerminalStackPanel (CENTER)   │   │
│ │ ┌───────────────────────────┐ │   │
│ │ │ ScrollableTextViewer      │ │   │
│ │ │ ┌───────┐ ┌───────┐       │ │   │
│ │ │ │input1 │ │input2 │ <- focus │   │
│ │ │ │       │ │[FOCUSED]         │ │   │
│ │ │ └───────┘ └─────▲───┘     │ │   │
│ │ └──────────────────┼────────┘ │   │
│ └────────────────────┼──────────┘   │
└──────────────────────┼───────────────┘
                       │
                       │ FocusChangeEvent
                       ▼
              FocusAwareScrollHandler
                       │
                       ▼
              Calculate visibility
              Auto-scroll if needed
```

### Step 1.1: Add Focus-Change Callback to TerminalRenderable

**File**: `TerminalRenderable.java`  
**Location**: After existing focus methods (around line 1150)

**Rationale**: The base TerminalRenderable needs to notify interested parties when focus changes. This is the source of truth for focus state.

**Implementation**:

```java
// Add interface near top of file (after imports)
public interface FocusChangeListener {
    void onFocusChange(TerminalRenderable component, boolean focused, boolean gaining);
}

// Add field
protected BiConsumer<TerminalRenderable, Boolean> onFocusChanged = null;

// Add register/unregister methods after existing focus methods
public void addFocusChangeListener(FocusChangeListener listener) {
    if (listener == null) return;
    // Register with base state machine
    getStateMachine().addListener((oldState, newState) -> {
        if (oldState.hasState(RenderableStates.STATE_FOCUSED) != 
            newState.hasState(RenderableStates.STATE_FOCUSED)) {
            boolean nowFocused = newState.hasState(RenderableStates.STATE_FOCUSED);
            listener.onFocusChange(this, nowFocused, nowFocused);
        }
    });
}
```

**Verification**:
```java
// Test in simple component
TerminalButton button = new TerminalButton("test");
button.addFocusChangeListener((comp, focused, gaining) -> {
    System.out.println(comp.getName() + " focus: " + focused);
});
```

### Step 1.2: Add Focus Tracking to TerminalScrollPanel

**File**: `TerminalScrollPanel.java`  
**Location**: New section after keyboard handlers

**Rationale**: Scroll panel needs to register listeners on all content children and track which component has focus.

**Implementation**:

```java
// Add field
private TerminalRenderable focusedChild = null;
private NoteBytesReadOnly focusListenerId = null;

// Add method to register focus tracking on content
private void registerContentFocusTracking() {
    TerminalRenderable content = getContent();
    if (content == null) return;
    
    // Register for focus changes on the content hierarchy
    registerFocusTrackingRecursive(content);
}

private void unregisterContentFocusTracking() {
    // Focus tracking cleaned up when content removed
    // Listener registrations are via noteBytes - need cleanup
    focusedChild = null;
}

private void registerFocusTrackingRecursive(TerminalRenderable renderable) {
    // Add listener for this component
    renderable.addFocusChangeListener((comp, focused, gaining) -> {
        if (gaining) {
            focusedChild = comp;
            onChildFocused(comp);
        } else if (focusedChild == comp) {
            focusedChild = null;
        }
    });
    
    // Recurse to children
    for (TerminalRenderable child : renderable.getChildren()) {
        registerFocusTrackingRecursive(child);
    }
}

protected void onChildFocused(TerminalRenderable child) {
    // Extension hook for subclasses
    // Called when any child gains focus
    Log.logMsg("[TerminalScrollPanel] Child focused: " + child.getName(), LOG_LEVEL);
}
```

**Integration Point**: Call `registerContentFocusTracking()` when:
- Content is set via `setContent()`
- Content is swapped via `swapContent()`
- Panel becomes active (STATE_ACTIVE added)

**Modification to setContent()**:
```java
public void setContent(TerminalRenderable content) {
    if (content == null) {
        clearPanel(BorderPanel.CENTER);
    } else {
        setPanel(BorderPanel.CENTER, content);
    }
    unregisterContentFocusTracking();  // Clean up old
    registerContentFocusTracking();      // Setup new
    requestLayoutUpdate();
}
```

**Verification**:
```java
TerminalScrollPanel panel = new TerminalScrollPanel("test");
ScrollableTextViewer viewer = new ScrollableTextViewer("content");
// Add interactive content with focus
viewer.addComponent(someFocusableInput);

panel.setContent(viewer);

// Focus the input
someFocusableInput.requestFocus();
// Should log: "[TerminalScrollPanel] Child focused: someInputName"
```

### Step 1.3: Add Focus Position Query API

**File**: `TerminalRenderable.java`  
**Purpose**: Allow getting absolute position of any renderable

**Addition**:
```java
/**
 * Get position in parent coordinate space
 */
public Point2D getPositionInParent() {
    TerminalRectangle region = getRegion();
    Point2D pos = new Point2D(region.getX(), region.getY());
    regionPool.recycle(region);
    return pos;
}

/**
 * Get position relative to ancestor, or absolute if no ancestor
 */
public Point2D getPositionRelativeTo(TerminalRenderable ancestor) {
    int x = 0, y = 0;
    TerminalRenderable current = this;
    
    while (current != null && current != ancestor) {
        TerminalRectangle region = current.getRegion();
        x += region.getX();
        y += region.getY();
        regionPool.recycle(region);
        current = current.getParent();
    }
    
    return new Point2D(x, y);
}
```

---

## Phase 2: Mouse-Interactive Scrollbars

### Purpose
Enable mouse interaction with scroll indicators (click positioning, drag scrolling).

### Prerequisites
- Mouse event infrastructure in TerminalContainer/ConsoleContainer
- Event routing to child components at mouse coordinates

### Step 2.1: Add Mouse Event Support to Scroll Indicators

**File**: `VScrollIndicator.java` and `HScrollIndicator.java`

**Implementation Pattern** (for VScrollIndicator):

```java
// Add to VScrollIndicator class

@Override
protected void setupStateTransitions() {
    super.setupStateTransitions();
    
    // Register click handler when started
    stateMachine.onStateAdded(RenderableStates.STATE_STARTED, (old, now, bit) -> {
        registerMouseHandler();
    });
    
    stateMachine.onStateRemoved(RenderableStates.STATE_STARTED, (old, now, bit) -> {
        removeMouseHandler();
    });
}

private NoteBytesReadOnly mouseHandlerId = null;
private boolean isDragging = false;

private void registerMouseHandler() {
    // Mouse down - start potential drag
    mouseHandlerId = addEventListener(
        EventBytes.EVENT_MOUSE_DOWN,  // or appropriate event type
        this::onMouseDown
    );
    
    // Also need mouse up and mouse move handlers
}

private void removeMouseHandler() {
    if (mouseHandlerId != null) {
        removeEventListener(mouseHandlerId);
        mouseHandlerId = null;
    }
}

private void onMouseDown(RoutedEvent event) {
    if (event instanceof MouseDownEvent mouseEvent) {
        // Calculate click position relative to indicator
        int localY = mouseEvent.getY() - getAbsoluteY();
        
        // Convert to scroll position
        int newScrollY = calculateScrollFromPosition(localY);
        
        // Notify parent scroll panel
        if (parent instanceof TerminalScrollPanel scrollPanel) {
            scrollPanel.scrollTo(scrollPanel.getScrollX(), newScrollY);
        }
        
        isDragging = true;
        event.setConsumed(true);
    }
}

private int calculateScrollFromPosition(int localY) {
    int height = getHeight();
    if (height <= 0 || max <= 0) return 0;
    
    // Calculate proportional position
    float percent = (float) localY / height;
    return (int) (percent * max);
}
```

**Alternative**: Instead of event listeners, use hit test in parent:

```java
// In TerminalScrollPanel - override hitTest
@Override
public TerminalRenderable hitTestChildren(Point2D point) {
    // Check if point is in scroll indicator
    if (vScrollIndicator != null && vScrollIndicator.hitTest(point)) {
        return vScrollIndicator;
    }
    if (hScrollIndicator != null && hScrollIndicator.hitTest(point)) {
        return hScrollIndicator;
    }
    return super.hitTestChildren(point);
}
```

### Step 2.2: Add Mouse Wheel Scrolling

**File**: `TerminalScrollPanel.java`

**Implementation**:

```java
// Add to constructor or setup
private void registerMouseWheelHandler() {
    TerminalRenderable content = getContent();
    if (content == null) return;
    
    // Register on content area
    content.addEventListener(
        EventBytes.EVENT_MOUSE_WHEEL,  // or appropriate type
        this::onMouseWheel
    );
}

private void onMouseWheel(RoutedEvent event) {
    if (event instanceof MouseWheelEvent wheelEvent) {
        int deltaY = wheelEvent.getScrollY();  // positive = down, negative = up
        
        // Scroll amount - adjust sensitivity
        int scrollAmount = deltaY * lineScrollAmount * 3;  // 3 lines per wheel tick
        
        if (verticalScrollEnabled) {
            scrollBy(0, scrollAmount);
            event.setConsumed(true);
        }
    }
}
```

---

## Phase 3: Scroll Event System

### Purpose
Add listener pattern for scroll changes to enable coordination between components.

### Implementation

**File**: `TerminalScrollPanel.java`

```java
// Add interface
public interface ScrollChangeListener {
    void onScrollChanged(int oldScrollX, int oldScrollY,
                        int newScrollX, int newScrollY);
}

// Add field
private final List<ScrollChangeListener> scrollListeners = new ArrayList<>();

// API methods
public void addScrollChangeListener(ScrollChangeListener listener) {
    if (listener != null && !scrollListeners.contains(listener)) {
        scrollListeners.add(listener);
    }
}

public void removeScrollChangeListener(ScrollChangeListener listener) {
    scrollListeners.remove(listener);
}

// Modify scrollTo to notify
@Override
public void scrollTo(int x, int y) {
    int oldX = this.scrollX;
    int oldY = this.scrollY;
    
    boolean changed = false;
    
    if (horizontalScrollEnabled && this.scrollX != x) {
        this.scrollX = Math.max(0, x);
        changed = true;
    }
    
    if (verticalScrollEnabled && this.scrollY != y) {
        this.scrollY = Math.max(0, y);
        changed = true;
    }
    
    if (changed) {
        requestLayoutUpdate();
        notifyScrollChanged(oldX, oldY, scrollX, scrollY);
    }
}

private void notifyScrollChanged(int oldX, int oldY, int newX, int newY) {
    for (ScrollChangeListener listener : scrollListeners) {
        listener.onScrollChanged(oldX, oldY, newX, newY);
    }
}
```

**Use Cases**:
```java
// Sync two scroll panels
panel1.addScrollChangeListener((ox, oy, nx, ny) -> {
    panel2.scrollTo(nx, ny);  // Sync scroll position
});

// Update custom header when scrolling
panel.addScrollChangeListener((ox, oy, nx, ny) -> {
    header.setScrollIndicator(nx);  // Show horizontal scroll in header
});
```

---

## Phase 4: Focus Visibility Auto-Scroll

### Purpose
Automatically scroll to keep focused elements visible.

### Prerequisites
- Phase 1 (focus tracking) complete
- Phase 3 (scroll events) complete

### Implementation

**File**: `TerminalScrollPanel.java`

**Modification to onChildFocused()**:

```java
protected void onChildFocused(TerminalRenderable child) {
    if (!autoScrollToFocus) return;  // Configurable flag
    
    // Get child position relative to CENTER stack
    TerminalStackPanel centerStack = getRegionStack(BorderPanel.CENTER);
    if (centerStack == null) return;
    
    Point2D childPos = child.getPositionRelativeTo(centerStack);
    TerminalRectangle childRegion = child.getRegion();
    
    int childLeft = childPos.getX();
    int childTop = childPos.getY();
    int childRight = childLeft + childRegion.getWidth();
    int childBottom = childTop + childRegion.getHeight();
    
    regionPool.recycle(childRegion);
    
    // Calculate viewport bounds
    int viewportLeft = scrollX;
    int viewportTop = scrollY;
    int viewportRight = scrollX + getCenterWidth();
    int viewportBottom = scrollY + getCenterHeight();
    
    // Determine visibility
    boolean needsHScroll = horizontalScrollEnabled &&
        (childLeft < viewportLeft || childRight > viewportRight);
    boolean needsVScroll = verticalScrollEnabled &&
        (childTop < viewportTop || childBottom > viewportBottom);
    
    // Calculate new scroll position to make child visible
    int newScrollX = scrollX;
    int newScrollY = scrollY;
    
    if (needsHScroll) {
        if (childLeft < viewportLeft) {
            // Scroll left to show child left edge
            newScrollX = childLeft;
        } else {
            // Scroll right to show child right edge
            newScrollX = childRight - getCenterWidth();
        }
    }
    
    if (needsVScroll) {
        if (childTop < viewportTop) {
            // Scroll up to show child top edge
            newScrollY = childTop;
        } else {
            // Scroll down to show child bottom edge
            newScrollY = childBottom - getCenterHeight();
        }
    }
    
    // Apply scroll
    if (newScrollX != scrollX || newScrollY != scrollY) {
        scrollTo(newScrollX, newScrollY);
    }
}
```

**Configuration**:
```java
private boolean autoScrollToFocus = true;

public void setAutoScrollToFocus(boolean enabled) {
    this.autoScrollToFocus = enabled;
}

public boolean isAutoScrollToFocus() {
    return autoScrollToFocus;
}
```

---

## Phase 5: Full Terminal Mode (Alternate Screen Buffer)

### Purpose
Enable "exploded" view where content takes over the entire terminal with native scrolling and mouse support.

### Terminal Standards Reference

**ANSI Escape Sequences**:
- Enter Alternate Screen Buffer: `ESC[?1049h` (smcup)
- Exit Alternate Screen Buffer: `ESC[?1049l` (rmcup)
- Clear Screen: `ESC[2J`
- Move Cursor Home: `ESC[H`
- Enable Mouse Tracking: `ESC[?1000h`
- Disable Mouse Tracking: `ESC[?1000l`

### Prerequisites

**TerminalRenderer Support Needed**:

```java
// New methods needed in TerminalRenderer/ConsoleContainer
void enterAlternateScreenBuffer();
void exitAlternateScreenBuffer();
void clearScreen();
void restoreApplicationView();
boolean isInAlternateBuffer();
void setAlternateBufferMode(boolean enabled);
```

### Step 5.1: Add Terminal Buffer Commands

**File**: `TerminalBatchBuilder.java` (if exists) or create `TerminalCommands.java`

```java
public class TerminalBufferCommands {
    // Escape sequences
    public static final String ENTER_ALT_BUFFER = "\u001b[?1049h";
    public static final String EXIT_ALT_BUFFER = "\u001b[?1049l";
    public static final String CLEAR_SCREEN = "\u001b[2J";
    public static final String CURSOR_HOME = "\u001b[H";
    public static final String ENABLE_MOUSE = "\u001b[?1000h";
    public static final String DISABLE_MOUSE = "\u001b[?1000l";
    
    // JLine3 equivalents
    public static void enterAlternateBuffer(Terminal terminal) {
        terminal.puts(InfoCmp.Capability.enter_ca_mode);  // smcup
    }
    
    public static void exitAlternateBuffer(Terminal terminal) {
        terminal.puts(InfoCmp.Capability.exit_ca_mode);  // rmcup
    }
}
```

### Step 5.2: Add Full Terminal Mode to ScrollPanel

**File**: `TerminalScrollPanel.java`

```java
// Add mode enum
public enum ViewMode {
    EMBEDDED,      // Normal: within application bounds
    FULL_TERMINAL  // Exploded: alternate screen buffer
}

// Add field
private ViewMode viewMode = ViewMode.EMBEDDED;
private TerminalRectangle embeddedBounds = null;  // Store pre-explode bounds

// API
public void setViewMode(ViewMode mode) {
    if (this.viewMode == mode) return;
    
    if (mode == ViewMode.FULL_TERMINAL) {
        enterFullTerminalMode();
    } else {
        exitFullTerminalMode();
    }
}

public ViewMode getViewMode() {
    return viewMode;
}

private void enterFullTerminalMode() {
    // Store current bounds for restoration
    embeddedBounds = getRegion();
    
    // Save scroll position
    int savedScrollX = scrollX;
    int savedScrollY = scrollY;
    
    // Signal renderer
    TerminalRenderer renderer = getTerminalRenderer();  // Need access method
    if (renderer != null) {
        renderer.enterAlternateBuffer();
    }
    
    // Resize to terminal dimensions
    TerminalRectangle terminalSize = getTerminalSize();  // From renderer
    setRegion(0, 0, terminalSize.getWidth(), terminalSize.getHeight());
    
    // Enable native scrolling via terminal
    if (renderer != null) {
        renderer.enableTerminalScrolling(contentHeight, scrollY);
    }
    
    // Restore scroll position
    scrollTo(savedScrollX, savedScrollY);
    
    viewMode = ViewMode.FULL_TERMINAL;
}

private void exitFullTerminalMode() {
    // Signal renderer
    TerminalRenderer renderer = getTerminalRenderer();
    if (renderer != null) {
        renderer.exitAlternateBuffer();
    }
    
    // Restore embedded bounds
    if (embeddedBounds != null) {
        setRegion(embeddedBounds);
        embeddedBounds = null;
    }
    
    viewMode = ViewMode.EMBEDDED;
}
```

### Step 5.3: Handle Terminal Scroll Events

When in full terminal mode, terminal scroll events (mouse wheel) are translated to the application:

```java
// In TerminalScrollPanel - handle terminal scroll events
private void onTerminalScroll(int delta) {
    // delta is lines scrolled by terminal/scrollbar
    scrollBy(0, delta);
    
    // Update terminal's scroll position
    TerminalRenderer renderer = getTerminalRenderer();
    if (renderer != null) {
        renderer.setTerminalScrollPosition(scrollY);
    }
}
```

### Text Selection in Full Mode

In full terminal mode, the terminal handles text selection natively. Application should:
1. Render content line-by-line without clipping
2. Allow terminal to handle selection
3. On exit, selection is lost (by terminal)

---

## Testing Strategy

### Unit Tests

```java
@Test
public void testFocusAutoScroll() {
    TerminalScrollPanel panel = new TerminalScrollPanel("test");
    panel.setSize(80, 24);
    panel.setAutoScrollToFocus(true);
    
    // Create content larger than viewport
    TerminalPanel content = // ... create 80x100 panel
    panel.setContent(content);
    
    // Add focusable at bottom
    TerminalButton bottomButton = new TerminalButton("bottom");
    bottomButton.setPosition(10, 90);
    content.addChild(bottomButton);
    
    // Focus should trigger scroll
    bottomButton.requestFocus();
    
    // Verify scrollY moved to show bottom button
    assertTrue(panel.getScrollY() > 0);
}

@Test
public void testScrollListener() {
    TerminalScrollPanel panel = new TerminalScrollPanel("test");
    
    AtomicInteger scrollCount = new AtomicInteger(0);
    panel.addScrollChangeListener((ox, oy, nx, ny) -> {
        scrollCount.incrementAndGet();
    });
    
    panel.scrollTo(0, 10);
    
    assertEquals(1, scrollCount.get());
}
```

### Integration Tests

```java
@Test
public void testNestedScrollPanels() {
    TerminalScrollPanel outer = new TerminalScrollPanel("outer");
    TerminalScrollPanel inner = new TerminalScrollPanel("inner");
    
    outer.setContent(inner);
    
    // Both should scroll independently
    outer.scrollTo(0, 10);
    inner.scrollTo(0, 5);
    
    assertEquals(10, outer.getScrollY());
    assertEquals(5, inner.getScrollY());
}
```

---

## Migration Path

### Existing Code Impact

**Backward Compatibility**: All phases are additive - no breaking changes to existing API.

**Deprecation Strategy**:
- Phase 1-3: New optional features
- Phase 4: Default on, can disable
- Phase 5: Opt-in only

### Gradual Rollout

1. **Week 1**: Phase 1 (focus awareness) - optional, no default behavior change
2. **Week 2**: Phase 2 (mouse interaction) - requires mouse event infrastructure
3. **Week 3**: Phase 3 (scroll events) - used internally in Phase 4
4. **Week 4**: Phase 4 (auto-scroll) - default on, adds auto-scroll capability
5. **Week 5+**: Phase 5 (full terminal mode) - requires renderer changes

---

## Dependencies on Other Components

### Required Changes in Other Files

| File | Change | Phase |
|------|--------|-------|
| TerminalRenderable.java | Add FocusChangeListener interface | 1 |
| TerminalRenderable.java | Add getPositionInParent(), getPositionRelativeTo() | 1 |
| TerminalRenderer/ConsoleContainer | Add alternate buffer support | 5 |
| EventBytes | Add MOUSE_WHEEL constant if absent | 2 |

### Coordination Points

1. **Event System**: Ensure mouse events are properly routed to scroll indicators
2. **Focus Management**: Coordinate focus tracking with existing focus index system
3. **Renderer**: Phase 5 requires terminal-level buffer switching support

---

## Performance Considerations

### Focus Tracking
- Recursive registration: O(n) where n = child count
- Event listener cost: One listener per component, minimal overhead
- Auto-scroll: Occurs only on focus change (infrequent)

### Mouse Interaction
- Click-to-position: Single calculation, no overhead
- Drag scrolling: May cause frequent scroll updates, consider throttling

### Scroll Events
- Listener notification: O(k) where k = listener count
- Keep listeners minimal for scroll events (should be rare)

### Full Terminal Mode
- No application rendering while in alt buffer
- Terminal handles all rendering and scrolling natively
- Application receives scroll position updates only

---

## Document Revision History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-04-20 | Initial development plan |

---

## Related Documents

- `SCROLLPANEL_ARCHITECTURE.md` - Component reference
- `ALTERNATE_SCREEN_MODE.md` - Terminal buffer deep dive (to be created)
