package org.vaadin.addons.joelpop.appnavlayout.ui.layout;

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
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
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

import java.util.ArrayList;
import java.util.Collections;
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
    // ValueSignal (not a plain field) so the window-resize Signal.effect in onAttach() can track
    // it as a dependency and react automatically when setNavSelector() changes it.
    private final ValueSignal<NavSelector> navSelectorSignal = new ValueSignal<>(NavSelector.defaultSelector());
    private int tabletMinShortSidePx = DEFAULT_TABLET_MIN_SHORT_SIDE_PX;
    private DeviceType deviceType;

    // Always-present layout containers
    final HorizontalLayout topBar;
    final HorizontalLayout viewHeaderSlot;

    private NavType activeNavType;
    private NavStrategy activeStrategy;

    // Buffered brand/user content — survives nav-type switches
    final List<Component> bufferedBrandContent = new ArrayList<>();
    Component bufferedUserMenu;

    // Holds the Location of the most recent completed navigation; updated in afterNavigation().
    // In Vaadin 25.2 this will be replaced by UI.routerStateSignal().map(RouterState::location).
    final ValueSignal<Location> navigationSignal = new ValueSignal<>(new Location(""));

    // Holds the active view component after each completed navigation; drives the view header slot.
    // In Vaadin 25.2 this will be replaced by UI.routerStateSignal().map(RouterState::currentView).
    private final ValueSignal<Component> currentViewSignal = new ValueSignal<>(null);

    private Function<MenuEntry, Supplier<Icon>>                     viewIconGenerator      = m -> null;
    private Function<MenuEntry, String>                             viewTitleGenerator     = m -> null;
    private Function<MenuEntry, NavGroup>                           viewNavGroupResolver   = m -> null;
    BiPredicate<String, String>                                     navPathMatcher         = String::equals;
    private boolean                                                 navMatchNested         = false;
    NavGrouper                                                      navGrouper             = new PathPrefixNavGrouper()
            .setNavGroupDefResolver(e -> viewNavGroupResolver.apply(e))
            .setViewIconGenerator(e -> viewIconGenerator.apply(e));
    ComponentRenderer<SideNavItem, NavNode>                         navNodeRenderer      = defaultNavNodeRenderer();
    private enum NavState { UNBUILT, BUILT, POPULATED }
    private NavState navState = NavState.UNBUILT;

    /**
     * Creates an {@code AppNavLayout} with an empty app title and the
     * {@link NavSelector#defaultSelector() default nav selector} (phone → touch,
     * tablet-portrait → rail, tablet-landscape/desktop → sidenav).
     */
    protected AppNavLayout() {
        super.setPrimarySection(Section.DRAWER);

        topBar = new HorizontalLayout();
        topBar.setWidthFull();
        topBar.setPadding(false);
        topBar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        topBar.getStyle().set("min-height", "var(--lumo-size-xl)");
        topBar.addClassName("app-top-bar");
        topBar.add(new DrawerToggle());

        viewHeaderSlot = new HorizontalLayout();
        viewHeaderSlot.setWidthFull();
        viewHeaderSlot.setPadding(true);
        viewHeaderSlot.setAlignItems(FlexComponent.Alignment.CENTER);
        viewHeaderSlot.addClassName("view-header-slot");
        viewHeaderSlot.setVisible(false);

        var topBlock = new VerticalLayout();
        topBlock.setPadding(false);
        topBlock.setSpacing(false);
        topBlock.setWidthFull();
        topBlock.add(topBar, viewHeaderSlot);
        super.addToNavbar(topBlock);

        Signal.effect(this, () -> rebuildViewHeader(currentViewSignal.get()));
    }

    /**
     * Creates an {@code AppNavLayout} with the given app title and the
     * {@link NavSelector#defaultSelector() default nav selector}.
     *
     * @param appTitle text displayed in the navigation bar; pass an empty string for no title
     */
    protected AppNavLayout(String appTitle) {
        this();
        this.appTitle = appTitle;
    }

    /**
     * Creates an {@code AppNavLayout} with the given app title and nav selector.
     *
     * @param appTitle    text displayed in the navigation bar; pass an empty string for no title
     * @param navSelector strategy that maps {@link DeviceType} and {@link Orientation} to a
     *                    {@link NavType}; use {@link NavSelector#defaultSelector()} for the
     *                    standard phone/tablet/desktop mapping
     */
    protected AppNavLayout(String appTitle, NavSelector navSelector) {
        this(appTitle);
        navSelectorSignal.set(navSelector);
    }

    // ——————————— Nav-type switching ————————————

    private void applyNavType(NavType navType) {
        if (navState == NavState.POPULATED && navType == this.activeNavType) {
            return;
        }

        if (navState != NavState.UNBUILT) {
            tearDownNav();
            navState = NavState.UNBUILT;
        }

        this.activeNavType = navType;
        activeStrategy = (navType == NavType.SIDENAV)
                ? new DesktopNavStrategy(this)
                : new TouchNavStrategy(this, navType == NavType.RAIL);
        buildNav();
        navState = NavState.BUILT;
        placeBrandAndUserContent();
        populateNav();
        rebuildViewHeader(currentViewSignal.peek());

        onNavTypeChanged(navType);
    }

    private void tearDownNav() {
        activeStrategy.tearDown();
        activeStrategy = null;
    }

    private void buildNav() {
        activeStrategy.build();
    }

    private void placeBrandAndUserContent() {
        if (navState == NavState.UNBUILT) {
            return;
        }
        activeStrategy.placeBrandAndUserContent();
    }

    private void populateNav() {
        // Pre-warm NavGroup-based nodes so path-based siblings merge with them.
        MenuConfiguration.getMenuEntries().stream()
                .filter(e -> viewNavGroupResolver.apply(e) != null)
                .forEach(navGrouper::nodeFor);

        activeStrategy.populate();
        navState = NavState.POPULATED;
    }

    private void repopulateNav() {
        if (navState == NavState.POPULATED) {
            navState = NavState.BUILT;
            populateNav();
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Null until async client-details round-trip completes; callers must null-check.
        var details = attachEvent.getUI().getPage().getExtendedClientDetails();
        deviceType = detectDeviceType(details, tabletMinShortSidePx);
        var page = attachEvent.getUI().getPage();
        // Registered with no direct applyNavType()
        // call beforehand: this effect's own immediate first fire performs the initial apply for
        // every device type. navSelectorSignal is a tracked dependency, so setNavSelector() later
        // re-fires this automatically — no manual re-registration needed.
        Signal.effect(this, () -> {
            var selector = navSelectorSignal.get();
            var size = page.windowSizeSignal().get();
            var orientation = size.width() >= size.height()
                    ? Orientation.LANDSCAPE : Orientation.PORTRAIT;
            applyNavType(selector.select(deviceType, orientation));
        });
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
     * belongs to a nav item's section. Used by touch/rail nav to highlight the active
     * icon. Default: {@link String#equals} (exact match).
     *
     * <p>Note: this predicate governs touch/rail active-item detection only. Desktop
     * {@link SideNav} highlights items via Vaadin's own router matching, which is
     * configured separately via {@link #setNavMatchNested}.
     */
    protected void setNavPathMatcher(BiPredicate<String, String> matcher) {
        navPathMatcher = matcher;
        repopulateNav();
    }

    /**
     * Controls whether desktop {@link SideNavItem}s use nested-route matching
     * ({@link SideNavItem#setMatchNested(boolean)}), which causes a parent item to
     * appear active whenever any of its child routes is current.
     * Default: {@code false} (items highlight only on an exact route match).
     *
     * <p>Set to {@code true} when sub-routes should keep the parent nav item
     * highlighted, for example when using a prefix-based path matcher.
     */
    protected void setNavMatchNested(boolean matchNested) {
        navMatchNested = matchNested;
        repopulateNav();
    }

    /**
     * Overrides the nav grouping strategy. Default: {@link PathPrefixNavGrouper}.
     *
     * <p><strong>Important:</strong> replacing the grouper severs the automatic wiring
     * that the default {@link PathPrefixNavGrouper} maintains to
     * {@link #setViewNavGroupResolver} and {@link #setViewIconGenerator}. After this
     * call, those two setters no longer influence grouping or leaf icons — they still
     * update internal fields and trigger a nav repopulation, but the custom grouper
     * is not consulted for those values. Configure the custom grouper directly before
     * passing it here.
     */
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
        navGrouper.reset();
        repopulateNav();
    }

    /** Sets the icon generator for leaf nav items. Return {@code null} or a supplier returning {@code null} to show no icon. */
    protected void setViewIconGenerator(Function<MenuEntry, Supplier<Icon>> generator) {
        viewIconGenerator = generator;
        repopulateNav();
    }

    /**
     * Sets the title generator for leaf nav items in the desktop {@link SideNav}.
     * Return {@code null} to fall back to {@code @Menu#title()}.
     *
     * <p>Note: this generator applies to desktop {@link SideNavItem} labels only.
     * Touch and rail nav item labels always use {@code NavNode.title()} (derived from
     * {@code @Menu(title=...)}); this generator has no effect on those surfaces.
     */
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
        if (activeStrategy == null) {
            // Fires once at construction via the Signal.effect below, before the first
            // applyNavType() runs; viewHeaderSlot is already empty/invisible at that point.
            return;
        }
        viewHeaderSlot.removeAll();
        activeStrategy.rebuildViewHeader(view);
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

    /**
     * Returns {@code true} if this session is using touch or rail nav (not desktop SideNav).
     *
     * <p>Returns {@code false} before the first {@link #onAttach(AttachEvent)} completes,
     * because device detection requires a client round-trip. Do not call this from a
     * subclass constructor.
     */
    protected boolean isMobile() {
        return activeNavType != null && activeNavType != NavType.SIDENAV;
    }

    // ——————————— Escape hatch ————————————

    /** Appends components directly to the top bar row. Prefer the named adaptive methods. */
    @Override
    public void addToNavbar(Component... components) {
        topBar.add(components);
    }

    private ComponentRenderer<SideNavItem, NavNode> defaultNavNodeRenderer() {
        return new ComponentRenderer<>(node -> {
            var title = node.menuEntry()
                    .map(e -> Optional.ofNullable(viewTitleGenerator.apply(e)).orElseGet(node::title))
                    .orElseGet(node::title);
            var item = node.menuEntry()
                    .map(e -> {
                        var navItem = new SideNavItem(title, RouteNavUtils.normalizedPath(e));
                        navItem.setMatchNested(navMatchNested);
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
            applyNavType(navSelectorSignal.get().select(deviceType, detectOrientation(details)));
        });
        return this;
    }

    /**
     * Overrides the strategy used to select the {@link NavType} for a given
     * device type and orientation. Default: {@link NavSelector#defaultSelector()}.
     *
     * <p>If the layout is already attached, the nav type is re-evaluated and
     * re-applied immediately via the window-resize effect registered in
     * {@link #onAttach}, which tracks this signal as a dependency.
     *
     * @return this, for chaining
     */
    public AppNavLayout setNavSelector(NavSelector selector) {
        navSelectorSignal.set(selector);
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
