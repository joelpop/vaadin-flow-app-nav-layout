package org.vaadin.addons.joelpop.appnavlayout.ui.layout;

import org.vaadin.addons.joelpop.appnavlayout.ui.touch.TouchNavBar;
import org.vaadin.addons.joelpop.appnavlayout.ui.touch.TouchSecondaryTabBar;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.DeviceType;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavSelector;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.Orientation;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGroup;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.PathPrefixNavGrouper;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.RouteNavUtils;
import org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderComponent;
import org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderTitle;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.ExtendedClientDetails;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base application layout providing adaptive navigation: a bottom icon bar
 * with a secondary tab bar on phone, a permanent left-strip rail on portrait
 * tablet, and a drawer-based {@link SideNav} on desktop.
 *
 * <p>Subclass, supply title and nav selector via {@code super(...)}, and
 * annotate with {@link com.vaadin.flow.router.Layout}. The {@link DrawerToggle},
 * drawer, {@link SideNav}, {@link TouchSecondaryTabBar}, and (on touch/rail devices)
 * {@link TouchNavBar} are wired automatically.
 *
 * <p>The {@link NavSelector} is re-evaluated dynamically on touch devices
 * whenever the viewport size changes (rotation, split-screen resize), switching
 * nav components in place without a page reload.
 *
 * <p>Use the purpose-named methods to place adaptive content:
 * <ul>
 *   <li>{@link #addBrandContent} — header on desktop, drawer top on mobile</li>
 *   <li>{@link #setUserMenu} — header trailing on desktop, drawer bottom on mobile</li>
 * </ul>
 *
 * <p>Override the nav grouping strategy via {@link #setNavGrouper} and the
 * {@link SideNavItem} renderer via {@link #setNavNodeRenderer}. Changes to any
 * nav configuration setter take effect immediately, even after attachment.
 *
 * <p>Per-view header content is assembled automatically on each navigation.
 * Views implementing {@link HasViewHeaderTitle} supply an icon+title component
 * (desktop only). Views implementing {@link HasViewHeaderComponent} supply an
 * action component shown on both platforms.
 *
 * <p>{@link #addToNavbar(Component...)} is preserved as an escape hatch.
 *
 * <p>All environment-specific values are supplied by the subclass constructor.
 *
 * <p><b>Rail mode is built entirely from standard {@code AppLayout} constructs.</b>
 * The rail is {@code AppLayout}'s own {@code navbar-bottom} slot (the same slot
 * the ordinary touch bottom bar uses), repositioned and restyled via CSS keyed
 * on the {@code nav-rail} attribute this class sets on itself — not a distinct
 * part of its own. Any companion component that understands
 * {@code AppLayout}'s standard {@code navbar-top}/{@code navbar-bottom} contract
 * therefore interoperates with rail mode automatically.
 */
@JsModule("./app-nav-layout.ts")
public abstract class AppNavLayout extends AppLayout implements AfterNavigationObserver {

    private static final int DEFAULT_TABLET_MIN_SHORT_SIDE_PX = 768;

    private String appTitle = "";
    private NavSelector navSelector = NavSelector.defaultSelector();
    private int tabletMinShortSidePx = DEFAULT_TABLET_MIN_SHORT_SIDE_PX;
    private DeviceType deviceType;

    // Always-present layout containers
    private final HorizontalLayout topBar;
    private final HorizontalLayout viewHeaderSlot;

    // Desktop nav components — non-null only in desktop mode
    private HorizontalLayout brandContainer;
    private Span desktopSpacer;
    private HorizontalLayout userContainer;
    private SideNav sideNav;

    // Touch/rail nav components — non-null only in touch or rail mode
    private VerticalLayout brandDrawerSlot;
    private VerticalLayout userDrawerSlot;
    private TouchSecondaryTabBar touchSecondaryTabBar;
    private TouchNavBar touchNavBar;
    private NavType activeNavType;

    // Buffered brand/user content — survives nav-type switches
    private final List<Component> bufferedBrandContent = new ArrayList<>();
    private Component bufferedUserMenu;

    // Holds the Location of the most recent completed navigation; updated in afterNavigation().
    // In Vaadin 25.2 this will be replaced by UI.routerStateSignal().map(RouterState::location).
    private final ValueSignal<Location> navigationSignal = new ValueSignal<>(new Location(""));

    // Holds the active view component after each completed navigation; drives the view header slot.
    // In Vaadin 25.2 this will be replaced by UI.routerStateSignal().map(RouterState::currentView).
    private final ValueSignal<Component> currentViewSignal = new ValueSignal<>(null);

    private Function<MenuEntry, Supplier<Icon>>                     viewIconGenerator      = m -> null;
    private Function<MenuEntry, String>                             viewTitleGenerator     = m -> null;
    private Function<MenuEntry, NavGroup>                           viewNavGroupResolver   = m -> null;
    private BiPredicate<String, String>                             navPathMatcher         = String::equals;
    private NavGrouper                                              navGrouper             = new PathPrefixNavGrouper()
            .setNavGroupDefResolver(e -> viewNavGroupResolver.apply(e))
            .setViewIconGenerator(e -> viewIconGenerator.apply(e));
    private ComponentRenderer<SideNavItem, NavNode>                 navNodeRenderer      = defaultNavNodeRenderer();
    private boolean navBuilt = false;
    private boolean navPopulated = false;

    protected AppNavLayout() {
        super.setPrimarySection(Section.DRAWER);

        topBar = new HorizontalLayout();
        topBar.setWidthFull();
        topBar.setPadding(false);
        topBar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        topBar.add(new DrawerToggle());

        viewHeaderSlot = new HorizontalLayout();
        viewHeaderSlot.setWidthFull();
        viewHeaderSlot.setPadding(true);
        viewHeaderSlot.setAlignItems(FlexComponent.Alignment.CENTER);
        viewHeaderSlot.setVisible(false);

        var topBlock = new VerticalLayout();
        topBlock.setPadding(false);
        topBlock.setSpacing(false);
        topBlock.setWidthFull();
        topBlock.add(topBar, viewHeaderSlot);
        super.addToNavbar(topBlock);

        Signal.effect(this, () -> rebuildViewHeader(currentViewSignal.get()));
    }

    protected AppNavLayout(String appTitle) {
        this();
        this.appTitle = appTitle;
    }

    protected AppNavLayout(String appTitle, NavSelector navSelector) {
        this(appTitle);
        this.navSelector = navSelector;
    }

    // ——————————— Nav-type switching ————————————

    private void applyNavType(NavType navType) {
        if (navBuilt && navType == this.activeNavType) {
            if (!navPopulated) {
                populateNav();
                rebuildViewHeader(currentViewSignal.peek());
            }
            return;
        }

        if (navBuilt) {
            tearDownNav();
            navBuilt = false;
            navPopulated = false;
        }

        this.activeNavType = navType;
        buildNav();
        navBuilt = true;
        placeBrandAndUserContent();
        populateNav();
        rebuildViewHeader(currentViewSignal.peek());

        onNavTypeChanged(navType);
    }

    private void tearDownNav() {
        if (activeNavType != NavType.SIDENAV) {
            topBar.remove(touchSecondaryTabBar);
            touchNavBar.getElement().removeFromParent();
            brandDrawerSlot.getElement().removeFromParent();
            userDrawerSlot.getElement().removeFromParent();
            touchSecondaryTabBar = null;
            touchNavBar = null;
            brandDrawerSlot = null;
            userDrawerSlot = null;
            getStyle().remove("--vaadin-app-layout-touch-optimized");
            if (activeNavType == NavType.RAIL) {
                getElement().removeAttribute("nav-rail");
                getStyle().remove("--nav-rail-width");
                getStyle().remove("padding-inline-start");
            }
        }
        else {
            topBar.remove(brandContainer, desktopSpacer, userContainer);
            sideNav.getElement().removeFromParent();
            viewHeaderSlot.removeClassNames(LumoUtility.Border.BOTTOM, LumoUtility.BorderColor.CONTRAST_10);
            brandContainer = null;
            desktopSpacer = null;
            userContainer = null;
            sideNav = null;
        }
    }

    private void buildNav() {
        if (activeNavType != NavType.SIDENAV) {
            touchSecondaryTabBar = new TouchSecondaryTabBar(navigationSignal);
            touchSecondaryTabBar.getStyle().set("min-width", "0");
            topBar.add(touchSecondaryTabBar);
            topBar.expand(touchSecondaryTabBar);

            brandDrawerSlot = new VerticalLayout();
            brandDrawerSlot.setPadding(false);
            brandDrawerSlot.setSpacing(false);

            userDrawerSlot = new VerticalLayout();
            userDrawerSlot.setPadding(false);
            userDrawerSlot.setSpacing(false);
            userDrawerSlot.getStyle().setMarginTop("auto");

            var page = UI.getCurrent().getPage();
            var direction = activeNavType == NavType.RAIL
                    ? FlexLayout.FlexDirection.COLUMN
                    : FlexLayout.FlexDirection.ROW;
            touchNavBar = new TouchNavBar(navigationSignal, page, direction);

            if (activeNavType == NavType.RAIL) {
                getElement().setAttribute("nav-rail", "");
                getStyle().set("--nav-rail-width", "5rem");
                getStyle().set("padding-inline-start", "var(--nav-rail-width)");
                setDrawerOpened(false);
            }

            // touch-optimized ensures the navbar-bottom slot is rendered by AppLayout
            getStyle().set("--vaadin-app-layout-touch-optimized", "true");
            super.addToDrawer(brandDrawerSlot, userDrawerSlot);
            super.addToNavbar(true, touchNavBar);
        }
        else {
            brandContainer = new HorizontalLayout();
            brandContainer.setPadding(false);
            brandContainer.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

            desktopSpacer = new Span();
            desktopSpacer.getStyle().set("flex", "1");

            userContainer = new HorizontalLayout();
            userContainer.setPadding(false);
            userContainer.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);

            topBar.add(brandContainer, desktopSpacer, userContainer);

            viewHeaderSlot.addClassNames(LumoUtility.Border.BOTTOM, LumoUtility.BorderColor.CONTRAST_10);

            sideNav = new SideNav();
            super.addToDrawer(sideNav);
        }
    }

    private void placeBrandAndUserContent() {
        if (!navBuilt) {
            return;
        }
        if (activeNavType != NavType.SIDENAV) {
            brandDrawerSlot.removeAll();
            bufferedBrandContent.forEach(brandDrawerSlot::add);
            userDrawerSlot.removeAll();
            if (bufferedUserMenu != null) {
                userDrawerSlot.add(bufferedUserMenu);
            }
        }
        else {
            brandContainer.removeAll();
            bufferedBrandContent.forEach(brandContainer::add);
            userContainer.removeAll();
            if (bufferedUserMenu != null) {
                userContainer.add(bufferedUserMenu);
            }
        }
    }

    private void populateNav() {
        // Pre-warm NavGroup-based nodes so path-based siblings merge with them.
        MenuConfiguration.getMenuEntries().stream()
                .filter(e -> viewNavGroupResolver.apply(e) != null)
                .forEach(navGrouper::nodeFor);

        if (activeNavType != NavType.SIDENAV) {
            touchNavBar.setPathMatcher(navPathMatcher);
            touchSecondaryTabBar.setNavGrouper(navGrouper);
            touchNavBar.setNavGrouper(navGrouper);
        }
        else {
            populateSideNav();
        }
        navPopulated = true;
    }

    private void repopulateNav() {
        if (navPopulated) {
            navPopulated = false;
            navGrouper.reset();
            populateNav();
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Null until async client-details round-trip completes; callers must null-check.
        var details = attachEvent.getUI().getPage().getExtendedClientDetails();
        deviceType = detectDeviceType(details, tabletMinShortSidePx);
        applyNavType(navSelector.select(deviceType, detectOrientation(details)));
        if (deviceType != DeviceType.DESKTOP) {
            var page = attachEvent.getUI().getPage();
            Signal.effect(this, () -> {
                var size = page.windowSizeSignal().get();
                var orientation = size.width() >= size.height()
                        ? Orientation.LANDSCAPE : Orientation.PORTRAIT;
                applyNavType(navSelector.select(deviceType, orientation));
            });
        }
    }

    // ——————————— Adaptive content API ————————————

    /**
     * Adds brand/identity content.
     * Desktop: placed in the header after the {@link DrawerToggle}.
     * Mobile: placed at the top of the navigation drawer.
     * Pass individual components; do not pre-wrap in a layout container.
     */
    protected void addBrandContent(Component... components) {
        Collections.addAll(bufferedBrandContent, components);
        placeBrandAndUserContent();
    }

    /**
     * Sets the user-context widget (avatar, name, account menu).
     * Desktop: placed trailing in the header.
     * Mobile: placed at the bottom of the navigation drawer.
     */
    protected void setUserMenu(Component userMenu) {
        this.bufferedUserMenu = userMenu;
        placeBrandAndUserContent();
    }

    // ——————————— NavGrouper / renderer API ————————————

    /**
     * Overrides the predicate used to determine whether the current navigation path
     * belongs to a nav item's section. Applied consistently across all nav renderings.
     * Default: {@link String#equals} (exact match).
     */
    protected void setNavPathMatcher(BiPredicate<String, String> matcher) {
        navPathMatcher = matcher;
        repopulateNav();
    }

    /** Overrides the nav grouping strategy. Default: {@link PathPrefixNavGrouper}. */
    protected void setNavGrouper(NavGrouper grouper) {
        navGrouper = grouper;
        repopulateNav();
    }

    /** Overrides the {@link SideNavItem} renderer for the desktop side nav. */
    protected void setNavNodeRenderer(ComponentRenderer<SideNavItem, NavNode> renderer) {
        navNodeRenderer = renderer;
        repopulateNav();
    }

    /**
     * Sets the nav-group resolver used by the default {@link PathPrefixNavGrouper}.
     * Return {@code null} to use path-based grouping for an entry.
     */
    protected void setViewNavGroupResolver(Function<MenuEntry, NavGroup> resolver) {
        viewNavGroupResolver = resolver;
        repopulateNav();
    }

    /** Sets the icon generator for leaf nav items. Return {@code null} or a supplier returning {@code null} to show no icon. */
    protected void setViewIconGenerator(Function<MenuEntry, Supplier<Icon>> generator) {
        viewIconGenerator = generator;
        repopulateNav();
    }

    /** Sets the title generator for leaf nav items. Return {@code null} to fall back to {@code @Menu#title()}. */
    protected void setViewTitleGenerator(Function<MenuEntry, String> generator) {
        viewTitleGenerator = generator;
        repopulateNav();
    }

    // ——————————— Navigation-driven view header ————————————

    /**
     * On each navigation: assembles the {@code viewHeaderSlot} from the current
     * view's {@link HasViewHeaderTitle} and {@link HasViewHeaderComponent} if present.
     *
     * <p>Desktop: title (left) + action component (right) when either is present.
     * Mobile: action component only; title is omitted to conserve vertical space.
     */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        navigationSignal.set(event.getLocation());
        currentViewSignal.set(getContent());
    }

    private void rebuildViewHeader(Component view) {
        var titleComponent = (view instanceof HasViewHeaderTitle h) ? h.getViewHeaderTitle() : null;
        var actionComponent = (view instanceof HasViewHeaderComponent h) ? h.getViewHeaderComponent() : null;

        viewHeaderSlot.removeAll();

        if (activeNavType != NavType.SIDENAV) {
            viewHeaderSlot.setVisible(actionComponent != null);
            if (actionComponent != null) {
                viewHeaderSlot.add(actionComponent);
            }
        }
        else {
            var hasContent = titleComponent != null || actionComponent != null;
            viewHeaderSlot.setVisible(hasContent);
            if (titleComponent != null) {
                viewHeaderSlot.add(titleComponent);
            }
            if (actionComponent != null) {
                viewHeaderSlot.add(actionComponent);
            }
        }
    }

    // ——————————— Protected hooks ————————————

    /**
     * Called whenever the active {@link NavType} is determined — including on
     * first attachment, not just on later changes. Default is a no-op.
     * Override to react to nav-type changes in subclass or companion code.
     */
    protected void onNavTypeChanged(NavType navType) {
    }

    /** Application title supplied by the subclass. */
    protected String getAppTitle() {
        return appTitle;
    }

    /** Whether this session is using touch or rail nav (not desktop SideNav). */
    protected boolean isMobile() {
        return activeNavType != null && activeNavType != NavType.SIDENAV;
    }

    // ——————————— Escape hatch ————————————

    /** Appends components directly to the top bar row. Prefer the named adaptive methods. */
    @Override
    public void addToNavbar(Component... components) {
        topBar.add(components);
    }

    // ——————————— Desktop nav ————————————

    private void populateSideNav() {
        sideNav.removeAll();
        var sideNavItems = new LinkedHashMap<NavNode, SideNavItem>();

        MenuConfiguration.getMenuEntries().forEach(entry -> {
            var node = navGrouper.nodeFor(entry);
            ensureAncestors(node, sideNavItems, navNodeRenderer);
            var item = navNodeRenderer.createComponent(node);
            sideNavItems.put(node, item);
            node.parent()
                    .ifPresentOrElse(
                            parent -> sideNavItems.get(parent).addItem(item),
                            () -> sideNav.addItem(item));
        });
    }

    private void ensureAncestors(NavNode node, LinkedHashMap<NavNode, SideNavItem> sideNavItems,
                                  ComponentRenderer<SideNavItem, NavNode> renderer) {
        node.parent().ifPresent(parent -> {
            if (!sideNavItems.containsKey(parent)) {
                ensureAncestors(parent, sideNavItems, renderer);
                var item = renderer.createComponent(parent);
                sideNavItems.put(parent, item);
                parent.parent()
                        .ifPresentOrElse(
                                grandparent -> sideNavItems.get(grandparent).addItem(item),
                                () -> sideNav.addItem(item));
            }
        });
    }

    private ComponentRenderer<SideNavItem, NavNode> defaultNavNodeRenderer() {
        return new ComponentRenderer<>(node -> {
            var title = node.menuEntry()
                    .map(e -> Optional.ofNullable(viewTitleGenerator.apply(e)).orElseGet(node::title))
                    .orElseGet(node::title);
            var item = node.menuEntry()
                    .map(e -> {
                        var navItem = new SideNavItem(title, RouteNavUtils.normalizedPath(e));
                        navItem.setMatchNested(navPathMatcher.test("a/b", "a"));
                        return navItem;
                    })
                    .orElseGet(() -> new SideNavItem(title));
            var icon = node.menuEntry()
                    .flatMap(e -> Optional.ofNullable(viewIconGenerator.apply(e)).map(Supplier::get))
                    .or(node::createIcon);
            icon.ifPresent(item::setPrefixComponent);
            return item;
        });
    }

    // ——————————— Device configuration API ————————————

    /**
     * Overrides the physical-screen-shorter-side threshold, in CSS pixels,
     * used to distinguish {@link DeviceType#TABLET} from {@link DeviceType#PHONE}
     * among touch devices (default {@code 768}, matching
     * {@code vaadin-flow-app-headroom}'s equivalent device detection).
     *
     * <p>If the layout is already attached, device type is re-evaluated and the
     * nav type re-applied immediately.
     *
     * @return this, for chaining
     * @throws IllegalArgumentException if {@code px} is negative
     */
    public AppNavLayout setTabletMinShortSidePx(int px) {
        requireNonNegative(px, "tabletMinShortSidePx");
        this.tabletMinShortSidePx = px;
        getUI().ifPresent(ui -> {
            var details = ui.getPage().getExtendedClientDetails();
            deviceType = detectDeviceType(details, tabletMinShortSidePx);
            applyNavType(navSelector.select(deviceType, detectOrientation(details)));
        });
        return this;
    }

    /**
     * Overrides the strategy used to select the {@link NavType} for a given
     * device type and orientation. Default: {@link NavSelector#defaultSelector()}.
     *
     * <p>If the layout is already attached, the nav type is re-evaluated and
     * re-applied immediately.
     *
     * @return this, for chaining
     */
    public AppNavLayout setNavSelector(NavSelector selector) {
        this.navSelector = selector;
        getUI().ifPresent(ui -> {
            var details = ui.getPage().getExtendedClientDetails();
            applyNavType(navSelector.select(deviceType, detectOrientation(details)));
        });
        return this;
    }

    /** Updates the application title returned by {@link #getAppTitle()}. */
    public AppNavLayout setAppTitle(String title) {
        this.appTitle = title;
        return this;
    }

    // ——————————— Device detection ————————————

    private static DeviceType detectDeviceType(ExtendedClientDetails details, int tabletMinShortSidePx) {
        if (details == null || !details.isTouchDevice()) {
            return DeviceType.DESKTOP;
        }
        int minDim = Math.min(details.getScreenWidth(), details.getScreenHeight());
        return minDim >= tabletMinShortSidePx ? DeviceType.TABLET : DeviceType.PHONE;
    }

    private static Orientation detectOrientation(ExtendedClientDetails details) {
        if (details == null) {
            return Orientation.LANDSCAPE;
        }
        return details.getWindowInnerWidth() >= details.getWindowInnerHeight()
                ? Orientation.LANDSCAPE : Orientation.PORTRAIT;
    }

    private static void requireNonNegative(int value, String paramName) {
        if (value < 0) {
            throw new IllegalArgumentException(paramName + " must be non-negative, got: " + value);
        }
    }
}
