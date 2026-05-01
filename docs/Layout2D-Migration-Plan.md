# Layout2D Migration Plan

## Overview

This document outlines the migration plan for updating the NetTerminal codebase to use the new `io.netnotes.engine.ui.layout2d` enums, replacing scattered usage of `Axis`, `Alignment`, `SizePreference`, and `LayoutOverflowStrategy`.

## Migration Strategy

### Phase 1: Core Panel Classes (High Priority)

**Files to Update:**
- `TerminalPanel.java`
- `TerminalAbstractStack.java`
- `TerminalHStack.java`
- `TerminalVStack.java`
- `TerminalStackPanel.java`

**Rationale:** These are the foundation classes used throughout the codebase. Updating them first ensures consistency and provides a template for the rest of the codebase.

### Phase 2: Panel Variants (Medium Priority)

**Files to Update:**
- `TerminalBorderPanel.java`
- `TerminalScrollPanel.java`
- `TerminalOverlayPanel.java`
- `TerminalDivider.java`

**Rationale:** These extend core panel classes and will benefit from the new enums.

### Phase 3: Components Using Layout (Medium Priority)

**Files to Update:**
- `TerminalLabel.java`
- `TerminalButton.java`
- `TerminalMessageBox.java`
- `TerminalProgressBar.java`
- `TerminalInstallStepRow.java`
- `TerminalWizardHeader.java`
- `TerminalWizardFooter.java`
- `TerminalSetupFlow.java`
- `TerminalDialog.java`
- `TerminalTextInput.java`
- `PasswordPrompt.java`
- `AnyKeyField.java`
- `MenuNavigator.java`
- `ScrollIndicator.java`

**Rationale:** These components use layout containers and will benefit from the new enums.

### Phase 4: Install Wizard Components (Low Priority)

**Files to Update:**
- `TerminalInstallWizard.java`
- `TerminalInstallStepRow.java`
- `TerminalWizardHeader.java`
- `TerminalWizardFooter.java`

**Rationale:** These are wizard-specific and can be updated later.

### Phase 5: Test Files (Low Priority)

**Files to Update:**
- All test files using the old enums

**Rationale:** Tests will be updated last to ensure the core functionality works first.

---

## Enum Mapping Reference

### 1. Axis → FlexDirection

| Old (Axis) | New (FlexDirection) | Usage |
|------------|---------------------|-------|
| `Axis.HORIZONTAL` | `FlexDirection.ROW` | Main axis horizontal (left-to-right) |
| `Axis.VERTICAL` | `FlexDirection.COLUMN` | Main axis vertical (top-to-bottom) |

**Migration Pattern:**
```java
// Before
Axis axis = Axis.HORIZONTAL;

// After
FlexDirection direction = FlexDirection.ROW;
```

### 2. Alignment → AlignContent / AlignSelf

| Old (Alignment) | New (AlignContent) | New (AlignSelf) | Usage |
|-----------------|-------------------|-----------------|-------|
| `Alignment.START` | `AlignContent.FLEX_START` | `AlignSelf.FLEX_START` | Start alignment |
| `Alignment.CENTER` | `AlignContent.CENTER` | `AlignSelf.CENTER` | Center alignment |
| `Alignment.END` | `AlignContent.FLEX_END` | `AlignSelf.FLEX_END` | End alignment |
| `Alignment.STRETCH` | `AlignContent.STRETCH` | `AlignSelf.STRETCH` | Stretch to fill available space |

**Migration Pattern:**
```java
// Before
Alignment alignment = Alignment.CENTER;

// After
AlignContent content = AlignContent.CENTER;
AlignSelf self = AlignSelf.CENTER;
```

**Note:** In TerminalPanel, the naming is confusing:
- `alignment` field → aligns children on the **cross-axis** (HTML/CSS `align-items`)
- `crossAlignment` field → aligns children on the **main-axis** (HTML/CSS `justify-content`)

This is the opposite of HTML/CSS terminology. During migration, consider:
1. Keep existing field names for backward compatibility
2. Add documentation clarifying the semantics
3. Or rename fields to match HTML/CSS (ALIGN_SELF and ALIGN_CONTENT)

### 3. SizePreference → FlexGrow / FlexShrink / FlexBasis (CRITICAL MAPPING)

**The Key Concept: `isAxisParentDependent()` and `isAxisContentDependent()`**

These determine the **layout phase**:
- **Parent-dependent** axes (FILL, PERCENT, INHERIT) → Laid out **top-down** (parent first, children second)
- **Content-dependent** axes (FIT_CONTENT) → Laid out **bottom-up** (children first, parent second)
- **Fixed** axes (STATIC) → No special handling

**Complementarity:** For any axis, exactly one of these is true:
```java
isAxisParentDependent(axis)  // true for FILL, PERCENT, INHERIT
isAxisContentDependent(axis) // true for FIT_CONTENT
```

| Old (SizePreference) | New (FlexGrow/FlexShrink/FlexBasis) | isAxisParentDependent() | isAxisContentDependent() | Layout Phase |
|----------------------|-------------------------------------|-------------------------|--------------------------|--------------|
| `SizePreference.STATIC` | `FlexGrow.NONE` / `FlexShrink.NONE` | `false` | `false` | N/A (fixed) |
| `SizePreference.FILL` | `FlexGrow.FULL` / `FlexShrink.FULL` | `true` | `false` | Top-down |
| `SizePreference.FIT_CONTENT` | `FlexBasis.CONTENT` | `false` | `true` | Bottom-up |
| `SizePreference.PERCENT` | `FlexBasis.percent(value)` | `true` | `false` | Top-down |
| `SizePreference.INHERIT` | `FlexGrow.FULL` / `FlexShrink.FULL` | `true` | `false` | Top-down |

**Migration Pattern:**
```java
// Before
SizePreference pref = SizePreference.FILL;

// After
FlexGrow grow = FlexGrow.FULL;
```

**For FIT_CONTENT (content-dependent):**
```java
// Before
SizePreference pref = SizePreference.FIT_CONTENT;

// After
FlexBasis basis = FlexBasis.content();
```

**For PERCENT (parent-dependent):**
```java
// Before
SizePreference pref = SizePreference.PERCENT(50);

// After
FlexBasis basis = FlexBasis.percent(50);  // Convenience (stores as 0.5)
FlexBasis basis = FlexBasis.percent(0.5); // Double precision
```

**For STATIC (fixed, not parent-dependent):**
```java
// Before
SizePreference pref = SizePreference.STATIC;

// After
FlexGrow grow = FlexGrow.NONE;
FlexShrink shrink = FlexShrink.NONE;
```

**For INHERIT (inherits parent behavior):**
```java
// Before
SizePreference pref = SizePreference.INHERIT;

// After
FlexGrow grow = FlexGrow.FULL;  // Inherits parent's grow/shrink behavior
FlexShrink shrink = FlexShrink.FULL;  // Inherits parent's grow/shrink behavior
```

**Helper Methods:**
```java
// FlexGrow/FlexShrink
grow.isFill()      // Returns true for FULL (parent-dependent)
grow.isNone()      // Returns true for NONE (fixed)
grow.toInt()       // Returns numeric value

// FlexBasis
basis.isContent()  // Returns true for CONTENT (content-dependent)
basis.isAuto()     // Returns true for AUTO (default, behaves like content)
basis.isPixels()   // Returns true for PIXELS
basis.isPercent()  // Returns true for PERCENT
basis.toPixels(containerSize)  // Convert to pixels

### 4. LayoutOverflowStrategy → Overflow

| Old (LayoutOverflowStrategy) | New (Overflow) | Usage |
|------------------------------|---------------|-------|
| `LayoutOverflowStrategy.CLIP` | `Overflow.HIDDEN` | Content is clipped |
| `LayoutOverflowStrategy.OVERFLOW` | `Overflow.VISIBLE` | Content may render outside bounds |
| `LayoutOverflowStrategy.SHRINK_FILL` | `Overflow.HIDDEN` (custom handling) | Custom shrink-fill logic |
| `LayoutOverflowStrategy.SHRINK_ALL` | `Overflow.HIDDEN` (custom handling) | Custom shrink-all logic |
| `LayoutOverflowStrategy.DISTRIBUTE_EQUAL` | `Overflow.HIDDEN` (custom handling) | Custom distribute-equal logic |
| `LayoutOverflowStrategy.SCROLL` | `Overflow.AUTO` (not yet implemented) | Scrollable (falls back to CLIP) |

**Migration Pattern:**
```java
// Before
LayoutOverflowStrategy strategy = LayoutOverflowStrategy.CLIP;

// After
Overflow overflow = Overflow.HIDDEN;
```

**Note:** The custom overflow strategies (SHRINK_FILL, SHRINK_ALL, DISTRIBUTE_EQUAL) will need custom handling in the new code. For now, they can use `Overflow.HIDDEN` with custom logic.

---

### Layout Phase Implications (CRITICAL)

The `isAxisParentDependent()` concept drives the two-phase layout system:

**Phase 1: Top-Down Layout (Parent-Dependent Axes)**
- Axes marked as `isAxisParentDependent()=true` are laid out first
- Parent containers are laid out before their children
- FILL, PERCENT, INHERIT → Top-down

**Phase 2: Bottom-Up Layout (Content-Dependent Axes)**
- Axes marked as `isAxisContentDependent()=true` (FIT_CONTENT) are laid out second
- Children are laid out before their parents
- Content-dependent sizing requires children to measure first
- CONTENT → Bottom-up

**Complementarity:**
```java
// For any axis, exactly one of these is true:
isAxisParentDependent(axis)  // true for FILL, PERCENT, INHERIT
isAxisContentDependent(axis) // true for FIT_CONTENT

// In Layout2D:
flexGrow.isFill()      // true for FILL (parent-dependent)
flexGrow.isNone()      // true for STATIC (fixed)
flexBasis.isContent()  // true for CONTENT (content-dependent)
flexBasis.isAuto()     // true for AUTO (content-dependent)
```

**Example:**
```java
// TerminalHStack: width is FIT_CONTENT (bottom-up), height is FILL (top-down)
FlexBasis widthBasis = FlexBasis.content();  // Bottom-up layout
FlexGrow heightGrow = FlexGrow.FULL;         // Top-down layout
```

This two-phase system ensures:
1. Content-dependent elements can measure their children first
2. Parent-dependent elements can distribute available space after children are measured
3. Fixed elements don't participate in layout phases

---

## Detailed Migration Steps

### Phase 1: Core Panel Classes

#### 1.1 TerminalPanel.java

**Changes Required:**

1. **Replace internal enums:**
   ```java
   // Remove
   public enum Axis { VERTICAL, HORIZONTAL }
   public enum Alignment { START, CENTER, END, STRETCH }

   // Add
   import io.netnotes.engine.ui.layout2d.*;
   ```

2. **Update field declarations:**
   ```java
   // Before
   private Axis axis = Axis.HORIZONTAL;
   private boolean wrap = false;
   private Alignment crossAlignment = Alignment.START;
   private Alignment alignment = Alignment.START;
   private LayoutOverflowStrategy overflowStrategy = LayoutOverflowStrategy.CLIP;

   // After
   private FlexDirection direction = FlexDirection.ROW;
   private FlexWrap wrap = FlexWrap.NOWRAP;
   private AlignSelf crossSelf = AlignSelf.FLEX_START;
   private AlignContent content = AlignContent.FLEX_START;
   private Overflow overflow = Overflow.HIDDEN;
   ```

3. **Update constructor:**
   ```java
   // Before
   public TerminalPanel(String name) {
       super(name, "term-panel");
       // ...
   }

   // After
   public TerminalPanel(String name) {
       super(name, "term-panel");
       this.direction = FlexDirection.ROW;
       this.wrap = FlexWrap.NOWRAP;
       this.crossSelf = AlignSelf.FLEX_START;
       this.content = AlignContent.FLEX_START;
       this.overflow = Overflow.HIDDEN;
       // ...
   }
   ```

4. **Update methods that use old enums:**
   - `getAxis()` → `getDirection()`
   - `setAxis(Axis)` → `setDirection(FlexDirection)`
   - `getAlignment()` → `getContent()` (or clarify naming)
   - `setAlignment(Alignment)` → `setContent(AlignContent)` (or clarify naming)
   - `getCrossAlignment()` → `getCrossSelf()` (or clarify naming)
   - `setCrossAlignment(Alignment)` → `setCrossSelf(AlignSelf)` (or clarify naming)
   - `getOverflowStrategy()` → `getOverflow()`
   - `setOverflowStrategy(LayoutOverflowStrategy)` → `setOverflow(Overflow)`
   - `isWrap()` → `isWrap()`
   - `setWrap(boolean)` → `setWrap(FlexWrap)` (accept boolean for convenience)

5. **Update layout logic:**
   - Replace `Axis.HORIZONTAL` with `FlexDirection.ROW`
   - Replace `Axis.VERTICAL` with `FlexDirection.COLUMN`
   - Replace `Alignment.START` with `AlignContent.FLEX_START`
   - Replace `Alignment.CENTER` with `AlignContent.CENTER`
   - Replace `Alignment.END` with `AlignContent.FLEX_END`
   - Replace `Alignment.STRETCH` with `AlignContent.STRETCH`
   - Replace `LayoutOverflowStrategy.CLIP` with `Overflow.HIDDEN`
   - Replace `LayoutOverflowStrategy.OVERFLOW` with `Overflow.VISIBLE`

6. **Update SizePreference to FlexBasis/FlexGrow (CRITICAL):**
   - Replace `SizePreference.FILL` with `FlexGrow.FULL` (parent-dependent → top-down layout)
   - Replace `SizePreference.FIT_CONTENT` with `FlexBasis.CONTENT` (content-dependent → bottom-up layout)
   - Replace `SizePreference.PERCENT(value)` with `FlexBasis.percent(value)` (parent-dependent → top-down layout)
   - Replace `SizePreference.STATIC` with `FlexGrow.NONE` / `FlexShrink.NONE` (fixed → no special handling)
   - Replace `SizePreference.INHERIT` with `FlexGrow.FULL` / `FlexShrink.FULL` (inherits parent's parent-dependent behavior)

#### 1.2 TerminalAbstractStack.java

**Changes Required:**

1. **Replace internal enums:**
   ```java
   // Remove
   public enum VAlignment { TOP, CENTER, BOTTOM }
   public enum HAlignment { LEFT, CENTER, RIGHT }

   // Add
   import io.netnotes.engine.ui.layout2d.*;
   ```

2. **Update field declarations:**
   ```java
   // Before
   protected LayoutOverflowStrategy overflowStrategy = LayoutOverflowStrategy.CLIP;
   protected VAlignment vAlignment;
   protected HAlignment hAlignment;

   // After
   protected Overflow overflow = Overflow.HIDDEN;
   protected AlignSelf crossSelf = AlignSelf.FLEX_START;
   protected AlignContent content = AlignContent.FLEX_START;
   ```

3. **Update constructor:**
   ```java
   // Before
   protected TerminalAbstractStack(
       String name,
       String groupPrefix,
       SizePreference defaultWidth,
       SizePreference defaultHeight,
       VAlignment defaultVAlign,
       HAlignment defaultHAlign
   ) {
       // ...
   }

   // After
   protected TerminalAbstractStack(
       String name,
       String groupPrefix,
       FlexGrow defaultWidthGrow,
       FlexBasis defaultWidthBasis,
       FlexGrow defaultHeightGrow,
       FlexBasis defaultHeightBasis,
       AlignSelf defaultCrossSelf
   ) {
       // ...
   }
   ```

4. **Update methods:**
   - `getOverflowStrategy()` → `getOverflow()`
   - `setOverflowStrategy(LayoutOverflowStrategy)` → `setOverflow(Overflow)`
   - `getVAlignment()` → `getCrossSelf()`
   - `setVAlignment(VAlignment)` → `setCrossSelf(AlignSelf)`
   - `getHAlignment()` → `getContent()`
   - `setHAlignment(HAlignment)` → `setContent(AlignContent)`

#### 1.3 TerminalHStack.java

**Changes Required:**

1. **Update constructor:**
   ```java
   // Before
   public TerminalHStack(String name) {
       super(
           name,
           "hstack",
           SizePreference.FIT_CONTENT,  // default child-width
           SizePreference.FILL,         // default child-height
           VAlignment.CENTER,
           HAlignment.LEFT
       );
   }

   // After
   public TerminalHStack(String name) {
       super(
           name,
           "hstack",
           FlexGrow.NONE,       // default child-width (FIT_CONTENT)
           FlexBasis.content(),  // default child-width
           FlexGrow.FULL,        // default child-height
           FlexBasis.content(),  // default child-height
           AlignSelf.CENTER      // cross self alignment
       );
   }
   ```

2. **Update layout logic:**
   - Replace `SizePreference.FILL` with `FlexGrow.FULL`
   - Replace `SizePreference.FIT_CONTENT` with `FlexBasis.CONTENT`
   - Replace `SizePreference.STATIC` with `FlexGrow.NONE` / `FlexShrink.NONE`
   - Replace `VAlignment.CENTER` with `AlignSelf.CENTER`
   - Replace `VAlignment.TOP` with `AlignSelf.FLEX_START`
   - Replace `VAlignment.BOTTOM` with `AlignSelf.FLEX_END`
   - Replace `HAlignment.LEFT` with `AlignContent.FLEX_START`
   - Replace `HAlignment.CENTER` with `AlignContent.CENTER`
   - Replace `HAlignment.RIGHT` with `AlignContent.FLEX_END`

3. **Update measurement logic:**
   - Replace `SizePreference` enum checks with `FlexGrow`/`FlexShrink`/`FlexBasis` checks
   - Use `FlexGrow.isFill()`, `FlexGrow.isNone()`, `FlexShrink.isFull()`, etc.

#### 1.4 TerminalVStack.java

**Changes Required:**

Similar to TerminalHStack, but:
- Constructor uses `FlexDirection.COLUMN` instead of `FlexDirection.ROW`
- All alignment references remain the same (cross-axis is horizontal)

#### 1.5 TerminalStackPanel.java

**Changes Required:**

Similar to TerminalAbstractStack, but:
- Uses `FlexDirection.COLUMN` (z-axis stack, not relevant to new enums)
- May need to add `FlexWrap` support for z-axis stacking

---

### Phase 2: Panel Variants

#### 2.1 TerminalBorderPanel.java

**Changes Required:**

1. **Update constructor to use new enums:**
   ```java
   public TerminalBorderPanel(String name, FlexDirection direction, AlignContent content, AlignSelf crossSelf) {
       super(name, direction, content, crossSelf);
       // ...
   }
   ```

2. **Update method signatures:**
   ```java
   public void setDirection(FlexDirection direction) { ... }
   public void setContent(AlignContent content) { ... }
   public void setCrossSelf(AlignSelf crossSelf) { ... }
   public void setOverflow(Overflow overflow) { ... }
   ```

#### 2.2 TerminalScrollPanel.java

**Changes Required:**

1. **Update overflow handling:**
   ```java
   // Before
   private LayoutOverflowStrategy overflowStrategy = LayoutOverflowStrategy.CLIP;

   // After
   private Overflow overflow = Overflow.HIDDEN;

   // Update setScrollPolicy method
   public void setScrollPolicy(Overflow overflow) {
       this.overflow = overflow;
       // ...
   }
   ```

2. **Update scroll logic:**
   - Use `Overflow.isScrollable()` to check if scrolling should be enabled
   - Use `Overflow.HIDDEN` for CLIP, `Overflow.VISIBLE` for OVERFLOW

#### 2.3 TerminalOverlayPanel.java

**Changes Required:**

1. **Update overflow handling:**
   ```java
   // Before
   private LayoutOverflowStrategy overflowStrategy = LayoutOverflowStrategy.OVERFLOW;

   // After
   private Overflow overflow = Overflow.VISIBLE;
   ```

#### 2.4 TerminalDivider.java

**Changes Required:**

1. **Update orientation handling:**
   ```java
   // Before
   private Axis axis = Axis.VERTICAL;

   // After
   // No longer needed - Divider doesn't use FlexDirection
   // Keep as-is for now
   ```

---

### Phase 3: Components Using Layout

#### 3.1 TerminalLabel.java

**Changes Required:**

1. **Update SizePreference to FlexBasis/FlexGrow:**
   ```java
   // Before
   private SizePreference widthPreference = SizePreference.STATIC;
   private SizePreference heightPreference = SizePreference.FIT_CONTENT;

   // After
   private FlexGrow widthGrow = FlexGrow.NONE;
   private FlexBasis widthBasis = FlexBasis.content();
   private FlexGrow heightGrow = FlexGrow.NONE;
   private FlexBasis heightBasis = FlexBasis.content();
   ```

2. **Update methods:**
   ```java
   public FlexGrow getWidthGrow() { return widthGrow; }
   public FlexBasis getWidthBasis() { return widthBasis; }
   public FlexGrow getHeightGrow() { return heightGrow; }
   public FlexBasis getHeightBasis() { return heightBasis; }

   public void setWidthGrow(FlexGrow grow) { this.widthGrow = grow; }
   public void setWidthBasis(FlexBasis basis) { this.widthBasis = basis; }
   public void setHeightGrow(FlexGrow grow) { this.heightGrow = grow; }
   public void setHeightBasis(FlexBasis basis) { this.heightBasis = basis; }
   ```

#### 3.2 TerminalButton.java

**Changes Required:**

Similar to TerminalLabel, update SizePreference to FlexGrow/FlexBasis.

#### 3.3 TerminalMessageBox.java

**Changes Required:**

Similar to TerminalLabel, update SizePreference to FlexGrow/FlexBasis.

#### 3.4 TerminalProgressBar.java

**Changes Required:**

Similar to TerminalLabel, update SizePreference to FlexGrow/FlexBasis.

#### 3.5 Install Wizard Components

**Changes Required:**

Similar to TerminalLabel, update SizePreference to FlexGrow/FlexBasis.

#### 3.6 TerminalTextInput.java, PasswordPrompt.java, AnyKeyField.java

**Changes Required:**

Similar to TerminalLabel, update SizePreference to FlexGrow/FlexBasis.

#### 3.7 MenuNavigator.java

**Changes Required:**

Similar to TerminalLabel, update SizePreference to FlexGrow/FlexBasis.

#### 3.8 ScrollIndicator.java (H and V)

**Changes Required:**

Similar to TerminalLabel, update SizePreference to FlexGrow/FlexBasis.

---

### Phase 4: Install Wizard Components

**Files:**
- `TerminalInstallWizard.java`
- `TerminalInstallStepRow.java`
- `TerminalWizardHeader.java`
- `TerminalWizardFooter.java`

**Changes Required:**

Similar to Phase 3, update SizePreference to FlexGrow/FlexBasis.

---

### Phase 5: Test Files

**Files:**
- All test files using `Axis`, `Alignment`, `SizePreference`, `LayoutOverflowStrategy`

**Changes Required:**

1. **Replace imports:**
   ```java
   // Before
   import io.netnotes.engine.ui.Axis;
   import io.netnotes.engine.ui.Alignment;
   import io.netnotes.engine.ui.SizePreference;
   import io.netnotes.engine.ui.LayoutOverflowStrategy;

   // After
   import io.netnotes.engine.ui.layout2d.*;
   ```

2. **Replace enum values:**
   - Use the mapping table above to replace enum values
   - Update test assertions accordingly

3. **Update test methods:**
   - Update method signatures if they use old enums
   - Update test data structures

---

## Backward Compatibility

### Temporary Compatibility Layer

To ease migration, we can add temporary compatibility methods:

```java
// In TerminalPanel.java
public Axis getAxis() {
    return direction == FlexDirection.ROW ? Axis.HORIZONTAL : Axis.VERTICAL;
}

public void setAxis(Axis axis) {
    this.direction = axis == Axis.HORIZONTAL ? FlexDirection.ROW : FlexDirection.COLUMN;
}

public Alignment getAlignment() {
    return content == AlignContent.FLEX_START ? Alignment.START :
           content == AlignContent.CENTER ? Alignment.CENTER :
           content == AlignContent.FLEX_END ? Alignment.END :
           Alignment.STRETCH;
}

// ... similar for other compatibility methods
```

**Note:** These should be marked as `@Deprecated` and removed after migration is complete.

---

## Testing Strategy

### Unit Tests

1. **Update existing tests** to use new enums
2. **Add new tests** for new enum features (align-content, flex-shrink, etc.)
3. **Verify backward compatibility** if using compatibility layer

### Integration Tests

1. **Test panel layouts** with new enums
2. **Test overflow handling** with new Overflow enum
3. **Test flex properties** with new FlexGrow/FlexShrink/FlexBasis enums

### Regression Tests

1. **Run all existing tests** after each migration phase
2. **Compare results** to ensure no behavior changes
3. **Fix any regressions** before moving to next phase

---

## Risk Assessment

### High Risk

- **TerminalPanel.java** - Core component, affects many other classes
- **TerminalAbstractStack.java** - Base class for most panel types

### Medium Risk

- **TerminalHStack.java** - Widely used in UI
- **TerminalVStack.java** - Widely used in UI
- **TerminalBorderPanel.java** - Widely used in UI
- **TerminalScrollPanel.java** - Widely used in UI

### Low Risk

- **Install wizard components** - Specific to wizard flow
- **Test files** - Can be updated later

---

## Timeline Estimate

### Phase 1: Core Panel Classes
- **TerminalPanel.java:** 4-6 hours
- **TerminalAbstractStack.java:** 3-4 hours
- **TerminalHStack.java:** 3-4 hours
- **TerminalVStack.java:** 3-4 hours
- **TerminalStackPanel.java:** 2-3 hours
- **Total:** 15-21 hours

### Phase 2: Panel Variants
- **TerminalBorderPanel.java:** 2-3 hours
- **TerminalScrollPanel.java:** 2-3 hours
- **TerminalOverlayPanel.java:** 1-2 hours
- **TerminalDivider.java:** 1-2 hours
- **Total:** 6-10 hours

### Phase 3: Components Using Layout
- **TerminalLabel.java:** 2-3 hours
- **TerminalButton.java:** 1-2 hours
- **TerminalMessageBox.java:** 1-2 hours
- **TerminalProgressBar.java:** 1-2 hours
- **Install wizard components:** 4-6 hours
- **TerminalTextInput.java, PasswordPrompt.java, AnyKeyField.java:** 3-4 hours
- **MenuNavigator.java:** 1-2 hours
- **ScrollIndicator.java:** 2-3 hours
- **Total:** 15-24 hours

### Phase 4: Install Wizard Components
- **TerminalInstallWizard.java:** 2-3 hours
- **TerminalInstallStepRow.java:** 1-2 hours
- **TerminalWizardHeader.java:** 1-2 hours
- **TerminalWizardFooter.java:** 1-2 hours
- **Total:** 5-9 hours

### Phase 5: Test Files
- **All test files:** 10-15 hours

### Total Estimated Time
**47-69 hours** (6-9 days for one developer)

---

## Success Criteria

1. **All core panel classes** use new enums
2. **All UI components** use new enums
3. **All tests** pass with new enums
4. **No backward compatibility issues** (or compatibility layer in place)
5. **Documentation** updated with new enums
6. **Performance** maintained or improved

---

## Next Steps

1. **Review this plan** with the team
2. **Identify blockers** or concerns
3. **Prioritize phases** based on business needs
4. **Start with Phase 1** (core panel classes)
5. **Test thoroughly** after each phase
6. **Update documentation** as migration progresses
