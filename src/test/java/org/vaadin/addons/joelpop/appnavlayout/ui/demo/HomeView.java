package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;

@Route("")
@Menu(title = "Home", icon = "vaadin:home", order = 1)
public class HomeView extends Div {
    public HomeView() {
        add(new Paragraph("Home view content"));
    }
}