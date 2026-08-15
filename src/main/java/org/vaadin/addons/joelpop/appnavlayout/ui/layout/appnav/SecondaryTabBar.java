package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.RouteNavUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.TabsVariant;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.shared.Registration;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The two-level drill-down bar built into {@link NavSlots#headerNav()}: depth-2 routes show flat
 * sibling tabs, depth-3+ routes show a {@code [← Back]} button plus the siblings under the same
 * two-segment parent prefix. Any {@link NavRenderer} for {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#TOUCH},
 * {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#RAIL}, or
 * {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#HEADER} that wants this behavior
 * holds one instance and calls {@link #render} from within its own {@code render}.
 *
 * <p>Builds and owns exactly one component tree — the same shape {@link NavItem} already has
 * ({@code extends Composite<Button>}) — so this is a real {@link Component} too, via
 * {@link Composite}, rather than a plain object that hands its tree to whoever calls
 * {@link #render}. Being an actual component is what lets {@link #addBlurListener} use Vaadin's
 * own {@code ComponentEvent}/{@code fireEvent} machinery directly, the same idiom
 * {@code AppNavLayout.addNavTypeChangedListener} already uses elsewhere in this add-on.
 */
@CssImport("./secondary-tab-bar.css")
class SecondaryTabBar extends Composite<HorizontalLayout> {

    private final boolean autoselect;
    private final boolean centered;

    private NavGrouper navGrouper;

    // Slot component this bar last attached into — used to detect a NavStrategy tear-down/rebuild
    // (fresh slot instances), since this bar outlives any single NavStrategy build/tearDown cycle.
    private HasComponents attachedSlot;

    private final HorizontalLayout headerNavBar;
    private final Button backButton;
    private final Tabs tabs;
    private final Map<Tab, MenuEntry> tabEntries = new HashMap<>();
    private String currentPath;
    private String currentRoot;
    private String currentParentLabel;
    private Class<? extends Component> currentParentRoute;

    /**
     * @param autoselect {@code true} (Vaadin's own {@code Tabs.setAutoselect} vocabulary, read
     *                    the same direction): selecting a tab acts on it immediately. {@code
     *                    false}: selecting a tab that still has deeper children beneath it only
     *                    previews them locally ({@link #rebuildForPath}, never {@code
     *                    UI.navigate()}) — used by {@link HeaderTabsNavRenderer}, where a group
     *                    tab should only expand, never auto-navigate.
     * @param centered centers the tabs within the row's own remaining space, after the Back
     *                  button — left {@code false} only for a row narrow enough that its tabs
     *                  already read as filling it (e.g. {@link SideRailNavRenderer}'s own
     *                  rail-adjacent header row). Independent of {@code autoselect} — neither
     *                  implies the other; each caller sets both according to its own needs.
     */
    SecondaryTabBar(boolean autoselect, boolean centered) {
        this.autoselect = autoselect;
        this.centered = centered;

        // Rarely a good reason to override Composite's own initContent() — HorizontalLayout has
        // a no-arg constructor, so the default (reflection-based) initContent() already builds
        // it correctly; getContent() in the constructor is all that's needed.
        headerNavBar = getContent();
        headerNavBar.setWidthFull();
        headerNavBar.setAlignItems(FlexComponent.Alignment.CENTER);
        headerNavBar.setVisible(false);
        headerNavBar.setPadding(false);
        headerNavBar.setSpacing(false);
        headerNavBar.addClassName("secondary-tab-bar");

        // Transparent background/no border, and symmetric (rather than the button's own default
        // horizontal-only) padding, come from secondary-tab-bar-back's own CSS (secondary-tab-
        // bar.css), rendering correctly under any theme, or none at all.
        backButton = new Button(VaadinIcon.ARROW_LEFT.create());
        backButton.addClassName("secondary-tab-bar-back");
        backButton.setVisible(false);
        backButton.addClickListener(unused -> handleBack());

        tabs = new Tabs();
        tabs.addThemeVariants(TabsVariant.SMALL);
        if (centered) {
            // Centers the tabs within the row's own remaining space, after the Back button, via
            // secondary-tab-bar-tabs's own CSS (secondary-tab-bar.css) — the Back button stays
            // anchored at the row's own start rather than being pulled into the centered group.
            tabs.addClassName("secondary-tab-bar-tabs");
        }
        tabs.addSelectedChangeListener(event -> {
            if (event.isFromClient()) {
                var entry = tabEntries.get(event.getSelectedTab());
                if (entry != null) {
                    if (!autoselect && hasDeeperEntries(entry)) {
                        rebuildForPath(RouteNavUtils.normalizedPath(entry));
                    }
                    else {
                        UI.getCurrent().navigate(entry.menuClass());
                    }
                }
            }
        });

        headerNavBar.add(backButton, tabs);

        if (!autoselect) {
            // "focusout" (unlike the native "blur" event BlurEvent below is named after) bubbles,
            // so this one listener catches focus leaving any descendant tab, not just this
            // element itself.
            getElement().addEventListener("focusout", event -> fireEvent(new BlurEvent(this,
                    event.getEventDataElement("event.relatedTarget").orElse(null))))
                    .addEventDataElement("event.relatedTarget");
        }
    }

    void render(NavRenderContext context) {
        this.navGrouper = context.navGrouper();
        this.currentPath = context.currentPath();
        var headerNav = context.slots().headerNav();
        if (attachedSlot != headerNav) {
            headerNav.add(this);
            attachedSlot = headerNav;
        }
        rebuildForPath(currentPath);
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
        // only visible in that same level-2 state, so both are guaranteed non-null here (except
        // the !autoselect branch below, which never reads currentParentRoute at all).
        if (!autoselect) {
            // Collapsing back a level is the mirror of expanding into one — stays local, never
            // navigates, the same non-committal rule the Tabs selection listener above applies.
            buildLevel1(MenuConfiguration.getMenuEntries(), currentRoot, currentParentLabel);
        }
        else if (currentParentRoute != null) {
            UI.getCurrent().navigate(currentParentRoute);
        }
        else {
            buildLevel1(MenuConfiguration.getMenuEntries(), currentRoot, currentParentLabel);
        }
    }

    private void rebuildTabs(LinkedHashMap<String, MenuEntry> canonical, String activeLabel) {
        tabs.removeAll();
        tabEntries.clear();
        Tab activeTab = null;
        for (var entry : canonical.entrySet()) {
            var tab = new Tab(entry.getKey());
            tabEntries.put(tab, entry.getValue());
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

    /** True if any menu entry's normalized path starts with {@code entry}'s own path + "/" —
     *  i.e., selecting this entry's tab would otherwise have shown more beneath it, regardless
     *  of whether the entry is itself also directly routable. Only consulted when
     *  {@code !autoselect}. */
    private static boolean hasDeeperEntries(MenuEntry entry) {
        var prefix = RouteNavUtils.normalizedPath(entry) + "/";
        return MenuConfiguration.getMenuEntries().stream()
                .anyMatch(e -> RouteNavUtils.normalizedPath(e).startsWith(prefix));
    }

    /**
     * Locally previews {@code rootEntry}'s own root's immediate (depth-2) children, as if it
     * were the current path, without navigating — for a primary root tab representing a group.
     * Deliberately not just {@code rebuildForPath(pathOfRootEntry)}: a representative entry
     * chosen for a root can be arbitrarily deep (e.g. the root's only leaves are three levels
     * down), and {@code rebuildForPath} would then skip straight to the back-button view instead
     * of the root's own immediate children. This always shows the flat level-1 view, regardless
     * of how deep {@code rootEntry} happens to be. Only meaningful when {@code !autoselect}.
     */
    void exploreRoot(MenuEntry rootEntry) {
        currentRoot = RouteNavUtils.pathSegments(RouteNavUtils.normalizedPath(rootEntry)).getFirst();
        buildLevel1(MenuConfiguration.getMenuEntries(), currentRoot, null);
    }

    /** Restores this bar to reflect the actually-active route, discarding whatever was being
     *  explored via {@link #exploreRoot} or a local drill-down. Only meaningful when
     *  {@code !autoselect}. */
    void restore() {
        rebuildForPath(currentPath);
    }

    /** True if {@code candidate} is this bar's own element, or a descendant of it — lets an
     *  owner ask "is this focus target mine?" without ever being handed the element itself. */
    boolean containsFocusTarget(Element candidate) {
        return isDescendantOf(candidate, getElement());
    }

    private static boolean isDescendantOf(Element candidate, Element ancestor) {
        for (var e = candidate; e != null; e = e.getParent()) {
            if (e.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fired when this bar is blurred (Vaadin's own vocabulary for "lost focus") — carrying
     * whatever element focus moved to, or {@code null} if it left the browser context entirely.
     * The DOM mechanism underneath is {@code "focusout"}, not the native {@code "blur"} event
     * this is named after: unlike {@code "blur"}, {@code "focusout"} bubbles, so one listener on
     * this bar's own element catches it leaving any descendant tab — {@code "blur"} wouldn't.
     * Only fired when constructed with {@code autoselect=false}.
     */
    static class BlurEvent extends ComponentEvent<SecondaryTabBar> {
        private final Element relatedTarget;

        BlurEvent(SecondaryTabBar source, Element relatedTarget) {
            super(source, true);
            this.relatedTarget = relatedTarget;
        }

        Element getRelatedTarget() {
            return relatedTarget;
        }
    }

    /** Notifies {@code listener} whenever this bar is blurred. This bar has no knowledge of any
     *  other component's own DOM position — deciding what, if anything, counts as a genuine
     *  blur of a wider explorable area is entirely up to the listener. */
    Registration addBlurListener(ComponentEventListener<BlurEvent> listener) {
        return addListener(BlurEvent.class, listener);
    }
}
