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
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.shared.Registration;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType;

import java.util.LinkedHashMap;
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

        var rootNodes = RootNavSupport.collectRootNodes(context);
        // Floor to nearest even number so the grid always has symmetric columns.
        var n = Math.max(2, (windowWidth / ITEM_PX) & ~1);

        var primaryBar = new FlexLayout();
        primaryBar.addClassName("nav-bar");
        // Explicit, not relying on the flex default (which computes to the same thing) — every
        // item is the same height regardless of whether its NavNode has an icon (see
        // createNavButton's own comment), so there's nothing left for BASELINE to align by
        // content shape; STRETCH is what AbstractTouchNavRenderer's row-direction bar uses for
        // the same reasoning.
        primaryBar.setAlignItems(FlexComponent.Alignment.STRETCH);

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

            // Overflow section below the primary bar; expands downward, visually painting past
            // AppLayout's navbar-bottom part without it ever needing to actually grow — see
            // expanding-touch-nav.css's own comment on why that matters (in short: it's what
            // keeps the chevron's one-time position measurement valid in both states). nav-bar
            // class matches AbstractTouchNavRenderer's nav-bar setup; STRETCH for the same
            // reasoning as primaryBar above (each wrapped row stretches its own items to that
            // row's tallest — a no-op once every item is already the same height).
            var overflowFlex = new FlexLayout();
            overflowFlex.addClassName("nav-bar");
            overflowFlex.setFlexWrap(FlexLayout.FlexWrap.WRAP);
            overflowFlex.setAlignItems(FlexComponent.Alignment.STRETCH);
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
        button.addClassName("expanding-touch-nav-chevron");
        button.addClickListener(e -> setExpanded(!expanded));

        // Opaque, round, bordered wrapper around the button — see expanding-touch-nav.css's own
        // comment on expanding-touch-nav-chevron-circle for why the visual styling lives here
        // rather than on the button itself. The button fills it completely — see that class's
        // own comment.
        var circle = new Div(button);
        circle.addClassName("expanding-touch-nav-chevron-circle");

        var row = new Div(circle);
        row.addClassName("expanding-touch-nav-chevron-row");
        // Positions this row (which takes up no space of its own — expanding-touch-nav.css sets
        // position:absolute) so the visible chevron *icon's* own center — the glyph actually
        // seen, not the larger circular button around it — lands exactly on AppLayout's real
        // navbar-bottom/content boundary. That boundary is NOT the same point as this row's own
        // container's top edge: Lumo's ::part(navbar-bottom) inherits a safe-area-inset-top
        // meant for the *top* bar, giving navbar-bottom its own padding-top, so its outer edge
        // sits measurably above wherever this add-on's own slotted content actually starts. A
        // given consumer's theme/CSS may patch that padding to something smaller, but not every
        // consumer will have, so reading the real rendered position of AppLayout's own
        // navbar-bottom part directly (rather than assuming any particular padding value) stays
        // correct regardless. Falls back to this row's own container's top if the part can't be
        // found for any reason, rather than throwing.
        row.getElement().executeJs("""
                requestAnimationFrame(() => {
                    const containerTop = this.parentElement.getBoundingClientRect().top;
                    const hostLayout = this.closest('vaadin-app-layout');
                    const navbarBottomPart = hostLayout?.shadowRoot
                            ?.querySelector('[part~="navbar-bottom"]');
                    const boundaryY = navbarBottomPart
                            ? navbarBottomPart.getBoundingClientRect().top
                            : containerTop;

                    const iconRect = $0.getBoundingClientRect();
                    const iconCenterY = iconRect.top + iconRect.height / 2;
                    const iconCenterOffsetFromRowTop = iconCenterY - this.getBoundingClientRect().top;

                    this.style.top = (boundaryY - iconCenterOffsetFromRowTop - containerTop) + 'px';
                });
                """, chevronIcon.getElement());
        return row;
    }

    private void setExpanded(boolean value) {
        expanded = value;
        container.getElement().getClassList().set("expanded", value);
        if (chevronIcon == null) {
            return;
        }
        if (value) {
            // Expanding: flip immediately, in the same instant the reveal animation starts —
            // attention shifts to the growing rows below before the flip itself would be
            // noticed on its own.
            chevronIcon.getElement().setAttribute("icon", "vaadin:chevron-down");
        }
        else {
            // Collapsing: flipping immediately (as above) would show the "collapsed" (up) icon
            // while the overflow rows are still fully visible, mid-shrink — an obvious mismatch
            // nothing else masks at that instant, unlike expanding above. Waits for the actual
            // CSS shrink (expanding-touch-nav.css's max-height transition) to finish before
            // flipping, so the two stay in sync instead of the icon jumping ahead of what's
            // still on screen — reads the transition's own duration via getComputedStyle rather
            // than hardcoding it, so this still tracks expanding-touch-nav.css's own duration if
            // that ever changes.
            //
            // The actual icon-attribute change happens through
            // chevronIcon.getElement().setAttribute (server-side), not a direct client-side DOM
            // mutation, so Flow's own server-side model of that attribute always matches what's
            // on screen — a client-side-only mutation would leave the server still believing the
            // icon said "down", making the next expand's setAttribute("icon", "...down") look
            // like a no-op and never reach the browser at all. Returning a Promise from the
            // executeJs and chaining .then() keeps the server as the one place that ever actually
            // sets this attribute, just delayed via the same measured duration.
            chevronIcon.getElement().executeJs("""
                    return new Promise((resolve) => {
                        const overflow = this.closest('.expanding-touch-nav')
                                ?.querySelector('.expanding-touch-nav-overflow');
                        if (!overflow) {
                            resolve();
                            return;
                        }
                        const durationMs = parseFloat(getComputedStyle(overflow).transitionDuration) * 1000;
                        setTimeout(resolve, durationMs);
                    });
                    """)
                    .then(ignored -> {
                        if (!expanded) {
                            chevronIcon.getElement().setAttribute("icon", "vaadin:chevron-up");
                        }
                    });
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
        // Button's height is fit-content around whatever this Div contains, so a section with no
        // icon needs an icon-sized *empty* placeholder here, not to be left out entirely — see
        // AbstractTouchNavRenderer.navItem's own comment for why: omitting it makes that item's
        // whole button shorter than its icon-bearing siblings in the same row, shifting its label
        // to a different vertical position. touch-nav-icon-placeholder is already loaded globally
        // via app-nav-layout.ts, not redefined here.
        if (icon != null) {
            content.add(icon);
        }
        else {
            var placeholder = new Div();
            placeholder.addClassName("touch-nav-icon-placeholder");
            content.add(placeholder);
        }
        content.add(label);

        var button = new Button();
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        button.addClassName("touch-nav-item");
        button.getElement().appendChild(content.getElement());

        Class<? extends Component> targetClass = node.menuEntry()
                .<Class<? extends Component>>map(MenuEntry::menuClass)
                .orElseGet(() -> RootNavSupport.firstChildOf(node, context));
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

    private void highlightActive(NavRenderContext context) {
        var activeRoot = RootNavSupport.activeRootFor(context);
        primaryButtons.forEach((node, button) ->
                button.getElement().getClassList().set("active", Objects.equals(node, activeRoot)));
        overflowButtons.forEach((node, button) ->
                button.getElement().getClassList().set("active", Objects.equals(node, activeRoot)));
    }
}
