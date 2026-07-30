package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import com.vaadin.flow.component.HasComponents;

/**
 * The full set of named locations {@link AppNavLayout} has for nav-item content. Every
 * {@link NavRenderer} sees this same shape regardless of which location it renders for; an
 * implementation is free to populate whichever slots are relevant to it and ignore the rest.
 */
public interface NavSlots {

    /** The desktop/landscape-tablet nav location, inside the drawer. */
    HasComponents drawer();

    /** The portrait-tablet nav location, the left-edge rail. */
    HasComponents sideRail();

    /** The phone nav location, the bottom bar. */
    HasComponents touchBar();

    /**
     * The shared drill-down location for nested routes, shown alongside {@link #sideRail()} or
     * {@link #touchBar()}. Distinct from the per-view header slot used by
     * {@link org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderTitle}/
     * {@link org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderComponent}.
     */
    HasComponents headerNav();
}
