package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.RouteNavUtils;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverVariant;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.signals.Signal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Shared behavior for the two touch-based {@link NavRenderer}s ({@link TouchBarNavRenderer},
 * {@link SideRailNavRenderer}): a primary icon bar (bottom bar or rail, depending on
 * {@link FlexLayout.FlexDirection}) built into the subclass's own {@link NavSlots} location,
 * plus the shared {@link SecondaryTabBar} drill-down bar.
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
    private Predicate<MenuEntry> navItemFilter = entry -> true;
    // The path render() was last called with — needed so the resize-driven Signal.effect below
    // (which can fire independently of render(), e.g. a phone rotating without a NavType change)
    // can re-apply active highlighting after it rebuilds items, not just on the next navigation.
    private String currentPath = "";

    // ——————————— Primary bar state ————————————
    private FlexLayout bar;
    private int maxIcons = 5;
    private NavNode overflowNode;
    private final Map<NavNode, NavItem> navItems = new LinkedHashMap<>();
    private final Map<NavNode, Button> overflowButtons = new LinkedHashMap<>();

    // Slot component this renderer last attached its bar into — used to detect a NavStrategy
    // tear-down/rebuild (fresh slot instances), since this renderer itself outlives any single
    // NavStrategy build/tearDown cycle.
    private HasComponents attachedPrimarySlot;

    private final SecondaryTabBar secondaryTabBar;

    AbstractTouchNavRenderer(FlexLayout.FlexDirection direction) {
        this.direction = direction;
        // Centered for the touch bar (TouchBarNavRenderer, direction == ROW), whose row spans a
        // full phone width; left as SideRailNavRenderer's own natural width otherwise (direction
        // == COLUMN) — its rail-adjacent header row isn't as wide.
        secondaryTabBar = new SecondaryTabBar(true, direction == FlexLayout.FlexDirection.ROW);
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
        this.navItemFilter = context.navItemFilter();
        this.currentPath = context.currentPath();
        ensureBuilt(context.slots());
        buildItems();
        highlightActive(currentPath);
        secondaryTabBar.render(context);
    }

    /**
     * Ensures the bar component exists and is attached into the given slots. This renderer
     * instance outlives any single {@code NavStrategy} build/tearDown cycle (it's held as a
     * long-lived field on {@code AppNavLayout}), so a slot component from a previous cycle is no
     * longer valid once torn down — detected here by identity, not by "already built once", so a
     * rebuild (e.g. a tablet rotating away and back) reconstructs and re-attaches rather than
     * silently keeping stale components parented to a discarded, detached slot.
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
                // STRETCH, not BASELINE — every item is the same height regardless of whether its
                // NavNode has an icon (NavItem's own placeholder keeps that true), so there's
                // nothing left for BASELINE to align by content shape; STRETCH is what the column/rail
                // branch above already uses, for the same "don't rely on flex's own per-item
                // baseline computation" reasoning.
                bar.setAlignItems(FlexComponent.Alignment.STRETCH);
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
    }

    // ——————————— Primary bar ————————————

    private void buildItems() {
        bar.removeAll();
        navItems.clear();
        overflowButtons.clear();
        overflowNode = null;

        var rootRoutes = new LinkedHashMap<NavNode, MenuEntry>();
        for (var entry : RootNavSupport.menuEntries(navItemFilter)) {
            rootRoutes.putIfAbsent(rootNodeFor(entry), entry);
        }
        var all = new ArrayList<>(rootRoutes.entrySet());

        var needsOverflow = all.size() > maxIcons;
        var primaryCount = needsOverflow ? maxIcons - 1 : all.size();

        for (var e : all.subList(0, Math.min(primaryCount, all.size()))) {
            var rootNode = e.getKey();
            var rep = e.getValue();
            var item = new NavItem(rootNode.title(), rootNode.createIcon().orElse(null), rep.menuClass());
            navItems.put(rootNode, item);
            bar.add(item);
        }

        if (needsOverflow) {
            var overflow = all.subList(primaryCount, all.size()).stream()
                    .map(Map.Entry::getValue)
                    .toList();
            overflowNode = NavNode.of("More", (Supplier<Icon>) null);
            var overflowTrigger = new NavItem("More", VaadinIcon.ELLIPSIS_DOTS_H.create(), null);
            navItems.put(overflowNode, overflowTrigger);
            bar.add(overflowTrigger);
            bar.add(createOverflowComponent(overflow, overflowTrigger.asButton(), overflowButtons));
        }
    }

    private void highlightActive(String path) {
        var currentRootNode = RootNavSupport.menuEntries(navItemFilter).stream()
                .filter(e -> owner.navPathMatcher.test(path, RouteNavUtils.normalizedPath(e)))
                .max(Comparator.comparingInt(e -> RouteNavUtils.pathSegments(RouteNavUtils.normalizedPath(e)).size()))
                .map(this::rootNodeFor)
                .orElse(null);

        var overflowActive = overflowButtons.containsKey(currentRootNode);

        navItems.forEach((root, item) -> {
            var active = root.equals(currentRootNode)
                    || (root == overflowNode && overflowActive);
            item.setActive(active);
        });

        overflowButtons.forEach((root, btn) ->
                btn.getElement().getClassList().set("active", root.equals(currentRootNode)));
    }

    private NavNode rootNodeFor(MenuEntry entry) {
        return RootNavSupport.rootOf(navGrouper.nodeFor(entry));
    }

    /**
     * Builds a nav-bar item {@link Button} wrapping {@code content}, and navigating to
     * {@code viewClass} on click (or doing nothing on click if {@code null} — the "More"
     * overflow trigger has no route of its own).
     *
     * <p>Plain, borderless, transparent-background chrome comes from this add-on's own CSS
     * (app-nav-layout.ts, keyed off the "overflow-nav-item" class a caller adds afterward),
     * rendering correctly under any theme, or none at all. An {@code .active} item is left at
     * the button's own plain default text color; the inactive state is forced to the muted
     * secondary color in that same CSS.
     *
     * <p>Exposed (not just used internally by {@link NavItem}) so a {@link #createOverflowComponent}
     * override that still wants per-entry buttons doesn't have to reimplement this wiring by
     * hand — reuse this rather than constructing a {@code Button} directly.
     */
    protected Button createNavButton(Component content, Class<? extends Component> viewClass) {
        var item = new Button(content);
        if (viewClass != null) {
            item.addClickListener(unused -> UI.getCurrent().navigate(viewClass));
        }
        return item;
    }

    /**
     * Creates the overflow-triggering component shown alongside the "More" bar item —
     * default: a {@link Popover} listing the overflowing entries. Override to replace it with a
     * different presentation shown in response to tapping the "More" trigger (e.g. a
     * {@code Dialog}) while keeping bar layout, active highlighting, and header-nav delegation
     * unchanged. Implementations that want the "More" item to highlight while one of their
     * entries is active should populate {@code overflowButtonsOut} the same way this default
     * does. If the replacement presentation still uses one button per entry, build them via
     * {@link #createNavButton} rather than a plain {@code new Button(...)} to keep the same
     * theme-adaptive active-color behavior.
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

            // See NavItem's own comment: Button lacks HasComponents and setText(String) only
            // appends a raw text node, so icon+label are composed in a plain Div passed as the
            // button's "icon" content instead. icon is left out entirely when a section has none
            // (same rationale as NavItem) rather than falling back to a placeholder glyph.
            var content = new Div(new Span(rootNode.title()));
            content.addClassName("overflow-nav-content");
            rootNode.createIcon().ifPresent(icon -> {
                icon.setSize("20px");
                content.addComponentAsFirst(icon);
            });

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
}
