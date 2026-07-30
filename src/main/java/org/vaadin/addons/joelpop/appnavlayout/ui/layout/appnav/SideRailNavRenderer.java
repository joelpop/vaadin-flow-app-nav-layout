package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.orderedlayout.FlexLayout;

/**
 * Default {@link NavRenderer} for {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#RAIL}
 * — a left-edge icon rail in {@link NavSlots#sideRail()}, with a "More" overflow trigger when
 * more root sections exist than fit, plus a two-level drill-down bar in
 * {@link NavSlots#headerNav()}.
 *
 * <p>Override {@link #createOverflowComponent} to replace the overflow presentation while
 * keeping everything else unchanged.
 */
public class SideRailNavRenderer extends AbstractTouchNavRenderer {

    public SideRailNavRenderer() {
        super(FlexLayout.FlexDirection.COLUMN);
    }

    @Override
    protected HasComponents primarySlot(NavSlots slots) {
        return slots.sideRail();
    }
}
