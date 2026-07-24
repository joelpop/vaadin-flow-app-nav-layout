package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    // Default selector: portrait → RAIL, landscape → SIDENAV.
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

    // ——————————— Navigation helper ————————————

    private void navigateTo(String path) {
        page.navigate(BASE_URL + path);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        // Extra settle time for ResizeObserver (fires in next animation frame after layout).
        page.waitForTimeout(150);
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
        page.setViewportSize(NARROW_WIDTH, PHONE_HEIGHT);
        page.waitForTimeout(200); // let the Signal.effect fire and DOM settle
        assertThat(page.locator(".touch-nav-item")).hasCount(2);
        pauseForHumanIfHeaded();

        // Widen back: buildItems() fires again; icons must be freshly created, not moved.
        page.setViewportSize(PHONE_WIDTH, PHONE_HEIGHT);
        page.waitForTimeout(200);
        assertThat(page.locator(".touch-nav-item")).hasCount(3);
        assertThat(page.locator(".touch-nav-item vaadin-icon")).hasCount(3);
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
        newTabletPortraitPage();
        navigateTo("/");

        // Initial RAIL state: drawer must be in overlay mode (not push mode).
        assertTrue((boolean) page.evaluate("() => document.querySelector('vaadin-app-layout').overlay"),
                "drawer should be in overlay mode at portrait tablet");
        pauseForHumanIfHeaded();

        // Rotate to landscape — default selector switches to SIDENAV.
        page.setViewportSize(TABLET_LANDSCAPE_WIDTH, TABLET_LANDSCAPE_HEIGHT);
        page.waitForTimeout(300);

        // Rotate back to portrait — RAIL rebuilds; overlay mode must be restored.
        page.setViewportSize(TABLET_PORTRAIT_WIDTH, TABLET_PORTRAIT_HEIGHT);
        page.waitForTimeout(300);

        assertTrue((boolean) page.evaluate("() => document.querySelector('vaadin-app-layout').overlay"),
                "drawer should remain in overlay mode after orientation change back to portrait");
        pauseForHumanIfHeaded();
    }

    @Test
    void secondaryNavSurvivesTabletOrientationChange() {
        newTabletPortraitPage();
        navigateTo("/catalog/products");
        assertThat(page.locator(".secondary-tab-bar vaadin-tabs")).isVisible();
        pauseForHumanIfHeaded();

        // Rotate to landscape — default selector switches to SIDENAV
        page.setViewportSize(TABLET_LANDSCAPE_WIDTH, TABLET_LANDSCAPE_HEIGHT);
        page.waitForTimeout(300);
        pauseForHumanIfHeaded();

        // Rotate back to portrait — RAIL rebuilds; secondary nav must reappear
        page.setViewportSize(TABLET_PORTRAIT_WIDTH, TABLET_PORTRAIT_HEIGHT);
        page.waitForTimeout(300);
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
    void sidenavModeActivatesOnTabletLandscape() {
        context.close();
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(TABLET_LANDSCAPE_WIDTH, TABLET_LANDSCAPE_HEIGHT)
                .setHasTouch(true));
        page = context.newPage();
        navigateTo("/");

        // SIDENAV mode: no rail, side nav visible.
        assertFalse(hasNavRailAttr(), "nav-rail must be absent in SIDENAV mode");
        assertThat(page.locator("vaadin-side-nav")).isVisible();
        assertThat(page.locator(".touch-nav-item")).not().isVisible();
        pauseForHumanIfHeaded();
    }

    @Test
    void navRailAttributeAbsentAfterOrientationChangeToSidenav() {
        newTabletPortraitPage();
        navigateTo("/");
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
        // The rail (navbar-bottom) is pinned to inset-inline-start:0 so AppHeadroom
        // can slide the top bar without leaving a gap above the rail.
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
}
