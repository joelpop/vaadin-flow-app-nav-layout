package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;

@Route("orders")
@Menu(title = "Orders", icon = "vaadin:list", order = 3)
public class OrdersView extends Div {
    public OrdersView() {
        add(new Paragraph("Orders view content"));
    }
}