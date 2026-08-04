package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.RouteNavUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverVariant;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.signals.Signal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Shared behavior for the two touch-based {@link NavRenderer}s ({@link TouchBarNavRenderer},
 * {@link SideRailNavRenderer}): a primary icon bar (bottom bar or rail, depending on
 * {@link FlexLayout.FlexDirection}) built into the subclass's own {@link NavSlots} location,
 * plus an identical two-level drill-down bar built into {@link NavSlots#headerNav()}.
 *
 * <p>Displays one icon per root nav section; when more sections exist than fit, the last slot
 * becomes an overflow trigger (default: a "More" button opening a {@link Popover}) — see
 * {@link #createOverflowComponent}.
 */
abstract class AbstractTouchNavRenderer implements NavRenderer {

    // Practical minimum slot size for a labeled icon item (icon + label, comfortable tap area).
    // Material Design: 60–168dp; Apple HIG: ~64pt. Narrower than this, items become unreadable.
    private static final int MIN_SLOT_PX = 72;

    private AppNavLayout owner;
    private final FlexLayout.FlexDirection direction;

    private NavGrouper navGrouper;
    // The path render() was last called with — needed so the resize-driven Signal.effect below
    // (which can fire independently of render(), e.g. a phone rotating without a NavType change)
    // can re-apply active highlighting after it rebuilds items, not just on the next navigation.
    private String currentPath = "";

    // ——————————— Primary bar state ————————————
    private FlexLayout bar;
    private int maxIcons = 5;
    private NavNode overflowNode;
    private final Map<NavNode, Button> navItems = new LinkedHashMap<>();
    private final Map<NavNode, Button> overflowButtons = new LinkedHashMap<>();

    // Slot components this renderer last attached its bar/headerNavBar into — used to detect a
    // NavStrategy tear-down/rebuild (fresh slot instances), since this renderer itself outlives
    // any single NavStrategy build/tearDown cycle.
    private HasComponents attachedPrimarySlot;
    private HasComponents attachedHeaderNavSlot;

    // ——————————— Header-nav (drill-down) state ————————————
    private HorizontalLayout headerNavBar;
    private Button backButton;
    private Tabs tabs;
    private final Map<Tab, Class<? extends Component>> tabPaths = new HashMap<>();
    private String currentRoot;
    private String currentParentLabel;
    private Class<? extends Component> currentParentRoute;

    AbstractTouchNavRenderer(FlexLayout.FlexDirection direction) {
        this.direction = direction;
    }

    // Package-private: only AppNavLayout (same package) can call this — a subclass in another
    // package can't, even via inheritance, since default access doesn't cross package
    // boundaries. Set once, immediately after construction, before this renderer's first
    // render() call.
    void attachOwner(AppNavLayout owner) {
        this.owner = owner;
    }

    /** Returns the location this renderer's primary bar belongs in — {@link NavSlots#sideRail()}
     *  or {@link NavSlots#touchBar()}, depending on the concrete subclass. */
    protected abstract HasComponents primarySlot(NavSlots slots);

    @Override
    public NavType navType() {
        return direction == FlexLayout.FlexDirection.COLUMN ? NavType.RAIL : NavType.TOUCH;
    }

    @Override
    public void render(NavRenderContext context) {
        this.navGrouper = context.navGrouper();
        this.currentPath = context.currentPath();
        ensureBuilt(context.slots());
        buildItems();
        highlightActive(currentPath);
        rebuildForPath(currentPath);
    }

    /**
     * Ensures the bar/headerNavBar components exist and are attached into the given slots.
     * This renderer instance outlives any single {@code NavStrategy} build/tearDown cycle (it's
     * held as a long-lived field on {@code AppNavLayout}), so a slot component from a previous
     * cycle is no longer valid once torn down — detected here by identity, not by "already built
     * once", so a rebuild (e.g. a tablet rotating away and back) reconstructs and re-attaches
     * rather than silently keeping stale components parented to discarded, detached slots.
     */
    private void ensureBuilt(NavSlots slots) {
        var primary = primarySlot(slots);
        if (bar == null || attachedPrimarySlot != primary) {
            bar = new FlexLayout();
            bar.addClassName("nav-bar");
            bar.setFlexDirection(direction);
            if (direction == FlexLayout.FlexDirection.COLUMN) {
                bar.setSizeFull();
                bar.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
                // STRETCH (not CENTER) so items are constrained to the rail's own width instead
                // of being free to take their own natural (label-length-dependent) width — a
                // wide label (e.g. "Analytics") could otherwise push an item, and therefore the
                // whole rail, wider than the rail's own explicit CSS width. Paired with
                // min-width:0 on each item below (needed because STRETCH still respects
                // min-width:auto's default shrink floor, same as flex-grow does for the row bar).
                bar.setAlignItems(FlexComponent.Alignment.STRETCH);
            }
            else {
                bar.setWidthFull();
                bar.setJustifyContentMode(FlexComponent.JustifyContentMode.EVENLY);
                bar.setAlignItems(FlexComponent.Alignment.BASELINE);
            }
            var page = UI.getCurrent().getPage();
            Signal.effect(bar, () -> {
                var size = page.windowSizeSignal().get();
                var rawAvailable = direction == FlexLayout.FlexDirection.COLUMN ? size.height() : size.width();
                // window.innerWidth/innerHeight (what size above is built from) includes the
                // unsafe strip behind a device notch, rounded corners, or home indicator — an
                // icon can't actually render there, so subtract it before deciding how many fit.
                // Only a JS round trip can read env(safe-area-inset-*); see the custom properties
                // it's bridged onto in app-nav-layout.ts.
                bar.getElement().executeJs(
                        "var s = getComputedStyle(document.documentElement);"
                        + "function px(name) { return parseFloat(s.getPropertyValue(name)) || 0; }"
                        + "return $0"
                        + "  ? px('--nav-safe-area-inset-top') + px('--nav-safe-area-inset-bottom')"
                        + "  : px('--nav-safe-area-inset-left') + px('--nav-safe-area-inset-right');",
                        direction == FlexLayout.FlexDirection.COLUMN)
                        .then(Double.class, unsafeInsetPx -> {
                            var available = rawAvailable - unsafeInsetPx;
                            var newMax = Math.max(1, (int) (available / MIN_SLOT_PX));
                            if (newMax != maxIcons) {
                                maxIcons = newMax;
                                buildItems();
                                highlightActive(currentPath);
                            }
                        });
            });
            primary.add(bar);
            attachedPrimarySlot = primary;
        }

        var headerNav = slots.headerNav();
        if (headerNavBar == null || attachedHeaderNavSlot != headerNav) {
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
            attachedHeaderNavSlot = headerNav;
        }
    }

    // ——————————— Primary bar ————————————

    private void buildItems() {
        bar.removeAll();
        navItems.clear();
        overflowButtons.clear();
        overflowNode = null;

        var rootRoutes = new LinkedHashMap<NavNode, MenuEntry>();
        for (var entry : MenuConfiguration.getMenuEntries()) {
            rootRoutes.putIfAbsent(rootNodeFor(entry), entry);
        }
        var all = new ArrayList<>(rootRoutes.entrySet());

        var needsOverflow = all.size() > maxIcons;
        var primaryCount = needsOverflow ? maxIcons - 1 : all.size();

        for (var e : all.subList(0, Math.min(primaryCount, all.size()))) {
            var rootNode = e.getKey();
            var rep = e.getValue();
            var item = navItem(rootNode.title(),
                               rootNode.createIcon().orElse(VaadinIcon.CIRCLE.create()),
                               rep.menuClass());
            navItems.put(rootNode, item);
            bar.add(item);
        }

        if (needsOverflow) {
            var overflow = all.subList(primaryCount, all.size()).stream()
                    .map(Map.Entry::getValue)
                    .toList();
            overflowNode = NavNode.of("More", (Supplier<Icon>) null);
            var overflowTrigger = navItem("More", VaadinIcon.ELLIPSIS_DOTS_H.create(), null);
            navItems.put(overflowNode, overflowTrigger);
            bar.add(overflowTrigger);
            bar.add(createOverflowComponent(overflow, overflowTrigger, overflowButtons));
        }
    }

    private void highlightActive(String path) {
        var currentRootNode = MenuConfiguration.getMenuEntries().stream()
                .filter(e -> owner.navPathMatcher.test(path, RouteNavUtils.normalizedPath(e)))
                .max(Comparator.comparingInt(e -> RouteNavUtils.pathSegments(RouteNavUtils.normalizedPath(e)).size()))
                .map(this::rootNodeFor)
                .orElse(null);

        var overflowActive = overflowButtons.containsKey(currentRootNode);

        navItems.forEach((root, item) -> {
            var active = root.equals(currentRootNode)
                    || (root == overflowNode && overflowActive);
            item.getElement().getClassList().set("active", active);
        });

        overflowButtons.forEach((root, btn) ->
                btn.getElement().getClassList().set("active", root.equals(currentRootNode)));
    }

    private NavNode rootNodeFor(MenuEntry entry) {
        var node = navGrouper.nodeFor(entry);
        while (node.parent().isPresent()) {
            node = node.parent().get();
        }
        return node;
    }

    private Button navItem(String title, Icon icon, Class<? extends Component> viewClass) {
        icon.setSize("20px");

        var titleSpan = new Span(title);
        titleSpan.addClassName("touch-nav-label");

        // Button lacks HasComponents and setText(String) only appends a raw text node (no
        // element to attach the label's own styling to), so icon+label are composed in a plain
        // Div passed as the button's "icon" content instead — see app-nav-layout.ts's
        // "touch-nav-content" rule for the resulting layout.
        var content = new Div(icon, titleSpan);
        content.addClassName("touch-nav-content");

        var item = createNavButton(content, viewClass);
        // Structural layout (sizing/padding) and the row-direction flex:1 1 0 live in this
        // add-on's own CSS (app-nav-layout.ts, keyed off "touch-nav-item"), not as Java-side
        // theme utility classes — see that file's header comment for why. min-width:0 there
        // overrides the flex-item default (min-width:auto, which pins the shrink floor to the
        // label's un-wrapped width) so an item can actually shrink below its own natural content
        // width and .touch-nav-label's ellipsis can engage instead of forcing the bar (row) or
        // the rail (column, via its own cross-axis stretch) wider than intended. Needed in both
        // directions: MIN_SLOT_PX only ever decided *whether* to show "More"/how many rail icons
        // fit, never enforced a real per-item width ceiling on rendering — a long label (e.g.
        // "Analytics") could otherwise push a row past its right edge, or push the rail wider
        // than its own explicit CSS width (which then throws off vaadin-app-layout's own
        // measurement-based sizing of the header, since it accounts for the rail's *rendered*,
        // not intended, width).
        item.addClassName("touch-nav-item");
        return item;
    }

    /**
     * Builds a nav-bar item {@link Button} wrapping {@code content}, themed so an {@code .active}
     * item (this add-on's own CSS, keyed off that class) picks up whichever theme is actually
     * loaded's own accent color, and navigating to {@code viewClass} on click (or doing nothing
     * on click if {@code null} — the "More" overflow trigger has no route of its own).
     *
     * <p>theme="tertiary" is a cross-theme-consistent variant name, not a Lumo-only mechanism
     * despite {@link ButtonVariant#LUMO_TERTIARY}'s legacy "LUMO_" naming — Aura's own button.css
     * keys off the identical theme="tertiary" attribute, and the un-themed base package supplies
     * its own sensible default too. This is what lets an {@code .active} item pick up the active
     * theme's own accent color (Lumo blue, Aura's accent, neutral under base) automatically,
     * matching {@code vaadin-side-nav-item}'s own selected-item color — the inactive state is
     * forced back to the neutral secondary color in CSS (app-nav-layout.ts).
     *
     * <p>Exposed (not just used internally by {@link #navItem}) so a {@link #createOverflowComponent}
     * override that still wants per-entry buttons doesn't have to reimplement this theming/wiring
     * by hand — reuse this rather than constructing a {@code Button} directly, or the item risks
     * silently losing the theme-adaptive color this method exists to guarantee.
     */
    protected Button createNavButton(Component content, Class<? extends Component> viewClass) {
        var item = new Button(content);
        item.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        if (viewClass != null) {
            item.addClickListener(unused -> UI.getCurrent().navigate(viewClass));
        }
        return item;
    }

    /**
     * Creates the overflow-triggering component shown alongside the "More" bar item —
     * default: a {@link Popover} listing the overflowing entries. Override to replace the
     * overflow presentation (e.g. an expand chevron or a swipeable strip) while keeping bar
     * layout, active highlighting, and header-nav delegation unchanged. Implementations that
     * want the "More" item to highlight while one of their entries is active should populate
     * {@code overflowButtonsOut} the same way this default does. If the replacement presentation
     * still uses one button per entry, build them via {@link #createNavButton} rather than a
     * plain {@code new Button(...)} to keep the same theme-adaptive active-color behavior.
     */
    protected Component createOverflowComponent(List<MenuEntry> overflowEntries, Button overflowTrigger,
                                                 Map<NavNode, Button> overflowButtonsOut) {
        var layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);

        var popover = new Popover();
        popover.addThemeVariants(PopoverVariant.ARROW);
        popover.setTarget(overflowTrigger);
        for (var entry : overflowEntries) {
            var rootNode = rootNodeFor(entry);
            var icon = rootNode.createIcon().orElse(VaadinIcon.CIRCLE.create());
            icon.setSize("20px");

            // See navItem()'s own comment: Button lacks HasComponents and setText(String) only
            // appends a raw text node, so icon+label are composed in a plain Div passed as the
            // button's "icon" content instead.
            var content = new Div(icon, new Span(rootNode.title()));
            content.addClassName("overflow-nav-content");

            var btn = createNavButton(content, entry.menuClass());
            // Structural layout (padding/alignment/color) lives in this add-on's own CSS
            // (app-nav-layout.ts, keyed off "overflow-nav-item"), not as Java-side theme
            // utility classes — see that file's header comment for why.
            btn.addClassName("overflow-nav-item");
            btn.setWidthFull();
            btn.addClickListener(unused -> popover.close());
            overflowButtonsOut.put(rootNode, btn);
            layout.add(btn);
        }
        popover.add(layout);
        return popover;
    }

    // ——————————— Header-nav (drill-down) ————————————

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
