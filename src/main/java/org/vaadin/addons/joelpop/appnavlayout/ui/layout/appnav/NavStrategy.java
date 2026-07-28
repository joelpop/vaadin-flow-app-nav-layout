package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import com.vaadin.flow.component.Component;

/**
 * Encapsulates the nav-type-specific behavior of {@link AppNavLayout}'s build/populate/teardown
 * lifecycle. One instance is created per active {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType}
 * and discarded on teardown; see {@link DesktopNavStrategy} and {@link TouchNavStrategy}.
 */
interface NavStrategy {

    /** Creates and attaches this strategy's nav components. */
    void build();

    /** Detaches and releases this strategy's nav components. */
    void tearDown();

    /** Places buffered brand/user content into this strategy's containers. */
    void placeBrandAndUserContent();

    /** Populates nav items from the current {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper}. */
    void populate();

    /** Rebuilds the view header slot for the given navigated-to view. */
    void rebuildViewHeader(Component view);
}
