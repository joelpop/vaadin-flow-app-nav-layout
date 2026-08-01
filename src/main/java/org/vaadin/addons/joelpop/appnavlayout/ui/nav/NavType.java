package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

/**
 * The chrome/{@code NavStrategy} built for a device/orientation scenario, declared by the
 * {@code navType()} of whichever
 * {@link org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav.NavRenderer} is active for that
 * scenario — not chosen independently.
 */
public enum NavType {
    /** Touch-optimised bottom bar and secondary tab bar. */
    TOUCH,
    /** Permanent left-strip icon rail. */
    RAIL,
    /** Drawer-based {@code SideNav}. */
    SIDENAV
}
