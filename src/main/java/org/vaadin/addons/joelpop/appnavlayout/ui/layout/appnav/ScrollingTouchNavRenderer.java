package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
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
 * An alternative {@link NavRenderer} for {@link NavType#TOUCH} — a horizontally scrollable bar
 * of root sections in {@link NavSlots#touchBar()}, with fading edge chevrons instead of the
 * default "More" popover. Every root section gets its own item; when there are more than fit,
 * the bar scrolls instead of overflowing into a secondary surface.
 */
@CssImport("./scrolling-touch-nav.css")
@JsModule("./scrolling-touch-nav-scroll-indicator.js")
public class ScrollingTouchNavRenderer implements NavRenderer {

    private static final int ITEM_PX = 72;
    // Drives both the chevrons' actual flex-basis and the available-width math below, from this
    // one place, so the two can't drift apart.
    private static final int CHEVRON_ZONE_PX = 33;

    private HasComponents attachedSlot;
    private NavRenderContext cachedContext;
    private int windowWidth = 5 * ITEM_PX;
    private Registration resizeRegistration;
    private final Map<NavNode, Button> navButtons = new LinkedHashMap<>();
    private FlexLayout bar;
    private Div wrapper;
    private Button leftChevron;
    private Button rightChevron;

    @Override
    public NavType navType() {
        return NavType.TOUCH;
    }

    // Called on every navigation. buildItems runs only when the slot changes;
    // subsequent renders just update the active-item highlight.
    @Override
    public void render(NavRenderContext context) {
        cachedContext = context;
        var slot = context.slots().touchBar();
        if (slot != attachedSlot) {
            attachedSlot = slot;
            buildBar(slot);
            buildItems(context);
        }
        highlightActive(context);
    }

    private void buildBar(HasComponents slot) {
        bar = new FlexLayout();
        bar.addClassName("scrolling-touch-nav");
        bar.addClassName("nav-bar");

        bar.addAttachListener(e -> {
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

        bar.addDetachListener(e -> {
            if (resizeRegistration != null) {
                resizeRegistration.remove();
                resizeRegistration = null;
            }
        });

        // Built with the exact same structure AbstractTouchNavRenderer.navItem gives a regular
        // nav item — icon + a same-class label placeholder inside .touch-nav-content, wrapped via
        // new Button(content), with the .touch-nav-item class for the same padding/min-width
        // reset — rather than an icon-only button sized by a guessed pixel value. Height is never
        // set anywhere here: however tall that construction resolves to under whatever theme is
        // actually active (fit-content under the base theme, --lumo-button-size under Lumo's
        // compatibility CSS, whatever Aura does) is exactly how tall a real nav item resolves to
        // too, since it's the identical construction — so the two are guaranteed to match without
        // this add-on ever having to know or guess what that height actually is. The label
        // placeholder is what makes that guarantee hold: an icon-only button and an icon+label
        // button don't resolve to the same fit-content height, so pairing the chevron's icon with
        // an empty (but real, same-class) label keeps its content shape identical to a real item's,
        // the same reasoning navItem's own icon placeholder applies in the other direction for a
        // section with no icon. theme="tertiary" (not new Button(content) alone) is what picks up
        // the active theme's own accent color, the same mechanism createNavButton relies on — there
        // being no generic --vaadin-* accent-color token to reach for instead. NOT the
        // "touch-nav-item" class itself, though — that class's own :not(.active) rule forces the
        // muted secondary color, and a chevron never becomes .active (it's not a route item), so it
        // would be permanently stuck muted. scrolling-touch-nav.css gives the chevron classes the
        // same padding/min-width reset directly instead, without that side effect.
        //
        // A real flex sibling of bar (via wrapper's own display:flex, scrolling-touch-nav.css),
        // not an absolutely-positioned overlay on top of it — an overlay leaves whichever item
        // happens to scroll underneath with a smaller effective tap target than the others, since
        // part of its area is covered by the (higher z-index) chevron. As a normal flex child,
        // the chevron's own box is never shared with an item's; buildItems sizes it to
        // CHEVRON_ZONE_PX (a real tap target, not just however wide its icon+placeholder content
        // happens to be) exactly when scrolling is possible, and collapses it to zero otherwise so
        // it doesn't cost items any width when there's nothing to scroll to.
        var leftIcon = VaadinIcon.ANGLE_LEFT.create();
        leftIcon.setSize("20px");
        // A non-breaking space, not truly empty — some browsers collapse a literally
        // content-less inline element's line box to zero height, which would pull the icon
        // above it down to wherever a zero-height line puts it instead of where a real label's
        // line box would. visibility:hidden (scrolling-touch-nav.css) keeps it from ever
        // painting anything, independent of what character is inside it.
        var leftLabelPlaceholder = new Span(" ");
        leftLabelPlaceholder.addClassName("touch-nav-label");
        leftLabelPlaceholder.addClassName("touch-nav-label-placeholder");
        var leftContent = new Div(leftIcon, leftLabelPlaceholder);
        leftContent.addClassName("touch-nav-content");
        leftChevron = new Button(leftContent);
        leftChevron.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        leftChevron.addClassName("scrolling-touch-nav-chevron-left");
        leftChevron.getElement().getStyle().set("cursor", "pointer");

        var rightIcon = VaadinIcon.ANGLE_RIGHT.create();
        rightIcon.setSize("20px");
        var rightLabelPlaceholder = new Span(" ");
        rightLabelPlaceholder.addClassName("touch-nav-label");
        rightLabelPlaceholder.addClassName("touch-nav-label-placeholder");
        var rightContent = new Div(rightIcon, rightLabelPlaceholder);
        rightContent.addClassName("touch-nav-content");
        rightChevron = new Button(rightContent);
        rightChevron.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        rightChevron.addClassName("scrolling-touch-nav-chevron-right");
        rightChevron.getElement().getStyle().set("cursor", "pointer");

        wrapper = new Div(leftChevron, bar, rightChevron);
        wrapper.addClassName("scrolling-touch-nav-wrapper");

        slot.removeAll();
        slot.add(wrapper);
    }

    // Called on slot change and window resize. Rebuilds buttons and updates the
    // scrolling-touch-nav-has-overflow class that CSS and JS use to control chevron visibility.
    private void buildItems(NavRenderContext context) {
        navButtons.clear();
        bar.removeAll();

        var rootNodes = collectRootNodes(context);
        var n = Math.max(1, windowWidth / ITEM_PX);
        var needsScroll = rootNodes.size() > n;

        if (needsScroll) {
            // The bar's own real width, once the two chevron zones claim their share of the
            // wrapper's flex row, is narrower than windowWidth by CHEVRON_ZONE_PX on each side —
            // sized against that, not the full window, so n items actually fit per page instead
            // of slightly overrunning the now-narrower bar.
            var availableForItems = Math.max(ITEM_PX, windowWidth - 2 * CHEVRON_ZONE_PX);
            var itemWidth = String.format("%.4fpx", (double) availableForItems / n);
            bar.getStyle()
               .set("overflow-x", "auto")
               .set("overflow-y", "hidden")
               .set("scrollbar-width", "none")
               .set("scroll-snap-type", "x mandatory")
               .set("-webkit-overflow-scrolling", "touch")
               .set("touch-action", "pan-x")
               .remove("justify-content");

            for (var node : rootNodes) {
                var button = createNavButton(node, context);
                button.getStyle()
                      .set("flex", "0 0 " + itemWidth)
                      .set("scroll-snap-align", "start");
                navButtons.put(node, button);
                bar.add(button);
            }

            leftChevron.getStyle().set("flex", "0 0 " + CHEVRON_ZONE_PX + "px");
            rightChevron.getStyle().set("flex", "0 0 " + CHEVRON_ZONE_PX + "px");
            wrapper.addClassName("scrolling-touch-nav-has-overflow");
        }
        else {
            bar.getStyle()
               .remove("overflow-x")
               .remove("overflow-y")
               .remove("scrollbar-width")
               .remove("scroll-snap-type")
               .remove("-webkit-overflow-scrolling")
               .remove("touch-action")
               .set("justify-content", "space-evenly");

            for (var node : rootNodes) {
                var button = createNavButton(node, context);
                button.getStyle().set("flex", "1");
                navButtons.put(node, button);
                bar.add(button);
            }

            leftChevron.getStyle().set("flex", "0 0 0");
            rightChevron.getStyle().set("flex", "0 0 0");
            wrapper.removeClassName("scrolling-touch-nav-has-overflow");
        }
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

    private Button createNavButton(NavNode node, NavRenderContext context) {
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
            button.addClickListener(e -> UI.getCurrent().navigate(tc));
        }

        return button;
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

        navButtons.forEach((node, button) ->
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
