package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.shared.Registration;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An alternative {@link NavRenderer} for {@link NavType#TOUCH} — a grid of root sections in
 * {@link NavSlots#touchBar()} that expands upward via a chevron when there are more than fit,
 * instead of the default "More" popover. The primary row always shows a fixed, even number of
 * items; the rest sit in a collapsible overflow section beneath it.
 */
@CssImport("./expanding-touch-nav.css")
public class ExpandingTouchNavRenderer implements NavRenderer {

    private static final int ITEM_PX = 72;

    private HasComponents attachedSlot;
    private NavRenderContext cachedContext;
    private int windowWidth = 5 * ITEM_PX;
    private Registration resizeRegistration;
    private boolean expanded;

    private FlexLayout container;
    private Icon chevronIcon;

    private final Map<NavNode, Button> primaryButtons = new LinkedHashMap<>();
    private final Map<NavNode, Button> overflowButtons = new LinkedHashMap<>();

    @Override
    public NavType navType() {
        return NavType.TOUCH;
    }

    @Override
    public void render(NavRenderContext context) {
        cachedContext = context;
        var slot = context.slots().touchBar();
        if (slot != attachedSlot) {
            attachedSlot = slot;
            buildContainer(slot);
        }
        buildItems(context);
    }

    private void buildContainer(HasComponents slot) {
        container = new FlexLayout();
        container.addClassName("expanding-touch-nav");
        container.setFlexDirection(FlexLayout.FlexDirection.COLUMN);

        container.addAttachListener(e -> {
            var ui = e.getUI();
            var details = ui.getPage().getExtendedClientDetails();
            if (details != null) {
                windowWidth = details.getWindowInnerWidth();
            }
            resizeRegistration = ui.getPage().addBrowserWindowResizeListener(event -> {
                var newWidth = event.getWidth();
                if (newWidth != windowWidth) {
                    windowWidth = newWidth;
                    if (cachedContext != null) {
                        buildItems(cachedContext);
                    }
                }
            });
        });

        container.addDetachListener(e -> {
            if (resizeRegistration != null) {
                resizeRegistration.remove();
                resizeRegistration = null;
            }
        });

        slot.removeAll();
        slot.add(container);
    }

    private void buildItems(NavRenderContext context) {
        primaryButtons.clear();
        overflowButtons.clear();
        chevronIcon = null;
        container.removeAll();
        setExpanded(false);

        var rootNodes = collectRootNodes(context);
        // Floor to nearest even number so the grid always has symmetric columns.
        var n = Math.max(2, (windowWidth / ITEM_PX) & ~1);

        var primaryBar = new FlexLayout();
        primaryBar.addClassName("nav-bar");

        if (rootNodes.size() <= n) {
            // All items fit — no chevron, no overflow section needed.
            for (var node : rootNodes) {
                var button = createNavButton(node, context, false);
                button.getStyle().set("flex", "1");
                primaryButtons.put(node, button);
                primaryBar.add(button);
            }
            container.add(primaryBar);
        }
        else {
            var itemFlex = "0 0 calc(100% / " + n + ")";

            // Centered chevron sits above the primary bar.
            container.add(buildChevronRow());

            // Primary bar — all n slots filled by nav items.
            for (var node : rootNodes.subList(0, n)) {
                var button = createNavButton(node, context, false);
                button.getStyle().set("flex", itemFlex);
                primaryButtons.put(node, button);
                primaryBar.add(button);
            }
            container.add(primaryBar);

            // Overflow section below the primary bar; expands downward as bar grows up.
            // nav-bar class and baseline alignment match AbstractTouchNavRenderer's nav-bar setup.
            var overflowFlex = new FlexLayout();
            overflowFlex.addClassName("nav-bar");
            overflowFlex.setFlexWrap(FlexLayout.FlexWrap.WRAP);
            overflowFlex.setAlignItems(FlexComponent.Alignment.BASELINE);
            overflowFlex.setWidthFull();

            for (var node : rootNodes.subList(n, rootNodes.size())) {
                var button = createNavButton(node, context, true);
                button.getStyle().set("flex", itemFlex);
                overflowButtons.put(node, button);
                overflowFlex.add(button);
            }

            var overflowSection = new Div(overflowFlex);
            overflowSection.addClassName("expanding-touch-nav-overflow");
            container.add(overflowSection);
        }

        highlightActive(context);
    }

    private Div buildChevronRow() {
        chevronIcon = VaadinIcon.CHEVRON_UP.create();

        var button = new Button(chevronIcon);
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        button.addClickListener(e -> setExpanded(!expanded));

        var row = new Div(button);
        row.addClassName("expanding-touch-nav-chevron-row");
        return row;
    }

    private void setExpanded(boolean value) {
        expanded = value;
        container.getElement().getClassList().set("expanded", value);
        if (chevronIcon != null) {
            chevronIcon.getElement().setAttribute("icon",
                    value ? "vaadin:chevron-down" : "vaadin:chevron-up");
        }
    }

    private Button createNavButton(NavNode node, NavRenderContext context, boolean inOverflow) {
        var icon = node.createIcon().orElse(null);
        if (icon != null) {
            icon.setSize("20px");
        }

        var label = new Span(node.title());
        label.addClassName("touch-nav-label");

        var content = new Div();
        content.addClassName("touch-nav-content");
        if (icon != null) {
            content.add(icon);
        }
        content.add(label);

        var button = new Button();
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        button.addClassName("touch-nav-item");
        button.getElement().appendChild(content.getElement());

        Class<? extends Component> targetClass = node.menuEntry()
                .<Class<? extends Component>>map(MenuEntry::menuClass)
                .orElseGet(() -> firstChildOf(node, context));
        if (targetClass != null) {
            final var tc = targetClass;
            button.addClickListener(e -> {
                if (inOverflow) {
                    setExpanded(false);
                }
                UI.getCurrent().navigate(tc);
            });
        }
        else if (inOverflow) {
            button.addClickListener(e -> setExpanded(false));
        }

        return button;
    }

    private List<NavNode> collectRootNodes(NavRenderContext context) {
        var seen = new LinkedHashMap<NavNode, MenuEntry>();
        for (var entry : MenuConfiguration.getMenuEntries()) {
            var node = context.navGrouper().nodeFor(entry);
            var root = rootOf(node);
            seen.putIfAbsent(root, entry);
        }
        return new ArrayList<>(seen.keySet());
    }

    private NavNode rootOf(NavNode node) {
        var current = node;
        while (current.parent().isPresent()) {
            current = current.parent().get();
        }
        return current;
    }

    private Class<? extends Component> firstChildOf(NavNode groupNode, NavRenderContext context) {
        return MenuConfiguration.getMenuEntries().stream()
                .filter(e -> rootOf(context.navGrouper().nodeFor(e)) == groupNode)
                .map(MenuEntry::menuClass)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void highlightActive(NavRenderContext context) {
        var path = context.currentPath();
        var activeRoot = MenuConfiguration.getMenuEntries().stream()
                .filter(e -> pathMatches(path, e.path()))
                .max(Comparator.comparingInt(e -> new Location(e.path()).getSegments().size()))
                .map(e -> rootOf(context.navGrouper().nodeFor(e)))
                .orElse(null);

        primaryButtons.forEach((node, button) ->
                button.getElement().getClassList().set("active", Objects.equals(node, activeRoot)));
        overflowButtons.forEach((node, button) ->
                button.getElement().getClassList().set("active", Objects.equals(node, activeRoot)));
    }

    private boolean pathMatches(String current, String entry) {
        var curSegs = new Location(current).getSegments();
        var entSegs = new Location(entry).getSegments();
        if (entSegs.isEmpty()) {
            return curSegs.isEmpty();
        }
        return curSegs.size() >= entSegs.size()
                && curSegs.subList(0, entSegs.size()).equals(entSegs);
    }
}
