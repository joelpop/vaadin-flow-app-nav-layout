package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper;

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
}
