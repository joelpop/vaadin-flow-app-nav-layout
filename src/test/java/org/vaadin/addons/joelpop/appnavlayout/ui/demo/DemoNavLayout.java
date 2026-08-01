package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Layout;
import org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav.AppNavLayout;

@Layout
public class DemoNavLayout extends AppNavLayout {
    public DemoNavLayout() {
        super("Demo App");
        addBrandContent(new Span("Demo App"));
        setUserMenu(new Span("User"));
    }
}