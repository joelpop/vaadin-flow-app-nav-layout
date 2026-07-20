/*
 * Browser-side CSS for AppNavLayout (loaded via @JsModule from AppNavLayout.java).
 *
 * This styles AppNavLayout's own constructs entirely in terms of standard
 * vaadin-app-layout parts and CSS custom properties that AppNavLayout's Java
 * code sets on itself (the nav-rail attribute, --nav-rail-width) — nothing
 * here references any other add-on. In particular, AppNavLayout's rail mode
 * works by repositioning AppLayout's own standard navbar-bottom part (the
 * same slot used for the ordinary touch bottom bar), rather than introducing
 * a distinct part of its own — which is exactly why generic tools that only
 * know about AppLayout's standard navbar-top/navbar-bottom contract (such as
 * an unrelated scroll-hiding add-on) can interoperate with it automatically,
 * without needing to know anything about AppNavLayout specifically.
 *
 * The CSS must live here (not in a Java @StyleSheet) because some rules target
 * vaadin-app-layout's internal shadow DOM via ::part() — a CSS selector that
 * can cross shadow-DOM boundaries from outside the component.
 */

const GLOBAL_STYLES = new CSSStyleSheet();
GLOBAL_STYLES.replaceSync(`
    /* Force overlay drawer mode on rail devices (portrait tablet exceeds the 800px media query). */
    vaadin-app-layout[nav-rail] {
        --vaadin-app-layout-drawer-overlay: true;
    }

    /* Rail: pin navbar-bottom slot to the left edge, below the top bar. */
    vaadin-app-layout[nav-rail]::part(navbar-bottom) {
        position: fixed !important;
        inset-block-start: var(--vaadin-app-layout-navbar-offset-top, 3.5rem);
        inset-block-end: 0;
        inset-inline-start: 0;
        width: var(--nav-rail-width, 5rem);
        z-index: 200;
        will-change: auto;
        padding-block-start: var(--lumo-space-s);
        padding-block-end: 0;
        background: var(--lumo-contrast-5pct);
        border-inline-end: 1px solid var(--lumo-contrast-10pct);
    }

    /* Drawer slides over the rail when opened. */
    vaadin-app-layout[nav-rail]::part(drawer) {
        z-index: 201;
    }

    /* Rail items: centered, with vertical padding for comfortable tap targets. */
    vaadin-app-layout[nav-rail] .touch-nav-item {
        padding-block: var(--lumo-space-s);
    }

    /* Active state for touch/rail nav items. */
    .touch-nav-item.active {
        color: var(--lumo-primary-color);
    }

    /* Overflow popover buttons: secondary by default, primary when active.
       ::part(label/prefix) reaches into vaadin-button's shadow DOM. */
    vaadin-button.overflow-nav-item:not(.active)::part(label),
    vaadin-button.overflow-nav-item:not(.active)::part(prefix) {
        color: var(--lumo-secondary-text-color);
    }
`);
document.adoptedStyleSheets = [...document.adoptedStyleSheets, GLOBAL_STYLES];

// vaadin-app-layout evaluates --vaadin-app-layout-drawer-overlay inside its
// window 'resize' handler (_resize → _updateOverlayMode). It does NOT watch
// element-level resize — only window resize. So whenever nav-rail is added
// (initial attach or orientation-driven rebuild), fire a synthetic window
// resize so AppLayout re-evaluates the variable now that both the nav-rail
// attribute and our CSS rule are live.
new MutationObserver(mutations => {
    for (const mutation of mutations) {
        if ((mutation.target as HTMLElement).hasAttribute('nav-rail')) {
            requestAnimationFrame(() => window.dispatchEvent(new Event('resize')));
            break;
        }
    }
}).observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['nav-rail'],
    subtree: true,
});