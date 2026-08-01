package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.Route;

/**
 * Demo view under {@link AlwaysSidenavLayout}, used to verify a scenario's configured
 * renderer determines its own chrome regardless of that scenario's default.
 */
@Route(value = "always-sidenav", layout = AlwaysSidenavLayout.class)
public class AlwaysSidenavView extends Div {

    public AlwaysSidenavView() {
        add(new Paragraph("Always sidenav view content"));
    }
}
