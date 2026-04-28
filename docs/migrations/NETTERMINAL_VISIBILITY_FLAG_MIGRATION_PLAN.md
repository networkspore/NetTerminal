# NetTerminal Migration Plan: Adopt Engine Source-Aware Visibility Flags

## Document Status
- Owner: NetTerminal maintainers
- Last updated: April 23, 2026
- Scope: `src/main/java/io/netnotes/terminal/**` and related tests
- Prerequisite: Engine release implementing `ENGINE_VISIBILITY_FLAG_MIGRATION_PLAN.md`
- Companion doc: `docs/migrations/ENGINE_VISIBILITY_FLAG_MIGRATION_PLAN.md`

## Purpose
Migrate NetTerminal away from `TerminalSizeable.isHiddenManaged()` and into engine-owned visibility authority + source-aware invisibility bits.

This document is intentionally concrete so migration can continue after context loss.

## Current NetTerminal Pain Points
1. Visibility authority is defined on sizing interface:
   - `src/main/java/io/netnotes/terminal/layout/TerminalSizeable.java`
2. Visibility and measurement expectations are coupled across prepass/layout-pass.
3. Containers mix two responsibilities:
   - deciding whether child is in flow
   - mutating child hidden state

## Required Engine APIs
Before starting NetTerminal migration, verify these exist on base renderable:
- `isManagedVisibilityAllowed()`
- `setManagedVisibilityAllowed(boolean)`
- `setInvisibleFlag(flag, enabled)`
- `hasInvisibleFlag(flag)`
- user/managed/forced invisibility constants
- compatibility `hide()/show()/isHidden()` still available

If any of the above is missing, stop and finish engine migration first.

## Migration Strategy

## Phase A: Compatibility Bridge (No Behavior Change)
Goal: compile against new engine while keeping old behavior.

Checklist:
- [ ] Keep `TerminalSizeable.isHiddenManaged()` temporarily.
- [ ] In `TerminalRegion`, map `isHiddenManaged` backing field to renderable authority policy.
- [ ] In `TerminalProgressBar`, do the same mapping.
- [ ] Add `@Deprecated` markers to old NetTerminal-only hidden-managed accessors.

Primary files:
- `src/main/java/io/netnotes/terminal/layout/TerminalSizeable.java`
- `src/main/java/io/netnotes/terminal/components/TerminalRegion.java`
- `src/main/java/io/netnotes/terminal/components/TerminalProgressBar.java`

Acceptance:
- Project compiles with no functional changes.

## Phase B: Container Authority Migration
Goal: replace `isHiddenManaged` checks with engine policy checks.

Checklist:
- [ ] Update `shouldManageHidden(...)` helpers to call child renderable authority API.
- [ ] Remove `TerminalSizeable` dependency from visibility authority decisions.
- [ ] Keep layout callbacks writing `builder.hidden(...)` for managed visibility output.

Primary files:
- `src/main/java/io/netnotes/terminal/components/panels/TerminalGroupRegion.java`
- `src/main/java/io/netnotes/terminal/components/panels/TerminalAbstractStack.java`
- `src/main/java/io/netnotes/terminal/components/panels/TerminalPanel.java`
- `src/main/java/io/netnotes/terminal/components/panels/TerminalHStack.java`
- `src/main/java/io/netnotes/terminal/components/panels/TerminalVStack.java`
- `src/main/java/io/netnotes/terminal/components/panels/TerminalOverlayPanel.java`
- `src/main/java/io/netnotes/terminal/components/panels/TerminalBorderPanel.java`

Acceptance:
- No compile-time references to `isHiddenManaged()` in panel authority logic.

## Phase C: Measurement Consistency Update (NetTerminal Side)
Goal: align measurement helpers with source-aware visibility behavior.

Checklist:
- [ ] Audit all `isHiddenDesired`-based measurement skips in `TerminalRegion` helpers.
- [ ] Change measurement inclusion checks to engine visibility source contract:
  - skip for user/forced invisibility
  - compensate/fallback for managed invisibility
- [ ] Preserve existing min-size fallback behavior for parent-dependent children.

Primary files:
- `src/main/java/io/netnotes/terminal/components/TerminalRegion.java`
- `src/main/java/io/netnotes/terminal/components/install/TerminalInstallWizard.java` (earmarked `isMeasuredVisible` concern)
- Any custom `measureContent(...)` overrides that check hidden state.

Acceptance:
- Managed visibility flips do not collapse fit-content parents unexpectedly.

## Phase D: Remove Legacy API
Goal: finish separation cleanly.

Checklist:
- [ ] Remove `isHiddenManaged()` from `TerminalSizeable`.
- [ ] Remove old hidden-managed fields from components.
- [ ] Replace all call sites with engine authority API.
- [ ] Update docs (`COMPONENT_STANDARDS.claude.md`) to new model.

Primary files:
- `src/main/java/io/netnotes/terminal/layout/TerminalSizeable.java`
- `src/main/java/io/netnotes/terminal/components/TerminalRegion.java`
- `src/main/java/io/netnotes/terminal/components/TerminalProgressBar.java`
- `docs/COMPONENT_STANDARDS.claude.md`

Acceptance:
- `rg -n "isHiddenManaged|setIsHiddenManaged" src/main/java` returns no migration-relevant usage.

## Test Plan

## Mandatory Test Runs
- `./mvnw -q -Dtest=TerminalInstallWizard* test`
- `./mvnw -q -Dtest=TerminalBorderPanelLayoutTest test`
- `./mvnw -q -Dtest=TerminalBorderPanelWithScrollTest test`
- `./mvnw -q -Dtest=TerminalDamageIntegrationTest test`

## Additional Search Audits
Run after each phase:
- `rg -n "isHiddenManaged|setIsHiddenManaged" src/main/java src/test/java`
- `rg -n "renderableIsExcluded\(|shouldManageHidden\(" src/main/java/io/netnotes/terminal/components/panels`
- `rg -n "isHiddenDesired\(" src/main/java/io/netnotes/terminal/components`

## Regression Scenarios to Verify
1. Child hidden by container overflow appears again when space returns.
2. User-hidden child remains hidden even when container wants it visible.
3. Overlay visible set transitions do not leak wrong visibility states.
4. Install wizard fit-content measurement remains stable during managed show/hide sections.

## Work Order (Recommended)
1. Phase A bridge.
2. Phase B container authority.
3. Phase C measurement consistency.
4. Phase D cleanup + docs.

This order minimizes blast radius and keeps bisects clean.

## Risks and Mitigations
1. Risk: accidental semantic change to user hide/show.
- Mitigation: keep compatibility wrappers until final cleanup.

2. Risk: managed-hidden prepass jitter in fit-content layouts.
- Mitigation: add targeted layout tests before removing legacy APIs.

3. Risk: panel-specific behavior regressions.
- Mitigation: run panel-focused tests at each phase boundary.

## Definition of Done
1. No remaining migration dependencies on `TerminalSizeable.isHiddenManaged`.
2. All visibility authority decisions use engine renderable policy API.
3. Measurement path follows source-aware visibility semantics.
4. Existing layout/damage/wizard tests pass.
5. Docs updated to describe the new source-aware model.

## Handoff Snapshot Template
Append this section during long-running migration pauses:

```md
## Handoff Snapshot (YYYY-MM-DD)
- Current phase:
- Completed checkboxes:
- Branch:
- Last commit:
- Failing tests and error summary:
- Files currently edited:
- Next exact command to run:
- Next exact method/function to edit:
```
