# Java Layout System vs HTML/CSS Layout Comparison

## Executive Summary

This document compares the NetTerminal Java layout system with HTML/CSS layout features to identify feature gaps and alignment opportunities.

---

## 1. CONTAINER TYPES

### HTML/CSS

| Container | Description | Key Features |
|-----------|-------------|--------------|
| **Flexbox** | One-dimensional layout | `flex-direction`, `flex-wrap`, `justify-content`, `align-items`, `align-content` |
| **Grid** | Two-dimensional layout | `grid-template-columns`, `grid-template-rows`, `grid-template-areas` |
| **Float** | Content flows around elements | `float`, `clear` |
| **Position** | Absolute positioning | `absolute`, `relative`, `fixed`, `sticky` |
| **Table** | Table-like layout | `display: table` |
| **Inline-block** | Inline elements with block behavior | `display: inline-block` |
| **Overflow** | Overflow handling | `overflow`, `overflow-x`, `overflow-y` |
| **Scroll** | Scrollable containers | `overflow: auto/scroll` |

### Java System

| Container | Description | Key Features |
|-----------|-------------|--------------|
| **Panel** | Single-axis flexible container | `axis`, `wrap`, `alignment`, `crossAlignment`, `overflowStrategy` |
| **HStack** | Horizontal stack (alias for Panel) | Same as Panel with HORIZONTAL axis |
| **VStack** | Vertical stack (alias for Panel) | Same as Panel with VERTICAL axis |
| **StackPanel** | Z-axis stack (single-visible) | `setVisibleContent()`, scroll support |
| **BorderPanel** | Panel with borders | `drawBorder`, `drawSeparators`, `borderStyle`, `title` |
| **ScrollPanel** | Panel with scrolling | Scroll offset control |
| **OverlayPanel** | Overlay/masked content | `OVERFLOW` strategy |
| **Group** | Sibling renderables | Shared layout callback |
| **GroupRegion** | Region managing a group | Layout group identity |

### Gaps Identified

| HTML/CSS Feature | Java Equivalent | Gap |
|------------------|-----------------|-----|
| **Grid** (2D) | None | ✗ No 2D grid layout |
| **Float** | None | ✗ No float-based layout |
| **Position (absolute/relative)** | `FloatingLayoutManager` | ⚠️ Separate system, not integrated |
| **Position (fixed/sticky)** | None | ✗ No fixed/sticky positioning |
| **Table** | None | ✗ No table layout |
| **Inline-block** | None | ✗ No inline-block behavior |
| **z-index** | StackPanel | ⚠️ Implicit Z-order, no explicit control |
| **Layering** | None | ✗ No explicit stacking layer support |

---

## 2. SIZE POLICIES (Flexbox/SizePreference)

### HTML/CSS

| Property | Values | Description |
|----------|--------|-------------|
| `width/height` | `auto`, `%`, `px`, `rem`, `vw`, `vh` | Element dimensions |
| `flex-grow` | `0` to `n` | How much to grow (default 0) |
| `flex-shrink` | `0` to `n` | How much to shrink (default 1) |
| `flex-basis` | `auto`, `%`, `px` | Initial size before flex adjustments |
| `min-width/height` | Any value | Minimum size constraints |
| `max-width/height` | Any value | Maximum size constraints |
| `aspect-ratio` | `auto`, `n/m` | Aspect ratio constraint |
| `object-fit` | `fill`, `contain`, `cover`, etc. | Content fitting |

### Java System

| Policy | Description | HTML/CSS Equivalent |
|--------|-------------|---------------------|
| **STATIC** | Fixed size independent of parent/children | `width/height: auto` with explicit pixel values |
| **FILL** | Take all available space on main axis | `flex-grow: 1` (or `width: 100%` for flex children) |
| **FIT_CONTENT** | Use preferred/requested size from content | `width: auto` (content-driven) |
| **PERCENT** | Use percentage of parent's dimension | `width: %` |
| **INHERIT** | Use parent's default preference | `width: auto` (inherits from parent) |

### Gaps Identified

| HTML/CSS Feature | Java Equivalent | Gap |
|------------------|-----------------|-----|
| **flex-shrink** | None | ✗ No shrink control for non-fill children |
| **flex-basis** | None | ✗ No explicit initial size before flex adjustments |
| **aspect-ratio** | None | ✗ No aspect ratio constraint |
| **object-fit** | None | ✗ No content fitting modes |
| **min/max constraints** | `minWidth/Height`, `maxWidth/Height` | ✗ Separate APIs (could be unified) |
| **intrinsic sizing** | `FIT_CONTENT` | ⚠️ Only for content, not container itself |

---

## 3. ALIGNMENT

### HTML/CSS

| Property | Values | Description |
|----------|--------|-------------|
| `justify-content` | `flex-start`, `flex-end`, `center`, `space-between`, `space-around`, `space-evenly` | Main-axis alignment |
| `align-items` | `flex-start`, `flex-end`, `center`, `stretch`, `baseline` | Cross-axis alignment (flex) |
| `align-content` | `flex-start`, `flex-end`, `center`, `space-between`, `space-around`, `stretch` | Multi-line alignment (flex-wrap) |
| `align-self` | `auto`, flex values | Individual item alignment |
| `text-align` | `left`, `center`, `right`, `justify` | Text alignment (not layout) |

### Java System

| Alignment | Main Axis | Cross Axis | HTML/CSS Equivalent |
|-----------|-----------|------------|---------------------|
| **START** | ✓ | ✓ | `flex-start` |
| **CENTER** | ✓ | ✓ | `center` |
| **END** | ✓ | ✓ | `flex-end` |
| **STRETCH** | ✗ | ✓ | `align-items: stretch` (only cross-axis) |

### Gaps Identified

| HTML/CSS Feature | Java Equivalent | Gap |
|------------------|-----------------|-----|
| **justify-content: space-between** | None | ✗ No space-between/around/evenly |
| **justify-content: center** | ✓ (alignment field) | ⚠️ Different name |
| **justify-content: flex-end** | ✓ (alignment field) | ⚠️ Different name |
| **align-content** | None | ✗ No multi-line alignment |
| **align-self** | None | ✗ No per-child alignment override |
| **text-align** | None | ✗ Text alignment (different concept) |

---

## 4. FLEX WRAPPING

### HTML/CSS

| Property | Values | Description |
|----------|--------|-------------|
| `flex-wrap` | `nowrap`, `wrap`, `wrap-reverse` | Whether children wrap |
| `flex-direction` | `row`, `row-reverse`, `column`, `column-reverse` | Direction of layout |

### Java System

| Feature | Description | HTML/CSS Equivalent |
|---------|-------------|---------------------|
| **wrap** | Boolean flag for wrapping | `flex-wrap: wrap` |
| **axis** | `HORIZONTAL` or `VERTICAL` | `flex-direction: row/column` |

### Gaps Identified

| HTML/CSS Feature | Java Equivalent | Gap |
|------------------|-----------------|-----|
| **wrap-reverse** | None | ✗ No reverse wrapping |
| **flex-direction: row-reverse** | None | ✗ No reverse direction |
| **flex-direction: column-reverse** | None | ✗ No reverse direction |

---

## 5. OVERFLOW HANDLING

### HTML/CSS

| Property | Values | Description |
|----------|--------|-------------|
| `overflow` | `visible`, `hidden`, `scroll`, `auto` | Overflow handling |
| `overflow-x` | `visible`, `hidden`, `scroll`, `auto` | Horizontal overflow |
| `overflow-y` | `visible`, `hidden`, `scroll`, `auto` | Vertical overflow |
| `overflow-clip-margin` | Length value | Clipping margin |

### Java System

| Strategy | Description | HTML/CSS Equivalent |
|----------|-------------|---------------------|
| **CLIP** | Children that overflow are hidden | `overflow: hidden` |
| **OVERFLOW** | Children render outside parent bounds | `overflow: visible` (with caveats) |
| **SHRINK_FILL** | FILL children receive exactly the available share | None (custom) |
| **SHRINK_ALL** | All children scale proportionally | None (custom) |
| **DISTRIBUTE_EQUAL** | Every visible child receives an equal share | None (custom) |
| **SCROLL** | Scrollable (not yet implemented) | `overflow: scroll/auto` |

### Gaps Identified

| HTML/CSS Feature | Java Equivalent | Gap |
|------------------|-----------------|-----|
| **overflow: visible** | `OVERFLOW` strategy | ⚠️ Different semantics |
| **overflow: hidden** | `CLIP` strategy | ⚠️ Different semantics |
| **overflow: scroll** | `SCROLL` strategy (not implemented) | ✗ Falls back to CLIP |
| **overflow: auto** | `SCROLL` strategy (not implemented) | ✗ Falls back to CLIP |
| **overflow-x/y** | None | ✗ Separate overflow per axis |
| **scrollbar styling** | None | ✗ No scrollbar customization |
| **overflow-clip-margin** | None | ✗ No clipping margin |

---

## 6. GAPS & MISSING FEATURES

### High Priority Gaps

| Feature | HTML/CSS | Java | Impact |
|---------|----------|------|--------|
| **Grid Layout** | ✓ 2D layout | ✗ | High - no 2D grid |
| **Flex-shrink** | ✓ Control shrinkage | ✗ | Medium - FIT_CONTENT can't shrink |
| **align-content** | ✓ Multi-line alignment | ✗ | Medium - no multi-line support |
| **align-self** | ✓ Per-child alignment | ✗ | Medium - no override |
| **overflow: scroll/auto** | ✓ Scrollable containers | ✗ SCROLL not implemented | High - scroll not functional |
| **flex-basis** | ✓ Initial size | ✗ | Low - FIT_CONTENT covers most use cases |
| **aspect-ratio** | ✓ Constraint | ✗ | Low - rarely needed |

### Medium Priority Gaps

| Feature | HTML/CSS | Java | Impact |
|---------|----------|------|--------|
| **Position (fixed/sticky)** | ✓ Fixed positioning | ✗ | Medium - overlay positioning |
| **Float** | ✓ Float-based layout | ✗ | Low - rarely used now |
| **Table** | ✓ Table layout | ✗ | Low - rarely used now |
| **Inline-block** | ✓ Inline-block | ✗ | Low - rarely used now |
| **z-index control** | ✓ Stacking context | ⚠️ Implicit | Medium - no explicit layering |

### Low Priority Gaps

| Feature | HTML/CSS | Java | Impact |
|---------|----------|------|--------|
| **scrollbar styling** | ✓ Custom scrollbars | ✗ | Low - terminal context |
| **overflow-clip-margin** | ✓ Clipping margin | ✗ | Low - rarely needed |
| **text-align** | ✓ Text alignment | ✗ | Low - text rendering separate |
| **display: contents** | ✓ Remove nesting | ✗ | Low - not a layout concern |

---

## 7. ALIGNMENT OPPORTUNITIES

### Naming Conventions

| HTML/CSS | Java | Suggestion |
|----------|------|------------|
| `justify-content` | `alignment` | Keep as-is (more concise) |
| `align-items` | `crossAlignment` | Keep as-is (clear) |
| `align-content` | - | Add `crossAxisAlignment` overload |
| `flex-wrap` | `wrap` | Keep as-is |
| `flex-direction` | `axis` | Keep as-is |
| `flex-basis` | - | Add `flexBasis` property |
| `flex-grow` | `FILL` | Consider `flexGrow` enum with values 0-10 |
| `flex-shrink` | - | Add `flexShrink` enum with values 0-10 |
| `overflow` | `overflowStrategy` | Keep as-is (more specific) |

### Missing Properties to Add

```java
// Flexbox-like properties for Panel
public enum FlexGrow {
    NONE(0),
    SMALL(1),
    MEDIUM(2),
    LARGE(3),
    FULL(10);  // Equivalent to FILL

    private final int value;
    FlexGrow(int value) { this.value = value; }
}

public enum FlexShrink {
    NONE(0),
    SMALL(1),
    MEDIUM(2),
    LARGE(3),
    FULL(10);

    private final int value;
    FlexShrink(int value) { this.value = value; }
}

public enum FlexBasis {
    AUTO,
    PIXELS(int pixels),
    PERCENT(double percent);

    // ... implementation
}

// Overflow improvements
public enum Overflow {
    VISIBLE,    // OVERFLOW strategy
    HIDDEN,     // CLIP strategy
    AUTO,       // SCROLL strategy (to be implemented)
    SCROLL,     // SCROLL strategy (to be implemented)
    OVERFLOW,   // OVERFLOW strategy
    CLIP        // CLIP strategy
}
```

---

## 8. IMPLEMENTATION PRIORITY

### Phase 1: Critical (High Impact, Low Effort)

1. **Implement SCROLL strategy** - Currently falls back to CLIP
   - Add scroll offset tracking to Panel and StackPanel
   - Implement scroll viewport clipping
   - Add scrollbar controls (or integrate with terminal scroll)

2. **Add align-content support** - Multi-line alignment
   - Add `crossAxisAlignment` enum with SPACE_BETWEEN/AROUND/EVENLY
   - Update layout logic for multi-line wrapping

3. **Add align-self support** - Per-child alignment override
   - Add `setAlignmentOverride()` method to children
   - Allow individual children to override container alignment

### Phase 2: Important (Medium Impact, Medium Effort)

4. **Add flex-shrink control**
   - Add `flexShrink` enum to SizePreference
   - Update layout logic to respect shrink values
   - Allow FIT_CONTENT children to shrink

5. **Add flex-basis support**
   - Add `flexBasis` property to children
   - Allow explicit initial size before flex adjustments

6. **Add flex-grow values**
   - Expand FILL to support graduated values (SMALL, MEDIUM, LARGE, FULL)
   - Keep backward compatibility with current FILL enum

### Phase 3: Nice-to-Have (Low Impact, Medium-High Effort)

7. **Add Grid layout**
   - Create TerminalGrid class
   - Implement 2D grid template columns/rows
   - Add grid area support

8. **Add position (fixed/sticky)**
   - Integrate FloatingLayoutManager properly
   - Add fixed positioning support
   - Add sticky positioning support

9. **Add z-index control**
   - Add `zIndex` property to renderables
   - Implement explicit stacking context

---

## 9. CONCLUSION

### Strengths

1. **Good 1D Flexbox-like support** - Panel/HStack/VStack cover most use cases
2. **Comprehensive size policies** - STATIC, FILL, FIT_CONTENT, PERCENT, INHERIT
3. **Rich overflow strategies** - CLIP, OVERFLOW, SHRINK_FILL, SHRINK_ALL, DISTRIBUTE_EQUAL
4. **Clear alignment API** - START, CENTER, END, STRETCH
5. **Border and styling** - BorderPanel adds visual polish

### Main Gaps

1. **No 2D grid layout** - Major gap for complex layouts
2. **No scrollable containers** - SCROLL strategy not implemented
3. **No flex-shrink control** - FIT_CONTENT children can't shrink
4. **No align-content** - Multi-line alignment missing
5. **No align-self** - Per-child alignment override missing

### Recommendation

Focus on **Phase 1** items first (SCROLL, align-content, align-self) as they:
- Have high user impact
- Can be implemented with moderate effort
- Fill the most glaring gaps with HTML/CSS parity
- Enable common use cases like scrollable panels and multi-line alignment

Grid layout (Phase 3) should be considered when 2D layouts become a requirement, as it's a more complex feature that may not be needed for terminal UI use cases.
