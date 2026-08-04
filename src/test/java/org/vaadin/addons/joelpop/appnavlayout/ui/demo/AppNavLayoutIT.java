package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for AppNavLayout.
 * Run with: mvn verify -Pit
 * Headed mode: mvn verify -Pit -Dplaywright.headed=true
 */
class AppNavLayoutIT {

    // Viewport sizes that cross the MIN_SLOT_PX=72 thresholds for the 3-view demo.
    // At 375px: maxIcons=5, all 3 primary, no overflow.
    // At 200px: maxIcons=2, 1 primary + overflow button, 2 views in overflow popover.
    private static final int PHONE_WIDTH    = 375;
    private static final int PHONE_HEIGHT   = 812;
    private static final int NARROW_WIDTH   = 200;
    private static final int DESKTOP_WIDTH  = 1280;
    private static final int DESKTOP_HEIGHT = 800;

    // Tablet: short side >= 768px triggers DeviceType.TABLET.
    // Default selector: RAIL in both orientations (see SplitTabletNavLayout/"/split-tablet" for
    // a demo layout that still gives the two orientations genuinely different chrome).
    private static final int TABLET_PORTRAIT_WIDTH   = 820;
    private static final int TABLET_PORTRAIT_HEIGHT  = 1180;
    private static final int TABLET_LANDSCAPE_WIDTH  = 1180;
    private static final int TABLET_LANDSCAPE_HEIGHT = 820;

    private static final String BASE_URL = "http://localhost:8099";

    private static Playwright playwright;
    private static Browser browser;
    private static boolean headed;
    private static int pauseMs;

    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        headed = Boolean.parseBoolean(System.getProperty("playwright.headed", "false"));
        pauseMs = Integer.parseInt(System.getProperty("playwright.pause", "1500"));
        var options = new BrowserType.LaunchOptions().setHeadless(!headed);
        if (headed) options.setSlowMo(250);
        browser = playwright.chromium().launch(options);
    }

    @AfterAll
    static void closeBrowser() {
        browser.close();
        playwright.close();
    }

    @BeforeEach
    void newPage() {
        // Default: desktop context, no touch.
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach
    void closePage() {
        context.close();
    }

    // ——————————— Context factories ————————————

    // Creates a context that reports navigator.maxTouchPoints > 0, which causes
    // Vaadin's ExtendedClientDetails.isTouchDevice() to return true, selecting touch nav.
    private Page newPhonePage() {
        context.close();
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(PHONE_WIDTH, PHONE_HEIGHT)
                .setHasTouch(true));
        page = context.newPage();
        return page;
    }

    private Page newTabletPortraitPage() {
        context.close();
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(TABLET_PORTRAIT_WIDTH, TABLET_PORTRAIT_HEIGHT)
                .setHasTouch(true));
        page = context.newPage();
        return page;
    }

    private Page newTabletLandscapePage() {
        context.close();
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(TABLET_LANDSCAPE_WIDTH, TABLET_LANDSCAPE_HEIGHT)
                .setHasTouch(true));
        page = context.newPage();
        return page;
    }

    // ——————————— Navigation helper ————————————

    private void navigateTo(String path) {
        page.navigate(BASE_URL + path);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        // Wait for the ResizeObserver's callback (fires in the next animation frame after
        // layout) instead of guessing a fixed delay: two rAFs guarantee the browser has painted
        // the post-layout frame and dispatched the observer callback.
        page.evaluate("() => new Promise(resolve => "
                + "requestAnimationFrame(() => requestAnimationFrame(resolve)))");
    }

    // ——————————— AppLayout shadow DOM helpers ————————————

    /**
     * Returns the bounding rect of a named shadow-DOM part of vaadin-app-layout.
     * Keys: x, y, width, height (all as Number, use num() to extract).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> shadowPartRect(String part) {
        return (Map<String, Object>) page.evaluate("""
            part => {
                const el = document.querySelector('vaadin-app-layout')
                    .shadowRoot.querySelector('[part~="' + part + '"]');
                if (!el) return null;
                const r = el.getBoundingClientRect();
                return { x: r.x, y: r.y, width: r.width, height: r.height };
            }""", part);
    }

    private double num(Map<String, Object> rect, String key) {
        return ((Number) rect.get(key)).doubleValue();
    }

    /** parseFloat of a computed style property of a named shadow-DOM part of vaadin-app-layout. */
    private double shadowPartComputedPx(String part, String cssProperty) {
        return ((Number) page.evaluate("""
            ([part, prop]) => {
                const el = document.querySelector('vaadin-app-layout')
                    .shadowRoot.querySelector('[part~="' + part + '"]');
                return parseFloat(getComputedStyle(el)[prop]);
            }""", List.of(part, cssProperty))).doubleValue();
    }

    /** y position of the .app-top-bar element (topBar HorizontalLayout). */
    private double topBarY() {
        var box = page.locator(".app-top-bar").boundingBox();
        return box != null ? box.y : Double.NaN;
    }

    /** Whether the vaadin-app-layout element has the nav-rail attribute. */
    private boolean hasNavRailAttr() {
        return (boolean) page.evaluate(
            "() => document.querySelector('vaadin-app-layout').hasAttribute('nav-rail')");
    }

    /** Whether the vaadin-app-layout element has the overlay attribute. */
    private boolean hasOverlayAttr() {
        return (boolean) page.evaluate(
            "() => document.querySelector('vaadin-app-layout').hasAttribute('overlay')");
    }

    /** x position of the first view-content element (slotted into the default slot). */
    private double viewContentX() {
        return ((Number) page.evaluate("""
            () => {
                const layout = document.querySelector('vaadin-app-layout');
                const content = [...layout.children].find(c => !c.hasAttribute('slot'));
                return content ? content.getBoundingClientRect().x : -1;
            }""")).doubleValue();
    }

    private void pauseForHumanIfHeaded() {
        if (headed) page.waitForTimeout(pauseMs);
    }

    // vaadin-app-layout's own overlay backdrop (RAIL mode, drawer opened) sits on top of the
    // page and intercepts pointer events for Playwright's normal actionability checks, even
    // though the target is genuinely visible and clickable to a real user. Force bypasses that
    // check. (Vaadin Copilot's dev-mode overlay used to cause the same problem elsewhere; it's
    // disabled for this profile via vaadin.copilot.enable=false in the pom's it profile.)
    private void forceClick(Locator locator) {
        locator.click(new Locator.ClickOptions().setForce(true));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // ORIGINAL TESTS — touch nav items, secondary nav, orientation changes
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void touchNavRendersAllItemsWithIconsAtPhoneViewport() {
        newPhonePage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        // Three views registered — all should appear as primary touch nav items.
        assertThat(page.locator(".touch-nav-item")).hasCount(3);
        // Each item must have an icon; no blank placeholder slots.
        assertThat(page.locator(".touch-nav-item vaadin-icon")).hasCount(3);
    }

    @Test
    void touchNavIconsRemainCorrectAfterNavBarRebuild() {
        newPhonePage();
        navigateTo("/");

        // Initial state: 3 primary items, 3 icons.
        assertThat(page.locator(".touch-nav-item")).hasCount(3);
        assertThat(page.locator(".touch-nav-item vaadin-icon")).hasCount(3);
        pauseForHumanIfHeaded();

        // Narrow to overflow territory: 1 primary + overflow button = 2 touch-nav-items.
        // hasCount() is a Playwright web-first assertion that polls until it matches (or times
        // out), so no explicit wait is needed for the Signal.effect to fire and the DOM to settle.
        page.setViewportSize(NARROW_WIDTH, PHONE_HEIGHT);
        assertThat(page.locator(".touch-nav-item")).hasCount(2);
        pauseForHumanIfHeaded();

        // Widen back: buildItems() fires again; icons must be freshly created, not moved.
        page.setViewportSize(PHONE_WIDTH, PHONE_HEIGHT);
        assertThat(page.locator(".touch-nav-item")).hasCount(3);
        assertThat(page.locator(".touch-nav-item vaadin-icon")).hasCount(3);
        pauseForHumanIfHeaded();
    }

    @Test
    void touchNavItemsShareBarWidthEquallyInsteadOfClipping() {
        // Regression test: touch-nav-item width used to be purely content-driven (icon + label,
        // space-evenly distributing only the leftover slack) — nothing shrank a label below its
        // natural nowrap width, so item widths varied with label length, and with enough items
        // or long enough labels the row could overflow past the bar's right edge with nothing to
        // shrink or scroll it back (MIN_SLOT_PX only decides *whether* to show "More", it isn't
        // enforced on rendering). Items must now be equal-width, shrinkable flex children instead,
        // which by construction can never sum past the bar's width regardless of label length.
        newPhonePage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        @SuppressWarnings("unchecked")
        var rawWidths = (List<Object>) page.evaluate(
                "() => Array.from(document.querySelectorAll('.touch-nav-item'))"
                        + ".map(e => e.getBoundingClientRect().width)");
        var widths = rawWidths.stream().mapToDouble(w -> ((Number) w).doubleValue()).toArray();

        assertEquals(3, widths.length, "expected the 3 root sections as primary touch-nav items");
        var min = Arrays.stream(widths).min().orElseThrow();
        var max = Arrays.stream(widths).max().orElseThrow();
        assertEquals(min, max, 1.0,
                "touch-nav-items must share the bar's width equally, not size to their own label");
    }

    @Test
    void touchNavActiveHighlightSurvivesResizeDrivenRebuild() {
        // Regression test: on a real device, rotating a phone doesn't change NavType (both
        // orientations resolve to TOUCH), so only the bar's own resize-driven Signal.effect
        // rebuilds items — nothing else re-invokes render()/highlightActive() the way a
        // navigation would. Crossing the overflow threshold here exercises that exact same
        // internal rebuild path (proven to fire reliably in this harness by
        // touchNavIconsRemainCorrectAfterNavBarRebuild), so it's an equally valid way to catch
        // "rebuilt items lost their active highlight" without depending on a literal
        // width/height swap actually crossing the threshold in headless Playwright.
        newPhonePage();
        navigateTo("/catalog/products");
        pauseForHumanIfHeaded();

        // "Catalog" root item is active for a nested catalog/* route.
        assertThat(page.locator(".touch-nav-item")).hasCount(3);
        assertThat(page.locator(".touch-nav-item.active")).hasCount(1);

        page.setViewportSize(NARROW_WIDTH, PHONE_HEIGHT);
        assertThat(page.locator(".touch-nav-item")).hasCount(2);
        assertThat(page.locator(".touch-nav-item.active")).hasCount(1);
        pauseForHumanIfHeaded();
    }

    @Test
    void sideNavRendersAtDesktopViewport() {
        page.setViewportSize(DESKTOP_WIDTH, DESKTOP_HEIGHT);
        navigateTo("/");
        pauseForHumanIfHeaded();

        assertThat(page.locator("vaadin-side-nav")).isVisible();
        // Home, Catalog group (+ Products and Categories children), Orders = 5 items.
        assertThat(page.locator("vaadin-side-nav-item")).hasCount(5);
    }

    @Test
    void navigationChangesActiveViewContent() {
        newPhonePage();
        navigateTo("/");

        // Navigate to Catalog via URL; verify the view content loads.
        navigateTo("/catalog");
        assertThat(page.locator("text=Catalog view content")).isVisible();
        pauseForHumanIfHeaded();
    }

    @Test
    void secondaryNavVisibleOnTabletPortraitSubRoute() {
        newTabletPortraitPage();
        navigateTo("/catalog/products");
        pauseForHumanIfHeaded();

        assertThat(page.locator(".secondary-tab-bar vaadin-tabs")).isVisible();
    }

    @Test
    void drawerOverlaysContentAfterOrientationChange() {
        // Both tablet orientations default to RAIL now, so this exercises SplitTabletNavLayout
        // ("/split-tablet"), which still gives landscape genuinely different (SIDENAV) chrome —
        // see its own doc comment.
        newTabletPortraitPage();
        navigateTo("/split-tablet");

        // Initial RAIL state: drawer must be in overlay mode (not push mode).
        assertTrue((boolean) page.evaluate("() => document.querySelector('vaadin-app-layout').overlay"),
                "drawer should be in overlay mode at portrait tablet");
        pauseForHumanIfHeaded();

        // Rotate to landscape — this layout's own selector switches to SIDENAV.
        page.setViewportSize(TABLET_LANDSCAPE_WIDTH, TABLET_LANDSCAPE_HEIGHT);
        page.waitForFunction(
            "() => !document.querySelector('vaadin-app-layout').hasAttribute('nav-rail')",
            null,
            new Page.WaitForFunctionOptions().setTimeout(3000));

        // Rotate back to portrait — RAIL rebuilds; overlay mode must be restored.
        page.setViewportSize(TABLET_PORTRAIT_WIDTH, TABLET_PORTRAIT_HEIGHT);
        page.waitForFunction(
            "() => document.querySelector('vaadin-app-layout').hasAttribute('nav-rail')",
            null,
            new Page.WaitForFunctionOptions().setTimeout(3000));

        assertTrue((boolean) page.evaluate("() => document.querySelector('vaadin-app-layout').overlay"),
                "drawer should remain in overlay mode after orientation change back to portrait");
        pauseForHumanIfHeaded();
    }

    @Test
    void secondaryNavSurvivesTabletOrientationChange() {
        // Both tablet orientations default to RAIL now, so an orientation change no longer tears
        // down and rebuilds the NavStrategy at all — the secondary tab bar simply stays visible
        // throughout, a stronger guarantee than the old default's "reappears after switching to
        // SIDENAV and back". That teardown/rebuild scenario is still covered separately, for API
        // consumers who explicitly configure a tablet-orientation split — see
        // drawerOverlaysContentAfterOrientationChange above (SplitTabletNavLayout).
        newTabletPortraitPage();
        navigateTo("/catalog/products");
        assertThat(page.locator(".secondary-tab-bar vaadin-tabs")).isVisible();
        pauseForHumanIfHeaded();

        page.setViewportSize(TABLET_LANDSCAPE_WIDTH, TABLET_LANDSCAPE_HEIGHT);
        assertTrue(hasNavRailAttr(), "nav-rail must remain set after rotating to landscape");
        assertThat(page.locator(".secondary-tab-bar vaadin-tabs")).isVisible();
        pauseForHumanIfHeaded();

        page.setViewportSize(TABLET_PORTRAIT_WIDTH, TABLET_PORTRAIT_HEIGHT);
        assertThat(page.locator(".secondary-tab-bar vaadin-tabs")).isVisible();
        pauseForHumanIfHeaded();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // NAV TYPE ACTIVATION — correct mode selected per device/orientation
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void touchModeActivatesOnPhone() {
        newPhonePage();
        navigateTo("/");

        // TOUCH mode: touch-optimized CSS variable set, bottom bar visible, no rail.
        assertFalse(hasNavRailAttr(), "nav-rail must be absent in TOUCH mode");
        assertThat(page.locator(".touch-nav-item").first()).isVisible();
        assertThat(page.locator("vaadin-side-nav")).not().isVisible();
        pauseForHumanIfHeaded();
    }

    @Test
    void railModeActivatesOnTabletPortrait() {
        newTabletPortraitPage();
        navigateTo("/");

        // RAIL mode: nav-rail attribute present, left rail visible.
        assertTrue(hasNavRailAttr(), "nav-rail attribute must be set in RAIL mode");
        assertThat(page.locator(".touch-nav-item").first()).isVisible();
        assertThat(page.locator("vaadin-side-nav")).not().isVisible();
        pauseForHumanIfHeaded();
    }

    @Test
    void railModeActivatesOnTabletLandscape() {
        // Cold-launching directly in landscape (not landscape-via-rotation-from-portrait)
        // matters — see sharedRailRendererActivatesRailInBothTabletOrientations below for why
        // that distinction once mattered for a real bug; this is the same check for the default
        // (now shared) renderer instead of an explicitly-configured one.
        context.close();
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(TABLET_LANDSCAPE_WIDTH, TABLET_LANDSCAPE_HEIGHT)
                .setHasTouch(true));
        page = context.newPage();
        navigateTo("/");

        // RAIL mode: nav-rail attribute present, left rail visible.
        assertTrue(hasNavRailAttr(), "nav-rail attribute must be set in RAIL mode");
        assertThat(page.locator(".touch-nav-item").first()).isVisible();
        assertThat(page.locator("vaadin-side-nav")).not().isVisible();
        pauseForHumanIfHeaded();
    }

    @Test
    void navRailAttributeAbsentAfterOrientationChangeToSidenav() {
        // Both tablet orientations default to RAIL now, so this exercises SplitTabletNavLayout
        // ("/split-tablet"), which still gives landscape genuinely different (SIDENAV) chrome —
        // see its own doc comment.
        newTabletPortraitPage();
        navigateTo("/split-tablet");
        assertTrue(hasNavRailAttr(), "nav-rail must be set in portrait (RAIL)");

        page.setViewportSize(TABLET_LANDSCAPE_WIDTH, TABLET_LANDSCAPE_HEIGHT);
        page.waitForFunction(
            "() => !document.querySelector('vaadin-app-layout').hasAttribute('nav-rail')",
            null,
            new Page.WaitForFunctionOptions().setTimeout(3000));

        assertFalse(hasNavRailAttr(), "nav-rail must be cleared after rotation to landscape (SIDENAV)");
        pauseForHumanIfHeaded();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // iOS PWA FIX — layout must fill the viewport so position:fixed elements anchor
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void layoutFillsViewportHeightEvenWithShortContent() {
        // min-height:100dvh on vaadin-app-layout ensures the layout always spans the
        // full viewport. Without it, in iOS PWA standalone mode the touch bar and rail
        // anchor incorrectly when the page content is shorter than the viewport.
        newPhonePage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        int viewportHeight = page.viewportSize().height;
        var box = page.locator("vaadin-app-layout").boundingBox();
        assertTrue(box.height >= viewportHeight - 1,
            "vaadin-app-layout height (%.0f) must fill viewport height (%d) via min-height:100dvh"
                .formatted(box.height, viewportHeight));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // TOUCH MODE — element positioning
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void topBarIsAtViewportTopInTouchMode() {
        newPhonePage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        // navbar-top has 8px padding-block; topBar sits at padding-top + any centering offset.
        // Assert it's within the top quarter of the navbar (not displaced into the content area).
        double navbarTopHeight = num(shadowPartRect("navbar-top"), "height");
        assertTrue(topBarY() < navbarTopHeight / 2.0,
            "topBar must be in the top half of the navbar in TOUCH mode, got y=" + topBarY());
    }

    @Test
    void touchBarIsAtViewportBottomInTouchMode() {
        newPhonePage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        int viewportHeight = page.viewportSize().height;
        var rect = shadowPartRect("navbar-bottom");
        double bottom = num(rect, "y") + num(rect, "height");
        assertEquals(viewportHeight, bottom, 2.0,
            "touch bar bottom edge must coincide with viewport bottom in TOUCH mode");
    }

    @Test
    void contentIsBelowNavbarInTouchMode() {
        // --vaadin-app-layout-navbar-offset-top is set by AppLayout's ResizeObserver to
        // the measured navbar-top height. This drives padding-block-start on the content,
        // keeping content below the fixed header. Verify the variable tracks the actual height.
        newPhonePage();
        navigateTo("/");

        double navbarHeight = num(shadowPartRect("navbar-top"), "height");
        String offsetTopStr = (String) page.evaluate(
            "() => getComputedStyle(document.querySelector('vaadin-app-layout'))" +
            ".getPropertyValue('--vaadin-app-layout-navbar-offset-top').trim()");
        double offsetTopPx = Double.parseDouble(offsetTopStr.replace("px", ""));

        assertEquals(navbarHeight, offsetTopPx, 2.0,
            "navbar-offset-top CSS variable must equal measured navbar-top height in TOUCH mode");
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // TOUCH MODE — view header slot (HasViewHeaderComponent)
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void viewHeaderSlotHiddenWhenNoActionComponentInTouchMode() {
        newPhonePage();
        navigateTo("/"); // HomeView does not implement HasViewHeaderComponent
        pauseForHumanIfHeaded();

        assertThat(page.locator(".view-header-slot")).isHidden();
    }

    @Test
    void viewHeaderSlotVisibleWhenActionComponentPresentInTouchMode() {
        newPhonePage();
        navigateTo("/catalog/detail"); // CatalogDetailView implements HasViewHeaderComponent
        pauseForHumanIfHeaded();

        assertThat(page.locator(".view-header-slot")).isVisible();
        assertThat(page.locator("#view-header-action")).isVisible();
    }

    /**
     * Regression test for the topBar-jumps-when-view-header-shows bug.
     *
     * Root cause (before fix): AppLayout's navbar-top has align-items:center and
     * min-height:3.5rem. When viewHeaderSlot was hidden, topBlock was shorter than
     * the min-height, causing AppLayout's flex centering to offset topBlock downward.
     * When viewHeaderSlot appeared and topBlock exceeded the min-height, the centering
     * offset collapsed to zero — topBar snapped to y=0, perceived as "the top bar
     * moves up."
     *
     * Fix: topBar.getStyle().set("min-height", "var(--lumo-size-xl)") — topBlock is
     * always at least as tall as the navbar's min-height, so the centering offset is
     * always zero regardless of viewHeaderSlot state.
     */
    @Test
    void topBarYStableWhenViewHeaderAppearsAndDisappearsInTouchMode() {
        newPhonePage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        double yWithoutHeader = topBarY();

        navigateTo("/catalog/detail");
        pauseForHumanIfHeaded();
        assertThat(page.locator(".view-header-slot")).isVisible();

        double yWithHeader = topBarY();
        assertEquals(yWithoutHeader, yWithHeader, 1.0,
            "topBar y must not change when view header slot appears in TOUCH mode");

        navigateTo("/");
        pauseForHumanIfHeaded();

        double yAfterRemoval = topBarY();
        assertEquals(yWithoutHeader, yAfterRemoval, 1.0,
            "topBar y must not change when view header slot disappears in TOUCH mode");
    }

    @Test
    void contentPushedDownWhenViewHeaderAppearsInTouchMode() {
        // When a view header slot appears, the navbar grows downward and the content
        // is pushed down — this is the desired behavior (not a regression).
        newPhonePage();
        navigateTo("/");

        double navbarHeightWithout = num(shadowPartRect("navbar-top"), "height");

        navigateTo("/catalog/detail");
        assertThat(page.locator(".view-header-slot")).isVisible();

        double navbarHeightWith = num(shadowPartRect("navbar-top"), "height");

        assertTrue(navbarHeightWith > navbarHeightWithout,
            "navbar-top must grow when view header slot is shown (content is pushed down)");
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // RAIL MODE — element positioning
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void topBarIsAtViewportTopInRailMode() {
        newTabletPortraitPage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        double navbarTopHeight = num(shadowPartRect("navbar-top"), "height");
        assertTrue(topBarY() < navbarTopHeight / 2.0,
            "topBar must be in the top half of the navbar in RAIL mode, got y=" + topBarY());
    }

    @Test
    void railBarIsAtViewportLeftEdge() {
        // The rail (navbar-bottom) is pinned to inset-inline-start:0 unconditionally so it
        // never has to react to the top bar's position — any component that repositions or
        // animates the top bar still sees a stable, gap-free left edge from the rail.
        newTabletPortraitPage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        var rect = shadowPartRect("navbar-bottom");
        assertEquals(0.0, num(rect, "x"), 2.0,
            "rail (navbar-bottom) must start at x=0 (viewport left edge)");
    }

    @Test
    void railBarSpansFullViewportHeight() {
        newTabletPortraitPage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        int viewportHeight = page.viewportSize().height;
        var rect = shadowPartRect("navbar-bottom");
        assertEquals(0.0, num(rect, "y"), 2.0, "rail top edge must be at y=0");
        assertEquals(viewportHeight, num(rect, "height"), 2.0,
            "rail must span the full viewport height");
    }

    @Test
    void navbarTopIsOffsetRightOfRailBarInRailMode() {
        // ::part(navbar-top) { inset-inline-start: var(--nav-rail-width) }
        // so the header does not overlap the rail strip.
        newTabletPortraitPage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        // Read the CSS-declared rail width via padding-inline-start on the layout element
        // (set to var(--nav-rail-width) in RAIL mode). Using bounding-box width of navbar-bottom
        // is unreliable because touch-nav-item labels can overflow the declared 80px.
        double railWidth = ((Number) page.evaluate(
            "() => parseFloat(getComputedStyle(document.querySelector('vaadin-app-layout')).paddingInlineStart)"
        )).doubleValue();
        double navbarTopX = num(shadowPartRect("navbar-top"), "x");
        assertEquals(railWidth, navbarTopX, 2.0,
            "navbar-top left edge must equal rail width so they don't overlap");
    }

    @Test
    void contentIsOffsetRightOfRailBarInRailMode() {
        // vaadin-app-layout gets padding-inline-start:var(--nav-rail-width) in RAIL mode
        // so view content never slides under the rail strip.
        newTabletPortraitPage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        // Read the CSS-declared rail width via padding-inline-start on the layout element
        // (set to var(--nav-rail-width) in RAIL mode). Using bounding-box width of navbar-bottom
        // is unreliable because touch-nav-item labels can overflow the declared 80px.
        double railWidth = ((Number) page.evaluate(
            "() => parseFloat(getComputedStyle(document.querySelector('vaadin-app-layout')).paddingInlineStart)"
        )).doubleValue();
        double contentX = viewContentX();
        assertEquals(railWidth, contentX, 2.0,
            "view content left edge must equal rail width in RAIL mode");
    }

    @Test
    void contentIsBelowNavbarInRailMode() {
        newTabletPortraitPage();
        navigateTo("/");

        double navbarHeight = num(shadowPartRect("navbar-top"), "height");
        String offsetTopStr = (String) page.evaluate(
            "() => getComputedStyle(document.querySelector('vaadin-app-layout'))" +
            ".getPropertyValue('--vaadin-app-layout-navbar-offset-top').trim()");
        double offsetTopPx = Double.parseDouble(offsetTopStr.replace("px", ""));

        assertEquals(navbarHeight, offsetTopPx, 2.0,
            "navbar-offset-top CSS variable must equal measured navbar-top height in RAIL mode");
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // RAIL MODE — drawer
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void drawerIsClosedByDefaultInRailMode() {
        newTabletPortraitPage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        var opened = (boolean) page.evaluate(
            "() => document.querySelector('vaadin-app-layout').drawerOpened");
        assertFalse(opened, "drawer must be closed by default in RAIL mode");
    }

    @Test
    void drawerIsInOverlayModeInRailMode() {
        newTabletPortraitPage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        // Overlay mode means the drawer slides over content rather than pushing it aside.
        assertTrue((boolean) page.evaluate("() => document.querySelector('vaadin-app-layout').overlay"),
            "drawer must be in overlay mode in RAIL mode");
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // RAIL MODE — view header slot (HasViewHeaderComponent)
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void viewHeaderSlotHiddenWhenNoActionComponentInRailMode() {
        newTabletPortraitPage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        assertThat(page.locator(".view-header-slot")).isHidden();
    }

    @Test
    void viewHeaderSlotVisibleWhenActionComponentPresentInRailMode() {
        newTabletPortraitPage();
        navigateTo("/catalog/detail");
        pauseForHumanIfHeaded();

        assertThat(page.locator(".view-header-slot")).isVisible();
        assertThat(page.locator("#view-header-action")).isVisible();
    }

    /** Rail-mode counterpart to the TOUCH-mode topBar stability test. */
    @Test
    void topBarYStableWhenViewHeaderAppearsAndDisappearsInRailMode() {
        newTabletPortraitPage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        double yWithoutHeader = topBarY();

        navigateTo("/catalog/detail");
        pauseForHumanIfHeaded();
        assertThat(page.locator(".view-header-slot")).isVisible();

        double yWithHeader = topBarY();
        assertEquals(yWithoutHeader, yWithHeader, 1.0,
            "topBar y must not change when view header slot appears in RAIL mode");

        navigateTo("/");
        pauseForHumanIfHeaded();

        double yAfterRemoval = topBarY();
        assertEquals(yWithoutHeader, yAfterRemoval, 1.0,
            "topBar y must not change when view header slot disappears in RAIL mode");
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // SIDENAV MODE — positioning and drawer
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void topBarIsAtViewportTopInSidenavMode() {
        page.setViewportSize(DESKTOP_WIDTH, DESKTOP_HEIGHT);
        navigateTo("/");
        pauseForHumanIfHeaded();

        double navbarTopHeight = num(shadowPartRect("navbar-top"), "height");
        assertTrue(topBarY() < navbarTopHeight / 2.0,
            "topBar must be in the top half of the navbar in SIDENAV mode, got y=" + topBarY());
    }

    @Test
    void drawerIsNotInOverlayModeInSidenavMode() {
        page.setViewportSize(DESKTOP_WIDTH, DESKTOP_HEIGHT);
        navigateTo("/");
        pauseForHumanIfHeaded();

        // On desktop, the drawer is persistent (push mode), not overlay.
        assertFalse((boolean) page.evaluate("() => document.querySelector('vaadin-app-layout').overlay"),
            "drawer must NOT be in overlay mode in SIDENAV mode");
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // BLIND SPOTS — overflow popover navigation, HasViewHeaderTitle (desktop),
    // DrawerToggle, scenario renderer overriding default chrome
    // ═══════════════════════════════════════════════════════════════════════════════

    @Test
    void overflowPopoverNavigatesToSelectedView() {
        newPhonePage();
        navigateTo("/");
        // Narrow to overflow territory: 1 primary item + "More" overflow button.
        page.setViewportSize(NARROW_WIDTH, PHONE_HEIGHT);
        assertThat(page.locator(".touch-nav-item")).hasCount(2);
        pauseForHumanIfHeaded();

        // Vaadin's Popover only opens on a real, trusted client click; a synthetic Playwright
        // click on its target dispatches the event but doesn't reliably trigger the open —
        // open it directly instead. The thing actually under test here is this project's own
        // navigation wiring on the overflow item click, not Popover's own open trigger.
        page.evaluate("() => document.querySelector('vaadin-popover').opened = true");
        assertThat(page.locator(".overflow-nav-item").first()).isVisible();
        pauseForHumanIfHeaded();

        page.locator(".overflow-nav-item").first().click();
        page.waitForURL(url -> !url.equals(BASE_URL + "/"),
            new Page.WaitForURLOptions().setTimeout(3000));
        pauseForHumanIfHeaded();
    }

    @Test
    void viewHeaderTitleAppearsOnDesktopForHasViewHeaderTitleView() {
        page.setViewportSize(DESKTOP_WIDTH, DESKTOP_HEIGHT);
        navigateTo("/catalog/titled"); // TitledDetailView implements HasViewHeaderTitle
        pauseForHumanIfHeaded();

        assertThat(page.locator(".view-header-slot")).isVisible();
        assertThat(page.locator(".view-header-slot h2")).hasText("Titled Detail");
        assertThat(page.locator(".view-header-slot vaadin-icon")).isVisible();
    }

    @Test
    void drawerToggleOpensAndClosesDrawerInRailMode() {
        newTabletPortraitPage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        assertFalse((boolean) page.evaluate("() => document.querySelector('vaadin-app-layout').drawerOpened"),
            "drawer must be closed before toggling");

        forceClick(page.locator("vaadin-drawer-toggle"));
        page.waitForFunction(
            "() => document.querySelector('vaadin-app-layout').drawerOpened",
            null,
            new Page.WaitForFunctionOptions().setTimeout(3000));
        pauseForHumanIfHeaded();

        forceClick(page.locator("vaadin-drawer-toggle"));
        page.waitForFunction(
            "() => !document.querySelector('vaadin-app-layout').drawerOpened",
            null,
            new Page.WaitForFunctionOptions().setTimeout(3000));
        pauseForHumanIfHeaded();
    }

    @Test
    void scenarioRendererOverridesDefaultChromeOnPhone() {
        // Phone would normally get TOUCH chrome by default, but AlwaysSidenavLayout configures
        // every scenario's renderer to SideNavDrawerNavRenderer, whose navType() is SIDENAV —
        // proving NavType is correctly derived from whichever renderer is actually configured,
        // not tied to device/orientation. This only checks that the SIDENAV component set (not
        // TOUCH) was built — AppLayout's own responsive drawer-open/overlay behavior is driven by
        // actual viewport width, independently of our NavType, so a narrow viewport may still
        // leave the drawer closed by default; that's a separate concern from which nav-type
        // strategy got selected.
        newPhonePage();
        navigateTo("/always-sidenav");
        pauseForHumanIfHeaded();

        assertThat(page.locator("vaadin-side-nav")).hasCount(1);
        assertThat(page.locator(".touch-nav-item")).hasCount(0);
    }

    @Test
    void railHeaderFillsAvailableWidthBesideRail() {
        // Regression test: ::part(navbar-top) is content-box, and AppLayout's default
        // touch-bar theme applies inline padding meant for the ordinary (non-rail) header
        // (~12.66px each side) that our nav-rail rule never reset — the same issue already
        // fixed above for ::part(navbar-bottom). Left unreset, the header's own slotted
        // content (topBlock, inline width:100%) resolved its percentage against a content
        // box ~25px narrower than the part's actual rendered width, leaving a gap between
        // the header and the viewport's right edge that had nothing to do with the rail.
        newTabletPortraitPage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        var navbarTopRect = shadowPartRect("navbar-top");
        double headerWidth = ((Number) page.evaluate(
                "() => document.querySelector('.app-top-bar').parentElement.getBoundingClientRect().width"))
                .doubleValue();

        assertEquals(num(navbarTopRect, "width"), headerWidth, 1.0,
                "header must fill the full width of its containing navbar-top part beside the rail");
    }

    @Test
    void touchNavAccountsForSafeAreaInsetsWhenComputingCapacity() {
        // Regression test: the "how many icons fit" calculation used window.innerWidth as
        // reported by page.windowSizeSignal() — which on a notched/rounded-corner device
        // includes an unsafe strip an icon can't actually render into. Simulate that strip
        // (there's no way to make headless Chromium report a real env(safe-area-inset-*)
        // value) and confirm capacity shrinks to account for it, the same way it already
        // shrinks for a genuinely narrower window.
        newPhonePage();
        page.setViewportSize(250, PHONE_HEIGHT);
        navigateTo("/");
        pauseForHumanIfHeaded();

        // Baseline: floor(250 / MIN_SLOT_PX=72) = 3, exactly enough for all 3 root sections.
        assertThat(page.locator(".touch-nav-item")).hasCount(3);

        page.evaluate("""
            () => {
                document.documentElement.style.setProperty('--nav-safe-area-inset-left', '40px');
                document.documentElement.style.setProperty('--nav-safe-area-inset-right', '40px');
            }
            """);
        page.setViewportSize(256, PHONE_HEIGHT);
        pauseForHumanIfHeaded();

        // 256px minus the 80px simulated strip leaves 176px: floor(176/72) = 2, one fewer than
        // the 3 root sections — the last must collapse into the "More" overflow trigger (both
        // the 1 remaining primary item and "More" itself carry the touch-nav-item class).
        assertThat(page.locator(".touch-nav-item")).hasCount(2);
    }

    @Test
    void touchBarTopPaddingOverridesThemesTopSafeAreaInset() {
        // Regression test: Lumo's own app-layout theme sets padding-top: var(--safe-area-inset-top)
        // on the generic [part~='navbar'] selector (correct for navbar-top, behind the status
        // bar/notch), but its navbar-bottom override never resets padding-top back down — so the
        // bottom bar inherits the TOP bar's safe-area inset as its own top padding, inflating its
        // height for no reason on any notched device (confirmed via a real iPhone showing an
        // oversized touch bar: padding-top exactly matched --safe-area-inset-top). This repo's own
        // demo theme (Aura) doesn't have that specific bug, so reproduce Lumo's exact buggy rule
        // shape directly inside vaadin-app-layout's shadow root and confirm our external
        // ::part(navbar-bottom) override still wins over it, the same way it would over Lumo's.
        newPhonePage();
        navigateTo("/");
        pauseForHumanIfHeaded();

        String paddingTopJs = "() => { var bar = document.querySelector('vaadin-app-layout')"
            + ".shadowRoot.querySelector('[part~=\"navbar-bottom\"]');"
            + " return getComputedStyle(bar).paddingTop; }";
        String baselinePaddingTop = (String) page.evaluate(paddingTopJs);

        page.evaluate("""
            () => {
                var sheet = new CSSStyleSheet();
                sheet.replaceSync("[part~='navbar'] { padding-top: var(--safe-area-inset-top); }");
                var al = document.querySelector('vaadin-app-layout');
                al.shadowRoot.adoptedStyleSheets = [...al.shadowRoot.adoptedStyleSheets, sheet];
                document.documentElement.style.setProperty('--safe-area-inset-top', '47px');
            }
            """);
        pauseForHumanIfHeaded();

        String paddingTopWithBuggyTheme = (String) page.evaluate(paddingTopJs);
        assertEquals(baselinePaddingTop, paddingTopWithBuggyTheme,
            "external ::part(navbar-bottom) override must win over a theme rule leaking the top bar's safe-area inset");
    }

    @Test
    void sharedRailRendererActivatesRailInBothTabletOrientations() {
        // Regression test for a real-world report: setTabletNavRenderer(SideRailNavRenderer::new)
        // shares one renderer instance across both tablet orientations. Before NavType was
        // derived from the active renderer, tablet landscape still defaulted to NavType.SIDENAV
        // (the drawer) regardless — the shared, rail-only renderer silently rendered into
        // DesktopNavStrategy's inert placeholder slot, never attached to the page. Cold-launching
        // directly in landscape (not landscape-via-rotation-from-portrait) matters: that's
        // exactly how the report reproduced.
        newTabletLandscapePage();
        navigateTo("/shared-rail");
        pauseForHumanIfHeaded();
        assertTrue(hasNavRailAttr(), "nav-rail must be set on cold landscape launch with a shared rail renderer");
        assertThat(page.locator(".touch-nav-item").first()).isVisible();

        page.setViewportSize(TABLET_PORTRAIT_WIDTH, TABLET_PORTRAIT_HEIGHT);
        page.waitForFunction(
            "() => document.querySelector('vaadin-app-layout').hasAttribute('nav-rail')",
            null, new Page.WaitForFunctionOptions().setTimeout(3000));
        assertTrue(hasNavRailAttr(), "nav-rail must remain set in portrait");

        page.setViewportSize(TABLET_LANDSCAPE_WIDTH, TABLET_LANDSCAPE_HEIGHT);
        page.waitForTimeout(500);
        assertTrue(hasNavRailAttr(), "nav-rail must still be set after rotating back to landscape");
        assertThat(page.locator(".touch-nav-item").first()).isVisible();
        pauseForHumanIfHeaded();
    }

    @Test
    void overlayClearsAfterRotatingOutOfRailMode() {
        // Regression test: the MutationObserver in app-nav-layout.ts that fires a synthetic
        // resize on nav-rail attribute changes only fired when the attribute was *added*, never
        // on removal — so AppLayout's own overlay mode, forced true for rail, never got
        // re-evaluated after leaving rail (portrait→landscape), leaving the drawer stuck in
        // overlay mode at zero width.
        //
        // Both tablet orientations default to RAIL now, so this exercises SplitTabletNavLayout
        // ("/split-tablet"), which still gives landscape genuinely different (SIDENAV) chrome —
        // see its own doc comment.
        newTabletPortraitPage();
        navigateTo("/split-tablet");
        assertTrue(hasNavRailAttr(), "nav-rail must be set in portrait (RAIL)");

        page.setViewportSize(TABLET_LANDSCAPE_WIDTH, TABLET_LANDSCAPE_HEIGHT);
        page.waitForFunction(
            "() => !document.querySelector('vaadin-app-layout').hasAttribute('nav-rail')",
            null, new Page.WaitForFunctionOptions().setTimeout(3000));
        pauseForHumanIfHeaded();

        assertFalse(hasOverlayAttr(), "overlay must clear after rotating out of rail mode into SIDENAV");
    }

    @Test
    void railRenderedWidthMatchesNavRailWidthVariable() {
        // Regression test for a real-world (iOS/WebKit) report: routed content rendered a few
        // pixels *under* the rail's true right edge. Root cause: navbar-bottom's content-box
        // sizing let AppLayout's own touch-optimized theme's inline padding (9px each side) stack
        // on top of the declared width, plus this part's own 1px border-inline-end — measured on
        // device at 80+9+9+1=99px rendered, while content's own offset (padding-inline-start on
        // the host) used the nominal --nav-rail-width (80px), landing content 19px short of the
        // rail's true edge. Fixed with !important (so this rule's own padding-inline:0 actually
        // wins that cascade fight) and border-box (so border stops stacking on top of width too).
        //
        // This assertion doesn't reproduce the original bug in this Chromium-based suite — a plain
        // (non-!important) padding-inline:0 already wins the cascade here, unlike in WebKit, so this
        // test passes even with the fix reverted. Kept anyway as a general correctness invariant
        // (content must never start left of the rail's rendered edge), not as WebKit-bug coverage;
        // real-device verification is the only check that actually exercises the original failure.
        newTabletLandscapePage();
        navigateTo("/shared-rail");

        var railRect = shadowPartRect("navbar-bottom");
        double railRight = num(railRect, "x") + num(railRect, "width");
        double contentX = viewContentX();

        assertTrue(contentX >= railRight,
            "content (x=" + contentX + ") must not start left of the rail's true right edge (x=" + railRight + ")");
    }

    @Test
    void navbarTopPaddingInlineIsFullyResetInRailMode() {
        // Companion regression test to railRenderedWidthMatchesNavRailWidthVariable above: found
        // by source inspection (no new real-world report) that navbar-top matches the exact same
        // AppLayout-internal [part~='navbar'] rule as navbar-bottom, at the same specificity, so
        // it's subject to the identical WebKit cascade fight over padding-inline. Fixed the same
        // way — !important on this rule's own padding-inline:0 — before any real-device symptom
        // was reported for the header specifically.
        //
        // Like the navbar-bottom test, this doesn't reproduce the original WebKit-only failure in
        // this Chromium-based suite (a plain, non-!important override already wins here), so it
        // passes even with the fix reverted. Kept as a general correctness invariant.
        newTabletLandscapePage();
        navigateTo("/shared-rail");

        assertEquals(0, shadowPartComputedPx("navbar-top", "paddingInlineStart"),
            "navbar-top's own padding-inline-start must be fully reset to 0 in rail mode");
        assertEquals(0, shadowPartComputedPx("navbar-top", "paddingInlineEnd"),
            "navbar-top's own padding-inline-end must be fully reset to 0 in rail mode");
    }

    @Test
    void navbarOffsetBottomStaysZeroInRailModeRegardlessOfOtherStylesheets() {
        // Regression test for a real-world report: an app combining this layout's rail mode with
        // a second, unrelated add-on that also styles vaadin-app-layout's navbar-top/navbar-bottom
        // parts saw its entire routed content area collapse to a ~32px sliver on tablet portrait.
        //
        // Root cause: AppLayout's own _updateOffsetSize() measures navbar-bottom's rendered height
        // and republishes it as --vaadin-app-layout-navbar-offset-bottom, which the base app-layout
        // styles apply directly as the *host's* own padding-bottom. In rail mode navbar-bottom is a
        // fixed, full-viewport-height strip (inset-block-start/end: 0), so that "bar height" is the
        // entire viewport — normally masked by an unrelated cascade tie elsewhere (confirmed earlier
        // this session: the bogus value propagates but never reached the host's rendered
        // padding-bottom in this suite's own plain demo, which keeps a small, unrelated, legitimate
        // padding-bottom of its own from a completely different source). Introducing a second
        // stylesheet that also touches these parts can tip that tie and let the bogus value actually
        // land, live-confirmed against a real app: host padding-bottom computed to the full viewport
        // height (1180px on an 1180px-tall viewport) with such a stylesheet present, squeezing
        // content to ~32px.
        //
        // Fixed by unconditionally zeroing --vaadin-app-layout-navbar-offset-bottom in rail mode
        // with !important — a fix that doesn't depend on which stylesheet wins any tie, so this
        // assertion holds regardless of what else is loaded on the page. Asserting on the variable
        // itself, not on the host's overall padding-bottom, since that overall value legitimately
        // includes other, unrelated small contributions this fix has no reason to zero out. This
        // suite can't add a second real add-on to reproduce the exact trigger, but the fix's
        // guarantee (the variable is always 0 in rail mode, full stop) is directly and fully
        // testable without it.
        newTabletPortraitPage();
        navigateTo("/shared-rail");

        String offsetBottomVar = (String) page.evaluate(
            "() => getComputedStyle(document.querySelector('vaadin-app-layout'))"
            + ".getPropertyValue('--vaadin-app-layout-navbar-offset-bottom')");

        assertEquals("0px", offsetBottomVar.trim(),
            "--vaadin-app-layout-navbar-offset-bottom must be zeroed in rail mode, not left holding "
                + "navbar-bottom's stretched-to-viewport rendered height for some later cascade tie to leak");
    }

    @Test
    void overflowPopoverItemsAreStyledUnderBaseTheme() {
        // Regression test for a real bug found while polishing the overflow popover's visual
        // design (a "could use some love" request, not a functional bug report): every rule in
        // app-nav-layout.ts styling .overflow-nav-item via a bare var(--lumo-*) reference (no
        // fallback) was silently non-functional. Root cause, confirmed: this demo module itself
        // runs Vaadin 25's true default — "base" (neither Lumo nor Aura; no theme attribute is
        // set anywhere, and disabling the only aura.css <link> present changes nothing about
        // this app's own components) — and "base" defines none of the --lumo-* custom
        // properties at all. Since "base" is the only theme guarantee for any Vaadin add-on, a
        // var(--lumo-*) reference with no fallback silently invalidates the *whole* declaration
        // it's in (not just that one property) for any consumer not explicitly using Lumo — so
        // the active/inactive text coloring silently fell through to whatever color was already
        // inherited (coincidentally dark, so this had gone unnoticed), and the pre-existing
        // :hover rule never did anything at all. Fixed by giving every --lumo-* reference in
        // this file an explicit var() fallback, and by replacing LumoUtility Java class usage
        // (which has no defining CSS at all under "base") with this add-on's own CSS classes.
        newPhonePage();
        navigateTo("/");
        page.setViewportSize(NARROW_WIDTH, PHONE_HEIGHT);
        assertThat(page.locator(".touch-nav-item")).hasCount(2);

        page.evaluate("() => document.querySelector('vaadin-popover').opened = true");
        var items = page.locator(".overflow-nav-item");
        assertThat(items.first()).isVisible();

        String inactiveColor = (String) items.first().evaluate("el => getComputedStyle(el).color");
        assertTrue(!inactiveColor.isBlank() && !inactiveColor.equals("rgba(0, 0, 0, 0)"),
            "inactive overflow item color must resolve via its fallback, not silently fail: " + inactiveColor);

        String beforeHover = (String) items.first().evaluate("el => getComputedStyle(el).backgroundColor");
        items.first().hover();
        String onHover = (String) items.first().evaluate("el => getComputedStyle(el).backgroundColor");
        assertTrue(!beforeHover.equals(onHover),
            "hover background must actually change (was previously a silent no-op): "
                + beforeHover + " -> " + onHover);
    }

    @Test
    void touchNavItemsFlexUnderBaseTheme() {
        // Regression test for the structural half of the same "base theme" bug covered by
        // overflowPopoverItemsAreStyledUnderBaseTheme above — this one matters more, since a
        // LumoUtility class with no defining CSS under "base" doesn't just mis-color an element,
        // it leaves display/flex-direction/align-items/gap/padding unset entirely: icons and
        // labels stack in whatever the browser's default block layout happens to do, not
        // centered or spaced at all. Asserts the structural properties directly rather than
        // relying on visibility/count checks, which this demo module's own existing tests
        // already did without ever catching this (a plain, unstyled block-layout button is
        // still "visible" and still countable).
        newPhonePage();
        navigateTo("/");

        // The icon+label flex layout lives on the inner ".touch-nav-content" wrapper, not the
        // ".touch-nav-item" vaadin-button host itself — Button lacks HasComponents and
        // setText(String) only appends a raw text node, so icon+label are composed in a plain
        // Div passed as the button's "icon" content instead (see AbstractTouchNavRenderer).
        var content = page.locator(".touch-nav-content").first();
        assertEquals("flex", content.evaluate("el => getComputedStyle(el).display"));
        assertEquals("column", content.evaluate("el => getComputedStyle(el).flexDirection"));
        assertEquals("center", content.evaluate("el => getComputedStyle(el).alignItems"));

        // Narrow to overflow territory: hasCount() is a web-first assertion that polls until it
        // matches, so no explicit wait is needed for the resize-driven Signal.effect to settle.
        page.setViewportSize(NARROW_WIDTH, PHONE_HEIGHT);
        assertThat(page.locator(".touch-nav-item")).hasCount(2);
        page.evaluate("() => document.querySelector('vaadin-popover').opened = true");
        var overflowContent = page.locator(".overflow-nav-content").first();
        assertEquals("flex", overflowContent.evaluate("el => getComputedStyle(el).display"));
        assertEquals("center", overflowContent.evaluate("el => getComputedStyle(el).alignItems"));
        var overflowItem = page.locator(".overflow-nav-item").first();
        assertTrue(((String) overflowItem.evaluate("el => getComputedStyle(el).paddingLeft")).matches("[1-9].*"),
            "overflow item must have real padding for a comfortable tap target, not the "
                + "vaadin-button's own tighter default (a plain class selector previously lost "
                + "this cascade fight before it was worth double-checking)");
    }
}
