package org.vaadin.addons.joelpop.appnavlayout.ui.demo;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import org.vaadin.addons.joelpop.appnavlayout.ui.view.HasViewHeaderTitle;

/**
 * Demo view at catalog/titled that contributes a view-header icon+title via
 * HasViewHeaderTitle. Not registered in @Menu, so it is routable but invisible
 * in the nav bar — used purely to exercise the HasViewHeaderTitle path
 * (desktop only) in integration tests.
 */
@Route(value = "catalog/titled", layout = DemoNavLayout.class)
@PageTitle("Titled Detail")
public class TitledDetailView extends Div implements HasViewHeaderTitle {

    public TitledDetailView() {
        add(new Paragraph("Titled detail view content"));
    }

    @Override
    public Icon getViewHeaderIcon() {
        return VaadinIcon.STAR.create();
    }
}
