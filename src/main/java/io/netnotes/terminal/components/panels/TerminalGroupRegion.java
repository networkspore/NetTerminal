package io.netnotes.terminal.components.panels;

import io.netnotes.terminal.TerminalRenderable;
import io.netnotes.terminal.components.TerminalRegion;
import io.netnotes.terminal.layout.TerminalLayoutCallback;
import io.netnotes.terminal.layout.TerminalLayoutGroupCallback;
import io.netnotes.terminal.layout.TerminalSizeable;

/**
 * TerminalGroupRegion — base class for any TerminalRegion that owns a
 * single layout group and wires every child it accepts into that group.
 *
 * LIFECYCLE CONTRACT
 * ──────────────────
 * 1. Construction  : the base constructor calls initGroup() as part of shared
 *                    setup. createLayoutCallback() should therefore return a
 *                    constructor-safe callback, typically a method reference
 *                    whose field access is deferred until layout time.
 *
 * 2. Detached      : clearLayoutManager() fires onLayoutManagerCleared(), which
 *                    is intentionally a no-op here. childGroups is preserved so
 *                    that collectChildGroups() can re-register the group when
 *                    this renderable is next attached. The layout manager's
 *                    cleanupNode() already removed the group from groupRegistry.
 *
 * 3. Re-attached   : registerRenderableInternal() calls collectChildGroups(),
 *                    which reads childGroups and reconstructs the group in
 *                    groupRegistry automatically. No action required here.
 *
 * 4. Destroyed     : onDestroying() calls destroyLayoutGroup(), which removes
 *                    the entry from childGroups AND (if still attached) from the
 *                    layout manager's groupRegistry. destroyInternal() then
 *                    calls childGroups.clear() as a safety net.
 *
 * CHILD WIRING
 * ────────────
 * addChild() is overridden to call addToLayoutGroup(child, layoutGroupId) for
 * every child. The add + group-enrollment sequence is batched so the layout
 * dirty mark issued by Renderable.addChild() sees the final group membership.
 * Subclasses that need different membership rules should override
 * onChildAddedToGroup() to be a no-op or route to a different group.
 */
public abstract class TerminalGroupRegion extends TerminalRegion {

    protected final String layoutGroupId;
    protected final String layoutCallbackId;
    protected TerminalLayoutGroupCallback layoutCallback = null;

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    /**
     * @param name        component name passed to TerminalRegion
     * @param groupPrefix short prefix for the group ID, e.g. "panel", "hstack"
     */
    protected TerminalGroupRegion(String name, String groupPrefix) {
        super(name);
        this.layoutGroupId = groupPrefix + "-" + name;
        this.layoutCallbackId = layoutGroupId + "-callback";
        initGroup();
    }

    /**
     * Registers the layout group and its callback.
     * Called by the base constructor as part of shared setup.
     */
    protected final void initGroup() {
        this.layoutCallback = createLayoutCallback();
        setGroupLayoutCallback(layoutGroupId, layoutCallback);
    }

    /**
     * Factory: return the layout group callback for this component.
     * Typically a method reference: {@code this::layoutChildren}.
     */
    protected abstract TerminalLayoutGroupCallback createLayoutCallback();

    // =========================================================================
    // LIFECYCLE — detach vs destroy
    // =========================================================================

    /**
     * Called when the layout manager reference is cleared (renderable detached,
     * but not destroyed). We deliberately do nothing here: childGroups must
     * survive so the group can be reconstructed when this renderable is
     * re-attached. The layout manager's cleanupNode() has already removed the
     * group from groupRegistry.
     *
     * Subclasses that need to react to detachment (e.g. cancel timers) should
     * override onDetachedFromLayout() instead.
     */
    @Override
    protected final void onLayoutManagerCleared() {
        onDetachedFromLayout();
    }

     /**
     * Returns true if the panel is allowed to manage this child's hidden state.
     * A child with isHiddenManaged()=false controls its own visibility; the
     * panel assigns coordinates but never forces hide/show on it.
     */
    protected boolean shouldManageHidden(TerminalRenderable child) {
        if (child instanceof TerminalSizeable s) return s.isHiddenManaged();
        return true;
    }



    /**
     * Extension point for subclasses that need to react to layout-manager
     * detachment without risking accidental group destruction.
     * The default implementation does nothing.
     */
    protected void onDetachedFromLayout() { }

    /**
     * Permanent teardown: remove the group from both childGroups and (if still
     * attached) from the layout manager's groupRegistry.
     * Subclasses that need extra teardown should call super.onDestroying() first.
     */
    @Override
    protected void onInternalDestroying() {
        removeLayoutGroup(layoutGroupId);
        layoutCallback = null;
    }

    // =========================================================================
    // CHILD WIRING
    // =========================================================================

    @Override
    public void addChild(TerminalRenderable child) {
        addChild(child, null);
    }

    @Override
    public void addChild(TerminalRenderable child, TerminalLayoutCallback cb) {
        if (!getUIExecutor().isCurrentThread()) {
            getUIExecutor().runLater(() -> addChild(child, cb));
            return;
        }
        super.addChild(child, cb);
        onChildAddedToGroup(child);

    }

    /**
     * Called immediately after a child is added to this renderable's child list.
     * Default behaviour: enroll the child in this component's layout group.
     *
     * Override to suppress automatic enrollment (e.g. TerminalBorderPanel, which
     * manages group membership explicitly for its internal region stacks) or to
     * route to a different group.
     */
    protected void onChildAddedToGroup(TerminalRenderable child) {
        addToLayoutGroup(child, layoutGroupId);
    }

    // =========================================================================
    // ACCESSORS
    // =========================================================================

    public String getLayoutGroupId()                        { return layoutGroupId;   }
    public String getLayoutCallbackId()                     { return layoutCallbackId; }
    public TerminalLayoutGroupCallback getLayoutCallback()  { return layoutCallback;  }

}
