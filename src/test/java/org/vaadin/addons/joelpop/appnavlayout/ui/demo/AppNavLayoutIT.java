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

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

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

    @Test
    void touchNavRendersAllItemsWithIconsAtPhoneViewport() {
        newPhonePage();
        page.navigate(BASE_URL + "/");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        pauseForHumanIfHeaded();

        // Three views registered — all should appear as primary touch nav items.
        assertThat(page.locator(".touch-nav-item")).hasCount(3);
        // Each item must have an icon; no blank placeholder slots.
        assertThat(page.locator(".touch-nav-item vaadin-icon")).hasCount(3);
    }

    @Test
    void touchNavIconsRemainCorrectAfterNavBarRebuild() {
        newPhonePage();
        page.navigate(BASE_URL + "/");
        page.waitForLoadState(LoadState.NETWORKIDLE);

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
        page.navigate(BASE_URL + "/");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        pauseForHumanIfHeaded();

        assertThat(page.locator("vaadin-side-nav")).isVisible();
        // Three views — three SideNavItems.
        assertThat(page.locator("vaadin-side-nav-item")).hasCount(3);
    }

    @Test
    void navigationChangesActiveViewContent() {
        newPhonePage();
        page.navigate(BASE_URL + "/");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Navigate to Catalog via URL; verify the view content loads.
        page.navigate(BASE_URL + "/catalog");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        assertThat(page.locator("text=Catalog view content")).isVisible();
        pauseForHumanIfHeaded();
    }

    private void pauseForHumanIfHeaded() {
        if (headed) page.waitForTimeout(pauseMs);
    }
}
