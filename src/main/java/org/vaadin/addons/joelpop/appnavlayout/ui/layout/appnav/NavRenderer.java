package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

/**
 * Builds and updates the nav-item content for one of {@link AppNavLayout}'s five
 * device/orientation scenarios (desktop, tablet portrait, tablet landscape, phone portrait,
 * phone landscape), placing it into whichever of {@link AppNavLayout}'s named locations
 * (see {@link NavSlots}) is appropriate. Register an implementation via
 * {@link AppNavLayout#setDesktopNavRenderer}, {@link AppNavLayout#setTabletPortraitNavRenderer},
 * {@link AppNavLayout#setTabletLandscapeNavRenderer} (or the {@link AppNavLayout#setTabletNavRenderer}
 * convenience setter), {@link AppNavLayout#setPhonePortraitNavRenderer}, or
 * {@link AppNavLayout#setPhoneLandscapeNavRenderer} (or {@link AppNavLayout#setPhoneNavRenderer})
 * to replace the default for that scenario.
 *
 * <p>Deliberately decoupled from nav organization: a renderer receives an already-configured
 * {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper} and never influences or
 * queries how the tree is grouped.
 */
public interface NavRenderer {

    /**
     * Builds or updates this renderer's content for the current nav state. Called once when
     * this location becomes active, again on every completed navigation, and again whenever nav
     * configuration changes (grouper swap, path matcher change). The implementation decides
     * which slot(s) in {@code context.slots()} to populate and how — including any overflow or
     * drill-down scaffolding it needs (a "More…" popover, a chevron, a swipeable container, etc).
     */
    void render(NavRenderContext context);
}
