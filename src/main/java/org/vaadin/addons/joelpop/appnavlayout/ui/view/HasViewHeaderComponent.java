package org.vaadin.addons.joelpop.appnavlayout.ui.view;

import com.vaadin.flow.component.Component;

/**
 * Implemented by views that want to contribute an action component to the
 * adaptive view header slot. {@link AppNavLayout} checks for this interface on
 * each navigation and places the result in the {@code viewHeaderSlot} on both
 * desktop and mobile.
 *
 * <p>Return {@code null} to suppress the slot. {@link AdminBaseView} implements
 * this interface for the common case of a single action button.
 */
public interface HasViewHeaderComponent {

    Component getViewHeaderComponent();
}
