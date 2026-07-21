package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

import com.vaadin.flow.server.menu.MenuEntry;

/**
 * Strategy for mapping {@link MenuEntry} items to their position in the navigation
 * tree. The returned {@link NavNode} encodes both the leaf and its full ancestor
 * chain via {@link NavNode#parent()}.
 *
 * <p>The default implementation is {@link PathPrefixNavGrouper}, which derives the
 * hierarchy from route path segments.
 *
 * <p><b>Contract for implementors:</b>
 * <ul>
 *   <li>{@link #nodeFor} must be <em>idempotent</em>: calling it multiple times with
 *       the same {@link MenuEntry} must return a node that is equal to the one
 *       returned on the first call. The framework calls {@code nodeFor} during both
 *       the initial nav build and on every subsequent navigation event (to resolve
 *       the active section for highlighting), so implementations that mutate state
 *       on the first call will produce incorrect results.
 *   <li>The order in which entries are passed to {@code nodeFor} is not guaranteed.
 *       Implementations that need a specific processing order (e.g. to register
 *       explicit group roots before path-based siblings) must handle that ordering
 *       internally, for example by lazily performing a full-population pass on the
 *       first call using {@link com.vaadin.flow.server.menu.MenuConfiguration#getMenuEntries()}.
 * </ul>
 */
@FunctionalInterface
public interface NavGrouper {

    /**
     * Returns the {@link NavNode} representing {@code entry} in the navigation tree.
     * Set {@code parent()} on the returned node to encode group nesting; leave it
     * absent for top-level items.
     *
     * <p>Must be idempotent — see the class-level contract above.
     */
    NavNode nodeFor(MenuEntry entry);

    /** Clears any cached state so the next {@link #nodeFor} calls start fresh. */
    default void reset() {}
}
