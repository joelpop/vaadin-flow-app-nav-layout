package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType;
import org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderComponent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * {@link NavStrategy} for {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#TOUCH} and
 * {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#RAIL}: a bottom/rail nav slot
 * rendered by whichever {@link NavRenderer} is active for the current scenario (default:
 * {@link TouchBarNavRenderer}/{@link SideRailNavRenderer}), a shared header-nav slot for
 * drill-down content, plus a drawer split into brand (top) and user (bottom) slots.
 * {@code navType == RAIL} selects the rail-specific chrome (column layout, fixed-width overlay
 * drawer); {@code TOUCH} is the ordinary phone bottom-bar chrome.
 */
final class TouchNavStrategy implements NavStrategy {

    private final AppNavLayout owner;
    private final boolean rail;

    private VerticalLayout brandDrawerSlot;
    private VerticalLayout userDrawerSlot;
    private VerticalLayout drawerContent;
    private Div headerNavSlot;
    private Div primaryNavSlot;
    // Unused by this NavType (the drawer, and whichever of sideRail/touchBar isn't active) —
    // shared, never-attached placeholder so NavRenderer implementations can safely call any
    // NavSlots accessor without a null check.
    private final Div inertSlot = new Div();

    TouchNavStrategy(AppNavLayout owner, NavType navType) {
        this.owner = owner;
        this.rail = navType == NavType.RAIL;
    }

    @Override
    public void build() {
        headerNavSlot = new Div();
        headerNavSlot.setWidthFull();
        // Allows the header-nav content to shrink below its content width inside the flex
        // topBar row (the flex-item default min-width:auto would otherwise force topBar to
        // overflow).
        headerNavSlot.getStyle().set("min-width", "0");
        owner.topBar.add(headerNavSlot);
        owner.topBar.expand(headerNavSlot);

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

        primaryNavSlot = new Div();
        if (rail) {
            primaryNavSlot.setSizeFull();
            owner.getElement().setAttribute("nav-rail", "");
            owner.getStyle().set("--vaadin-app-layout-drawer-overlay", "true");
            owner.getStyle().set("--nav-rail-width", "5rem");
            owner.getStyle().set("padding-inline-start", "var(--nav-rail-width)");
            owner.setDrawerOpened(false);
        }
        else {
            primaryNavSlot.setWidthFull();
        }

        // touch-optimized ensures the navbar-bottom slot is rendered by AppLayout. This is an
        // AppLayout-internal custom property, not public API — verify it still exists and still
        // means this against the AppLayout changelog when upgrading Vaadin.
        owner.getStyle().set("--vaadin-app-layout-touch-optimized", "true");
        owner.addToDrawer(drawerContent);
        owner.addToNavbar(true, primaryNavSlot);
    }

    @Override
    public void tearDown() {
        headerNavSlot.getElement().removeFromParent();
        primaryNavSlot.getElement().removeFromParent();
        drawerContent.getElement().removeFromParent();
        headerNavSlot = null;
        primaryNavSlot = null;
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
        owner.bufferedBranding.forEach(brandDrawerSlot::add);
        userDrawerSlot.removeAll();
        if (owner.bufferedUserMenu != null) {
            userDrawerSlot.add(owner.bufferedUserMenu);
        }
    }

    @Override
    public void populate() {
        var slots = rail
                ? new NavSlotsImpl(inertSlot, primaryNavSlot, inertSlot, headerNavSlot)
                : new NavSlotsImpl(inertSlot, inertSlot, primaryNavSlot, headerNavSlot);
        var context = new NavRenderContextImpl(
                owner.navGrouper, owner.navigationSignal.peek().getPath(), slots, owner.navPathMatcher);
        owner.activeRenderer.render(context);
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
