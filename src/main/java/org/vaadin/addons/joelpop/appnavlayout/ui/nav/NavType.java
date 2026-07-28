package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

/**
 * The navigation style to render for this session, selected by a {@link NavSelector}
 * from the detected {@link DeviceType} and {@link Orientation}.
 */
public enum NavType {
    /** Touch-optimised bottom bar and secondary tab bar. */
    TOUCH,
    /** Permanent left-strip icon rail for portrait tablet. */
    RAIL,
    /** Drawer-based {@code SideNav}. */
    SIDENAV
}
