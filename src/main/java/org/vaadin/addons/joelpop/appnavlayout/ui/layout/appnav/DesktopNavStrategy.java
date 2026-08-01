package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderComponent;
import org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderTitle;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.theme.lumo.LumoUtility;

/** {@link NavStrategy} for {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#SIDENAV}:
 *  header brand/user content, plus a drawer nav slot rendered by whichever {@link NavRenderer}
 *  is active for the current scenario (default: {@link SideNavDrawerNavRenderer}, building a
 *  {@link SideNav}). */
final class DesktopNavStrategy implements NavStrategy {

    private final AppNavLayout owner;

    private HorizontalLayout brandContainer;
    private HorizontalLayout userContainer;
    private VerticalLayout drawerNavSlot;
    // Unused by this NavType — shared, never-attached placeholder so NavRenderer implementations
    // can safely call any NavSlots accessor without a null check.
    private final Div inertSlot = new Div();

    DesktopNavStrategy(AppNavLayout owner) {
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

        owner.topBar.add(brandContainer, userContainer);
        owner.topBar.expand(brandContainer);

        owner.viewHeaderSlot.addClassNames(LumoUtility.Border.BOTTOM, LumoUtility.BorderColor.CONTRAST_10);

        drawerNavSlot = new VerticalLayout();
        drawerNavSlot.setPadding(false);
        drawerNavSlot.setSpacing(false);
        drawerNavSlot.setSizeFull();
        owner.addToDrawer(drawerNavSlot);
    }

    @Override
    public void tearDown() {
        owner.topBar.remove(brandContainer, userContainer);
        drawerNavSlot.getElement().removeFromParent();
        owner.viewHeaderSlot.removeClassNames(LumoUtility.Border.BOTTOM, LumoUtility.BorderColor.CONTRAST_10);
        brandContainer = null;
        userContainer = null;
        drawerNavSlot = null;
    }

    @Override
    public void placeBrandAndUserContent() {
        brandContainer.removeAll();
        owner.bufferedBrandContent.forEach(brandContainer::add);
        userContainer.removeAll();
        if (owner.bufferedUserMenu != null) {
            userContainer.add(owner.bufferedUserMenu);
        }
    }

    @Override
    public void populate() {
        var slots = new NavSlotsImpl(drawerNavSlot, inertSlot, inertSlot, inertSlot);
        var context = new NavRenderContextImpl(owner.navGrouper, owner.navigationSignal.peek().getPath(), slots);
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
