package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper;

record NavRenderContextImpl(NavGrouper navGrouper, String currentPath, NavSlots slots) implements NavRenderContext {
}
