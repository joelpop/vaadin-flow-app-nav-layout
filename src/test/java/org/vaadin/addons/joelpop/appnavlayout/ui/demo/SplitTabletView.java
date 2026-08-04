package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.Route;

@Route(value = "split-tablet", layout = SplitTabletNavLayout.class)
public class SplitTabletView extends Div {
    public SplitTabletView() {
        add(new Paragraph("Split tablet view content"));
    }
}
