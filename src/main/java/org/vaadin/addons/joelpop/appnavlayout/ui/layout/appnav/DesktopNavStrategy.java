package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode;
import org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderComponent;
import org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderTitle;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.LinkedHashMap;

/** {@link NavStrategy} for {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#SIDENAV}: header brand/user content plus a drawer {@link SideNav}. */
final class DesktopNavStrategy implements NavStrategy {

    private final AppNavLayout owner;

    private HorizontalLayout brandContainer;
    private HorizontalLayout userContainer;
    private SideNav sideNav;

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

        sideNav = new SideNav();
        owner.addToDrawer(sideNav);
    }

    @Override
    public void tearDown() {
        owner.topBar.remove(brandContainer, userContainer);
        sideNav.getElement().removeFromParent();
        owner.viewHeaderSlot.removeClassNames(LumoUtility.Border.BOTTOM, LumoUtility.BorderColor.CONTRAST_10);
        brandContainer = null;
        userContainer = null;
        sideNav = null;
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
        populateSideNav();
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

    private void populateSideNav() {
        sideNav.removeAll();
        var sideNavItems = new LinkedHashMap<NavNode, SideNavItem>();

        MenuConfiguration.getMenuEntries().forEach(entry -> {
            var node = owner.navGrouper.nodeFor(entry);
            ensureAncestors(node, sideNavItems, owner.navNodeRenderer);
            var item = owner.navNodeRenderer.createComponent(node);
            sideNavItems.put(node, item);
            node.parent()
                    .ifPresentOrElse(
                            parent -> sideNavItems.get(parent).addItem(item),
                            () -> sideNav.addItem(item));
        });
    }

    private void ensureAncestors(NavNode node, LinkedHashMap<NavNode, SideNavItem> sideNavItems,
                                  ComponentRenderer<SideNavItem, NavNode> renderer) {
        node.parent().ifPresent(parent -> {
            if (!sideNavItems.containsKey(parent)) {
                ensureAncestors(parent, sideNavItems, renderer);
                var item = renderer.createComponent(parent);
                sideNavItems.put(parent, item);
                parent.parent()
                        .ifPresentOrElse(
                                grandparent -> sideNavItems.get(grandparent).addItem(item),
                                () -> sideNav.addItem(item));
            }
        });
    }
}
