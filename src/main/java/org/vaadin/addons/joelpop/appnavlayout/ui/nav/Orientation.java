package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

/**
 * Viewport orientation detected from window inner dimensions at connection time.
 *
 * <p>Defaults to {@link #LANDSCAPE} when {@code ExtendedClientDetails} is {@code null}
 * (before the async client round-trip completes).
 */
public enum Orientation {
    PORTRAIT,
    LANDSCAPE
}
