package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

import com.vaadin.flow.server.menu.MenuEntry;

/**
 * Strategy for mapping {@link MenuEntry} items to their position in the navigation
 * tree. Called once per entry; the returned {@link NavNode} encodes both the leaf
 * and its full ancestor chain via {@link NavNode#parent()}.
 *
 * <p>The default implementation is {@link PathPrefixNavGrouper}, which derives the
 * hierarchy from route path segments.
 */
@FunctionalInterface
public interface NavGrouper {

    /**
     * Returns the {@link NavNode} representing {@code entry} in the navigation tree.
     * Set {@code parent()} on the returned node to encode group nesting; leave it
     * absent for top-level items.
     */
    NavNode nodeFor(MenuEntry entry);

    /** Clears any cached state so the next {@link #nodeFor} calls start fresh. */
    default void reset() {}
}
