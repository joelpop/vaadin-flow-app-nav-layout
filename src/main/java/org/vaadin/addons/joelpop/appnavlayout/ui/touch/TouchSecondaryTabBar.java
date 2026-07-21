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
 * Two-level secondary tab bar for touch and rail navigation. It renders a
 * horizontal {@link Tabs} row whose content adapts to the current URL depth:
 *
 * <ul>
 *   <li><b>Level 1</b> (depth-2 routes, e.g. {@code orders/list}): shows flat sibling
 *       tabs for all routes that share the same first path segment. No back button.
 *   <li><b>Level 2</b> (depth-3+ routes, e.g. {@code catalog/detail/view}): shows a
 *       {@code ←} back button and the sibling tabs under the two-segment parent prefix.
 *       The back button returns to level 1 without navigating when no direct parent
 *       route exists, or navigates to it when one does.
 * </ul>
 *
 * <p>The bar hides itself ({@code setVisible(false)}) for top-level routes (depth &lt; 2)
 * and for routes not present in {@link com.vaadin.flow.server.menu.MenuConfiguration}.
 * The bar is hidden and inert until {@link #setNavGrouper} is called.
 *
 * <p>Structural logic uses {@code @Route} template paths; {@code @Menu} titles are
 * display-only and do not affect depth or grouping.
 */
public class TouchSecondaryTabBar extends HorizontalLayout {

    private final Signal<Location> navigationSignal;
    private NavGrouper navGrouper;
    private final Button backButton;
    private final Tabs tabs = new Tabs();
    private final Map<Tab, MenuEntry> tabCanonical = new HashMap<>();

    private String currentRoot;
    private String currentParentLabel;
    private String currentParentRoute;

    /** Builds the component shell; call {@link #setNavGrouper} before attaching so content populates on first attach. */
    public TouchSecondaryTabBar(Signal<Location> navigationSignal) {
        this.navigationSignal = navigationSignal;

        setWidthFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setVisible(false);
        setPadding(false);
        setSpacing(false);
        addClassName("secondary-tab-bar");

        backButton = new Button(VaadinIcon.ARROW_LEFT.create());
        backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        backButton.setVisible(false);
        backButton.addClickListener(_ -> handleBack());

        tabs.setWidthFull();

        add(backButton, tabs);
        Signal.effect(this, () -> rebuildForPath(navigationSignal.get().getPath()));
    }

    /**
     * Sets the grouper used to derive tab labels and section membership. Must be
     * called before the bar is useful; the bar remains hidden until this is set.
     * Triggers an immediate rebuild for the current navigation path.
     */
    public void setNavGrouper(NavGrouper navGrouper) {
        this.navGrouper = navGrouper;
        rebuildForPath(navigationSignal.peek().getPath());
    }

    /**
     * Rebuilds the tab bar from scratch based on the route depth of {@code path}.
     * Depth-2 routes show level-1 sibling tabs; depth-3+ routes show
     * [← Back] + the depth-3 siblings under the same two-segment parent prefix.
     */
    private void rebuildForPath(String path) {
        if (navGrouper == null) {
            setVisible(false);
            return;
        }

        var entries = MenuConfiguration.getMenuEntries();

        var currentEntry = entries.stream()
                .filter(e -> path.equals(RouteNavUtils.normalizedPath(e)))
                .findFirst().orElse(null);

        if (currentEntry == null) {
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
            buildLevel1(entries, currentRoot, navGrouper.nodeFor(currentEntry).title());
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
            // Derive the active label the same way buildCanonicalBySegment would:
            // use the navGrouper title for the entry at routeParentPrefix if one exists,
            // otherwise humanise the second path segment.
            var fallbackLabel = entries.stream()
                    .filter(e -> routeParentPrefix.equals(RouteNavUtils.normalizedPath(e)))
                    .findFirst()
                    .map(e -> navGrouper.nodeFor(e).title())
                    .orElseGet(() -> RouteNavUtils.routeSegmentLabel(segs.get(1)));
            buildLevel1(entries, segs.getFirst(), fallbackLabel);
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
