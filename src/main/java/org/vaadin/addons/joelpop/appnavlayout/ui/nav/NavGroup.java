package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

import com.vaadin.flow.component.icon.Icon;

/**
 * A named grouping node in the navigation hierarchy.
 * Root groups have a null parent.
 */
public interface NavGroup {
    String title();
    Icon icon();
    NavGroup parent();
}
