package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;

@Route("catalog")
@Menu(title = "Catalog", icon = "vaadin:package", order = 2)
public class CatalogView extends Div {
    public CatalogView() {
        add(new Paragraph("Catalog view content"));
    }
}