package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.shared.Registration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * An alternative {@link NavRenderer} for {@link NavType#HEADER} — root sections rendered as a
 * {@link Tabs} strip spanning the header; selecting a leaf navigates directly, selecting a group
 * only reveals that root's own children in the shared {@link SecondaryTabBar} drill-down row
 * beneath, never navigating on its own — the same non-committal rule the drill-down row itself
 * applies to a group tab of its own (see {@link SecondaryTabBar#SecondaryTabBar(boolean, boolean)}).
 * Losing focus on the whole nav hierarchy — either row — without ever landing on a leaf restores
 * both to whatever the real current view actually is.
 */
public class HeaderTabsNavRenderer implements NavRenderer {

    private HasComponents attachedSlot;
    private Tabs tabsComponent;
    private final Map<NavNode, Tab> tabs = new LinkedHashMap<>();
    private final Map<Tab, NavNode> tabNodes = new LinkedHashMap<>();
    private NavRenderContext cachedContext;
    private Registration secondaryBlurRegistration;

    // Selecting a tab that still has deeper children beneath it only previews them in the
    // drill-down row below, never navigates on its own; its tabs are centered, since this row
    // spans a full desktop header width unlike the narrower touch bar/rail row every other
    // renderer uses this bar in.
    private final SecondaryTabBar secondaryTabBar = new SecondaryTabBar(false, true);

    @Override
    public NavType navType() {
        return NavType.HEADER;
    }

    @Override
    public void render(NavRenderContext context) {
        cachedContext = context;
        ensureBuilt(context.slots().tabStrip());
        buildTabs(context);
        highlightActive(context);
        secondaryTabBar.render(context);
    }

    /**
     * Ensures {@link #tabsComponent} exists and is attached into the given slot. This renderer
     * outlives any single {@code NavStrategy} build/tearDown cycle, so a slot component from a
     * previous cycle is no longer valid once torn down — detected here by identity, not by
     * "already built once", so a rebuild (e.g. a renderer swap) reconstructs and re-attaches
     * rather than silently keeping stale components parented to a discarded slot.
     */
    private void ensureBuilt(HasComponents slot) {
        if (tabsComponent == null || attachedSlot != slot) {
            tabsComponent = new Tabs();
            tabsComponent.setWidthFull();
            tabsComponent.addSelectedChangeListener(event -> {
                if (event.isFromClient()) {
                    var node = tabNodes.get(event.getSelectedTab());
                    if (node != null) {
                        var leafClass = node.menuEntry().map(MenuEntry::menuClass).orElse(null);
                        if (leafClass != null) {
                            UI.getCurrent().navigate(leafClass);
                        }
                        else {
                            // No route of its own — a group only reveals its children in the
                            // drill-down row below, it never navigates anywhere on its own.
                            secondaryTabBar.explore(node);
                        }
                    }
                }
            });

            // This renderer listening to its own owned component — not a peer reference.
            tabsComponent.getElement().addEventListener("focusout", event ->
                    handlePossibleBlur(event.getEventDataElement("event.relatedTarget").orElse(null)))
                    .addEventDataElement("event.relatedTarget");

            // (Re)subscribe rather than stack on top of any previous subscription:
            // secondaryTabBar isn't rebuilt in lockstep with tabsComponent, so without this its
            // own listener list would accumulate a duplicate entry every time this renderer's
            // own slot changes.
            if (secondaryBlurRegistration != null) {
                secondaryBlurRegistration.remove();
            }
            secondaryBlurRegistration = secondaryTabBar.addBlurListener(
                    event -> handlePossibleBlur(event.getRelatedTarget()));

            slot.add(tabsComponent);
            attachedSlot = slot;
        }
    }

    // Rebuilt wholesale on every render(), same "rebuild, don't patch" approach every renderer in
    // this family already uses.
    private void buildTabs(NavRenderContext context) {
        tabsComponent.removeAll();
        tabs.clear();
        tabNodes.clear();

        for (var node : RootNavSupport.collectRootNodes(context)) {
            var icon = node.createIcon().orElse(null);
            if (icon != null) {
                // Smaller than the touch/rail items' own 20px — Tabs' own compact scale reads
                // oversized next to a full-size icon.
                icon.setSize("16px");
            }
            var label = new Span(node.title());
            var tab = (icon != null) ? new Tab(icon, label) : new Tab(label);

            tabs.put(node, tab);
            tabNodes.put(tab, node);
            tabsComponent.add(tab);
        }
    }

    private void highlightActive(NavRenderContext context) {
        var activeRoot = RootNavSupport.activeRootFor(context);
        tabsComponent.setSelectedTab(tabs.entrySet().stream()
                .filter(e -> Objects.equals(e.getKey(), activeRoot))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null));
    }

    /**
     * Fires on focus leaving either this renderer's own tab strip or {@link #secondaryTabBar}'s
     * own row — checked against both, since exploring can move focus freely between the two
     * without that counting as a blur of the wider explorable area. {@code secondaryTabBar}
     * itself never needs to know this renderer exists; it only exposes a generic blur
     * notification and containment query (see {@link SecondaryTabBar#addBlurListener} /
     * {@link SecondaryTabBar#containsFocusTarget}).
     */
    private void handlePossibleBlur(Element relatedTarget) {
        if (relatedTarget == null
                || (!isDescendantOf(relatedTarget, tabsComponent.getElement())
                    && !secondaryTabBar.containsFocusTarget(relatedTarget))) {
            restore();
        }
    }

    /** Restores both rows to reflect the actually-active route, discarding whatever was being
     *  explored without ever landing on a leaf. */
    private void restore() {
        if (cachedContext != null) {
            highlightActive(cachedContext);
        }
        secondaryTabBar.restore();
    }

    private static boolean isDescendantOf(Element candidate, Element ancestor) {
        for (var e = candidate; e != null; e = e.getParent()) {
            if (e.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }
}
