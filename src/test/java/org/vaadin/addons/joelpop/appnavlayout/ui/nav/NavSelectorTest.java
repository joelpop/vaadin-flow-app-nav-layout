package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NavSelectorTest {

    private final NavSelector selector = NavSelector.defaultSelector();

    @Test
    void desktopAlwaysSelectsSidenavRegardlessOfOrientation() {
        assertEquals(NavType.SIDENAV, selector.select(DeviceType.DESKTOP, Orientation.LANDSCAPE));
        assertEquals(NavType.SIDENAV, selector.select(DeviceType.DESKTOP, Orientation.PORTRAIT));
    }

    @Test
    void tabletLandscapeSelectsSidenav() {
        assertEquals(NavType.SIDENAV, selector.select(DeviceType.TABLET, Orientation.LANDSCAPE));
    }

    @Test
    void tabletPortraitSelectsRail() {
        assertEquals(NavType.RAIL, selector.select(DeviceType.TABLET, Orientation.PORTRAIT));
    }

    @Test
    void phoneAlwaysSelectsTouchRegardlessOfOrientation() {
        assertEquals(NavType.TOUCH, selector.select(DeviceType.PHONE, Orientation.LANDSCAPE));
        assertEquals(NavType.TOUCH, selector.select(DeviceType.PHONE, Orientation.PORTRAIT));
    }
}
