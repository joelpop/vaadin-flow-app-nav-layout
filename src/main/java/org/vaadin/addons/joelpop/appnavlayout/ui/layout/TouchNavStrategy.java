package org.vaadin.addons.joelpop.appnavlayout.ui.layout;

import org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderComponent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * {@link NavStrategy} for {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#TOUCH} and
 * {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#RAIL}: a bottom/rail {@link TouchNavBar}
 * plus a drawer split into brand (top) and user (bottom) slots. {@code rail} selects the
 * rail-specific layout (column direction, fixed-width overlay drawer); otherwise this is the
 * ordinary phone bottom-bar layout.
 */
final class TouchNavStrategy implements NavStrategy {

    private final AppNavLayout owner;
    private final boolean rail;

    private VerticalLayout brandDrawerSlot;
    private VerticalLayout userDrawerSlot;
    private VerticalLayout drawerContent;
    private TouchSecondaryTabBar touchSecondaryTabBar;
    private TouchNavBar touchNavBar;

    TouchNavStrategy(AppNavLayout owner, boolean rail) {
        this.owner = owner;
        this.rail = rail;
    }

    @Override
    public void build() {
        touchSecondaryTabBar = new TouchSecondaryTabBar(owner.navigationSignal);
        touchSecondaryTabBar.getStyle().set("min-width", "0");
        owner.topBar.add(touchSecondaryTabBar);
        owner.topBar.expand(touchSecondaryTabBar);

        brandDrawerSlot = new VerticalLayout();
        brandDrawerSlot.setPadding(false);
        brandDrawerSlot.setSpacing(false);

        userDrawerSlot = new VerticalLayout();
        userDrawerSlot.setPadding(false);
        userDrawerSlot.setSpacing(false);

        drawerContent = new VerticalLayout();
        drawerContent.setPadding(false);
        drawerContent.setSpacing(false);
        drawerContent.setSizeFull();
        drawerContent.add(brandDrawerSlot, userDrawerSlot);
        drawerContent.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        var page = UI.getCurrent().getPage();
        var direction = rail ? FlexLayout.FlexDirection.COLUMN : FlexLayout.FlexDirection.ROW;
        touchNavBar = new TouchNavBar(owner.navigationSignal, page, direction);

        if (rail) {
            owner.getElement().setAttribute("nav-rail", "");
            owner.getStyle().set("--vaadin-app-layout-drawer-overlay", "true");
            owner.getStyle().set("--nav-rail-width", "5rem");
            owner.getStyle().set("padding-inline-start", "var(--nav-rail-width)");
            owner.setDrawerOpened(false);
        }

        // touch-optimized ensures the navbar-bottom slot is rendered by AppLayout
        owner.getStyle().set("--vaadin-app-layout-touch-optimized", "true");
        owner.addToDrawer(drawerContent);
        owner.addToNavbar(true, touchNavBar);
    }

    @Override
    public void tearDown() {
        owner.topBar.remove(touchSecondaryTabBar);
        touchNavBar.getElement().removeFromParent();
        drawerContent.getElement().removeFromParent();
        touchSecondaryTabBar = null;
        touchNavBar = null;
        brandDrawerSlot = null;
        userDrawerSlot = null;
        drawerContent = null;
        owner.getStyle().remove("--vaadin-app-layout-touch-optimized");
        if (rail) {
            owner.getElement().removeAttribute("nav-rail");
            owner.getStyle().remove("--vaadin-app-layout-drawer-overlay");
            owner.getStyle().remove("--nav-rail-width");
            owner.getStyle().remove("padding-inline-start");
        }
    }

    @Override
    public void placeBrandAndUserContent() {
        brandDrawerSlot.removeAll();
        owner.bufferedBrandContent.forEach(brandDrawerSlot::add);
        userDrawerSlot.removeAll();
        if (owner.bufferedUserMenu != null) {
            userDrawerSlot.add(owner.bufferedUserMenu);
        }
    }

    @Override
    public void populate() {
        touchNavBar.setPathMatcher(owner.navPathMatcher);
        touchSecondaryTabBar.setNavGrouper(owner.navGrouper);
        touchNavBar.setNavGrouper(owner.navGrouper);
    }

    @Override
    public void rebuildViewHeader(Component view) {
        var actionComponent = (view instanceof HasViewHeaderComponent h) ? h.getViewHeaderComponent() : null;

        owner.viewHeaderSlot.setVisible(actionComponent != null);
        if (actionComponent != null) {
            owner.viewHeaderSlot.add(actionComponent);
        }
    }
}
