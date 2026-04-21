# Netnotes-Engine Component Standards & API Manual

## Overview

This document serves as an API reference for the Netnotes rendering system, explaining how components interact, their dependencies, and the conventions governing the architecture. It is designed for AI agents to understand the system's sources of truth, file relationships, and implementation patterns.

---

## Architecture Overview

### Layered Hierarchy

```
Renderable (Base)
├── TerminalRenderable (Terminal-specific)
│   ├── TerminalRegion (Base region component)
│   │   ├── TerminalPanel (Single-axis container)
│   │   ├── TerminalHStack (Horizontal stack)
│   │   ├── TerminalVStack (Vertical stack)
│   │   ├── TerminalOverlayPanel (Multi-visible Z-axis stacking)
│   │   └── TerminalGroupRegion (Base group-owner)
│   │       └── [Panel implementations]
```

### Core Dependencies

**Sources of Truth:**
1. **Renderable.java** - Base class for all renderables (state machine, event handling, damage tracking)
2. **RenderableLayoutManager.java** - Single-pass layout engine
3. **TerminalLayoutGroupCallback** - Group layout orchestration
4. **TerminalLayoutData** - Layout data carrier

**Terminal-Specific Extensions:**
- **TerminalRenderable.java** - 2D coordinate system, rendering helpers
- **TerminalRegion.java** - Size preferences, insets, dimensionality
- **TerminalLayoutGroup.java** - Group ownership and member management

---

## Renderable.java (Base Class)

### Purpose
Abstract base class providing state machine, event filtering, damage tracking, and rendering orchestration for all renderable nodes.

### Key Responsibilities

#### 1. State Machine
- Manages renderable states via `BitFlagStateMachine`
- States: `RENDERABLE` → `STARTED` → `ATTACHED` → `RENDERED` → `IDLE`
- Visibility states: `HIDDEN_DESIRED`, `EFFECTIVELY_HIDDEN`, `INVISIBLE_DESIRED`, `EFFECTIVELY_INVISIBLE`

#### 2. Spatial Region System
```java
protected S region;                    // Current committed region
protected S requestedRegion;           // User-requested region
protected SpatialRegionPool<S> regionPool;  // Object pooling
```

#### 3. Damage Tracking (Zero-Allocation)
```java
protected S damage = null;  // Accumulates damage regions
protected boolean childrenDirty = false;  // Structural changes
```

**Damage Flow:**
1. `invalidate(S localRegion)` - Mark region dirty
2. `propagateDamageUp(S absChildDamage)` - Accumulate to parent
3. `reportDamage(S absoluteRegion)` - Report to root accumulator
4. `scheduleRender()` - Trigger rendering

#### 4. Event Handling
- Event filtering by source path
- Routed event dispatch with bubbling
- Keyboard focus management

#### 5. Rendering Pipeline
```java
void toBatch(B batch, S clipRegion)
    ├── renderSelf(B batch)  // Subclass implements content
    └── renderChildrenByLayer(B batch, S visibleClip, S forcedRegion)
        └── Sort by layerIndex (0-3) then zOrder
```

**Layer System:**
- `LAYER_NORMAL = 0` - Regular children
- `LAYER_FLOATING = 1` - Floating elements
- `LAYER_MODAL = 2` - Modal dialogs
- `LAYER_NOTIFICATION = 3` - Notifications

#### 6. Tree Mutations (Serialized)
```java
void addChild(R child, LCB layoutCallback)  // Runs on uiExecutor
void removeChild(R child)                    // Runs on uiExecutor
void clearChildren()                         // Batched removal
```

#### 7. Layout Integration
```java
void requestLayoutUpdate()              // Mark dirty
void applyLayoutData(LD layoutData)     // Apply committed geometry
void measureContent(LC[] childContexts) // Bottom-up measurement
```

### API Methods

**Hierarchy:**
```java
R getParent()                           // Get parent node
List<R> getChildren()                   // Get children (snapshot)
void addChild(R child)                  // Add child (auto-layout)
void removeChild(R child)               // Remove child (auto-damage)
```

**Spatial:**
```java
S getRegion()                           // Copy of current region
S getAbsoluteRegion()                   // Local + parent transform
boolean hitTest(P point)                // Point containment
```

**Visibility:**
```java
boolean isVisible()                     // Effectively visible
boolean isHidden()                      // Hidden desired
boolean isEffectivelyHidden()           // Hidden (including blockers)
void setVisible(boolean visible)        // Toggle visibility
void setHidden(boolean hidden)          // Set hidden state
```

**Focus:**
```java
boolean isFocusable()                   // Can receive focus
boolean hasFocus()                      // Currently focused
void requestFocus()                     // Request focus
void clearFocus()                       // Clear focus
```

**Damage:**
```java
void invalidate(S localRegion)          // Invalidate specific region
void invalidate()                       // Invalidate entire region
void scheduleRender()                   // Trigger render request
```

---

## RenderableLayoutManager.java

### Purpose
Single-pass layout engine that traverses the tree depth-sorted, computes layout data once per pass, and commits results.

### Pass Model

**Two-Call Model per Node:**
1. **Content Pre-Pass** (bottom-up): `measureContent()` called on content-sized nodes
2. **Layout Pass** (top-down): `calculateLayout()` → `applyLayoutData()`

### Key Concepts

#### 1. Layout Callbacks
```java
LCB layoutCallback      // Top-down: position children
LCB contentCallback     // Bottom-up: measure content
```

#### 2. Group Management
```java
// Groups owned by a parent
G groupRegistry.get(groupId);

// Group member
node.setMemberGroup(group);

// Group callback
layoutGroup.executeLayoutCallback(contexts);
```

#### 3. Dirty Propagation
```java
markLayoutDirty(R renderable)  // Mark node dirty
    └── addToDirtySet(node)     // Fan out to groups + ancestors
```

**Ancestor Fan-out:** Content-sized nodes dirty ALL ancestors until first non-content-sized ancestor.

#### 4. Visibility Flip
```java
if (node just became visible) {
    // Children never laid out while visible
    injectSubtreeIntoCurrentPass(child);
}
```

#### 5. Floating Layer
```java
floatingRegistry.get(renderable);  // Floating nodes
floatingLayer.add(renderable);      // Floating layer manager
```

### API Methods

**Registration:**
```java
void registerRenderable(R renderable, LCB layoutCallback)
void unregisterRenderable(R renderable)
```

**Dirty Marking:**
```java
void markLayoutDirty(R renderable)              // Mark dirty
void markLayoutDirtyImmediate(R renderable)     // Immediate
void requestLayout()                            // Schedule pass
```

**Batching:**
```java
void beginRequestBatch()                        // Start batch
void endRequestBatch()                          // End batch
void batchRequests(Runnable operations)         // Execute batch
```

**Group Management:**
```java
void createGroup(String groupId)                // Create group
void addToGroup(R renderable, String groupId)    // Add member
void removeFromGroup(R renderable)               // Remove member
void destroyGroup(String groupId)                // Destroy group
void setGroupLayoutCallback(String id, GCB cb)   // Set callback
```

**State Queries:**
```java
boolean isLayoutExecuting()                     // Layout pass active
boolean hasPendingLayout()                      // Dirty nodes exist
boolean isInCurrentPass(R renderable)           // In active pass
```

---

## TerminalRenderable.java

### Purpose
Terminal-specific extension providing 2D coordinate system, rendering helpers, and terminal text operations.

### Key Differences from Base Renderable

#### 1. Coordinate System
```java
// 2D spatial (x, y, width, height)
TerminalRectangle region;  // Not generic Point2D/SpatialRegion

// Local coordinates (0,0 = top-left)
int getX()                  // Left edge
int getY()                  // Top edge
int getWidth()              // Columns
int getHeight()             // Rows
```

#### 2. Rendering Helpers
```java
// Text rendering with boundary enforcement
void printAt(batch, int x, int y, String text, TextStyle style)

// Shape rendering
void drawBox(batch, int x, int y, int width, int height, ...)
void drawHLine(batch, int x, int y, int length, ...)
void drawVLine(batch, int x, int y, int length, ...)
void drawSparkline(batch, int x, int y, int width, int height, ...)

// Cursor control
void moveCursor(batch, int x, int y)
void showCursor(batch)
void hideCursor(batch)

// Bitmap rendering
void drawBitmap(batch, int x, int y, int width, int height, ...)
void drawBrailleBitmap(batch, int x, int y, int width, int height, ...)
void drawSextantBitmap(batch, int x, int y, int width, int height, ...)
```

#### 3. Coordinate Translation
```java
// Local to absolute
int toAbsoluteX(int localX)  // localX + parentAbsoluteX
int toAbsoluteY(int localY)  // localY + parentAbsoluteY

// Absolute to local
int getAbsoluteX()           // Absolute position
int getAbsoluteY()           // Absolute position
```

#### 4. Boundary Enforcement
All rendering methods automatically:
- Check `isEffectivelyHidden()`
- Clamp to component bounds
- Intersect with clip region
- Convert local to absolute coordinates

---

## TerminalRegion.java

### Purpose
Base region component defining size preferences, insets, and dimensionality for terminal components.

### Dimensionality Model

**Four Axes:**
```java
AXIS_X = 0    // Horizontal position
AXIS_Y = 1    // Vertical position
AXIS_W = 2    // Width
AXIS_H = 3    // Height
```

**Axis Classification:**
```java
isPositionAxis(axis)      // AXIS_X or AXIS_Y
isAxisParentDependent(axis)  // Width/Height depends on parent
isAxisContentDependent(axis) // Width/Height depends on content
```

### Size Preferences

```java
SizePreference.STATIC        // Fixed size (from requestedRegion)
SizePreference.FIT_CONTENT   // Size from measured content
SizePreference.FILL          // Size from available space
SizePreference.PERCENT       // Size as percentage of available
SizePreference.INHERIT       // Use parent's preference
```

**Is Content Sizable:**
```java
isSizedByContent()  // Any axis with FIT_CONTENT?
```

### Insets

```java
TerminalInsets insets;  // Padding/border enforcement
getInsets()             // Returns effective insets (padding or border-enforced)
setInsets(int all)      // Set uniform padding
setInsets(TerminalInsets padding)  // Set custom padding
```

### Min Size

```java
int minWidth;
int minHeight;
getMinWidth()
getMinHeight()
setMinSize(int w, int h)
```

### Content Measurement

```java
TerminalRectangle measureContent(TerminalLayoutContext[] childContexts)
    // Bottom-up: children measured before parents
    // Returns: content footprint for this node
```

**Resolution Priority:**
1. In-flight `measuredContentBounds` (freshest)
2. Child's `requestedRegion` (user-staged)
3. Child's committed `region` (last known)

---

## TerminalGroupRegion.java

### Purpose
Base class for components that own a layout group and automatically wire children into that group.

### Lifecycle Contract

#### 1. Construction
```java
TerminalGroupRegion(String name, String groupPrefix)
    └── initGroup()  // Creates layout group and callback
```

#### 2. Detachment
```java
onLayoutManagerCleared()
    └── onDetachedFromLayout()  // Extension point (default: no-op)
```
**Important:** `childGroups` preserved for re-attachment.

#### 3. Re-attachment
```java
registerRenderableInternal()
    └── collectChildGroups()
        └── registerPendingGroups()  // Reconstructs group in groupRegistry
```

#### 4. Destruction
```java
onInternalDestroying()
    └── removeLayoutGroup(groupId)  // Removes from childGroups + groupRegistry
```

### Child Wiring

```java
void addChild(TerminalRenderable child, TerminalLayoutCallback cb)
    └── onChildAddedToGroup(child)  // Default: addToLayoutGroup(child, layoutGroupId)
```

**Override to Change Group Membership:**
- `onChildAddedToGroup()` - Suppress automatic enrollment
- Route to different group ID

### Accessors

```java
String getLayoutGroupId()           // "groupPrefix-name"
String getLayoutCallbackId()        // "groupPrefix-name-callback"
TerminalLayoutGroupCallback getLayoutCallback()
```

---

## TerminalPanel.java

### Purpose
Versatile single-axis layout container supporting horizontal/vertical orientation, wrapping, alignment, overflow strategies, and borders.

### Configuration

**Axis & Alignment:**
```java
enum Axis { HORIZONTAL, VERTICAL }
enum Alignment { START, CENTER, END, STRETCH }

setAxis(Axis axis)
setAlignment(Alignment alignment)
setCrossAlignment(Alignment alignment)
```

**Overflow Strategies:**
```java
enum LayoutOverflowStrategy {
    CLIP,              // Hide overflow (default)
    OVERFLOW,          // Render outside bounds
    SHRINK_FILL,       // FILL children get exact share
    SHRINK_ALL,        // All children scale proportionally
    DISTRIBUTE_EQUAL   // Equal share to all visible
}
```

**Spacing & Wrapping:**
```java
setSpacing(int spacing)      // Gap between children
setWrap(boolean wrap)        // Wrap to next line/column
```

**Size Constraints:**
```java
setMaxWidth(int maxWidth)
setMaxHeight(int maxHeight)
```

**Border & Title:**
```java
setDrawBorder(boolean enabled)
setBorderStyle(LineStyle style)
setTitle(String title)
setTitlePosition(Position position)
setBorderTextStyle(TextStyle style)
setFocusedBorderTextStyle(TextStyle style)
```

**Padding:**
```java
setPadding(int all)                  // Uniform padding
setPadding(int vertical, horizontal) // Vertical/horizontal
setInsets(TerminalInsets insets)      // Custom insets
```

**Fill Style:**
```java
setFillStyle(TextStyle style)  // Background fill when enabled
```

### Layout Callback

```java
private void layoutChildren(
    TerminalLayoutContext[] contexts,
    Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
) {
    // 1. Collect metadata (preferences, hidden status)
    // 2. Resolve raw sizes (FIT_CONTENT, PERCENT, STATIC, FILL)
    // 3. Resolve main-axis FILL based on overflow strategy
    // 4. Place children with alignment
    // 5. Apply overflow rules (CLIP vs OVERFLOW)
}
```

**Pass 2 - Raw Size Resolution:**
```java
// Width preference resolution
switch (widthPrefs[i]) {
    case FILL -> widths[i] = axis == HORIZONTAL ? -1 : availableCross;
    case FIT_CONTENT -> widths[i] = measuredContentBounds.getWidth();
    case PERCENT -> widths[i] = (int)(availablePrimary * percent);
    case STATIC -> widths[i] = requestedRegion.getWidth();
}
```

**Pass 3 - Main-Axis FILL:**
```java
// rawFillPrimary = (availablePrimary - totalResolvedPrimary) / fillPrimaryCount
switch (overflowStrategy) {
    case SHRINK_FILL -> FILL children get rawFillPrimary
    case SHRINK_ALL -> Scale all children by available / total
    case DISTRIBUTE_EQUAL -> Equal share to all visible
    default -> FILL children get rawFillPrimary
}
```

**Pass 4 - Placement:**
```java
// Cross-axis alignment
if (crossAlignment == STRETCH) {
    width = availableCrossAtCursor;
} else {
    // CENTER or START alignment
    // Adjust position by remaining space
}
```

### Measure Content

```java
TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
    // Width contribution: sum of FIT/STATIC child widths
    // Height contribution: max of FIT/STATIC child heights
    // Exclude FILL and PERCENT children (depend on available space)
}
```

---

## TerminalOverlayPanel.java

### Purpose
Multi-visible Z-axis stacking container where multiple renderables occupy the same space.

### Configuration

**Max Visible Nodes:**
```java
setMaxVisibleNodes(int max)
    // -1 = unlimited (default)
    // 0 = none
    // N = up to N visible simultaneously
```

**Scroll & Overflow:**
```java
setScrollOffset(int x, int y)
setOverflowStrategy(LayoutOverflowStrategy strategy)
```

**Padding:**
```java
setPadding(TerminalInsets insets)
setContentPadding(int pad)
```

### Stack Management

**Add to Stack:**
```java
void addToStack(TerminalRenderable renderable)
    // Unlimited mode: shows immediately
    // Capped mode (N>0): waits until showContent()
    // Mode 0: always hidden
```

**Visibility Control:**
```java
void showContent(TerminalRenderable renderable)  // Add to visible set
void showContent(String name)                     // By name
void hideContent(TerminalRenderable renderable)   // Remove from visible set
void hideContent(String name)                     // By name
void hideAll()                                     // Hide all managed
```

**Query Stack:**
```java
List<TerminalRenderable> getStackContents()       // All children
List<TerminalRenderable> getVisibleSet()          // Currently visible
boolean contains(TerminalRenderable renderable)   // In stack?
boolean contains(String name)                     // By name?
boolean isVisible(TerminalRenderable renderable)   // In visible set?
boolean isVisible(String name)                    // By name?
```

### Visibility Policy

```java
private boolean visibilityPolicy(TerminalRenderable renderable, boolean isVisible) {
    if (!isVisible) return true;
    if (maxVisibleNodes == 0) return false;
    return visibleSet.contains(renderable);
}
```

**Applied to each child via `setVisibilityPolicy()`:**
- Child can only self-show if in visible set and maxVisibleNodes permits
- Hiding is always allowed (panel can always push child hidden)

### Layout

```java
private void layoutStack(
    TerminalLayoutContext[] contexts,
    Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
) {
    // All visible children share same origin
    int x = ins.getLeft() - scrollOffsetX;
    int y = ins.getTop() - scrollOffsetY;

    for (TerminalLayoutContext context : contexts) {
        TerminalRenderable child = context.getRenderable();

        if (!child.isVisible()) {
            // Set hidden=true, no coordinates
            continue;
        }

        boolean inVisible = maxVisibleNodes != 0 && visibleSet.contains(child);
        if (!inVisible) {
            if (managed) {
                dataInterfaces.get(child.getName())
                    .setLayoutData(TerminalLayoutData.getBuilder().hidden(true).build());
            }
            continue;
        }

        // Resolve child dimensions independently
        int childWidth  = resolveChildDimension(child, context, viewportWidth,  true);
        int childHeight = resolveChildDimension(child, context, viewportHeight, false);

        // Apply overflow strategy
        boolean outOfBounds = (x + childWidth <= 0) || (x >= parentPanel.getWidth())
            || (y + childHeight <= 0) || (y >= parentPanel.getHeight());

        TerminalLayoutData.TerminalLayoutDataBuilder builder = TerminalLayoutData.getBuilder()
            .setX(x)
            .setY(y)
            .setWidth(Math.max(0, childWidth))
            .setHeight(Math.max(0, childHeight));

        if (managed) {
            builder.hidden(outOfBounds);
        }

        dataInterfaces.get(child.getName()).setLayoutData(builder.build());
    }
}
```

### Measure Content

```java
TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
    // Footprint = intersection of all visible children's bounds
    int intersectW = Integer.MAX_VALUE;
    int intersectH = Integer.MAX_VALUE;

    for (TerminalRenderable visible : visibleSet) {
        TerminalLayoutContext ctx = findContext(childContexts, visible);
        if (ownWP == SizePreference.FIT_CONTENT) {
            intersectW = Math.min(intersectW, readDimension(ctx, true));
        }
        if (ownHP == SizePreference.FIT_CONTENT) {
            intersectH = Math.min(intersectH, readDimension(ctx, false));
        }
    }

    return Math.max(getMinWidth(),  intersectW + ins.getHorizontal());
    return Math.max(getMinHeight(), intersectH + ins.getVertical());
}
```

---

## TerminalAbstractStack.java

### Purpose
Shared base for TerminalHStack and TerminalVStack with axis-independent features.

### Shared Features

**Alignment:**
```java
enum VAlignment { TOP, CENTER, BOTTOM }
enum HAlignment { LEFT, CENTER, RIGHT }

setVAlignment(VAlignment vAlignment)
setHAlignment(HAlignment hAlignment)
```

**Padding & Border:**
```java
setPadding(int value)
setInsets(TerminalInsets newInsets)
setDrawBorder(boolean drawBorder)
setDrawSeparators(boolean drawSeparators)
setBorderStyle(LineStyle style)
setBorderTextStyle(TextStyle style)
```

**Spacing & Overflow:**
```java
setSpacing(int spacing)
setOverflowStrategy(LayoutOverflowStrategy strategy)
```

**Border Enforced Insets:**
```java
updateBorderInsets()
    // Clamps each side to at least 1 for box-drawing characters

getInsets()
    // Returns borderInsets if drawBorder=true, else raw padding
```

### Axis-Specific Implementation

**Subclasses must provide:**
```java
// Axis-specific layout pass
private void layoutAllChildren(...)

// Constructor (called at end to register group)
initLayoutCallback()

// Preferred sizing
int getPreferredWidth()   // Axis-aware
int getPreferredHeight()  // Axis-aware

// Rendering
protected void renderSelf(TerminalBatchBuilder batch)
```

**Example: TerminalHStack**
```java
// Width = sum of children widths + gaps
// Height = max of children heights
```

**Example: TerminalVStack**
```java
// Width = max of children widths
// Height = sum of children heights + gaps
```

---

## TerminalLayoutGroupCallback.java

### Purpose
Functional interface extending `GroupLayoutCallback` for terminal-specific layout orchestration.

### Signature

```java
@FunctionalInterface
public interface TerminalLayoutGroupCallback extends GroupLayoutCallback<
    TerminalBatchBuilder,        // Batch builder type
    TerminalRenderable,          // Renderable type
    Point2D,                     // Point type (2D)
    TerminalRectangle,           // Region type
    TerminalLayoutData,          // Layout data type
    TerminalLayoutContext,       // Layout context type
    TerminalLayoutNode,          // Layout node type
    TerminalLayoutGroupCallback  // Self-type
> {
    // Default: no additional methods
}
```

---

## TerminalLayoutGroup.java

### Purpose
Group implementation for terminal layout system.

### Group Ownership

```java
// Owner
G getOwner()
void addOwnedGroup(G group)

// Members
List<L> getMembers()
void setMemberGroup(G group)

// Callback
GCB getLayoutCallback()
void setLayoutCallback(GCB callback)
```

---

## TerminalLayoutData.java

### Purpose
Terminal-specific layout data carrier with axis-selective merging.

### Key Features

#### 1. Axis-Selective Merge
```java
void mergeIntoRegion(TerminalRectangle current, TerminalRectangle target) {
    target.copyFrom(current);
    if (spatialRegion != null) {
        target.setParentAbsolutePosition(spatialRegion.getParentAbsolutePosition());
        // Only overwrite axes that were explicitly set
        if (hasAxisChange(AXIS_X)) target.setX(spatialRegion.getX());
        if (hasAxisChange(AXIS_Y)) target.setY(spatialRegion.getY());
        if (hasAxisChange(AXIS_W)) target.setWidth(spatialRegion.getWidth());
        if (hasAxisChange(AXIS_H)) target.setHeight(spatialRegion.getHeight());
    }
}
```

**Why Axis-Selective?**
- Top-down pass (parent) should not overwrite FIT_CONTENT dimensions
- Bottom-up pass (children) should not overwrite parent-assigned positions

#### 2. Builder Pattern

```java
TerminalLayoutData.TerminalLayoutDataBuilder builder =
    TerminalLayoutData.getBuilder()
        .setX(x)
        .setY(y)
        .setWidth(width)
        .setHeight(height)
        .hidden(true);  // Optional

TerminalLayoutData data = builder.build();
```

#### 3. Axis Change Tracking

```java
boolean setX = false;
boolean setY = false;
boolean setWidth = false;
boolean setHeight = false;

void reset() {
    super.reset();
    setX = false;
    setY = false;
    setWidth = false;
    setHeight = false;
}
```

---

## TerminalLayoutCallback.java

### Purpose
Functional interface extending `LayoutCallback` for terminal layout orchestration.

### Signature

```java
@FunctionalInterface
public interface TerminalLayoutCallback extends LayoutCallback<
    TerminalBatchBuilder,        // Batch builder type
    TerminalRenderable,          // Renderable type
    Point2D,                     // Point type
    TerminalRectangle,           // Region type
    TerminalLayoutContext,       // Layout context type
    TerminalLayoutData,          // Layout data type
    TerminalLayoutCallback       // Self-type
> {
    // Default: no additional methods
}
```

---

## Rendering Pipeline

### toBatch() Flow

```java
// Called by parent or root
void toBatch(B batch, S clipRegion) {
    toBatchInternal(batch, clipRegion, null);
}

void toBatchInternal(B batch, S clipRegion, S forcedRegion) {
    if (!isVisible()) return;

    // Calculate absolute bounds
    S absBounds = region.copy();
    absBounds.translate(region.getParentAbsolutePosition());

    // Intersect with clip
    S visibleClip = absBounds.intersection(clipRegion);

    boolean hasSelfDamage = damage != null;
    boolean isForced = forcedRegion != null && absBounds.intersects(forcedRegion);

    if (hasSelfDamage) {
        // Render self in damaged area
        batch.pushClipRegion(damage.intersection(visibleClip));
        renderSelf(batch);
        renderChildrenByLayer(batch, visibleClip, damage);  // Forced region
    } else if (isForced) {
        // Parent painted over us
        batch.pushClipRegion(visibleClip);
        renderSelf(batch);
        renderChildrenByLayer(batch, visibleClip, visibleClip);
    }

    if (childrenDirty) {
        // Structural change (add/remove/reorder)
        renderChildrenByLayer(batch, visibleClip, null);
    }

    childrenDirty = false;
    recycleDamage();
}
```

### renderChildrenByLayer()

```java
protected void renderChildrenByLayer(B batch, S visibleClip, S forcedRegion) {
    if (childrenDirty) {
        // Sort by layerIndex then zOrder
        long[] sortKeys = new long[children.size()];
        int count = 0;
        for (R child : children) {
            if (child.getRenderingParent() == self()) {
                sortKeys[count] = ((long) child.getLayerIndex() << 60)
                    | (((long) child.getZOrder() - Integer.MIN_VALUE) << 28)
                    | (long) count;
                renderBuffer[count] = child;
                count++;
            }
        }
        Arrays.sort(sortKeys, 0, count);
        // Reconstruct renderBuffer in sorted order
        // ...
        childrenDirty = false;
    }

    for (int i = 0; i < renderCount; i++) {
        renderBuffer[i].toBatchInternal(batch, visibleClip, forcedRegion);
    }
}
```

**Layer Sort Key Layout:**
```
[63:60] layerIndex  (4 bits, 0-15)
[59:28] zOrder       (32 bits, unsigned)
[27:0]  index        (28 bits, 0-268,435,455)
```

---

## Layout Pass Flow

### Single-Pass Model

```
1. markLayoutDirty(node)
   └── addToDirtySet(node)
       ├── Fan out to group members
       ├── Fan out to group owner
       └── Fan out to ancestors (content-sized)

2. performUpdate()
   ├── expandDescendantsInto(dirtyNodes)
   ├── depthSort(nodes)
   ├── runContentPrePass(nodes)
   └── processNode(node) [repeated for all dirty nodes]

3. runContentPrePass()
   ├── Bottom-up traversal (leaves first)
   ├── Measure content-sized nodes
   └── Cache measuredContentBounds

4. processNode(node)
   ├── calculateLayout(context)
   ├── applyLayoutData(calculatedLayout)
   └── fireOwnedGroups(node)

5. applyLayoutData()
   ├── Update region
   ├── Update state machine
   └── Invalidate (damage propagation)
```

### Content Pre-Pass

```java
private void runContentPrePass(List<L> dirtyNodes, Set<L> passNodes) {
    // Reverse traversal (leaves first)
    for (int i = dirtyNodes.size() - 1; i >= 0; i--) {
        L node = dirtyNodes.get(i);
        if (!node.isSizedByContent()) continue;
        if (node.isContentMeasured()) continue;
        measureSingleNode(node, passNodes);
    }
}
```

### Group Callback Execution

```java
private void fireOwnedGroups(L ownerNode) {
    for (G group : ownerNode.getOwnedGroups()) {
        if (group.getOwner() != ownerNode) continue;

        List<L> members = group.getMembers();
        LC[] contexts = createContextArray(members.size());

        for (int i = 0; i < members.size(); i++) {
            L member = members.get(i);
            LC ctx = member.getInFlightContext();
            if (ctx == null) {
                ctx = createRenderableContext(member);
                member.setInFlightContext(ctx);
            }
            ctx.initialize(member);
            contexts[i] = ctx;
        }

        group.executeLayoutCallback(contexts);
    }
}
```

---

## Event Handling

### Routed Event Dispatch

```java
public boolean dispatchEvent(RoutedEvent event) {
    if (!isVisible()) return false;

    boolean handled = false;
    if (!isKeyboardEvent(event) || !isFocusable() || hasFocus()) {
        handled = eventRegistry.dispatch(event);
    }

    if (event.isConsumed()) return true;

    // Bubble up to children
    if (!children.isEmpty()) {
        for (R child : getLogicalChildren()) {
            if (child.dispatchEvent(event)) {
                break;
            }
            if (event.isConsumed()) {
                break;
            }
        }
    }

    return handled;
}
```

### Keyboard Focus

```java
void requestFocus() {
    if (!focusable) return;
    focusRequestToken = FOCUS_REQUEST_SEQ++;
    stateMachine.addState(RenderableStates.STATE_FOCUS_DESIRED);
    if (layoutManager != null) {
        layoutManager.requestFocus(self());
    }
}

void clearFocus() {
    if (hasFocus()) {
        stateMachine.removeState(RenderableStates.STATE_FOCUSED);
    }
    stateMachine.removeState(RenderableStates.STATE_FOCUS_DESIRED);
}
```

---

## Object Pooling

### Region Pooling

```java
protected final SpatialRegionPool<S> regionPool;

S region = regionPool.obtain();      // Get from pool
region.copyFrom(other);              // Modify
regionPool.recycle(region);          // Return to pool
```

**Benefits:**
- Zero-allocation damage tracking
- Reused regions across invalidations
- No GC pressure

### Layout Context Pooling

```java
LC[] contexts = createContextArray(size);
// Use contexts
recycleLayoutContexts(contexts);     // Return to pool
```

### Layout Data Pooling

```java
TerminalLayoutData data = TerminalLayoutDataPool.getInstance().obtainData();
// Use data
recycleLayoutData(data);             // Return to pool
```

---

## Common Patterns

### Creating a Custom Panel

```java
public class MyPanel extends TerminalGroupRegion {
    public MyPanel(String name) {
        super(name, "my-panel");
    }

    @Override
    protected TerminalLayoutGroupCallback createLayoutCallback() {
        return this::layoutChildren;
    }

    private void layoutChildren(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        // 1. Get parent region
        TerminalRectangle parent = contexts[0].getParentRegion();

        // 2. Calculate available space
        int availableWidth = parent.getWidth() - getInsets().getHorizontal();
        int availableHeight = parent.getHeight() - getInsets().getVertical();

        // 3. Layout children
        for (TerminalLayoutContext context : contexts) {
            TerminalRenderable child = context.getRenderable();

            if (shouldManageHidden(child) && child.isHidden()) {
                dataInterfaces.get(child.getName())
                    .setLayoutData(TerminalLayoutData.getBuilder().build());
                continue;
            }

            // Resolve child size
            int childWidth = resolveChildSize(child, context, availableWidth);
            int childHeight = resolveChildSize(child, context, availableHeight);

            // Apply padding
            int x = getInsets().getLeft();
            int y = getInsets().getTop();

            // Set layout data
            dataInterfaces.get(child.getName()).setLayoutData(
                TerminalLayoutData.getBuilder()
                    .setX(x)
                    .setY(y)
                    .setWidth(childWidth)
                    .setHeight(childHeight)
                    .build()
            );

            // Advance cursor
            x += childWidth + getSpacing();
        }
    }

    @Override
    public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
        // Calculate content footprint
        int maxWidth = 0;
        int maxHeight = 0;

        for (TerminalLayoutContext context : childContexts) {
            TerminalRenderable child = context.getRenderable();
            if (child.isHidden()) continue;

            TerminalRectangle childBounds = context.getMeasuredContentBounds();
            if (childBounds != null) {
                maxWidth = Math.max(maxWidth, childBounds.getWidth());
                maxHeight = Math.max(maxHeight, childBounds.getHeight());
            }
        }

        TerminalRectangle measured = getRegionPool().obtain();
        measured.set(0, 0,
            maxWidth + getInsets().getHorizontal(),
            maxHeight + getInsets().getVertical()
        );
        return measured;
    }

    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        // Draw panel background or decorations
        if (hasFocus()) {
            drawBox(batch, 0, 0, getWidth(), getHeight(),
                "My Panel", Position.TOP_CENTER, LineStyle.SINGLE,
                TextStyle.FOCUSED);
        }
    }
}
```

### Creating a Custom Component with Animation

```java
public class AnimatedButton extends TerminalGroupRegion {
    private int targetX, targetY, targetWidth, targetHeight;
    private int currentX, currentY, currentWidth, currentHeight;

    public AnimatedButton(String name) {
        super(name, "animated-button");
    }

    @Override
    protected TerminalLayoutGroupCallback createLayoutCallback() {
        return this::layoutChildren;
    }

    private void layoutChildren(
        TerminalLayoutContext[] contexts,
        Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
    ) {
        // Get target position from first child (the button itself)
        TerminalLayoutContext context = contexts[0];
        TerminalLayoutData data = context.getLayoutData();

        targetX = data.getX();
        targetY = data.getY();
        targetWidth = data.getWidth();
        targetHeight = data.getHeight();

        // Animate towards target
        animate();
    }

    private void animate() {
        // Linear interpolation
        currentX += (targetX - currentX) * 0.1f;
        currentY += (targetY - currentY) * 0.1f;
        currentWidth += (targetWidth - currentWidth) * 0.1f;
        currentHeight += (targetHeight - currentHeight) * 0.1f;

        // Request layout update
        requestLayoutUpdate();

        // Continue animating
        if (Math.abs(targetX - currentX) > 0.5f) {
            scheduleAnimation();
        }
    }

    private void scheduleAnimation() {
        uiExecutor.runLater(() -> {
            animate();
        }, 16);  // ~60fps
    }

    @Override
    protected void renderSelf(TerminalBatchBuilder batch) {
        // Draw button at animated position
        drawButton(batch, currentX, currentY, currentWidth, currentHeight,
            "Button", Position.CENTER, false, TextStyle.NORMAL);
    }
}
```

---

## Dependencies Summary

### Core Engine (Netnotes-Engine)

**Base Classes:**
- `Renderable.java` - State machine, events, damage tracking, rendering
- `RenderableLayoutManager.java` - Single-pass layout engine
- `SpatialRegionPool.java` - Object pooling for regions
- `BitFlagStateMachine.java` - State management

**Layout System:**
- `LayoutNode.java` - Layout node wrapper
- `LayoutContext.java` - Layout context for measurement
- `LayoutData.java` - Layout data carrier
- `LayoutCallback.java` - Layout callback interface
- `GroupLayoutCallback.java` - Group layout callback interface
- `GroupLayout.java` - Group implementation

**Rendering:**
- `BatchBuilder.java` - Batch builder interface
- `Renderer.java` - Renderer orchestration
- `RenderPhase.java` - Render pipeline phases

### Terminal-Specific (NetTerminal)

**Base Classes:**
- `TerminalRenderable.java` - 2D coordinate system, rendering helpers
- `TerminalRegion.java` - Size preferences, insets
- `TerminalRectangle.java` - 2D rectangle (x, y, width, height)
- `TerminalRectanglePool.java` - Object pooling for rectangles

**Layout System:**
- `TerminalLayoutNode.java` - Layout node wrapper
- `TerminalLayoutContext.java` - Layout context
- `TerminalLayoutData.java` - Layout data carrier
- `TerminalLayoutCallback.java` - Layout callback
- `TerminalLayoutGroupCallback.java` - Group layout callback
- `TerminalLayoutGroup.java` - Group implementation
- `TerminalLayoutManager.java` - Layout manager wrapper

**Components:**
- `TerminalPanel.java` - Single-axis container
- `TerminalHStack.java` - Horizontal stack
- `TerminalVStack.java` - Vertical stack
- `TerminalOverlayPanel.java` - Multi-visible stacking
- `TerminalGroupRegion.java` - Base group owner
- `TerminalAbstractStack.java` - Shared stack features

**Utilities:**
- `TerminalInsets.java` - Insets/padding
- `TerminalSizeable.java` - Size preference interface
- `TerminalBatchBuilder.java` - Terminal batch builder
- `TextStyle.java` - Text styling
- `Position.java` - Text positioning

---

## Conventions

### Naming Conventions

**Component Names:**
- `Terminal[ComponentName]` - All terminal components
- `[ComponentName]Region` - Base region class
- `[ComponentName]Panel` - Container panels

**Group IDs:**
- `"groupPrefix-componentName"` - e.g., `"hstack-header"`, `"overlay-panel"`

**Callback IDs:**
- `"groupPrefix-componentName-callback"` - e.g., `"hstack-header-callback"`

### File Organization

```
Netnotes-Engine/
├── src/main/java/io/netnotes/engine/ui/renderer/
│   ├── Renderable.java                    # Base class
│   ├── RenderableLayoutManager.java      # Layout manager
│   ├── LayoutNode.java                    # Layout node
│   ├── LayoutContext.java                 # Layout context
│   ├── LayoutData.java                    # Layout data
│   ├── LayoutCallback.java                # Callback interface
│   └── GroupLayoutCallback.java           # Group callback

NetTerminal/
├── src/main/java/io/netnotes/terminal/
│   ├── TerminalRenderable.java            # Terminal base
│   ├── TerminalRegion.java                # Terminal region
│   ├── TerminalRectangle.java             # 2D rectangle
│   ├── layout/
│   │   ├── TerminalLayoutNode.java
│   │   ├── TerminalLayoutContext.java
│   │   ├── TerminalLayoutData.java
│   │   ├── TerminalLayoutCallback.java
│   │   ├── TerminalLayoutGroupCallback.java
│   │   └── TerminalLayoutGroup.java
│   └── components/
│       ├── TerminalRegion.java
│       ├── panels/
│       │   ├── TerminalPanel.java
│       │   ├── TerminalHStack.java
│       │   ├── TerminalVStack.java
│       │   ├── TerminalOverlayPanel.java
│       │   ├── TerminalGroupRegion.java
│       │   └── TerminalAbstractStack.java
```

### Coding Conventions

**Layout Callbacks:**
```java
private void layoutChildren(
    TerminalLayoutContext[] contexts,
    Map<String, LayoutDataInterface<TerminalLayoutData>> dataInterfaces
) {
    // 1. Get parent region
    // 2. Calculate available space
    // 3. Iterate over children
    // 4. Resolve child sizes
    // 5. Apply spacing/alignment
    // 6. Set layout data
}
```

**Measure Content:**
```java
@Override
public TerminalRectangle measureContent(TerminalLayoutContext[] childContexts) {
    // 1. Calculate content footprint
    // 2. Apply insets
    // 3. Return measured region
}
```

**Rendering:**
```java
@Override
protected void renderSelf(TerminalBatchBuilder batch) {
    // 1. Draw background/fill
    // 2. Draw decorations (borders, titles)
    // 3. Draw children (if needed)
}
```

---

## Common Pitfalls

### 1. Forgetting to Request Layout Update

```java
// WRONG
setWidth(newWidth);
setHeight(newHeight);

// CORRECT
setWidth(newWidth);
requestLayoutUpdate();
```

### 2. Not Recycling Regions

```java
// WRONG
TerminalRectangle region = regionPool.obtain();
region.set(0, 0, 100, 100);
// Forgot to recycle!

// CORRECT
TerminalRectangle region = regionPool.obtain();
try {
    region.set(0, 0, 100, 100);
    // Use region
} finally {
    regionPool.recycle(region);
}
```

### 3. Modifying Children During Layout

```java
// WRONG - Modifies tree during layout pass
private void layoutChildren(...) {
    for (TerminalLayoutContext context : contexts) {
        TerminalRenderable child = context.getRenderable();
        if (child.isHidden()) {
            removeChild(child);  // Structural change during layout!
        }
    }
}

// CORRECT - Use visibility instead
private void layoutChildren(...) {
    for (TerminalLayoutContext context : contexts) {
        TerminalRenderable child = context.getRenderable();
        if (shouldManageHidden(child) && child.isHidden()) {
            dataInterfaces.get(child.getName())
                .setLayoutData(TerminalLayoutData.getBuilder().build());
        }
    }
}
```

### 4. Not Using Object Pooling

```java
// WRONG - Creates new objects
private void layoutChildren(...) {
    int[] widths = new int[count];
    TerminalRectangle[] regions = new TerminalRectangle[count];
    // ... allocate new arrays every layout pass
}

// CORRECT - Reuse arrays
private int[] widths;
private TerminalRectangle[] regions;

private void layoutChildren(...) {
    if (widths == null || widths.length < count) {
        widths = new int[count];
        regions = new TerminalRectangle[count];
    }
    // ... reuse arrays
}
```

### 5. Forgetting to Handle Group Membership

```java
// WRONG - Component doesn't wire children to group
public class MyPanel extends TerminalRegion {
    public MyPanel(String name) {
        super(name);
    }

    @Override
    public void addChild(TerminalRenderable child) {
        super.addChild(child);
        // Forgot to add to group!
    }
}

// CORRECT - Override onChildAddedToGroup
public class MyPanel extends TerminalGroupRegion {
    public MyPanel(String name) {
        super(name, "my-panel");
    }

    @Override
    protected void onChildAddedToGroup(TerminalRenderable child) {
        // Group is automatically wired by TerminalGroupRegion
    }
}
```

---

## Animation Patterns

### Simple Animation

```java
private void animatePosition() {
    int startX = getRegion().getX();
    int startY = getRegion().getY();
    int targetX = startX + 100;
    int targetY = startY + 100;

    animate(startX, startY, targetX, targetY);
}

private void animate(int startX, int startY, int targetX, int targetY) {
    int currentX = startX;
    int currentY = startY;

    uiExecutor.runLater(() -> {
        currentX += (targetX - currentX) * 0.1f;
        currentY += (targetY - currentY) * 0.1f;

        setPosition((int) currentX, (int) currentY);

        if (Math.abs(targetX - currentX) > 0.5f) {
            animate((int) currentX, (int) currentY, targetX, targetY);
        }
    }, 16);  // ~60fps
}
```

### Animation with Layout Update

```java
private void animateSize() {
    int startWidth = getWidth();
    int startHeight = getHeight();
    int targetWidth = startWidth + 50;
    int targetHeight = startHeight + 50;

    animateSize(startWidth, startHeight, targetWidth, targetHeight);
}

private void animateSize(int startWidth, int startHeight,
                         int targetWidth, int targetHeight) {
    int currentWidth = startWidth;
    int currentHeight = startHeight;

    uiExecutor.runLater(() -> {
        currentWidth += (targetWidth - currentWidth) * 0.1f;
        currentHeight += (targetHeight - currentHeight) * 0.1f;

        setSize((int) currentWidth, (int) currentHeight);
        requestLayoutUpdate();

        if (Math.abs(targetWidth - currentWidth) > 0.5f) {
            animateSize((int) currentWidth, (int) currentHeight,
                       targetWidth, targetHeight);
        }
    }, 16);
}
```

### Animation with Smooth Interpolation

```java
private float animationProgress = 0f;
private boolean isAnimating = false;

public void animateTransition(int startX, int startY,
                              int targetX, int targetY,
                              float durationMs) {
    if (isAnimating) return;
    isAnimating = true;
    animationProgress = 0f;

    uiExecutor.runLater(() -> {
        float dt = 16f;  // Frame delta
        animationProgress += dt / durationMs;

        if (animationProgress >= 1f) {
            animationProgress = 1f;
            isAnimating = false;
            setPosition(targetX, targetY);
        } else {
            // Ease-in-out interpolation
            float t = animationProgress;
            float eased = t < 0.5f ? 2 * t * t : -1 + (4 - 2 * t) * t;

            int currentX = (int) (startX + (targetX - startX) * eased);
            int currentY = (int) (startY + (targetY - startY) * eased);

            setPosition(currentX, currentY);
            requestLayoutUpdate();

            uiExecutor.runLater(() -> animateTransition(
                startX, startY, targetX, targetY, durationMs), 16);
        }
    }, 16);
}
```

---

## State Machine Transitions

### Renderable States

```
RENDERABLE (initial)
    ↓ addState(RENDERABLE)
STARTED
    ↓ addState(STARTED)
ATTACHED
    ↓ addState(ATTACHED)
RENDERED
    ↓ addState(RENDERED)
IDLE (terminal idle)
```

### Visibility States

```
HIDDEN_DESIRED
    ↓ addState(EFFECTIVELY_HIDDEN)
EFFECTIVELY_HIDDEN
    ↓ addState(EFFECTIVELY_INVISIBLE)
EFFECTIVELY_INVISIBLE
```

### Focus States

```
FOCUS_DESIRED
    ↓ addState(FOCUSED)
FOCUSED
    ↓ removeState(FOCUSED)
FOCUS_DESIRED
```

### State Transition Callbacks

```java
// Visibility state changes
stateMachine.onStateAdded(RenderableStates.STATE_EFFECTIVELY_HIDDEN, (old, now, bit) -> {
    onHidden();
});
stateMachine.onStateRemoved(RenderableStates.STATE_EFFECTIVELY_HIDDEN, (old, now, bit) -> {
    onUnhide();
});

// Focus state changes
stateMachine.onStateAdded(RenderableStates.STATE_FOCUSED, (old, now, bit) -> {
    onFocusGained();
});
stateMachine.onStateRemoved(RenderableStates.STATE_FOCUSED, (old, now, bit) -> {
    onFocusLost();
});
```

---

## Testing Patterns

### Unit Test for Layout Callback

```java
@Test
public void testTerminalPanelLayout() {
    TerminalPanel panel = new TerminalPanel("test-panel");
    panel.setAxis(TerminalPanel.Axis.HORIZONTAL);
    panel.setWidthPreference(SizePreference.FIT_CONTENT);
    panel.setHeightPreference(SizePreference.FIT_CONTENT);

    TerminalTextButton button1 = new TerminalTextButton("Button 1");
    TerminalTextButton button2 = new TerminalTextButton("Button 2");

    panel.addChild(button1);
    panel.addChild(button2);

    // Trigger layout
    panel.requestLayoutUpdate();

    // Verify layout data
    TerminalLayoutData data1 = button1.getLayoutData();
    assertNotNull(data1);
    assertTrue(data1.getX() >= 0);
    assertTrue(data1.getY() >= 0);
    assertTrue(data1.getWidth() > 0);
    assertTrue(data1.getHeight() > 0);

    TerminalLayoutData data2 = button2.getLayoutData();
    assertNotNull(data2);
    assertTrue(data2.getX() >= 0);
    assertTrue(data2.getY() >= 0);
    assertTrue(data2.getWidth() > 0);
    assertTrue(data2.getHeight() > 0);
}
```

### Integration Test for Damage Tracking

```java
@Test
public void testDamagePropagation() {
    TerminalPanel panel = new TerminalPanel("test-panel");
    TerminalTextButton button = new TerminalTextButton("Button");

    panel.addChild(button);

    // Trigger layout
    panel.requestLayoutUpdate();

    // Invalidate button
    button.invalidateRegion(0, 0, 10, 10);

    // Verify damage propagated to panel
    TerminalRectangle panelDamage = panel.getDamage();
    assertNotNull(panelDamage);
    assertTrue(panelDamage.getWidth() > 0);
    assertTrue(panelDamage.getHeight() > 0);

    // Render panel
    TerminalBatchBuilder batch = new TerminalBatchBuilder();
    panel.toBatch(batch, null);

    // Verify damage cleared
    panel.clearRenderFlag();
    assertNull(panel.getDamage());
}
```

---

## Performance Considerations

### 1. Minimize Object Creation

```java
// WRONG - Creates new arrays every layout pass
private void layoutChildren(...) {
    int[] widths = new int[count];
    int[] heights = new int[count];
    // ...
}

// CORRECT - Reuse arrays
private int[] widths;
private int[] heights;

private void layoutChildren(...) {
    if (widths == null || widths.length < count) {
        widths = new int[Math.max(widths.length, count * 2)];
        heights = new int[Math.max(heights.length, count * 2)];
    }
    // ...
}
```

### 2. Use Object Pooling

```java
// WRONG - Creates new regions
private void layoutChildren(...) {
    TerminalRectangle region = regionPool.obtain();
    region.set(x, y, width, height);
    // Use region
    regionPool.recycle(region);
}

// CORRECT - Reuse regions
private TerminalRectangle reusableRegion;

private void layoutChildren(...) {
    if (reusableRegion == null) {
        reusableRegion = regionPool.obtain();
    }
    reusableRegion.set(x, y, width, height);
    // Use reusableRegion
}
```

### 3. Batch Layout Updates

```java
// WRONG - Multiple layout updates
button1.requestLayoutUpdate();
button2.requestLayoutUpdate();
button3.requestLayoutUpdate();

// CORRECT - Single layout update
panel.beginRequestBatch();
panel.addChild(button1);
panel.addChild(button2);
panel.addChild(button3);
panel.endRequestBatch();
```

### 4. Avoid Layout During Layout

```java
// WRONG - Modifies tree during layout
private void layoutChildren(...) {
    for (TerminalLayoutContext context : contexts) {
        TerminalRenderable child = context.getRenderable();
        if (child.isHidden()) {
            removeChild(child);  // Structural change during layout!
        }
    }
}

// CORRECT - Use visibility instead
private void layoutChildren(...) {
    for (TerminalLayoutContext context : contexts) {
        TerminalRenderable child = context.getRenderable();
        if (shouldManageHidden(child) && child.isHidden()) {
            dataInterfaces.get(child.getName())
                .setLayoutData(TerminalLayoutData.getBuilder().build());
        }
    }
}
```

---

## Summary

This document provides a comprehensive reference for the Netnotes-Engine and NetTerminal component system. Key takeaways:

1. **Renderable.java** is the source of truth for state, events, and damage tracking
2. **RenderableLayoutManager.java** provides single-pass layout with two-call model
3. **TerminalRenderable.java** extends with 2D coordinate system and rendering helpers
4. **TerminalGroupRegion.java** provides group ownership and child wiring
5. **Layout callbacks** orchestrate positioning and measurement
6. **Object pooling** eliminates GC pressure
7. **Damage tracking** enables efficient dirty rendering
8. **Layer system** controls render order (normal → floating → modal → notification)
9. **Animation** is done via layout updates with interpolation
10. **State machine** manages transitions with callbacks

For implementing new components:
1. Extend `TerminalGroupRegion` (or `TerminalRegion` for simple components)
2. Implement `createLayoutCallback()` for positioning
3. Implement `measureContent()` for sizing
4. Implement `renderSelf()` for rendering
5. Use object pooling to minimize allocations
6. Request layout updates when state changes
7. Handle visibility and hidden state properly
