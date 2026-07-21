package org.vaadin.addons.joelpop.appnavlayout.ui.view;

import com.vaadin.flow.component.Component;

/**
 * Implemented by views that want to contribute an action component to the
 * adaptive view header slot. {@link AppNavLayout} checks for this interface on
 * each navigation and places the result in the {@code viewHeaderSlot} on both
 * desktop and mobile.
 *
 * <p>Return {@code null} to suppress the slot.
 */
public interface HasViewHeaderComponent {

    /**
     * Returns the component to place in the view header slot, or {@code null} to suppress the slot.
     *
     * <p>Called on every navigation event. If this method returns a stateful component that is
     * already attached elsewhere in the layout, return that same instance; otherwise return a
     * new instance on each call.
     */
    Component getViewHeaderComponent();
}
