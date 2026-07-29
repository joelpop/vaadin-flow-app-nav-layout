package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Span;
import org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav.AppNavLayout;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType;

/**
 * Demo layout using a custom NavSelector that always selects SIDENAV, regardless
 * of device or orientation — used to verify a custom NavSelector actually
 * overrides the default device-based mapping in integration tests.
 */
public class AlwaysSidenavLayout extends AppNavLayout {

    public AlwaysSidenavLayout() {
        super("Always Sidenav", (device, orientation) -> NavType.SIDENAV);
        addBrandContent(new Span("Always Sidenav"));
    }
}
