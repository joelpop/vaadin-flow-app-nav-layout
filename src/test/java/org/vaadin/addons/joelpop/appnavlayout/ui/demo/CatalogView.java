package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.Route;

@Route("catalog")
public class CatalogView extends Div {
    public CatalogView() {
        add(new Paragraph("Catalog view content"));
    }
}