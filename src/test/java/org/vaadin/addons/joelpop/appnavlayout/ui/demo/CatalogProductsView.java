package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;

@Route("catalog/products")
@Menu(title = "Products", order = 2.1)
public class CatalogProductsView extends Div {
    public CatalogProductsView() {
        add(new Paragraph("Catalog products content"));
    }
}
