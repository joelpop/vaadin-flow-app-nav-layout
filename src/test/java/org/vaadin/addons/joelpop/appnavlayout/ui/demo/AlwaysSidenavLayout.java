package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Span;
import org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav.AppNavLayout;
import org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav.SideNavDrawerNavRenderer;

/**
 * Demo layout that always shows the drawer-based SideNav, regardless of device or
 * orientation — used to verify that a scenario's renderer determines its own chrome even
 * when it doesn't match that scenario's default.
 */
public class AlwaysSidenavLayout extends AppNavLayout {

    public AlwaysSidenavLayout() {
        addBranding(new Span("Always Sidenav"));
        setDesktopNavRenderer(SideNavDrawerNavRenderer::new);
        setTabletNavRenderer(SideNavDrawerNavRenderer::new);
        setPhoneNavRenderer(SideNavDrawerNavRenderer::new);
    }
}
