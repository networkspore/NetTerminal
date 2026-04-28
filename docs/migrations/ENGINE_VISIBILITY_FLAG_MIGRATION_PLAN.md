# Engine Migration Plan: Source-Aware Visibility Flags

## Document Status
- Owner: Rendering/Layout team
- Last updated: April 23, 2026
- Scope: `io.netnotes.engine.ui.renderer` visibility model and layout integration
- Companion doc: `docs/migrations/NETTERMINAL_VISIBILITY_FLAG_MIGRATION_PLAN.md`

## Why This Migration Exists
Current behavior mixes these concerns:
- User intent (`hide()/show()`, explicit API visibility)
- Container/layout-driven visibility (layout callbacks setting `hidden(true)`)
- Forced/system invisibility (engine-level safety or blocking states)

That coupling creates unstable measurement behavior in prepass/layout-pass boundaries and makes ownership unclear.

## Goals
1. Introduce source-aware invisibility flags in engine renderables.
2. Preserve backward compatibility for `hide()/show()/isHidden()` while migrating call sites.
3. Make measurement rules deterministic based on prior committed visibility source.
4. Separate "authority to manage child visibility" from "current visibility state".

## Non-Goals
1. Changing terminal text style conceal (`TextStyle.hidden`) semantics.
2. Reworking unrelated focus/state machine bits.
3. Removing compatibility APIs in the first migration release.

## Target Model

### New Flag Bits (on base `Renderable`)
- `VIS_USER_INVISIBLE`
- `VIS_MANAGED_INVISIBLE`
- `VIS_FORCED_INVISIBLE`

### Derived State
- `isInvisibleDesired()` => any invisibility bit set
- `isEffectivelyInvisible()` => self invisible desired OR ancestor effectively invisible

### Authority (Not a Bit)
Add explicit policy flag:
- `isManagedVisibilityAllowed()`
- `setManagedVisibilityAllowed(boolean)`

This replaces the old pattern where sizing interfaces carry visibility-control policy.

## API Contract (Engine)

### Additions
- `int getInvisibleFlags()`
- `boolean hasInvisibleFlag(int flag)`
- `void setInvisibleFlag(int flag, boolean enabled)`
- `void clearInvisibleFlags(int mask)`
- `boolean isManagedVisibilityAllowed()`
- `void setManagedVisibilityAllowed(boolean allowed)`

### Backward-Compatible Mappings
- `hide()` => `setInvisibleFlag(VIS_USER_INVISIBLE, true)`
- `show()` => `setInvisibleFlag(VIS_USER_INVISIBLE, false)`
- `isHidden()` => `hasInvisibleFlag(VIS_USER_INVISIBLE)`

### Layout Data Mapping
Existing `LayoutData.hidden(true/false)` must map to `VIS_MANAGED_INVISIBLE` only.
It must never mutate user intent bits.

## Measurement Rules (Engine Contract)
These rules are required for stable prepass behavior:
1. Skip content measurement only when `VIS_USER_INVISIBLE` or `VIS_FORCED_INVISIBLE` is active.
2. If only `VIS_MANAGED_INVISIBLE` is active, do not hard-skip measurement:
   - Use prior committed measured bounds when available.
   - Fall back to min-size floor if no prior measurement exists.
3. If no invisibility bits are active, measure normally.

## Implementation Phases

## Phase 0: Prep and Safeguards
Checklist:
- [ ] Add a temporary feature toggle if engine release risk is high.
- [ ] Add logging for visibility source changes in debug mode only.
- [ ] Add test scaffolding for flag transitions.

Deliverable:
- Baseline tests that lock current behavior.

## Phase 1: Introduce Flag Infrastructure
Checklist:
- [ ] Add invisibility bit constants and storage.
- [ ] Add new flag APIs.
- [ ] Keep old APIs and map them to user bit.
- [ ] Add managed-visibility authority policy on renderable.

Deliverable:
- Engine compiles with old call sites unchanged.

## Phase 2: Wire Layout Data to Managed Bit
Checklist:
- [ ] Ensure `hidden(true/false)` in layout-apply path toggles `VIS_MANAGED_INVISIBLE`.
- [ ] Verify this path no longer touches user bit.
- [ ] Keep compatibility semantics for external callers of `hide()/show()`.

Deliverable:
- Managed and user visibility can diverge without corruption.

## Phase 3: Measurement Behavior Update
Checklist:
- [ ] Implement the measurement skip/fallback rules from this document.
- [ ] Add regression tests for managed-hidden prepass and next-pass re-show.
- [ ] Ensure no oscillation/collapse in fit-content parent during managed visibility flips.

Deliverable:
- Deterministic prepass behavior independent of group-callback timing.

## Phase 4: Deprecation and Cleanup
Checklist:
- [ ] Mark legacy visibility-policy hooks as deprecated if they live outside renderable.
- [ ] Document migration path for downstream projects.
- [ ] Schedule removal date/version for legacy shims.

Deliverable:
- Migration-ready engine release notes.

## Test Matrix (Engine)

### Unit Tests
- Flag toggling idempotence.
- User and managed bits independent.
- Forced bit dominance behavior.
- Effective invisibility propagation through parent/child.

### Layout Integration Tests
- Managed hidden node remains measurable via fallback in prepass.
- User hidden node excluded from content measurement.
- Managed unhide after one pass restores layout without collapse jump.

### Compatibility Tests
- Existing calls to `hide()/show()/isHidden()` still behave as user-intent APIs.
- Existing layout callbacks using `hidden(true/false)` still function.

## Acceptance Criteria
1. No regressions in existing engine visibility/focus tests.
2. New tests verify bit-source separation and measurement fallback behavior.
3. Downstream project (`NetTerminal`) can remove `isHiddenManaged` dependency.
4. No forced API break in first migration release.

## Rollback Plan
If regressions are detected:
1. Keep new flags but route layout-managed updates back to legacy hidden path behind a toggle.
2. Disable measurement fallback rule and revert to current skip behavior.
3. Keep compatibility wrappers intact while rolling forward fixes.

## Handoff Notes Template
When pausing work, append this section at bottom before handoff:

```md
## Handoff Snapshot (YYYY-MM-DD)
- Completed phases:
- In-progress phase:
- Last commit hash:
- Failing tests:
- Next exact file/function to edit:
- Risks discovered:
```
