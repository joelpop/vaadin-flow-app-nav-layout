package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;

@Route("catalog/categories")
@Menu(title = "Categories", order = 2.2)
public class CatalogCategoriesView extends Div {
    public CatalogCategoriesView() {
        add(new Paragraph("Catalog categories content"));
    }
}
