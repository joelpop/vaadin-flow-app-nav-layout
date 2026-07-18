package org.vaadin.addons.joelpop.appnavlayout.ui.touch;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.RouteNavUtils;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverVariant;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Icon navigation bar for touch devices, rendered as a bottom bar
 * ({@link FlexDirection#ROW}) or a left-edge rail ({@link FlexDirection#COLUMN}).
 * Displays one icon per root nav section derived from the {@link NavGrouper}; when
 * more sections exist than {@code MAX_ICONS}, the last slot becomes a "More" button
 * opening a {@link Popover} overflow list.
 *
 * <p>Reacts to navigation via a {@link Signal}&lt;{@link Location}&gt; supplied at
 * construction; the active icon tracks the current route's root section as
 * determined by the {@link NavGrouper}.
 */
public class TouchNavBar extends FlexLayout {

    // Practical minimum slot size for a labeled icon item (icon + label, comfortable tap area).
    // Material Design: 60–168dp; Apple HIG: ~64pt. Narrower than this, items become unreadable.
    private static final int MIN_SLOT_PX = 72;

    private NavGrouper navGrouper;
    private BiPredicate<String, String> pathMatcher = String::equals;
    private int maxIcons = 5;
    private NavNode overflowNode;
    private final Map<NavNode, Div>    navItems        = new LinkedHashMap<>();
    private final Map<NavNode, Button> overflowButtons = new LinkedHashMap<>();

    /** Builds the bar shell in the given flex direction; call {@link #setNavGrouper} to populate items. */
    public TouchNavBar(Signal<Location> navigationSignal, Page page, FlexDirection direction) {
        setFlexDirection(direction);
        if (direction == FlexDirection.COLUMN) {
            setSizeFull();
            setJustifyContentMode(FlexComponent.JustifyContentMode.START);
            setAlignItems(FlexComponent.Alignment.CENTER);
            getStyle().set("gap", "var(--lumo-space-m)");
        }
        else {
            setWidthFull();
            setJustifyContentMode(FlexComponent.JustifyContentMode.EVENLY);
            setAlignItems(FlexComponent.Alignment.BASELINE);
        }
        Signal.effect(this, () -> {
            var size = page.windowSizeSignal().get();
            var available = direction == FlexDirection.COLUMN ? size.height() : size.width();
            var newMax = Math.max(1, available / MIN_SLOT_PX);
            if (newMax != maxIcons) {
                maxIcons = newMax;
                if (navGrouper != null) {
                    buildItems();
                }
            }
        });
        Signal.effect(this, () -> highlightActive(navigationSignal.get().getPath()));
    }

    /** Sets the predicate used to determine whether the current path belongs to a nav item's section. */
    public void setPathMatcher(BiPredicate<String, String> pathMatcher) {
        this.pathMatcher = pathMatcher;
    }

    /** Assigns the grouper and builds nav items from all {@link MenuConfiguration} entries. */
    public void setNavGrouper(NavGrouper navGrouper) {
        this.navGrouper = navGrouper;
        buildItems();
    }

    private void buildItems() {
        removeAll();
        navItems.clear();
        overflowButtons.clear();
        overflowNode = null;

        var rootRoutes = new LinkedHashMap<NavNode, MenuEntry>();
        for (var entry : MenuConfiguration.getMenuEntries()) {
            rootRoutes.putIfAbsent(rootNodeFor(entry), entry);
        }
        var all = new ArrayList<>(rootRoutes.entrySet());

        var needsHamburger = all.size() > maxIcons;
        var primaryCount = needsHamburger ? maxIcons - 1 : all.size();

        for (var e : all.subList(0, Math.min(primaryCount, all.size()))) {
            var rootNode = e.getKey();
            var rep = e.getValue();
            var item = navItem(rootNode.title(),
                               rootNode.createIcon().orElse(VaadinIcon.CIRCLE.create()),
                               RouteNavUtils.normalizedPath(rep));
            navItems.put(rootNode, item);
            add(item);
        }

        if (needsHamburger) {
            var overflow = all.subList(primaryCount, all.size()).stream()
                    .map(Map.Entry::getValue)
                    .toList();
            overflowNode = NavNode.of("More", (Supplier<Icon>) null);
            var hamburger = navItem("More", VaadinIcon.ELLIPSIS_DOTS_H.create(), null);
            navItems.put(overflowNode, hamburger);
            add(hamburger);
            add(overflowPopover(overflow, hamburger, overflowButtons));
        }
    }

    private void highlightActive(String path) {
        if (navGrouper == null) return;
        var currentRootNode = MenuConfiguration.getMenuEntries().stream()
                .filter(e -> pathMatcher.test(path, RouteNavUtils.normalizedPath(e)))
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

    private static Div navItem(String title, Icon icon, String path) {
        icon.setSize("20px");

        var titleSpan = new Span(title);
        titleSpan.addClassNames(LumoUtility.FontSize.XXSMALL, LumoUtility.FontWeight.BOLD,
                LumoUtility.TextOverflow.ELLIPSIS);

        var item = new Div();
        item.add(icon, titleSpan);
        item.addClassNames("touch-nav-item",
                LumoUtility.Display.FLEX,
                LumoUtility.FlexDirection.COLUMN,
                LumoUtility.AlignItems.CENTER,
                LumoUtility.TextColor.SECONDARY);

        if (path != null) {
            item.getElement().addEventListener("click", _ -> UI.getCurrent().navigate(path));
        }
        return item;
    }

    private Popover overflowPopover(List<MenuEntry> entries, Div target,
                                     Map<NavNode, Button> overflowButtons) {
        var layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(false);

        var popover = new Popover();
        popover.addThemeVariants(PopoverVariant.ARROW);
        popover.setTarget(target);
        for (var entry : entries) {
            var rootNode = rootNodeFor(entry);
            var btn = new Button(rootNode.title(),
                                 rootNode.createIcon().orElse(VaadinIcon.CIRCLE.create()), _ -> {
                UI.getCurrent().navigate(RouteNavUtils.normalizedPath(entry));
                popover.close();
            });
            btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            btn.addClassName("overflow-nav-item");
            btn.setWidthFull();
            btn.getStyle().setJustifyContent(Style.JustifyContent.FLEX_START);
            overflowButtons.put(rootNode, btn);
            layout.add(btn);
        }
        popover.add(layout);
        return popover;
    }
}
