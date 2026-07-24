package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.router.Route;
import org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderComponent;

/**
 * Demo view at catalog/detail that contributes a view-header action component.
 * Not registered in @Menu, so it is routable but invisible in the nav bar —
 * used purely to exercise the HasViewHeaderComponent path in integration tests.
 */
@Route(value = "catalog/detail", layout = DemoNavLayout.class)
public class CatalogDetailView extends Div implements HasViewHeaderComponent {

    public CatalogDetailView() {
        add(new Paragraph("Catalog detail view content"));
    }

    @Override
    public Component getViewHeaderComponent() {
        var btn = new Button("Add Item");
        btn.setId("view-header-action");
        return btn;
    }
}
