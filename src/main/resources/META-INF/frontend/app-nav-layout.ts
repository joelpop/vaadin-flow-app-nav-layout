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
    /* Ensure the layout always fills the viewport so position:fixed elements anchor
       correctly in iOS PWA mode regardless of whether the html/body height chain is set. */
    vaadin-app-layout {
        min-height: 100dvh;
    }

    /* Force overlay drawer mode on rail devices (portrait tablet exceeds the 800px media query). */
    vaadin-app-layout[nav-rail] {
        --vaadin-app-layout-drawer-overlay: true;
    }

    /* Rail: pin navbar-bottom slot to the full left edge, top to bottom.
       Starting at 0 (not at navbar-offset-top) means the rail never jumps when
       navigation changes the header height, and a scroll-hiding top bar does
       not leave a gap above the rail. The companion ::part(navbar) rule below
       keeps the fixed top bar out of the rail's x=0–5rem strip. */
    vaadin-app-layout[nav-rail]::part(navbar-bottom) {
        position: fixed !important;
        inset-block-start: 0;
        inset-block-end: 0;
        inset-inline-start: 0;
        width: var(--nav-rail-width, 5rem);
        z-index: 200;
        will-change: auto;
        padding-block-start: var(--lumo-space-s);
        padding-block-end: 0;
        /* This part's box-sizing is content-box, and AppLayout's own default touch-bar theme
           applies inline padding meant for the ordinary bottom bar (~12.66px each side) — left
           unreset, that padding adds on top of the width above (measured rendering 80px + 25.3px
           = 105.3px instead of 80px), which then throws off AppLayout's own internal navbar-top
           width calculation (it accounts for this part's actual rendered width). The rail has no
           use for that inset; the items inside size themselves. */
        padding-inline: 0;
        background: var(--lumo-contrast-5pct);
        border-inline-end: 1px solid var(--lumo-contrast-10pct);
    }

    /* Drawer slides over the rail when opened. */
    vaadin-app-layout[nav-rail]::part(drawer) {
        z-index: 201;
    }

    /* Both navbars carry "navbar" in their part list (part="navbar navbar-top" and
       part="navbar navbar-bottom"), so ::part(navbar) would match both. Use the more
       specific ::part(navbar-top) to target only the top bar. AppLayout's own
       transition: inset-inline-start remains intact.
       This part is also content-box with AppLayout's default touch-bar padding
       (~12.66px each side) left unreset, same as navbar-bottom above. Left in place,
       the header's own slotted content resolves its width% against a content box
       that's 25.3px narrower than the space actually available beside the rail. */
    vaadin-app-layout[nav-rail]::part(navbar-top) {
        inset-inline-start: var(--nav-rail-width, 5rem);
        padding-inline: 0;
    }

    /* Reset native <button> defaults so touch-nav-item / overflow-nav-item look like the design. */
    button.touch-nav-item,
    button.overflow-nav-item {
        background: none;
        border: none;
        padding: 0;
        cursor: pointer;
        font: inherit;
        text-align: start;
    }

    /* Rail items: centered, with vertical padding for comfortable tap targets. */
    vaadin-app-layout[nav-rail] .touch-nav-item {
        padding-block: var(--lumo-space-s);
    }

    /* Active state for touch/rail/overflow nav items. */
    .touch-nav-item.active,
    .overflow-nav-item.active {
        color: var(--lumo-primary-color);
    }

    /* Overflow popover buttons: secondary by default, primary when active. */
    .overflow-nav-item:not(.active) {
        color: var(--lumo-secondary-text-color);
    }

    .overflow-nav-item:hover {
        background: var(--lumo-contrast-5pct);
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