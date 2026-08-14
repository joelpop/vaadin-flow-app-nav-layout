package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.orderedlayout.FlexLayout;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * An alternative {@link NavRenderer} for {@link NavType#RAIL} — every rail item that represents a
 * nav group gets a right-pointing chevron instead of navigating to a representative child; tapping
 * it pops up a flyout listing that group's own children, recursively, to whatever depth the route
 * hierarchy actually goes. Never touches {@link NavSlots#headerNav()} — unlike the default
 * {@link SideRailNavRenderer}, this renderer never needs the shared drill-down bar, since every
 * level of the tree is reachable directly from the rail itself.
 */
@CssImport("./flyout-rail-nav.css")
public class FlyoutRailNavRenderer implements NavRenderer {

    private HasComponents attachedSlot;
    private FlexLayout rootColumn;

    @Override
    public NavType navType() {
        return NavType.RAIL;
    }

    // The default 5rem, plus room for the chevron zone, added on top rather than carved out of
    // it — content should get exactly what the default rail's own items get, not less. The
    // addition mirrors flyout-rail-nav.css's own chevron box exactly (16px box + its own
    // padding-inline on both sides + the row's own gap before it), read from the same tokens that
    // file uses so it can't drift out of sync with a theme's actual values the way a flat guessed
    // width (e.g. "7rem") would. Kept in sync by hand with that file's own numbers, the same way
    // ScrollingTouchNavRenderer's CHEVRON_ZONE_PX is with its own CSS.
    @Override
    public String railWidth() {
        return "calc(5rem + 16px + 2 * var(--vaadin-padding-s) + var(--vaadin-gap-s))";
    }

    @Override
    public void render(NavRenderContext context) {
        var slot = context.slots().sideRail();
        if (rootColumn == null || attachedSlot != slot) {
            rootColumn = new FlexLayout();
            rootColumn.addClassName("nav-bar");
            rootColumn.addClassName("flyout-rail-column");
            rootColumn.setFlexDirection(FlexLayout.FlexDirection.COLUMN);
            rootColumn.setSizeFull();
            slot.add(rootColumn);
            attachedSlot = slot;
        }
        populate(context);
    }

    // Rebuilt wholesale on every render(), same "rebuild, don't patch" approach every renderer in
    // this family already uses.
    private void populate(NavRenderContext context) {
        rootColumn.removeAll();
        var childrenIndex = RootNavSupport.childrenOf(context);
        var activeChain = RootNavSupport.activeChainFor(context);
        populateList(RootNavSupport.collectRootNodes(context), childrenIndex, activeChain, rootColumn::add);
    }

    /**
     * Builds one {@link NavItem} per node in {@code nodes} — a leaf if it has its own
     * {@code menuEntry()}, a branch otherwise — attaching each via {@code addTo} (the root column
     * for the top-level list, or the parent {@link NavItem}'s own {@code addItem} for anything
     * nested), then recurses into each node's own children the same way, so the flyout cascade
     * goes exactly as deep as the tree does.
     *
     * <p>Whether a leaf reserves the chevron zone's width is decided once per list, not per item:
     * if any node in {@code nodes} is itself a group, every leaf in that same list also reserves
     * the space via {@link NavItem#reserveChevronSpace}, so their content stays aligned with their
     * branch siblings. A list with no groups at all reserves nothing, matching how every other
     * built-in renderer's rows already look.
     */
    private void populateList(List<NavNode> nodes, Map<NavNode, List<NavNode>> childrenIndex,
                               Set<NavNode> activeChain, Consumer<NavItem> addTo) {
        var needsChevronReservation = nodes.stream().anyMatch(childrenIndex::containsKey);
        for (var node : nodes) {
            var item = node.menuEntry()
                    .map(entry -> new NavItem(node.title(), node.createIcon().orElse(null), entry.menuClass()))
                    .orElseGet(() -> new NavItem(node.title(), node.createIcon().orElse(null)));
            item.setActive(activeChain.contains(node));
            addTo.accept(item);

            var children = childrenIndex.get(node);
            if (children != null) {
                populateList(children, childrenIndex, activeChain, item::addItem);
            }
            else if (needsChevronReservation) {
                item.reserveChevronSpace();
            }
        }
    }
}
