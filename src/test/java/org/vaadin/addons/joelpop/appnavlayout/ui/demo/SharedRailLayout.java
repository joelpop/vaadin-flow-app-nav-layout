package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Span;
import org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav.AppNavLayout;
import org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav.SideRailNavRenderer;

/**
 * Demo layout using {@code setTabletNavRenderer(SideRailNavRenderer::new)} — one shared
 * {@link SideRailNavRenderer} instance across both tablet orientations. Regression coverage
 * for a real-world report: before {@code NavType} was derived from the active renderer, tablet
 * landscape still defaulted to {@code NavType.SIDENAV} (the drawer) despite the shared renderer
 * being rail-only, silently rendering into a slot that was never attached to the page.
 */
public class SharedRailLayout extends AppNavLayout {

    public SharedRailLayout() {
        addBranding(new Span("Shared Rail"));
        setTabletNavRenderer(SideRailNavRenderer::new);
    }
}
