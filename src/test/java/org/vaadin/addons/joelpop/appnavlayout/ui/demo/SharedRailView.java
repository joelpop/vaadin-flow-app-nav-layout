package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.Route;

@Route(value = "shared-rail", layout = SharedRailLayout.class)
public class SharedRailView extends Div {
    public SharedRailView() {
        add(new Paragraph("Shared rail view content"));
    }
}
