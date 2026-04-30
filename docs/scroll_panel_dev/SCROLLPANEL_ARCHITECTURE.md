# TerminalScrollPanel Architecture & API Reference

## Document Purpose

This document serves as the definitive technical reference for TerminalScrollPanel, its dependencies, and their interactions. It documents the current implementation, identifies extension points, and establishes the foundation for enhancements described in the development plan.

---

## Component Hierarchy

```
Renderable (base)
└── TerminalRenderable (2D coordinates)
    └── TerminalRegion (size preferences, insets — empty by nature)
        └── TerminalGroupRegion (group ownership)
            └── TerminalPanel (single-axis layout)
                └── TerminalAbstractStack (shared stack features)
                    ├── TerminalHStack (horizontal)
                    └── TerminalVStack (vertical)
            └── TerminalGroupRegion
                └── TerminalBorderPanel (5-region border layout)
                    └── TerminalScrollPanel ← THIS COMPONENT
                        └── TerminalStackPanel (CENTER content holder)
```

**Note:** `TerminalRegion` is intentionally empty-by-nature and does not own children or support FIT_CONTENT. Components that need child ownership should extend `TerminalGroupRegion`.

### Dependency Chain

**TerminalScrollPanel depends on:**
1. `TerminalBorderPanel` - 5-region container (TOP/BOTTOM/LEFT/RIGHT/CENTER)
2. `TerminalStackPanel` - Z-axis stacking with single visible child
3. `VScrollIndicator/HScrollIndicator` - Visual scroll position markers
4. `ScrollableTextViewer` - Example scrollable content implementation

---

## Current Implementation Details

### TerminalScrollPanel

**Location**: `/home/iospore/Dev/Netnotes/NetTerminal/src/main/java/io/netnotes/terminal/components/panels/TerminalScrollPanel.java`

**Core Responsibility**: Coordinate scrolling for a single visible content in the CENTER region, with optional scroll indicators in the border regions.

#### State Model

```
STATE_INACTIVE (10) ──activate()──► STATE_ACTIVE (11)
     ▲                                     │
     │                                     │
     └──deactivate()───────────────────────┘
```

**State-Dependent Behavior:**
```java
STATE_INACTIVE → keyHandler unregistered
STATE_ACTIVE → keyHandler registered (if keyboardScrollEnabled)
```

#### Scroll Coordinate System

```
(scrollX=0, scrollY=0)              (scrollX>0, scrollY=0)
┌─────────────────┬───┐              ┌─────────────────┬───┐
│ Content starts  │ V │              │  scrollX →    │ V │
│ here            │ S │              │  content      │ S │
│                 │ c │              │  shifted      │ c │
├─────────────────┼───┤              ├─────────────────┼───┤
│                 │   │              │                 │   │
│                 │   │              │                 │   │
│                 ├───┤              │                 ├───┤
│                 │ H │              │                 │ H │
└─────────────────┴───┘              └─────────────────┴───┘

(scrollX=0, scrollY>0)                (scrollX>0, scrollY>0)
┌─────────────────┬───┐              ┌─────────────────┬───┐
│                 │ V │              │  content        │ V │
│                 │ S │              │  shifted both │ S │
├─────────────────┼───┤              ├─────────────────┼───┤
│  scrollY →      │   │              │  directions   │   │
│  content        │   │              │               │   │
│  shifted        ├───┤              │               ├───┤
│  down           │ H │              │               │ H │
└─────────────────┴───┘              └─────────────────┴───┘
```

**Scroll Direction Interpretation:**
- Positive `scrollX`: Viewport moves RIGHT (content appears to shift LEFT)
- Positive `scrollY`: Viewport moves DOWN (content appears to shift UP)
- Applied to CENTER stack: `centerStack.setScrollOffsetDuringLayout(scrollX, scrollY)`

#### Configuration Options

```java
// Scroll directions
boolean verticalScrollEnabled = true;
boolean horizontalScrollEnabled = false;

// Scroll modes
ScrollMode scrollMode = ScrollMode.FIT_TO_VIEWPORT | ScrollMode.FIXED_SIZE;

// Indicator placement
VScrollPosition vScrollPosition = VScrollPosition.LEFT | VScrollPosition.RIGHT;
HScrollPosition hScrollPosition = HScrollPosition.TOP | HScrollPosition.BOTTOM;

// Navigation increments
int lineScrollAmount = 1;    // lines per arrow key
int pageScrollAmount = 0;    // 0 = use viewport height

// Behavior toggles
boolean keyboardScrollEnabled = true;
boolean autoShowScrollIndicators = true;
```

#### Content API

```java
// Set content (clears existing)
void setContent(TerminalRenderable content)

// Add content for swapping
void addContent(TerminalRenderable content)

// Swap visible content
void swapContent(TerminalRenderable newContent)
void swapContent(String contentName)

// Get current content
TerminalRenderable getContent()

// Clear content
void clearContent()
```

#### Scroll Control API

```java
// Direct scroll
void scrollTo(int x, int y)
void scrollBy(int dx, int dy)
void scrollToTop()
void scrollToBottom()

// Keyboard shortcuts (via KeyRunTable)
UP/DOWN:    scrollLineUp/Down()    // -/+ lineScrollAmount
LEFT/RIGHT: scrollLineLeft/Right() // -/+ lineScrollAmount
PAGE_UP:    pageUp()                // -pageScrollAmount (or -viewportHeight)
PAGE_DOWN:  pageDown()              // +pageScrollAmount (or +viewportHeight)
HOME:       scrollToTop()           // scrollY = 0
END:        scrollToBottom()         // scrollY = max
```

---

### TerminalBorderPanel

**Location**: `/home/iospore/Dev/Netnotes/NetTerminal/src/main/java/io/netnotes/terminal/components/panels/TerminalBorderPanel.java`

**Core Responsibility**: Five-region border layout with named stack panels in each position.

#### Region Layout

```
┌─────────────────────────────────┐
│          TOP (hstack)           │ ← TerminalStackPanel with hstack sizing
├─────────┬───────────┬─────────┤
│  LEFT   │  CENTER   │  RIGHT  │ ← CENTER: FILL/FILL, others: FIT/FILL or FILL/FIT
│ (vstack) │  (any)   │ (vstack)│
├─────────┴───────────┴─────────┤
│        BOTTOM (hstack)          │
└─────────────────────────────────┘
```

**Region Sizing Defaults:**
```java
CENTER:  SizePreference.FILL, SizePreference.FILL
TOP:     SizePreference.FILL, SizePreference.FIT_CONTENT  // fixed height
BOTTOM:  SizePreference.FILL, SizePreference.FIT_CONTENT  // fixed height
LEFT:    SizePreference.FIT_CONTENT, SizePreference.FILL  // fixed width
RIGHT:   SizePreference.FIT_CONTENT, SizePreference.FILL  // fixed width
```

**Reserved Sizing:**
```java
// Specify minimum size when region is empty
void setReservedTopHeight(int height)
void setReservedBottomHeight(int height)
void setReservedLeftWidth(int width)    
void setReservedRightWidth(int width)
```

#### Content Management

```java
// Set/replace content in region
void setPanel(BorderPanel region, TerminalRenderable child)

// Swap to existing or add new content
void swapPanel(BorderPanel region, TerminalRenderable newChild)

// Add to stack without making visible
void addToPanel(BorderPanel region, TerminalRenderable child)

// Remove from region
void removeFromPanel(BorderPanel region, TerminalRenderable child)

// Clear region
void clearPanel(BorderPanel region)
TerminalRenderable getPanel(BorderPanel region)
TerminalStackPanel getRegionStack(BorderPanel region)
```

---

### TerminalStackPanel

**Location**: `/home/iospore/Dev/Netnotes/NetTerminal/src/main/java/io/netnotes/terminal/components/panels/TerminalStackPanel.java`

**Core Responsibility**: Z-axis stacking container with exactly ONE visible child at a time. Used by TerminalScrollPanel for CENTER content.

**Visibility Model:**
```java
// Only currentContent may be visible
private boolean visibilityPolicy(TerminalRenderable renderable, boolean isVisible) {
    if (!isVisible) return true;             // always allow hiding
    return renderable == currentContent;     // only current may show
}

// Applied to all children via setVisibilityPolicy()
```

**Scroll Offset Application:**
```java
// Position in layout callback (local coordinates)
int x = ins.getLeft() - scrollOffsetX;
int y = ins.getTop() - scrollOffsetY;

// Child positioned at (x, y) with child dimensions
```

**Overflow Strategies:**
```java
CLIP    → Child hidden if positioned outside parent bounds
OVERFLOW → Child positioned with offset but not clipped by parent
```

**Key APIs:**
```java
// Scroll (coordinates local to stack)
void setScrollOffset(int x, int y)              // triggers layout
void setScrollOffsetDuringLayout(int x, int y) // during pass, no re-trigger

// Content management (single visible)
void setVisibleContent(TerminalRenderable renderable)
void setVisibleContent(String name)
TerminalRenderable getContent()

// Stack management
void addToStack(TerminalRenderable renderable)
void removeFromStack(TerminalRenderable renderable)
void clearStack()
boolean contains(TerminalRenderable renderable)
```

---

### Scroll Indicators

**VScrollIndicator** (vertical scrollbar):
```java
// Fixed width of 1 cell
setMinWidth(1)

// Styles: SIMPLE (moving block), BAR (track + thumb), ARROWS (▲/▼ arrows)
Style style = Style.SIMPLE | Style.BAR | Style.ARROWS;

// Update position
void updatePosition(int current, int max, int viewportSize)
```

**HScrollIndicator** (horizontal scrollbar):
```java
// Fixed height of 1 cell
// Similar to VScrollIndicator but horizontal
```

**ScrollIndicator Interface:**
```java
public interface ScrollIndicator<R> {
    R getRenderable();
    void updatePosition(int current, int max, int viewportSize);
}
```

---

## Layout Flow

### TerminalScrollPanel Layout Pass

```
1. layoutAllPanels() called by layout manager
   ├── Call super.layoutAllPanels() - positions TOP/BOTTOM/LEFT/RIGHT/CENTER stacks
   └── Calculate scroll state and update indicators

2. Scroll calculation:
   └── if (CENTER content exists && not excluded):
       ├── Get allocated region for CENTER stack
       ├── Calculate viewport size: allocated - padding
       ├── Get content size: measured or current region
       ├── Calculate max scroll: content - viewport (clamped to 0)
       ├── Clamp current scroll to valid range
       ├── Apply to CENTER stack: setScrollOffsetDuringLayout(scrollX, scrollY)
       └── Update scroll indicators with current position + max
```

### Content Size Resolution

**ScrollMode.FIT_TO_VIEWPORT:**
```
Content dimensions = based on TerminalSizeable preferences
- FIT_CONTENT → measuredContentBounds or requested/current
- FILL → viewport size (but content may extend via fixed size)
- PERCENT → viewport * percent
- STATIC → fixed size

CENTER stack sizing: FILL/FILL (fills parent, scrolls if content larger)
```

**ScrollMode.FIXED_SIZE:**
```
Content dimensions = based on explicit preferences
- FIT_CONTENT → measure content, size to content
- FILL → use requested/current size
- Content does NOT resize to viewport

CENTER stack sizing: FIT_CONTENT/FIT_CONTENT
(parent sizes to content, scrolling shows hidden portions)
```

---

## Current Limitations

### 1. Focus Management Gap

**Current State:** Scroll panel registers keyboard handlers when active, but has no awareness of child focus.

**Gap:** When a focused child component (e.g., text input) is scrolled outside the visible viewport, it remains focused but invisible. There's no mechanism to:
- Auto-scroll to keep focused child visible
- Detect focus changes within content
- Coordinate with nested scrollable components

### 2. Mouse Interaction Absent

**Current State:** Scroll indicators are purely visual.

**Gap:** No mouse event handling on:
- Scroll indicators (click/drag positioning)
- CENTER content area (mouse wheel scrolling)

### 3. Single Container Mode

**Current State:** Always renders within application bounds.

**Gap:** No support for alternate screen buffer mode that would:
- Switch terminal to native scrolling
- Support native mouse wheel
- Allow text selection across entire content
- Restore application view on exit

### 4. Nested Scroll Coordination

**Current State:** Each scroll panel operates independently.

**Gap:** No coordination between nested scroll panels (e.g., scroll panel inside scrolled content).

---

## Extension Points

### 1. Layout Callback Override

```java
@Override
protected void layoutAllPanels(
    TerminalLayoutContext[] contexts,
    Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
) {
    // Insert custom logic before super call
    // Call super for standard layout
    super.layoutAllPanels(contexts, dataInterfaces);
    // Insert custom logic after layout committed
    
    // Access CENTER stack allocated region
    TerminalRectangle allocated = resolveAllocatedRegion(dataInterfaces, centerStack);
    
    // Update scroll indicators
    // Calculate overflow detection
    // Trigger focus visibility check
}
```

### 2. Content Size Calculation Hook

```java
// Called during layout to resolve content dimensions
private TerminalRectangle getContentSize(
    TerminalRenderable content,
    TerminalLayoutContext centerContext,
    int viewportWidth,
    int viewportHeight
) {
    // Can be extended to:
    // - Consider focused element position
    // - Add padding around content
    // - Support virtual scrolling (content larger than actual)
}
```

### 3. Keyboard Handler Extension

```java
private final KeyRunTable keyRunTable = new KeyRunTable(new NoteBytesRunnablePair[]{
    // Add custom key bindings here
    new NoteBytesRunnablePair(KeyCodeBytes.CTRL_HOME, this::scrollToTop),
    new NoteBytesRunnablePair(KeyCodeBytes.CTRL_END, this::scrollToBottom),
});
```

### 4. Scroll Listener Pattern

(Currently not implemented - see Development Plan for addition)

```java
// Proposed API
void setOnScroll(ScrollChangeListener listener)

interface ScrollChangeListener {
    void onScroll(int oldScrollX, int oldScrollY, 
                  int newScrollX, int newScrollY,
                  boolean userInitiated);
}
```

---

## Integration Patterns

### Pattern 1: Text Area with Scrolling

```java
// Scroll panel contains a text viewer
TerminalScrollPanel scrollPanel = new TerminalScrollPanel("text-viewer");

ScrollableTextViewer textViewer = new ScrollableTextViewer("content");
textViewer.setWordWrap(true);
textViewer.setMaxLines(10000);

// Set viewport sizing
scrollPanel.setScrollMode(TerminalScrollPanel.ScrollMode.FIT_TO_VIEWPORT);

// Set as content
scrollPanel.setContent(textViewer);

// Configure scroll indicators
scrollPanel.setVScrollPosition(TerminalScrollPanel.VScrollPosition.RIGHT);

// Populate content
textViewer.addLines(
    "Line 1 of content",
    "Line 2 of content",
    // ... many lines
);
```

### Pattern 2: Complex Panel with Embedded Scroll

```java
// Create container
TerminalPanel container = new TerminalPanel("container");
container.setAxis(TerminalPanel.Axis.VERTICAL);

// Add header (fixed)
TerminalPanel header = new TerminalPanel("header");
header.setHeightPreference(SizePreference.FIT_CONTENT);
container.addChild(header);

// Add scrollable content
TerminalScrollPanel scrollContent = new TerminalScrollPanel("content-area");
scrollContent.setScrollMode(TerminalScrollPanel.ScrollMode.FILL);
container.addChild(scrollContent);

// Content fills remaining space
```

### Pattern 3: Swap Between Multiple Scroll Panels

```java
TerminalScrollPanel mainViewer = new TerminalScrollPanel("main-viewer");

// Create multiple content panels
ScrollableTextViewer panel1 = new ScrollableTextViewer("panel1");
ScrollableTextViewer panel2 = new ScrollableTextViewer("panel2");

// Add both to CENTER stack (hidden initially)
mainViewer.addContent(panel1);
mainViewer.addContent(panel2);

// Swap visible
mainViewer.swapContent("panel1");  // panel1 visible, panel2 hidden
mainViewer.swapContent("panel2");  // panel2 visible, panel1 hidden
```

---

## Debugging & Diagnostics

### Render Diagnostics Integration

```java
// Log swap operations
RenderDiagnostics.logSwapTraceEvent(
    traceOwner,
    "TerminalScrollPanel.swapContent:start",
    () -> "scrollPanel=" + RenderDiagnostics.summarizeRenderable(this)
        + "\n\tpreviousContent=" + RenderDiagnostics.summarizeRenderable(previousContent)
        + "\n\tnewContent=" + RenderDiagnostics.summarizeRenderable(newContent)
);

// Log viewport/content sizing issues
RenderDiagnostics.logRenderBlocker(
    "scrollpanel-viewport-collapse:" + getName(),
    "TerminalScrollPanel.layoutAllPanels",
    "non-positive-viewport",
    () -> "viewport=" + viewportWidth + "x" + viewportHeight
);
```

### Key Diagnostic Fields

```java
// To debug panel layout:
// - scrollX, scrollY: Current scroll position
// - scrollMode: Which sizing mode is active
// - contentPadding: Applied padding to CENTER
// - verticalScrollEnabled, horizontalScrollEnabled
// - viewport size: Get from allocated region calculation
// - content size: Get from getContentSize() result
```

---

## Version Information

- **Document Version**: 1.0
- **Last Updated**: 2025-04-20
- **Engine Version**: 0.11.0+
- **Related Documents**: 
  - `SCROLLPANEL_DEVELOPMENT_PLAN.md` - Implementation roadmap
  - `ALTERNATE_SCREEN_MODE.md` - Terminal buffer switching deep dive

---

## File Locations

| Component | Path |
|-----------|------|
| TerminalScrollPanel | `/home/iospore/Dev/Netnotes/NetTerminal/src/main/java/io/netnotes/terminal/components/panels/TerminalScrollPanel.java` |
| TerminalBorderPanel | `/home/iospore/Dev/Netnotes/NetTerminal/src/main/java/io/netnotes/terminal/components/panels/TerminalBorderPanel.java` |
| TerminalStackPanel | `/home/iospore/Dev/Netnotes/NetTerminal/src/main/java/io/netnotes/terminal/components/panels/TerminalStackPanel.java` |
| Scroll Indicators | `/home/iospore/Dev/Netnotes/NetTerminal/src/main/java/io/netnotes/terminal/components/VScrollIndicator.java` |
| | `/home/iospore/Dev/Netnotes/NetTerminal/src/main/java/io/netnotes/terminal/components/HScrollIndicator.java` |
| ScrollIndicator Interface | `/home/iospore/Dev/Netnotes/Netnotes-Engine/src/main/java/io/netnotes/engine/ui/ScrollIndicator.java` |
| BorderPanel Regions | `/home/iospore/Dev/Netnotes/Netnotes-Engine/src/main/java/io/netnotes/engine/ui/BorderPanel.java` |
