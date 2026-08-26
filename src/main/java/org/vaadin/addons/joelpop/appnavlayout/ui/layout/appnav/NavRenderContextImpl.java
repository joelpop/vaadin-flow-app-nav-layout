package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavGrouper;
import com.vaadin.flow.server.menu.MenuEntry;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

record NavRenderContextImpl(
        NavGrouper navGrouper, String currentPath, NavSlots slots, BiPredicate<String, String> navPathMatcher,
        Predicate<MenuEntry> navItemFilter)
        implements NavRenderContext {
}
