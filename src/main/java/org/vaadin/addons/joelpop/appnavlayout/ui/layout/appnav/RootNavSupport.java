package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.RouteNavUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Shared nav-tree utilities for {@link NavRenderer}s in this package. {@link #menuEntries}
 * (both overloads) is the single place {@code MenuConfiguration.getMenuEntries()} gets filtered
 * against {@link NavRenderContext#navItemFilter()} — every other method here, and every renderer
 * that builds a nav tree, reads entries through it rather than calling
 * {@code MenuConfiguration.getMenuEntries()} directly, so an excluded entry never reaches a
 * {@code NavGrouper}'s own {@code nodeFor}. {@link #rootOf}, {@link #collectRootNodes}, and
 * {@link #activeRootFor} collapse every entry to one item per root section, regardless of how
 * deep its actual route nests — used by {@link ScrollingTouchNavRenderer},
 * {@link ExpandingTouchNavRenderer}, and {@link HeaderTabsNavRenderer}. Not used by
 * {@link AbstractTouchNavRenderer}'s own renderers, whose active-highlighting has an extra
 * "More" overflow-trigger case these don't need — those instead call the {@code Predicate}
 * overload of {@link #menuEntries} directly. {@link #childrenOf} and {@link #activeChainFor}
 * don't collapse to root — they're for renderers that show the tree at every depth, such as
 * {@link FlyoutRailNavRenderer} and {@link SecondaryTabBar}. {@link #firstChildOf} works at any
 * depth, not just root — a group node's first navigable descendant, wherever in the tree that
 * group actually sits.
 */
final class RootNavSupport {

    private RootNavSupport() {}

    /** {@code MenuConfiguration.getMenuEntries()}, filtered against {@code context}'s own
     *  {@link NavRenderContext#navItemFilter()}. */
    static List<MenuEntry> menuEntries(NavRenderContext context) {
        return menuEntries(context.navItemFilter());
    }

    /** Same as {@link #menuEntries(NavRenderContext)}, for a caller (currently only
     *  {@link AbstractTouchNavRenderer}) that caches the filter itself rather than holding onto
     *  a whole {@link NavRenderContext}. */
    static List<MenuEntry> menuEntries(Predicate<MenuEntry> filter) {
        return MenuConfiguration.getMenuEntries().stream().filter(filter).toList();
    }

    /** Walks {@code node}'s parent chain up to its top-level root group or leaf. */
    static NavNode rootOf(NavNode node) {
        var current = node;
        while (current.parent().isPresent()) {
            current = current.parent().get();
        }
        return current;
    }

    /** One {@link NavNode} per distinct root section, in {@link #menuEntries}'s own order. */
    static List<NavNode> collectRootNodes(NavRenderContext context) {
        var seen = new LinkedHashMap<NavNode, MenuEntry>();
        for (var entry : menuEntries(context)) {
            var node = context.navGrouper().nodeFor(entry);
            seen.putIfAbsent(rootOf(node), entry);
        }
        return new ArrayList<>(seen.keySet());
    }

    /** The first {@link MenuEntry} found under {@code groupNode} whose route has a real view
     *  class — {@link #firstChildOf}'s own helper, kept separate since it's still useful on its
     *  own wherever more than just the view class is needed. Matches any descendant, not just a
     *  direct child — {@code groupNode} itself might have no directly routable child of its own,
     *  only grandchildren or deeper. */
    private static Optional<MenuEntry> firstChildEntryOf(NavNode groupNode, NavRenderContext context) {
        return menuEntries(context).stream()
                .filter(e -> hasAncestor(context.navGrouper().nodeFor(e), groupNode))
                .filter(e -> e.menuClass() != null)
                .findFirst();
    }

    /** True if {@code ancestor} is {@code node} itself or somewhere in its parent chain. */
    private static boolean hasAncestor(NavNode node, NavNode ancestor) {
        for (var current = node; current != null; current = current.parent().orElse(null)) {
            if (current.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    /** The view class of the first leaf found under {@code groupNode} — for a group item (no
     *  {@code menuEntry()} of its own) that still needs somewhere to navigate on click, whether
     *  {@code groupNode} is a root section ({@link HeaderTabsNavRenderer}, and the touch/rail
     *  renderers' own root buttons) or a nested group revealed mid-drill-down
     *  ({@link SecondaryTabBar}). */
    static Class<? extends Component> firstChildOf(NavNode groupNode, NavRenderContext context) {
        return firstChildEntryOf(groupNode, context).map(MenuEntry::menuClass).orElse(null);
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
        return menuEntries(context).stream()
                .filter(e -> context.navPathMatcher().test(path, RouteNavUtils.normalizedPath(e)))
                .max(Comparator.comparingInt(e -> RouteNavUtils.pathSegments(RouteNavUtils.normalizedPath(e)).size()))
                .map(e -> rootOf(context.navGrouper().nodeFor(e)))
                .orElse(null);
    }

    /**
     * The parent→direct-children index for the current nav tree, to any depth. Built the same
     * way {@link SideNavDrawerNavRenderer#ensureAncestors} walks ancestors when materializing the
     * desktop drawer's nested {@code SideNav} — for every entry, its resolved node and every
     * not-yet-seen ancestor above it are registered into their own parent's list, bottom-up.
     */
    static Map<NavNode, List<NavNode>> childrenOf(NavRenderContext context) {
        var children = new LinkedHashMap<NavNode, List<NavNode>>();
        var seen = new HashSet<NavNode>();
        for (var entry : menuEntries(context)) {
            registerWithAncestors(context.navGrouper().nodeFor(entry), children, seen);
        }
        return children;
    }

    private static void registerWithAncestors(NavNode node, Map<NavNode, List<NavNode>> children,
                                                Set<NavNode> seen) {
        if (!seen.add(node)) {
            return;
        }
        node.parent().ifPresent(parent -> {
            children.computeIfAbsent(parent, unused -> new ArrayList<>()).add(node);
            registerWithAncestors(parent, children, seen);
        });
    }

    /**
     * The entire ancestor chain (the most path-specific matching entry's own node, then every
     * ancestor above it up to the root) for whichever entry is currently active, via the same
     * {@link NavRenderContext#navPathMatcher()} resolution {@link #activeRootFor} uses — but
     * returning every node along the way rather than stopping at the root, so a renderer can mark
     * each ancestor group active too, not just the leaf itself. Empty if nothing matches.
     */
    static Set<NavNode> activeChainFor(NavRenderContext context) {
        var path = context.currentPath();
        var chain = new LinkedHashSet<NavNode>();
        menuEntries(context).stream()
                .filter(e -> context.navPathMatcher().test(path, RouteNavUtils.normalizedPath(e)))
                .max(Comparator.comparingInt(e -> RouteNavUtils.pathSegments(RouteNavUtils.normalizedPath(e)).size()))
                .ifPresent(e -> {
                    var node = context.navGrouper().nodeFor(e);
                    while (node != null) {
                        chain.add(node);
                        node = node.parent().orElse(null);
                    }
                });
        return chain;
    }
}
