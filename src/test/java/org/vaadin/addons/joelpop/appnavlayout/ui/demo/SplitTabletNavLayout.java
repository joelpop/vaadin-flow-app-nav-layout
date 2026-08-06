package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Span;
import org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav.AppNavLayout;
import org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav.SideNavDrawerNavRenderer;

/**
 * Demo layout with tablet portrait and landscape given genuinely different chrome (rail vs.
 * drawer) — recreates the relationship the two tablet orientations used to have by default,
 * before both defaulted to rail. Kept so the orientation-driven NavType-switch regression tests
 * (drawer overlay mode, nav-rail attribute toggling) still have a real rail↔sidenav transition
 * to exercise. Deliberately has no {@code @Menu}-annotated routes of its own — this add-on's
 * nav bar/rail/drawer content is drawn from the app-wide {@code MenuConfiguration}, not scoped
 * per layout instance, so adding one here would add a stray item to every other demo layout's
 * nav too.
 */
public class SplitTabletNavLayout extends AppNavLayout {

    public SplitTabletNavLayout() {
        addBranding(new Span("Split Tablet"));
        setTabletLandscapeNavRenderer(SideNavDrawerNavRenderer::new);
    }
}
