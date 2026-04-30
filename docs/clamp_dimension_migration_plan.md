# Clamp Dimension Migration Plan

## Objective

Migrate all components that use `Math.max()` to clamp dimensions to use `clampDimension(int value, boolean isWidth)` instead. This ensures all dimension constraints (min/max) are consistently applied across the component system.

## Current State Analysis

### Files Using Math.max for Dimension Clamping

| Component | Lines | Pattern | Current Behavior |
|-----------|-------|---------|------------------|
| **TerminalPanel** | 481, 507 | `Math.max(minWidth, width)` | Clamps to min only |
| **TerminalOverlayPanel** | 523, 550 | `Math.min(intersectW, Math.max(minWidth, width))` | Clamps to min, then intersects |
| **TerminalStackPanel** | 481, 507 | `Math.max(minWidth, width)` | Clamps to min only |
| **TerminalVStack** | 262 | `Math.max(min, scaled)` | Scaling with min preservation |
| **TerminalHStack** | 262 | `Math.max(min, scaled)` | Scaling with min preservation |

### Files Using Math.max for Scaling (Different Purpose)

| Component | Lines | Pattern | Why It's Different |
|-----------|-------|---------|-------------------|
| **TerminalVStack** | 262 | `Math.max(min, scaled)` | Preserving minimums during distribution scale |
| **TerminalHStack** | 262 | `Math.max(min, scaled)` | Preserving minimums during distribution scale |

These scaling cases should **NOT** be changed to `clampDimension()` because they're preserving minimum sizes during a distribution operation, not enforcing container constraints.

---

## Interference Cases

### Critical Interference: TerminalOverlayPanel & TerminalStackPanel

**Problem:** Both components compute intersection bounds that include `Math.max(minWidth, width)` before applying `Math.min(intersectW, ...)`.

**Current Code Pattern:**
```java
// TerminalOverlayPanel.measureContent (lines 523, 550)
intersectW = Math.min(intersectW, Math.max(minWidth, width));
intersectH = Math.min(intersectH, Math.max(minHeight, height));
```

**Why It Interferes:**
1. The intersection calculation already assumes each child is at least `minWidth`/`minHeight`
2. If the child is actually larger than `maxWidth`/`maxHeight`, the intersection will be wrong
3. The parent's `maxWidth`/`maxHeight` constraints are not respected in the intersection calculation
4. This can cause the parent to be allocated more space than it should receive

**Impact:**
- **High severity** — Can cause incorrect parent sizing
- Affects `TerminalOverlayPanel` (multi-visible Z-axis stacking)
- Affects `TerminalStackPanel` (Z-axis stacking with single visible child)
- Both are used as layout containers in the scroll panel architecture

---

## Migration Plan

### Phase 1: TerminalPanel (Low Risk)

**File:** `src/main/java/io/netnotes/terminal/components/panels/TerminalPanel.java`

**Lines:** 481, 507

**Change:**
```java
// Before
contentW = Math.max(minWidth, width);

// After
contentW = clampDimension(width, true);
```

**Risk:** Low — Only affects own dimension clamping in `measureContent`

---

### Phase 2: TerminalStackPanel (High Risk)

**File:** `src/main/java/io/netnotes/terminal/components/panels/TerminalStackPanel.java`

**Lines:** 481, 507

**Change:**
```java
// Before
intersectW = Math.min(intersectW, Math.max(minWidth, width));

// After
intersectW = Math.min(intersectW, clampDimension(width, true));
```

**Risk:** Medium — Affects intersection calculation, but only within `measureContent`

**Testing Required:**
- Verify parent containers receive correct size allocations
- Test with children that exceed parent max dimensions
- Ensure scroll panel layout doesn't break

---

### Phase 3: TerminalOverlayPanel (Critical Risk)

**File:** `src/main/java/io/netnotes/terminal/components/panels/TerminalOverlayPanel.java`

**Lines:** 523, 550

**Change:**
```java
// Before
intersectW = Math.min(intersectW, Math.max(minWidth, width));
intersectH = Math.min(intersectH, Math.max(minHeight, height));

// After
intersectW = Math.min(intersectW, clampDimension(width, true));
intersectH = Math.min(intersectH, clampDimension(height, false));
```

**Risk:** High — Affects intersection calculation that determines parent size

**Testing Required:**
- **MUST** run full scroll panel integration tests
- Test with multiple visible children in different positions
- Verify overflow clipping works correctly
- Test with children that have min/max constraints

**Verification:**
```bash
# Run all layout tests
mvn test -Dtest=*Layout*Test

# Run scroll panel specific tests
mvn test -Dtest=*ScrollPanel*Test

# Run integration tests
mvn test -Dtest=*Integration*Test
```

---

### Phase 4: TerminalVStack & TerminalHStack (No Change)

**Files:** `TerminalVStack.java`, `TerminalHStack.java`

**Lines:** 262 (both)

**Decision:** DO NOT CHANGE

**Reason:** These use `Math.max()` to preserve minimum sizes during distribution scaling, not to enforce container constraints. The scaling operation distributes available space, and `Math.max()` ensures no child shrinks below its minimum.

**Current Code:**
```java
widths[i] = Math.max(min, (int)(widths[i] * scale));
```

**What It Does:**
- Child gets scaled allocation
- If scaled allocation < min, use min instead
- This is correct behavior for distribution

**What `clampDimension()` Would Do:**
```java
widths[i] = clampDimension((int)(widths[i] * scale), true);
```

This would incorrectly also clamp to `maxWidth`, breaking the distribution logic.

---

## Verification Strategy

### Pre-Migration Validation

1. **Audit all uses of Math.max for dimension clamping**
   - Confirm all instances are identified in the table above
   - Verify TerminalVStack/HStack scaling cases are excluded

2. **Create regression tests**
   - Test cases for children exceeding max dimensions
   - Test cases for children at min dimensions
   - Test cases for children at intermediate dimensions

### Post-Migration Validation

1. **Unit tests**
   ```bash
   mvn test -Dtest=TerminalPanelTest
   mvn test -Dtest=TerminalStackPanelTest
   mvn test -Dtest=TerminalOverlayPanelTest
   ```

2. **Integration tests**
   ```bash
   mvn test -Dtest=TerminalScrollPanelTest
   mvn test -Dtest=TerminalBorderPanelTest
   ```

3. **Manual testing**
   - Open scroll panel in UI
   - Verify all children render correctly
   - Test resize operations
   - Test with various child configurations

---

## Rollback Plan

If any migration causes issues:

1. **Immediate:** Revert the specific file's changes using git
2. **Investigate:** Run tests to identify failure mode
3. **Fix:** Determine if the issue is in the migration or a pre-existing bug
4. **Revert:** If migration is at fault, document the issue and skip that component

---

## Success Criteria

- [x] All Math.max dimension clamping uses identified
- [x] Interference cases documented and understood
- [x] Migration plan created with risk assessment
- [ ] TerminalPanel migrated and tested
- [ ] TerminalStackPanel migrated and tested
- [ ] TerminalOverlayPanel migrated and tested
- [ ] TerminalVStack/HStack confirmed as no-change
- [ ] All tests passing post-migration
- [ ] No regressions in scroll panel layout

---

## Notes

- `clampDimension()` is protected in `TerminalRegion` and accessible to subclasses
- The method already handles both min and max constraints correctly
- All changes are isolated to `measureContent()` methods
- No changes needed to `layoutChildren()` methods
