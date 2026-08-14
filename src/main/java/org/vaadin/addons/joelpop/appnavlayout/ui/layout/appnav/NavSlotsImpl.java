package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import com.vaadin.flow.component.HasComponents;

/** Trivial {@link NavSlots} holder; strategies pass their real, attached slot container for the
 *  location(s) they own, and a shared inert (unattached) one for the rest. */
record NavSlotsImpl(HasComponents drawer, HasComponents sideRail, HasComponents touchBar,
                     HasComponents headerNav, HasComponents tabStrip) implements NavSlots {
}
