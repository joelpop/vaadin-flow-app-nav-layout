package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderComponent;
import org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderTitle;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * {@link NavStrategy} for {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#HEADER}:
 * a primary tab strip spanning the header, alongside brand/user content, rendered by whichever
 * {@link NavRenderer} is active for the current scenario (e.g. {@link HeaderTabsNavRenderer}),
 * plus a drill-down row beneath it for a group's children. No drawer — the shared
 * {@link com.vaadin.flow.component.applayout.DrawerToggle} is hidden while this strategy is
 * active, restored once it tears down.
 */
final class HeaderNavStrategy implements NavStrategy {

    private final AppNavLayout owner;

    private HorizontalLayout brandContainer;
    private HorizontalLayout userContainer;
    private Div tabStripSlot;
    private Div headerNavSlot;
    // Unused by this NavType (the drawer, sideRail, touchBar) — shared, never-attached
    // placeholder so NavRenderer implementations can safely call any NavSlots accessor without a
    // null check.
    private final Div inertSlot = new Div();

    HeaderNavStrategy(AppNavLayout owner) {
        this.owner = owner;
    }

    @Override
    public void build() {
        brandContainer = new HorizontalLayout();
        brandContainer.setPadding(false);
        brandContainer.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        userContainer = new HorizontalLayout();
        userContainer.setPadding(false);
        userContainer.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

        tabStripSlot = new Div();
        tabStripSlot.setWidthFull();
        // Allows the tab strip to shrink below its content width inside the flex topBar row (the
        // flex-item default min-width:auto would otherwise force topBar to overflow) — same
        // reasoning as TouchNavStrategy's own headerNavSlot.
        tabStripSlot.getStyle().set("min-width", "0");

        owner.topBar.add(brandContainer, tabStripSlot, userContainer);
        // The tab strip is what should grow to fill available space, not the brand — unlike
        // DesktopNavStrategy, which has nothing else competing for that space.
        owner.topBar.expand(tabStripSlot);
        owner.setDrawerToggleVisible(false);

        headerNavSlot = new Div();
        headerNavSlot.setWidthFull();
        owner.insertHeaderRow(headerNavSlot);

        owner.viewHeaderSlot.addClassName("view-header-slot-bordered");
    }

    @Override
    public void tearDown() {
        owner.topBar.remove(brandContainer, tabStripSlot, userContainer);
        owner.setDrawerToggleVisible(true);
        owner.removeHeaderRow(headerNavSlot);
        owner.viewHeaderSlot.removeClassName("view-header-slot-bordered");
        brandContainer = null;
        userContainer = null;
        tabStripSlot = null;
        headerNavSlot = null;
    }

    @Override
    public void placeBrandAndUserContent() {
        brandContainer.removeAll();
        owner.bufferedBranding.forEach(brandContainer::add);
        userContainer.removeAll();
        if (owner.bufferedUserMenu != null) {
            userContainer.add(owner.bufferedUserMenu);
        }
    }

    @Override
    public void populate() {
        var slots = new NavSlotsImpl(inertSlot, inertSlot, inertSlot, headerNavSlot, tabStripSlot);
        var context = new NavRenderContextImpl(
                owner.navGrouper, owner.navigationSignal.peek().getPath(), slots, owner.navPathMatcher,
                owner.navItemFilter);
        owner.activeRenderer.render(context);
    }

    @Override
    public void rebuildViewHeader(Component view) {
        var titleComponent = (view instanceof HasViewHeaderTitle h) ? h.getViewHeaderTitle() : null;
        var actionComponent = (view instanceof HasViewHeaderComponent h) ? h.getViewHeaderComponent() : null;

        var hasContent = titleComponent != null || actionComponent != null;
        owner.viewHeaderSlot.setVisible(hasContent);
        if (titleComponent != null) {
            owner.viewHeaderSlot.add(titleComponent);
        }
        if (actionComponent != null) {
            owner.viewHeaderSlot.add(actionComponent);
        }
    }
}
