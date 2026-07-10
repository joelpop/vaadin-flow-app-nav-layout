package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

/**
 * Strategy that selects the navigation style for a session based on the
 * detected {@link DeviceType} and {@link Orientation}.
 *
 * <p>The selector is invoked once at layout construction time, and again
 * dynamically on touch devices whenever the viewport size changes (rotation,
 * split-screen resize). The returned {@link NavType} determines whether touch
 * components or a drawer-based {@link com.vaadin.flow.component.sidenav.SideNav}
 * are rendered.
 *
 * <p>The default selector ({@link #defaultSelector()}) uses {@link NavType#SIDENAV}
 * for desktop and landscape-mode tablets, and {@link NavType#TOUCH} otherwise.
 */
@FunctionalInterface
public interface NavSelector {

    /**
     * Selects the nav type for this session.
     *
     * @param deviceType the coarse device category
     * @param orientation the current viewport orientation
     * @return the nav type to render; never {@code null}
     */
    NavType select(DeviceType deviceType, Orientation orientation);

    /**
     * Returns the default selector: {@link NavType#SIDENAV} for {@link DeviceType#DESKTOP}
     * and landscape {@link DeviceType#TABLET}; {@link NavType#RAIL} for portrait
     * {@link DeviceType#TABLET}; {@link NavType#TOUCH} for phones.
     */
    static NavSelector defaultSelector() {
        return (device, orientation) -> {
            if (device == DeviceType.DESKTOP) {
                return NavType.SIDENAV;
            }
            if (device == DeviceType.TABLET) {
                return orientation == Orientation.LANDSCAPE ? NavType.SIDENAV : NavType.RAIL;
            }
            return NavType.TOUCH;
        };
    }
}
