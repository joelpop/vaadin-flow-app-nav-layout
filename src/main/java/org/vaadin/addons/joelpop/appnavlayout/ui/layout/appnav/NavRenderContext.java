package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper;

import java.util.function.BiPredicate;

/**
 * Everything a {@link NavRenderer} needs to build or update its content for the current
 * nav state.
 */
public interface NavRenderContext {

    /** The current nav grouping strategy; call {@link NavGrouper#nodeFor} for each entry from
     *  {@code com.vaadin.flow.server.menu.MenuConfiguration#getMenuEntries()}. */
    NavGrouper navGrouper();

    /** The path of the currently active navigation, with any leading {@code /} stripped. */
    String currentPath();

    /** The full set of named locations available to render into. */
    NavSlots slots();

    /**
     * The configured {@link AppNavLayout#setNavPathMatcher} predicate for active-item matching
     * (default {@code String::equals}). A {@link NavRenderer} that highlights an active item by
     * comparing paths itself — rather than delegating to something that already respects it,
     * like Vaadin's own router matching — should test against this rather than hardcoding its
     * own comparison, or {@code setNavPathMatcher} silently won't affect it.
     */
    BiPredicate<String, String> navPathMatcher();
}
