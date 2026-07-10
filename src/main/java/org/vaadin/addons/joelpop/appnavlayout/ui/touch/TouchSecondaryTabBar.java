package org.vaadin.addons.joelpop.appnavlayout.ui.touch;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.signals.Signal;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.RouteNavUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recursive drill-down secondary tab bar. Shows the current depth's siblings
 * with a Back button when drilled below the first level. Tapping any tab
 * navigates to its canonical route; {@code afterNavigation} rebuilds the bar
 * entirely from the new URL depth.
 *
 * <p>Back is the only "no-navigation" special case: when the parent group has
 * no direct view, the bar reverts to the parent level without navigating.
 *
 * <p>Structural logic (depth, grouping, prefix matching) uses the {@code @Route}
 * template path. {@code @Menu} titles are used only for display labels.
 */
public class TouchSecondaryTabBar extends HorizontalLayout {

    private NavGrouper navGrouper;
    private final Button backButton;
    private final Tabs tabs = new Tabs();
    private final Map<Tab, MenuEntry> tabCanonical = new HashMap<>();

    private String currentRoot;
    private String currentParentLabel;
    private String currentParentRoute;

    /** Builds the component shell; call {@link #setNavGrouper} before attaching so content populates on first attach. */
    public TouchSecondaryTabBar(Signal<Location> navigationSignal) {

        setWidthFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setVisible(false);
        setPadding(false);
        setSpacing(false);

        backButton = new Button(VaadinIcon.ARROW_LEFT.create());
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        backButton.setVisible(false);
        backButton.addClickListener(_ -> handleBack());

        tabs.setWidthFull();

        add(backButton, tabs);
        Signal.effect(this, () -> rebuildForPath(navigationSignal.get().getPath()));
    }

    /** Assigns the grouper used to resolve group labels during {@code afterNavigation}. */
    public void setNavGrouper(NavGrouper navGrouper) {
        this.navGrouper = navGrouper;
    }

    /**
     * Rebuilds the tab bar from scratch based on the route depth of {@code path}.
     * Depth-2 routes show level-1 sibling tabs; depth-3+ routes show
     * [← Back] + the depth-3 siblings under the same two-segment parent prefix.
     */
    private void rebuildForPath(String path) {
        var entries = MenuConfiguration.getMenuEntries();

        var current = entries.stream()
                .map(RouteNavUtils::normalizedPath)
                .filter(path::equals)
                .findFirst().orElse(null);

        if (current == null) {
            setVisible(false);
            return;
        }

        var segs = RouteNavUtils.pathSegments(path);

        if (segs.size() < 2) {
            setVisible(false);
            return;
        }

        currentRoot = segs.getFirst();

        if (segs.size() == 2) {
            buildLevel1(entries, currentRoot, RouteNavUtils.routeSegmentLabel(segs.get(1)));
        }
        else {
            buildLevel2(entries, segs, path);
        }
    }

    // ——————————— Level building ————————————

    private void buildLevel1(List<MenuEntry> entries, String routeRoot, String activeLabel) {
        var canonical = buildCanonicalBySegment(entries, routeRoot);
        if (canonical.isEmpty()) {
            setVisible(false);
            return;
        }

        backButton.setVisible(false);
        currentParentLabel = null;
        currentParentRoute = null;

        rebuildTabs(canonical, activeLabel);
        setVisible(true);
    }

    private void buildLevel2(List<MenuEntry> entries, List<String> segs, String activePath) {
        var routeParentPrefix = segs.getFirst() + "/" + segs.get(1);

        currentParentRoute = entries.stream()
                .filter(e -> RouteNavUtils.normalizedPath(e).equals(routeParentPrefix))
                .findFirst()
                .map(RouteNavUtils::normalizedPath)
                .orElse(null);

        var siblings = entries.stream()
                .filter(e -> RouteNavUtils.normalizedPath(e).startsWith(routeParentPrefix + "/")
                        && RouteNavUtils.pathSegments(e.path()).size() == 3)
                .sorted(Comparator.comparingDouble(e -> e.order() != null ? e.order() : Double.MAX_VALUE))
                .toList();

        if (siblings.isEmpty()) {
            buildLevel1(entries, segs.getFirst(), currentParentLabel);
            return;
        }

        currentParentLabel = entries.stream()
                .filter(e -> activePath.equals(RouteNavUtils.normalizedPath(e)))
                .findFirst()
                .flatMap(e -> navGrouper.nodeFor(e).parent().map(n -> n.title()))
                .orElse(RouteNavUtils.routeSegmentLabel(segs.get(1)));

        var canonical = new LinkedHashMap<String, MenuEntry>();
        for (var e : siblings) {
            canonical.put(RouteNavUtils.leafTitle(e), e);
        }

        var activeLabel = RouteNavUtils.leafTitle(
                siblings.stream().filter(e -> activePath.equals(RouteNavUtils.normalizedPath(e)))
                        .findFirst().orElse(siblings.getFirst()));

        backButton.setVisible(true);
        rebuildTabs(canonical, activeLabel);
        setVisible(true);
    }

    // ——————————— Back ————————————

    private void handleBack() {
        if (currentParentRoute != null) {
            UI.getCurrent().navigate(currentParentRoute);
        }
        else {
            buildLevel1(MenuConfiguration.getMenuEntries(), currentRoot, currentParentLabel);
        }
    }

    // ——————————— Helpers ————————————

    private void rebuildTabs(LinkedHashMap<String, MenuEntry> canonical, String activeLabel) {
        tabs.removeAll();
        tabCanonical.clear();
        Tab activeTab = null;
        for (var entry : canonical.entrySet()) {
            var tab = new Tab(entry.getKey());
            var navPath = RouteNavUtils.normalizedPath(entry.getValue());
            tab.getElement().addEventListener("click", _ -> UI.getCurrent().navigate(navPath));
            tabs.add(tab);
            tabCanonical.put(tab, entry.getValue());
            if (entry.getKey().equals(activeLabel)) {
                activeTab = tab;
            }
        }
        tabs.setSelectedTab(activeTab);
    }

    /**
     * Groups entries under {@code routeRoot} by the label derived from their second
     * route segment. Filtering and depth detection use the {@code @Route} template.
     */
    private LinkedHashMap<String, MenuEntry> buildCanonicalBySegment(List<MenuEntry> entries, String routeRoot) {
        var allSub = entries.stream()
                .filter(e -> RouteNavUtils.normalizedPath(e).startsWith(routeRoot + "/"))
                .sorted(Comparator.comparingDouble(e -> e.order() != null ? e.order() : Double.MAX_VALUE))
                .toList();

        var result = new LinkedHashMap<String, MenuEntry>();
        for (var e : allSub) {
            var eSegs = RouteNavUtils.pathSegments(e.path());
            var node = navGrouper.nodeFor(e);
            var label = eSegs.size() == 2
                    ? node.title()
                    : node.parent().map(n -> n.title())
                            .orElse(RouteNavUtils.routeSegmentLabel(eSegs.get(1)));
            var isDepthTwo = eSegs.size() == 2;
            if (isDepthTwo) {
                result.put(label, e);
            }
            else {
                result.putIfAbsent(label, e);
            }
        }
        return result;
    }
}
