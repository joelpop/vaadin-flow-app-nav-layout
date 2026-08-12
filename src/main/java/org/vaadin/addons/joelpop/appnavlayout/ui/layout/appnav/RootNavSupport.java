package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.RouteNavUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Shared root-node resolution for {@link NavRenderer}s that collapse every entry to one item per
 * root section, regardless of how deep its actual route nests — used by
 * {@link ScrollingTouchNavRenderer} and {@link ExpandingTouchNavRenderer}. Not used by
 * {@link AbstractTouchNavRenderer}'s own renderers, whose active-highlighting has an extra
 * "More" overflow-trigger case these don't need.
 */
final class RootNavSupport {

    private RootNavSupport() {}

    /** Walks {@code node}'s parent chain up to its top-level root group or leaf. */
    static NavNode rootOf(NavNode node) {
        var current = node;
        while (current.parent().isPresent()) {
            current = current.parent().get();
        }
        return current;
    }

    /** One {@link NavNode} per distinct root section, in {@code MenuConfiguration.getMenuEntries()}'s own order. */
    static List<NavNode> collectRootNodes(NavRenderContext context) {
        var seen = new LinkedHashMap<NavNode, MenuEntry>();
        for (var entry : MenuConfiguration.getMenuEntries()) {
            var node = context.navGrouper().nodeFor(entry);
            seen.putIfAbsent(rootOf(node), entry);
        }
        return new ArrayList<>(seen.keySet());
    }

    /** The view class of the first leaf found under {@code groupNode} — for a group item (no
     *  {@code menuEntry()} of its own) that still needs somewhere to navigate on click. */
    static Class<? extends Component> firstChildOf(NavNode groupNode, NavRenderContext context) {
        return MenuConfiguration.getMenuEntries().stream()
                .filter(e -> rootOf(context.navGrouper().nodeFor(e)) == groupNode)
                .map(MenuEntry::menuClass)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * The root section whose most path-specific entry matches the current path, via the
     * configured {@link NavRenderContext#navPathMatcher()} — the same resolution
     * {@link AbstractTouchNavRenderer#highlightActive} does for its own renderers, so a custom
     * {@code setNavPathMatcher} affects every renderer's active-item highlighting the same way,
     * not just the built-in ones. {@code null} if nothing matches.
     */
    static NavNode activeRootFor(NavRenderContext context) {
        var path = context.currentPath();
        return MenuConfiguration.getMenuEntries().stream()
                .filter(e -> context.navPathMatcher().test(path, RouteNavUtils.normalizedPath(e)))
                .max(Comparator.comparingInt(e -> RouteNavUtils.pathSegments(RouteNavUtils.normalizedPath(e)).size()))
                .map(e -> rootOf(context.navGrouper().nodeFor(e)))
                .orElse(null);
    }
}
