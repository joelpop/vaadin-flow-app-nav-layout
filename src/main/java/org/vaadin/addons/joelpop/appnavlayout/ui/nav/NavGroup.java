package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

import com.vaadin.flow.component.icon.Icon;

import java.util.function.Supplier;

/**
 * A named grouping node in the navigation hierarchy.
 * Root groups have a null parent.
 */
public interface NavGroup {
    String title();
    Supplier<Icon> icon();
    NavGroup parent();
}
