package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.DeviceType;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.Orientation;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGroup;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.PathPrefixNavGrouper;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.RouteNavUtils;
import org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderComponent;
import org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderTitle;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.ComponentUtil;
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
import com.vaadin.flow.shared.Registration;
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
 * with a secondary tab bar on phone, a permanent left-strip rail on tablet,
 * and a drawer-based {@link SideNav} on desktop.
 *
 * <p>Subclass, supply a title via {@code super(...)}, and annotate with
 * {@link com.vaadin.flow.router.Layout}. The {@link DrawerToggle} and drawer are wired
 * automatically; the nav-item content for each of the five device/orientation scenarios
 * this layout distinguishes — desktop, tablet portrait, tablet landscape, phone portrait, and
 * phone landscape — is built by an independently pluggable {@link NavRenderer}, supplied
 * lazily since only one scenario is ever relevant to a given session.
 * See {@link #setDesktopNavRenderer}, {@link #setTabletPortraitNavRenderer},
 * {@link #setTabletLandscapeNavRenderer}, {@link #setPhonePortraitNavRenderer}, and
 * {@link #setPhoneLandscapeNavRenderer} (plus the {@link #setTabletNavRenderer}/
 * {@link #setPhoneNavRenderer} convenience setters covering both orientations at once),
 * defaulting to {@link SideNavDrawerNavRenderer} (desktop), {@link SideRailNavRenderer}
 * (tablet, both orientations), and {@link TouchBarNavRenderer} (phone, both orientations)
 * respectively. The {@link NavType} chrome built for a scenario (rail vs.
 * bottom bar vs. drawer) is not a separate choice — it's {@link NavRenderer#navType()} of
 * whichever renderer is configured for that scenario, so a scenario's renderer and its chrome
 * can never disagree with each other.
 *
 * <p>The active scenario is re-evaluated dynamically on touch devices whenever the viewport
 * size changes (rotation, split-screen resize), switching nav components in place without a
 * page reload.
 *
 * <p>Use the purpose-named methods to place adaptive content:
 * <ul>
 *   <li>{@link #addBranding} — header on desktop, drawer top on mobile</li>
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

    private int tabletMinShortSidePx = DEFAULT_TABLET_MIN_SHORT_SIDE_PX;
    private DeviceType deviceType;
    // Tracked alongside deviceType (not just computed ad hoc inside the resize effect) so
    // resolveScenarioRenderer() can resolve the active scenario at any time, not only at the
    // moment the effect fires.
    private Orientation orientation;

    // Always-present layout containers
    final HorizontalLayout topBar;
    final HorizontalLayout viewHeaderSlot;
    // The VerticalLayout stacking topBar and viewHeaderSlot as full-width header rows — private,
    // not exposed directly; a NavStrategy needing an additional row goes through
    // insertHeaderRow()/removeHeaderRow() below instead, so this class keeps ownership of its own
    // structure rather than letting a strategy reach in and manipulate it directly.
    private final VerticalLayout topBlock;
    // The always-present drawer toggle, shown by default; hidden only by a NavStrategy whose
    // NavType has no drawer to toggle (currently HeaderNavStrategy) via
    // setDrawerToggleVisible() below, rather than exposing this field for a strategy to call
    // .setVisible() on directly.
    private final DrawerToggle drawerToggle;

    private NavType activeNavType;
    private NavStrategy activeStrategy;
    // The renderer instance currently active for this session — set by applyNavType(), read
    // directly by NavStrategy.populate() implementations (same package-private field-access
    // pattern as topBar/navGrouper below). Also the identity applyNavType() guards its rebuild
    // decision on: two scenarios can share a NavType while resolving to different renderer
    // instances (e.g. two different SIDENAV-based renderers for tablet portrait vs. landscape),
    // so the guard can't key on NavType alone.
    NavRenderer activeRenderer;

    // Buffered brand/user content — survives nav-type switches
    final List<Component> bufferedBranding = new ArrayList<>();
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
    // The lambdas forward to the mutable viewNavGroupResolver/viewIconGenerator fields above
    // rather than closing over their initial (no-op) values, so setViewNavGroupResolver()/
    // setViewIconGenerator() affect this already-created grouper without needing to rebuild it.
    NavGrouper                                                      navGrouper             = new PathPrefixNavGrouper()
            .setNavGroupDefResolver(e -> viewNavGroupResolver.apply(e))
            .setViewIconGenerator(e -> viewIconGenerator.apply(e));
    ComponentRenderer<SideNavItem, NavNode>                         navNodeRenderer      = defaultNavNodeRenderer();
    // One independently overridable, lazily-materialized NavRenderer per device/orientation
    // scenario — deliberately NOT keyed by NavType, so a scenario can be given its own renderer
    // even when it currently shares a NavType (and therefore a NavStrategy/chrome) with another
    // scenario, e.g. tablet-portrait and tablet-landscape both resolve to NavType.RAIL by default
    // but are independently configurable here. Only one scenario is ever relevant to a given
    // session (deviceType is fixed once attached), so each is a memoize()d Supplier — constructed
    // at most once, on first actual use, rather than eagerly building all five up front. Both
    // tablet and phone deliberately share one memoized Supplier across their own two fields by
    // default (see memoize()'s javadoc for why that matters, not just for laziness).
    Supplier<NavRenderer>                                           desktopNavRenderer         = memoize(SideNavDrawerNavRenderer::new);
    Supplier<NavRenderer>                                           tabletPortraitNavRenderer  = memoize(SideRailNavRenderer::new);
    Supplier<NavRenderer>                                           tabletLandscapeNavRenderer = tabletPortraitNavRenderer;
    Supplier<NavRenderer>                                           phonePortraitNavRenderer   = memoize(TouchBarNavRenderer::new);
    Supplier<NavRenderer>                                           phoneLandscapeNavRenderer  = phonePortraitNavRenderer;

    /**
     * Tracks this layout's rebuild lifecycle. Valid transitions: {@code UNBUILT → BUILT} via
     * {@link #buildNav()}, {@code BUILT → POPULATED} via {@link #populateNav()}, and any
     * state {@code → UNBUILT} via {@link #tearDownNav()}.
     */
    private enum NavState {
        /** No nav components created yet. */
        UNBUILT,
        /** Structural shell (containers, active {@link NavStrategy}) created, awaiting population. */
        BUILT,
        /** Fully rendered and reactive — nav items populated from the current {@link NavGrouper}. */
        POPULATED
    }
    private NavState navState = NavState.UNBUILT;

    /**
     * Creates an {@code AppNavLayout} with the default renderers for each scenario
     * (phone → touch, tablet → rail, desktop → sidenav).
     */
    protected AppNavLayout() {
        super.setPrimarySection(Section.DRAWER);

        topBar = new HorizontalLayout();
        topBar.setWidthFull();
        topBar.setPadding(false);
        topBar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        // Prevents topBar from being flex-centred inside navbar-top (and jumping vertically)
        // when viewHeaderSlot is hidden; see the topBarYStableWhen* IT tests.
        topBar.getStyle().set("min-height", "var(--lumo-size-xl)");
        topBar.addClassName("app-top-bar");
        drawerToggle = new DrawerToggle();
        topBar.add(drawerToggle);

        viewHeaderSlot = new HorizontalLayout();
        viewHeaderSlot.setWidthFull();
        viewHeaderSlot.setPadding(true);
        viewHeaderSlot.setAlignItems(FlexComponent.Alignment.CENTER);
        viewHeaderSlot.addClassName("view-header-slot");
        viewHeaderSlot.setVisible(false);

        topBlock = new VerticalLayout();
        topBlock.setPadding(false);
        topBlock.setSpacing(false);
        topBlock.setWidthFull();
        topBlock.add(topBar, viewHeaderSlot);
        super.addToNavbar(topBlock);

        // Fires immediately with currentViewSignal == null, before any NavStrategy exists;
        // rebuildViewHeader() explicitly no-ops in that case (see its activeStrategy == null guard).
        Signal.effect(this, () -> rebuildViewHeader(currentViewSignal.get()));
    }

    // ——————————— Header row / drawer toggle access for NavStrategy implementations ————————————

    /**
     * Inserts {@code row} as an additional full-width header row, between {@link #topBar} and
     * {@link #viewHeaderSlot} — for a {@link NavStrategy} whose chrome needs more than
     * {@code topBar}'s own single row (e.g. {@link HeaderNavStrategy}'s own drill-down row).
     */
    void insertHeaderRow(Component row) {
        topBlock.addComponentAtIndex(1, row);
    }

    /** Removes a row previously added via {@link #insertHeaderRow}. */
    void removeHeaderRow(Component row) {
        topBlock.remove(row);
    }

    /**
     * Shows or hides the shared {@link DrawerToggle} — hidden by a {@link NavStrategy} whose
     * {@link NavType} has no drawer to toggle (currently only {@link HeaderNavStrategy}).
     */
    void setDrawerToggleVisible(boolean visible) {
        drawerToggle.setVisible(visible);
    }

    // ——————————— Nav-type switching ————————————

    /**
     * Resolves the {@link NavRenderer} for the current {@link DeviceType}/{@link Orientation}
     * scenario, then applies it. No-ops if the resolved renderer is identical (by reference) to
     * the already-{@link NavState#POPULATED} {@link #activeRenderer} — deliberately keyed on
     * renderer identity, not {@link NavType}, since two scenarios can share a NavType while
     * resolving to different renderer instances (e.g. two different SIDENAV-based renderers for
     * tablet portrait vs. landscape); a NavType-keyed guard would silently skip the rebuild those
     * need. Otherwise tears down the previous {@link NavStrategy} (if one was built), creates the
     * strategy for the renderer's {@link NavRenderer#navType() navType()}, then runs it through
     * the rebuild sequence: {@link #buildNav()} (structural components) →
     * {@link #placeBrandAndUserContent()} (buffered brand/user content) → {@link #populateNav()}
     * (nav items from the current {@link NavGrouper}, reaching {@link NavState#POPULATED}) →
     * {@link #rebuildViewHeader} (view header for the current view). Fires
     * {@link NavTypeChangedEvent} last, once the new nav type is fully in place — note its
     * {@code navType}/{@code previousNavType} can be equal on a renderer-only rebuild.
     */
    private void applyNavType() {
        var renderer = resolveScenarioRenderer();
        if (navState == NavState.POPULATED && renderer == this.activeRenderer) {
            return;
        }

        if (navState != NavState.UNBUILT) {
            tearDownNav();
            navState = NavState.UNBUILT;
        }

        var navType = renderer.navType();
        var previousNavType = this.activeNavType;
        this.activeRenderer = renderer;
        this.activeNavType = navType;
        activeStrategy = switch (navType) {
            case SIDENAV -> new DesktopNavStrategy(this);
            case HEADER -> new HeaderNavStrategy(this);
            case TOUCH, RAIL -> new TouchNavStrategy(this, navType);
        };
        buildNav();
        navState = NavState.BUILT;
        placeBrandAndUserContent();
        populateNav();
        rebuildViewHeader(currentViewSignal.peek());

        var event = new NavTypeChangedEvent(this, navType, previousNavType);
        onNavTypeChanged(event);
        ComponentUtil.fireEvent(this, event);
    }

    private void tearDownNav() {
        activeStrategy.tearDown();
        activeStrategy = null;
    }

    private void buildNav() {
        activeStrategy.build();
    }

    private void placeBrandAndUserContent() {
        // No-op before nav is built (e.g. called from a subclass constructor via addBranding()/
        // setUserMenu()); content is already buffered and gets placed by applyNavType() instead.
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
        // Never null. Browser details are collected during UI init, so in normal operation
        // this is already fully populated by the time onAttach() runs; only in rare cases does
        // it return a placeholder with -1 dimensions (not null) if collection hasn't finished.
        var details = attachEvent.getUI().getPage().getExtendedClientDetails();
        deviceType = detectDeviceType(details, tabletMinShortSidePx);
        var page = attachEvent.getUI().getPage();
        // Registered with no direct applyNavType() call beforehand: this effect's own immediate
        // first fire performs the initial apply for every device type.
        Signal.effect(this, () -> {
            var size = page.windowSizeSignal().get();
            orientation = size.width() >= size.height()
                    ? Orientation.LANDSCAPE : Orientation.PORTRAIT;
            applyNavType();
        });
    }

    // ——————————— Adaptive content API ————————————

    /**
     * Adds brand/identity content.
     * Desktop: placed in the header after the {@link DrawerToggle}.
     * Mobile: placed at the top of the navigation drawer.
     * Pass individual components; do not pre-wrap in a layout container.
     */
    protected void addBranding(Component... components) {
        Collections.addAll(bufferedBranding, components);
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
     * Overrides the {@link NavRenderer} used for the desktop scenario, constructed at most once,
     * the first time it's actually needed. Default: {@link SideNavDrawerNavRenderer}.
     *
     * <p>If this scenario is currently active, it is torn down and rebuilt immediately.
     */
    protected void setDesktopNavRenderer(Supplier<NavRenderer> renderer) {
        desktopNavRenderer = memoize(renderer);
        forceRebuildNav();
    }

    /**
     * Overrides the {@link NavRenderer} used for the portrait-tablet scenario, constructed at
     * most once, the first time it's actually needed. Default: {@link SideRailNavRenderer}.
     *
     * <p>If this scenario is currently active, it is torn down and rebuilt immediately.
     */
    protected void setTabletPortraitNavRenderer(Supplier<NavRenderer> renderer) {
        tabletPortraitNavRenderer = memoize(renderer);
        forceRebuildNav();
    }

    /**
     * Overrides the {@link NavRenderer} used for the landscape-tablet scenario, constructed at
     * most once, the first time it's actually needed. Default: the same memoized
     * {@link SideRailNavRenderer} instance as the portrait-tablet default (see
     * {@link #setTabletNavRenderer}) — both resolve to {@link NavType#RAIL} by default and need
     * no orientation-specific behavior.
     *
     * <p>If this scenario is currently active, it is torn down and rebuilt immediately.
     */
    protected void setTabletLandscapeNavRenderer(Supplier<NavRenderer> renderer) {
        tabletLandscapeNavRenderer = memoize(renderer);
        forceRebuildNav();
    }

    /**
     * Overrides the {@link NavRenderer} used for both tablet orientations at once, sharing a
     * single memoized instance between them — equivalent to calling
     * {@link #setTabletPortraitNavRenderer} and {@link #setTabletLandscapeNavRenderer} with a
     * shared supplier, not two independent ones. Both orientations resolve their chrome from
     * {@code renderer}'s own {@link NavRenderer#navType() navType()}, so e.g. supplying
     * {@link SideNavDrawerNavRenderer} correctly gives both orientations sidenav chrome — this is
     * the same sharing relationship both tablet orientations and phone's two orientations already
     * have by default (see the field comment above), just with an explicit renderer instead.
     */
    protected void setTabletNavRenderer(Supplier<NavRenderer> renderer) {
        var shared = memoize(renderer);
        tabletPortraitNavRenderer = shared;
        tabletLandscapeNavRenderer = shared;
        forceRebuildNav();
    }

    /**
     * Overrides the {@link NavRenderer} used for the portrait-phone scenario, constructed at
     * most once, the first time it's actually needed. Default: {@link TouchBarNavRenderer}.
     *
     * <p>If this scenario is currently active, it is torn down and rebuilt immediately.
     */
    protected void setPhonePortraitNavRenderer(Supplier<NavRenderer> renderer) {
        phonePortraitNavRenderer = memoize(renderer);
        forceRebuildNav();
    }

    /**
     * Overrides the {@link NavRenderer} used for the landscape-phone scenario, constructed at
     * most once, the first time it's actually needed. Default: the same memoized
     * {@link TouchBarNavRenderer} instance as the portrait-phone default (see
     * {@link #setPhoneNavRenderer}) — both resolve to {@link NavType#TOUCH} by default and need
     * no orientation-specific behavior.
     *
     * <p>If this scenario is currently active, it is torn down and rebuilt immediately.
     */
    protected void setPhoneLandscapeNavRenderer(Supplier<NavRenderer> renderer) {
        phoneLandscapeNavRenderer = memoize(renderer);
        forceRebuildNav();
    }

    /**
     * Overrides the {@link NavRenderer} used for both phone orientations at once, sharing a
     * single memoized instance between them — equivalent to calling
     * {@link #setPhonePortraitNavRenderer} and {@link #setPhoneLandscapeNavRenderer} with a
     * shared supplier, not two independent ones. This is the right default relationship for
     * phone specifically, since both orientations already resolve to the same {@link NavType}
     * and the same slot; using two independently-constructed instances would leave a stale one
     * still attached after a same-session orientation change re-resolves to the other.
     */
    protected void setPhoneNavRenderer(Supplier<NavRenderer> renderer) {
        var shared = memoize(renderer);
        phonePortraitNavRenderer = shared;
        phoneLandscapeNavRenderer = shared;
        forceRebuildNav();
    }

    /**
     * Wraps {@code supplier} so it's invoked at most once — the first call constructs and caches
     * the {@link NavRenderer}; every later call returns that same instance. Needed both to avoid
     * constructing all five scenarios' renderers when only one is ever relevant to a given
     * session, and for correctness: a {@link NavRenderer} is a stateful component holder
     * (attached slot references, built child components), so resolving a *different* instance
     * for what should be the same scenario would leave the previous one's components orphaned
     * but still attached.
     */
    private Supplier<NavRenderer> memoize(Supplier<NavRenderer> supplier) {
        var cache = new NavRenderer[1];
        return () -> {
            if (cache[0] == null) {
                cache[0] = supplier.get();
                wireOwner(cache[0]);
            }
            return cache[0];
        };
    }

    /**
     * Gives the built-in renderer classes ({@link SideNavDrawerNavRenderer},
     * {@link AbstractTouchNavRenderer} and its subclasses) their owner reference right after
     * construction, so their public constructors can stay no-arg — callers never need to (and
     * can't be trusted to) pass {@code this} through correctly. A fully custom {@link NavRenderer}
     * that doesn't extend one of these matches neither branch and is left alone; it only ever
     * needs the public {@link NavRenderContext} it's called with, never an owner reference.
     */
    private void wireOwner(NavRenderer renderer) {
        if (renderer instanceof SideNavDrawerNavRenderer r) {
            r.attachOwner(this);
        }
        else if (renderer instanceof AbstractTouchNavRenderer r) {
            r.attachOwner(this);
        }
    }

    /**
     * Resolves the {@link NavRenderer} configured for the current {@link DeviceType}/
     * {@link Orientation} scenario — a direct mapping, independent of {@link NavType}: which
     * chrome that renderer requires is derived from it afterwards, via
     * {@link NavRenderer#navType()}, not chosen separately.
     */
    private NavRenderer resolveScenarioRenderer() {
        return switch (deviceType) {
            case DESKTOP -> desktopNavRenderer.get();
            case TABLET -> (orientation == Orientation.LANDSCAPE ? tabletLandscapeNavRenderer : tabletPortraitNavRenderer).get();
            case PHONE -> (orientation == Orientation.LANDSCAPE ? phoneLandscapeNavRenderer : phonePortraitNavRenderer).get();
        };
    }

    /**
     * Re-evaluates the active scenario's renderer after a renderer setter call — unlike
     * {@link #repopulateNav()}, needed when a setter swaps out the component a location is built
     * from, not just how it's populated. Delegates to {@link #applyNavType()}'s own
     * renderer-identity guard rather than forcing a rebuild unconditionally: a setter call that
     * affected a scenario other than the currently-active one resolves to the same active
     * renderer instance and correctly no-ops.
     */
    private void forceRebuildNav() {
        if (navState != NavState.UNBUILT) {
            applyNavType();
        }
    }

    /**
     * Sets the nav-group resolver used by the default {@link PathPrefixNavGrouper}.
     * Return {@code null} to use path-based grouping for an entry.
     *
     * <p>Takes effect via the default {@code PathPrefixNavGrouper}'s lambda closure over this
     * field; has no effect if {@link #setNavGrouper} has been called with a custom grouper.
     */
    protected void setViewNavGroupResolver(Function<MenuEntry, NavGroup> resolver) {
        viewNavGroupResolver = resolver;
        navGrouper.reset();
        repopulateNav();
    }

    /**
     * Sets the icon generator for leaf nav items. Return {@code null} or a supplier returning
     * {@code null} to show no icon.
     *
     * <p>Takes effect via the default {@code PathPrefixNavGrouper}'s lambda closure over this
     * field; has no effect if {@link #setNavGrouper} has been called with a custom grouper.
     */
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
     * view's {@link HasViewHeaderTitle} and {@link HasViewHeaderComponent} if present, and
     * re-invokes the active {@link NavRenderer}(s) so active-item highlighting and drill-down
     * content stay current.
     *
     * <p>Desktop: title (left) + action component (right) when either is present.
     * Mobile: action component only; title is omitted to conserve vertical space.
     */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        navigationSignal.set(event.getLocation());
        currentViewSignal.set(getContent());
        if (activeStrategy != null) {
            populateNav();
        }
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
     * Called whenever the active {@link NavType} is determined and applied — including on
     * first attachment, not just on later changes. Default is a no-op. Override to react to
     * nav-type changes in a subclass or companion code; for code that doesn't subclass
     * {@code AppNavLayout}, see {@link #addNavTypeChangedListener}.
     */
    protected void onNavTypeChanged(NavTypeChangedEvent event) {
    }

    /**
     * Adds a listener for {@link NavTypeChangedEvent}, fired whenever the active
     * {@link NavType} is determined and applied — including on first attachment.
     * For subclasses, overriding {@link #onNavTypeChanged} is usually simpler.
     *
     * @return a registration for removing the listener
     */
    public Registration addNavTypeChangedListener(ComponentEventListener<NavTypeChangedEvent> listener) {
        return addListener(NavTypeChangedEvent.class, listener);
    }

    /**
     * Returns {@code true} if this session is using touch or rail nav (not desktop SideNav or
     * header tabs).
     *
     * <p>Returns {@code false} before the first {@link #onAttach(AttachEvent)} completes,
     * because device detection requires a client round-trip. Do not call this from a
     * subclass constructor.
     */
    protected boolean isMobile() {
        return activeNavType == NavType.TOUCH || activeNavType == NavType.RAIL;
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
     * among touch devices (default {@code 768} — the common responsive-design
     * convention for the tablet/phone boundary, matching an iPad's portrait-mode
     * shortest side and Bootstrap's {@code md} breakpoint).
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
            orientation = detectOrientation(details);
            applyNavType();
        });
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
