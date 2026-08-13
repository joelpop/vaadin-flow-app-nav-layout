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
 *
 * "base" (no Lumo, no Aura, no theme attribute at all) is the only theme any Vaadin
 * add-on can rely on — a consuming app may use Lumo, Aura, a fully custom theme, or
 * nothing. So this file references only the generic, theme-agnostic --vaadin-*
 * design tokens (text/background/border colors, padding/gap scale — all defined
 * unconditionally by @vaadin/component-base's style-props, confirmed present even
 * with no theme selected at all), never a --lumo-* or --aura-* token directly. Both
 * Lumo and Aura redefine these exact same --vaadin-* names to their own theme's
 * values (e.g. Lumo's color.css sets --vaadin-text-color-secondary: var(--lumo-
 * secondary-text-color)), so referencing the generic name still adapts correctly
 * under either theme — there's nothing to gain from naming the theme-specific
 * token directly, and doing so would only work under that one theme. Where no
 * generic token exists at all (font-size, icon-size — typographic scale isn't part
 * of the base design-token set), a plain literal value is used instead of guessing
 * at a theme-specific one.
 */

const GLOBAL_STYLES = new CSSStyleSheet();
GLOBAL_STYLES.replaceSync(`
    /* Bridges the env() safe-area-inset-* values (unsafe strips behind a device notch,
       rounded corners, or home indicator) onto custom properties so Java code can read them
       via getComputedStyle — env() itself isn't queryable directly, only through a property
       it's been assigned to. Read by AbstractTouchNavRenderer to keep the "how many icons fit"
       calculation from overestimating on notched devices, where window.innerWidth/innerHeight
       includes those unsafe strips but a labeled icon can't actually render inside them. */
    :root {
        --nav-safe-area-inset-top: env(safe-area-inset-top, 0px);
        --nav-safe-area-inset-right: env(safe-area-inset-right, 0px);
        --nav-safe-area-inset-bottom: env(safe-area-inset-bottom, 0px);
        --nav-safe-area-inset-left: env(safe-area-inset-left, 0px);
    }

    /* Ensure the layout always fills the viewport so position:fixed elements anchor
       correctly regardless of whether the html/body height chain is set. */
    vaadin-app-layout {
        min-height: 100dvh;
    }

    /* dvh requires the viewport to be "exercised" via an actual geometry change before it
       computes correctly on iOS — in standalone (installed-PWA) display mode, WebKit skips
       that on cold launch, so 100dvh can settle to a different value than window.innerHeight
       until the first rotation, leaving position:fixed content (e.g. the touch bar) short of
       the true bottom edge. Confirmed on-device: static vh doesn't have this bug, and
       standalone mode has no browser toolbar to create the vh-vs-dvh gap dvh exists to solve
       in the first place, so it's safe to prefer here — verified across cold launch and
       rotation in both orientations. display-mode can't be simulated in this repo's Playwright
       IT suite, so this rule is untested there; it's real-device verified only. */
    @media (display-mode: standalone) {
        vaadin-app-layout {
            min-height: 100vh;
        }
    }

    /* Lumo's own app-layout theme sets padding-top: var(--safe-area-inset-top) on the generic
       [part~='navbar'] selector — correct for navbar-top, which sits behind the status bar/
       notch — but its own navbar-bottom override never resets padding-top back down. Since
       navbar-bottom also carries the "navbar" part token, the bottom bar inherits the TOP
       bar's safe-area inset as its own top padding, inflating its height for no reason (the
       bottom bar's top edge isn't behind anything unsafe). Applies to the ordinary touch bar
       too, not just the rail — this isn't nav-rail-scoped. */
    vaadin-app-layout::part(navbar-bottom) {
        padding-top: var(--vaadin-app-layout-navbar-padding-top, var(--vaadin-padding-s));
    }

    /* Force overlay drawer mode on rail devices (portrait tablet exceeds the 800px media query).

       Also neutralizes --vaadin-app-layout-navbar-offset-bottom, which AppLayout derives from
       navbar-bottom's own rendered height (its internal offset-size calculation reads this
       part's getBoundingClientRect().height) and then applies as the *host's own* padding-bottom.
       That measurement assumes navbar-bottom is an ordinary horizontal bottom bar; in rail mode
       it's a fixed, full-viewport-height vertical strip (inset-block-start/end: 0 below), so the
       "bar height" it reports is the viewport's full height — and once anything else adopts a
       stylesheet that also touches these parts (any other CSS reaching the same navbar-top/
       navbar-bottom parts, e.g. a separate scroll-behavior add-on styling the same AppLayout,
       shifts which stylesheet's rules win adjacent cascade ties), that bogus value can start
       actually landing as the host's real padding-bottom instead of being incidentally masked —
       collapsing the entire content area to a sliver (hostPaddingBottom computing to the full
       viewport height, e.g. 1180px on an 1180px-tall viewport, with such a stylesheet present,
       squeezes routed content down to a ~32px strip). The rail already reserves its space via
       padding-inline-start, not padding-bottom, so there's nothing for this to legitimately
       contribute here regardless of what else is loaded on the page. !important because relying
       on which stylesheet happens to be adopted last is exactly the failure mode this fixes. */
    vaadin-app-layout[nav-rail] {
        --vaadin-app-layout-drawer-overlay: true;
        --vaadin-app-layout-navbar-offset-bottom: 0px !important;
    }

    /* Rail: pin navbar-bottom slot to the full left edge, top to bottom.
       Starting at 0 (not at navbar-offset-top) means the rail never jumps when
       navigation changes the header height, and a scroll-hiding top bar does
       not leave a gap above the rail. The companion ::part(navbar-top) rule below
       keeps the fixed top bar out of the rail's x=0–5rem strip. */
    vaadin-app-layout[nav-rail]::part(navbar-bottom) {
        position: fixed !important;
        inset-block-start: 0;
        inset-block-end: 0;
        inset-inline-start: 0;
        z-index: 200;
        will-change: auto;
        padding-block-start: var(--vaadin-padding-s);
        padding-block-end: 0;
        /* --nav-rail-width is also what padding-inline-start on the host (below) uses to push
           routed content clear of the rail — that only lands correctly if this part's true
           rendered width is exactly --nav-rail-width, not merely its declared content width.
           box-sizing defaults to content-box, under which padding/border stack on top of the
           declared width; AppLayout's own touch-optimized theme applies 9px of its own inline
           padding to this part (meant for the ordinary horizontal bottom bar) with the same
           specificity as this rule, and can be (re)adopted after it, so a plain "padding-inline: 0"
           here silently loses that fight — confirmed by measuring the rendered part at 99px
           (80 + 9 + 9 + this rule's own 1px border-inline-end) despite this rule "resetting" the
           padding. Two fixes, not one: !important actually wins the padding fight, and border-box
           (so border no longer stacks on top of the declared width either) makes the two numbers
           equal by construction instead of by arithmetic that has to be re-verified by hand every
           time either side changes. */
        box-sizing: border-box !important;
        width: var(--nav-rail-width, 5rem);
        padding-inline: 0 !important;
        background: var(--vaadin-background-container);
        border-inline-end: 1px solid var(--vaadin-border-color-secondary);
    }

    /* Drawer slides over the rail when opened. */
    vaadin-app-layout[nav-rail]::part(drawer) {
        z-index: 201;
    }

    /* Both navbars carry "navbar" in their part list (part="navbar navbar-top" and
       part="navbar navbar-bottom"), so ::part(navbar) would match both. Use the more
       specific ::part(navbar-top) to target only the top bar. AppLayout's own
       transition: inset-inline-start remains intact.
       Same cascade fight as navbar-bottom above, same root cause: this part matches
       AppLayout's own shadow-DOM-internal [part~='navbar'] rule, which applies 9px of
       padding-inline via --vaadin-padding-s at the same specificity as this external
       ::part() override, and can win the tie on WebKit despite this rule "resetting" it
       — confirmed for navbar-bottom by direct measurement; navbar-top is governed by
       the identical rule/variable, so !important is needed here for the same reason,
       not merely for consistency. Left unreset, the header's own slotted content
       resolves its width% against a content box up to 18px (9px each side) narrower
       than the space actually available beside the rail. No explicit width/box-sizing
       fix needed here (unlike navbar-bottom): this part's width comes from two insets,
       not from a declared width that padding/border could stack on top of. */
    vaadin-app-layout[nav-rail]::part(navbar-top) {
        inset-inline-start: var(--nav-rail-width, 5rem);
        padding-inline: 0 !important;
    }

    /* Primary nav bar: display/flex-direction/justify-content/align-items are set inline from
       Java (AbstractTouchNavRenderer.ensureBuilt), since they depend on which of the two touch
       renderers (rail vs. bottom bar) is active — only the theme-flavored bit (gap) lives here,
       rail-scoped to match Java's own COLUMN-direction-only gap application. */
    vaadin-app-layout[nav-rail] .nav-bar {
        gap: var(--vaadin-gap-m);
    }

    /* This is a real vaadin-button (theme="tertiary" — see AbstractTouchNavRenderer.navItem's own
       comment for why), not a bare reset element, so only sizing is overridden here; the button's
       own tertiary-variant CSS already supplies a transparent background and borderless look. */
    .touch-nav-item {
        /* Overrides the flex-item default (min-width:auto, which pins the shrink floor to the
           label's un-wrapped width) so an item can shrink below its own natural content width
           and .touch-nav-label's ellipsis can engage instead of forcing the bar/rail wider than
           intended — see AbstractTouchNavRenderer.navItem's own comment for the full story. */
        min-width: 0;
        padding: 0;
    }

    /* Row-direction (bottom bar) items share the bar's width equally; column-direction (rail)
       items are stretched to the rail's own width by the rail's own align-items:stretch instead. */
    vaadin-app-layout:not([nav-rail]) .touch-nav-item {
        flex: 1 1 0;
    }

    /* Rail items: vertical padding for comfortable tap targets. */
    vaadin-app-layout[nav-rail] .touch-nav-item {
        padding-block: var(--vaadin-padding-s);
    }

    /* Icon+label layout for both touch/rail items and overflow-popover items lives on this inner
       wrapper, not the vaadin-button host itself — Button lacks HasComponents and setText(String)
       only appends a raw text node (no element to attach .touch-nav-label's styling to), so the
       icon and label are composed in a plain Div passed as the button's "icon" content instead;
       see AbstractTouchNavRenderer.navItem's own comment. */
    .touch-nav-content {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--vaadin-gap-xs);
        min-width: 0;
    }

    /* Stands in for the icon on a section that has none, at the same 20px footprint
       AbstractTouchNavRenderer.navItem gives a real icon — see that method's own comment for why
       this needs to take up the same space rather than being left out. */
    .touch-nav-icon-placeholder {
        width: 20px;
        height: 20px;
    }

    .touch-nav-label {
        /* No generic --vaadin-font-size-* scale exists (typography isn't part of the base
           design-token set — see file header), so a plain literal value is used here. */
        font-size: 0.75rem;
        font-weight: 700;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        /* align-items:center above (needed to center the icon) doesn't stretch this label to
           the item's own width, so without this the ellipsis/overflow above never has anything
           to actually shrink against — it would just overflow instead of truncating. */
        max-width: 100%;
    }

    /* Active vs. inactive state for touch/rail/overflow nav items. Both are real vaadin-buttons
       with theme="tertiary" (see AbstractTouchNavRenderer.navItem's own comment), whose own CSS
       already colors the button's text in the active theme's own accent (Lumo blue, Aura's
       accent, or a neutral default under base) — exactly the behavior the user expects to match
       vaadin-side-nav-item's own selected-item color. Inactive items override that back down to
       the muted secondary color; !important is needed to win over the button's own tertiary-
       variant color rule, which the theme's own stylesheet sets at equal selector specificity. */
    .touch-nav-item:not(.active),
    .overflow-nav-item:not(.active) {
        color: var(--vaadin-text-color-secondary) !important;
    }

    /* Overflow popover buttons: full-width, left-aligned (Button's own default centers content). */
    .overflow-nav-item {
        justify-content: flex-start;
        padding-inline: var(--vaadin-padding-m);
        padding-block: var(--vaadin-padding-m);
    }

    /* Icon+label row layout — see .touch-nav-content above for why this lives on an inner
       wrapper rather than the button host itself. */
    .overflow-nav-content {
        display: flex;
        align-items: center;
        gap: var(--vaadin-gap-m);
    }

    /* !important needed to win over the button's own tertiary-variant background rule (transparent),
       set by the theme's own stylesheet at equal selector specificity — same fight as the active/
       inactive color rule above. */
    .overflow-nav-item:hover {
        background: var(--vaadin-background-container) !important;
    }

    /* :active (not just :hover) gives touch input real tap feedback — on a touchscreen,
       :hover can stick after a tap instead of clearing, or never engage at all, since these
       items exist specifically for touch/rail nav. "Strong" variant so it's visibly a step up
       from the :hover background above, not just a repeat of it. */
    .overflow-nav-item:active {
        background: var(--vaadin-background-container-strong) !important;
    }

    /* Desktop-only view header bottom border — toggled on/off by DesktopNavStrategy's own
       build()/tearDown(), since only that NavType's chrome ever wants it. */
    .view-header-slot-bordered {
        border-bottom: 1px solid var(--vaadin-border-color-secondary);
    }

    /* Default HasViewHeaderTitle.getViewHeaderTitle() layout: icon + heading + optional suffix. */
    .view-header-title {
        gap: var(--vaadin-gap-s);
    }

    /* No generic --vaadin-icon-size-* scale exists — see file header — so a plain literal
       value is used here. */
    .view-header-icon {
        width: 1.5rem;
        height: 1.5rem;
    }

    .view-header-title-text {
        /* No generic --vaadin-font-size-* scale exists — see file header. */
        font-size: 1.375rem;
        font-weight: 600;
        color: var(--vaadin-text-color);
        margin: 0;
    }
`);
document.adoptedStyleSheets = [...document.adoptedStyleSheets, GLOBAL_STYLES];

// vaadin-app-layout evaluates --vaadin-app-layout-drawer-overlay inside its
// window 'resize' handler (_resize → _updateOverlayMode). It does NOT watch
// element-level resize — only window resize. So whenever nav-rail changes
// (added on initial attach/orientation-driven rebuild into rail, or removed on
// leaving it), fire a synthetic window resize so AppLayout re-evaluates the
// variable now that both the nav-rail attribute and our CSS rule are settled.
// Must cover removal too, not just addition: leaving rail mode also removes
// --vaadin-app-layout-drawer-overlay, and without a resize nudge afterward,
// AppLayout's own overlay state never gets re-evaluated and stays stuck true.
new MutationObserver(() => {
    requestAnimationFrame(() => window.dispatchEvent(new Event('resize')));
}).observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['nav-rail'],
    subtree: true,
});