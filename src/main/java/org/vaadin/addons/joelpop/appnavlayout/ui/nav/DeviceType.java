package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

/**
 * Coarse device category detected from touch capability and physical screen size.
 *
 * <p>Detection depends on {@code ExtendedClientDetails}, which is {@code null} until an
 * async client round-trip completes; {@link #DESKTOP} is the default both during that
 * window and for any non-touch device.
 */
public enum DeviceType {
    PHONE,
    TABLET,
    DESKTOP
}
