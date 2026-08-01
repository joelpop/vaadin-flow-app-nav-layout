package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.server.menu.MenuConfiguration;

import java.util.LinkedHashMap;

/**
 * Default {@link NavRenderer} for {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#SIDENAV}
 * — builds a full {@link SideNav} hierarchy into {@link NavSlots#drawer()}, using the
 * {@code SideNavItem} renderer configured via {@link AppNavLayout#setNavNodeRenderer} and the
 * nested-match setting from {@link AppNavLayout#setNavMatchNested}. Never touches
 * {@link NavSlots#headerNav()} — the tree already shows nesting inline.
 */
public class SideNavDrawerNavRenderer implements NavRenderer {

    private AppNavLayout owner;
    private SideNav sideNav;
    // The drawer slot sideNav is currently attached to — used to detect a NavStrategy
    // tear-down/rebuild (a fresh slot instance), since this renderer outlives any single
    // build/tearDown cycle. Compared by identity, not "already built once", so a rebuild (e.g. a
    // tablet rotating away from and back to SIDENAV) reconstructs rather than leaving sideNav
    // attached to a discarded, detached drawer.
    private HasComponents attachedDrawerSlot;

    public SideNavDrawerNavRenderer() {
    }

    // Package-private: only AppNavLayout (same package) can call this — a subclass in another
    // package can't, even via inheritance, since default access doesn't cross package
    // boundaries. Set once, immediately after construction, before this renderer's first
    // render() call.
    void attachOwner(AppNavLayout owner) {
        this.owner = owner;
    }

    @Override
    public NavType navType() {
        return NavType.SIDENAV;
    }

    @Override
    public void render(NavRenderContext context) {
        var drawer = context.slots().drawer();
        if (sideNav == null || attachedDrawerSlot != drawer) {
            sideNav = new SideNav();
            drawer.add(sideNav);
            attachedDrawerSlot = drawer;
        }
        populateSideNav(context.navGrouper());
    }

    private void populateSideNav(NavGrouper navGrouper) {
        sideNav.removeAll();
        var sideNavItems = new LinkedHashMap<NavNode, SideNavItem>();

        MenuConfiguration.getMenuEntries().forEach(entry -> {
            var node = navGrouper.nodeFor(entry);
            ensureAncestors(node, sideNavItems);
            var item = owner.navNodeRenderer.createComponent(node);
            sideNavItems.put(node, item);
            node.parent()
                    .ifPresentOrElse(
                            parent -> sideNavItems.get(parent).addItem(item),
                            () -> sideNav.addItem(item));
        });
    }

    private void ensureAncestors(NavNode node, LinkedHashMap<NavNode, SideNavItem> sideNavItems) {
        node.parent().ifPresent(parent -> {
            if (!sideNavItems.containsKey(parent)) {
                ensureAncestors(parent, sideNavItems);
                var item = owner.navNodeRenderer.createComponent(parent);
                sideNavItems.put(parent, item);
                parent.parent()
                        .ifPresentOrElse(
                                grandparent -> sideNavItems.get(grandparent).addItem(item),
                                () -> sideNav.addItem(item));
            }
        });
    }
}
