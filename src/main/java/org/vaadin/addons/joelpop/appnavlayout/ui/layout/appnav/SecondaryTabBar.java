package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.RouteNavUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The two-level drill-down bar built into {@link NavSlots#headerNav()}: depth-2 routes show flat
 * sibling tabs, depth-3+ routes show a {@code [← Back]} button plus the siblings under the same
 * two-segment parent prefix. Any {@link NavRenderer} for {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#TOUCH}
 * or {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#RAIL} that wants this behavior
 * holds one instance and calls {@link #render} from within its own {@code render}.
 */
class SecondaryTabBar {

    private NavGrouper navGrouper;

    // Slot component this bar last attached into — used to detect a NavStrategy tear-down/rebuild
    // (fresh slot instances), since this renderer instance outlives any single NavStrategy
    // build/tearDown cycle.
    private HasComponents attachedSlot;

    private HorizontalLayout headerNavBar;
    private Button backButton;
    private Tabs tabs;
    private final Map<Tab, Class<? extends Component>> tabPaths = new HashMap<>();
    private String currentRoot;
    private String currentParentLabel;
    private Class<? extends Component> currentParentRoute;

    void render(NavRenderContext context) {
        this.navGrouper = context.navGrouper();
        ensureBuilt(context.slots().headerNav());
        rebuildForPath(context.currentPath());
    }

    /**
     * Ensures {@link #headerNavBar} exists and is attached into the given slot. This instance
     * outlives any single {@code NavStrategy} build/tearDown cycle, so a slot component from a
     * previous cycle is no longer valid once torn down — detected here by identity, not by
     * "already built once", so a rebuild (e.g. a tablet rotating away and back) reconstructs and
     * re-attaches rather than silently keeping stale components parented to a discarded slot.
     */
    private void ensureBuilt(HasComponents headerNav) {
        if (headerNavBar == null || attachedSlot != headerNav) {
            headerNavBar = new HorizontalLayout();
            headerNavBar.setWidthFull();
            headerNavBar.setAlignItems(FlexComponent.Alignment.CENTER);
            headerNavBar.setVisible(false);
            headerNavBar.setPadding(false);
            headerNavBar.setSpacing(false);
            headerNavBar.addClassName("secondary-tab-bar");

            backButton = new Button(VaadinIcon.ARROW_LEFT.create());
            backButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
            backButton.setVisible(false);
            backButton.addClickListener(unused -> handleBack());

            tabs = new Tabs();
            tabs.setWidthFull();
            tabs.addSelectedChangeListener(event -> {
                if (event.isFromClient()) {
                    var viewClass = tabPaths.get(event.getSelectedTab());
                    if (viewClass != null) {
                        UI.getCurrent().navigate(viewClass);
                    }
                }
            });

            headerNavBar.add(backButton, tabs);
            headerNav.add(headerNavBar);
            attachedSlot = headerNav;
        }
    }

    /**
     * Rebuilds the drill-down bar from scratch based on the route depth of {@code path}.
     * Depth-2 routes show level-1 sibling tabs; depth-3+ routes show
     * [← Back] + the depth-3 siblings under the same two-segment parent prefix.
     */
    private void rebuildForPath(String path) {
        var entries = MenuConfiguration.getMenuEntries();

        var currentEntry = entries.stream()
                .filter(e -> path.equals(RouteNavUtils.normalizedPath(e)))
                .findFirst().orElse(null);

        if (currentEntry == null) {
            headerNavBar.setVisible(false);
            return;
        }

        var segs = RouteNavUtils.pathSegments(path);

        if (segs.size() < 2) {
            headerNavBar.setVisible(false);
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

    private void buildLevel1(List<MenuEntry> entries, String routeRoot, String activeLabel) {
        var canonical = buildCanonicalBySegment(entries, routeRoot);
        if (canonical.isEmpty()) {
            headerNavBar.setVisible(false);
            return;
        }

        backButton.setVisible(false);
        currentParentLabel = null;
        currentParentRoute = null;

        rebuildTabs(canonical, activeLabel);
        headerNavBar.setVisible(true);
    }

    private void buildLevel2(List<MenuEntry> entries, List<String> segs, String activePath) {
        var routeParentPrefix = segs.getFirst() + "/" + segs.get(1);

        currentParentRoute = entries.stream()
                .filter(e -> RouteNavUtils.normalizedPath(e).equals(routeParentPrefix))
                .findFirst()
                .map(MenuEntry::menuClass)
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
        headerNavBar.setVisible(true);
    }

    private void handleBack() {
        // currentRoot/currentParentRoute are only null until segs.size() >= 2 first populates
        // them in buildLevel2(); handleBack() is only reachable via the back button, which is
        // only visible in that same level-2 state, so both are guaranteed non-null here.
        if (currentParentRoute != null) {
            UI.getCurrent().navigate(currentParentRoute);
        }
        else {
            buildLevel1(MenuConfiguration.getMenuEntries(), currentRoot, currentParentLabel);
        }
    }

    private void rebuildTabs(LinkedHashMap<String, MenuEntry> canonical, String activeLabel) {
        tabs.removeAll();
        tabPaths.clear();
        Tab activeTab = null;
        for (var entry : canonical.entrySet()) {
            var tab = new Tab(entry.getKey());
            tabPaths.put(tab, entry.getValue().menuClass());
            tabs.add(tab);
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
