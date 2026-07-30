package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.orderedlayout.FlexLayout;

/**
 * Default {@link NavRenderer} for {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#TOUCH}
 * — a bottom icon bar in {@link NavSlots#touchBar()}, with a "More" overflow trigger when more
 * root sections exist than fit, plus a two-level drill-down bar in {@link NavSlots#headerNav()}.
 *
 * <p>Override {@link #createOverflowComponent} to replace the overflow presentation (e.g. an
 * expand chevron or a swipeable strip) while keeping everything else unchanged.
 */
public class TouchBarNavRenderer extends AbstractTouchNavRenderer {

    public TouchBarNavRenderer() {
        super(FlexLayout.FlexDirection.ROW);
    }

    @Override
    protected HasComponents primarySlot(NavSlots slots) {
        return slots.touchBar();
    }
}
