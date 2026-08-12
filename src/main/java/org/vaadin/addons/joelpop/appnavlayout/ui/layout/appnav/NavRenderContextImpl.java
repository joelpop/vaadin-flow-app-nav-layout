package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper;

import java.util.function.BiPredicate;

record NavRenderContextImpl(
        NavGrouper navGrouper, String currentPath, NavSlots slots, BiPredicate<String, String> navPathMatcher)
        implements NavRenderContext {
}
