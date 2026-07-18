package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.server.menu.MenuEntry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Default {@link NavGrouper} — derives the navigation hierarchy from route path
 * segments, mirroring the structure of {@code @Route} paths.
 *
 * <p>Group metadata comes from a {@link NavGroup} resolver when set, falling back to
 * humanised path segment labels. Leaf titles come from {@link RouteNavUtils#leafTitle(MenuEntry)}.
 * Leaf icons come from the configured {@code viewIconGenerator} (default: none).
 * Path-based group nodes carry no icon; use a {@link NavGroup} resolver with
 * {@link NavGroup#icon()} to assign icons to groups.
 *
 * <p>When a {@link NavGroup} resolver assigns a root group to any view, all views
 * sharing the same first path segment are merged into that group automatically,
 * even if they carry no annotation — provided all NavGroup-assigned views are
 * processed by {@link #nodeFor} before any path-based views are processed.
 *
 * <p>Group nodes are cached so the same {@link NavNode} instance is returned for
 * the same group identity across multiple {@link #nodeFor} calls.
 */
public final class PathPrefixNavGrouper implements NavGrouper {

    private Function<MenuEntry, NavGroup>       navGroupDefResolver = e -> null;
    private Function<MenuEntry, Supplier<Icon>> viewIconGenerator   = e -> null;

    private final Map<String, NavNode> cache            = new LinkedHashMap<>();
    private final Map<NavGroup, NavNode> defCache        = new LinkedHashMap<>();
    private final Map<String, NavNode>   firstSegToDefRoot = new LinkedHashMap<>();

    /** Sets the resolver that maps a {@link MenuEntry} to its {@link NavGroup}; return {@code null} to use path-based grouping. */
    public PathPrefixNavGrouper setNavGroupDefResolver(Function<MenuEntry, NavGroup> resolver) {
        this.navGroupDefResolver = resolver;
        return this;
    }

    /** Sets the icon generator for leaf nav nodes; default returns no icon. */
    public PathPrefixNavGrouper setViewIconGenerator(Function<MenuEntry, Supplier<Icon>> generator) {
        this.viewIconGenerator = generator;
        return this;
    }

    @Override
    public void reset() {
        cache.clear();
        defCache.clear();
        firstSegToDefRoot.clear();
    }

    @Override
    public NavNode nodeFor(MenuEntry entry) {
        var def = navGroupDefResolver.apply(entry);
        if (def != null) {
            var segs = RouteNavUtils.pathSegments(entry.path());
            if (!segs.isEmpty()) {
                var rootDef = def;
                while (rootDef.parent() != null) {
                    rootDef = rootDef.parent();
                }
                firstSegToDefRoot.putIfAbsent(segs.getFirst(), defGroupNode(rootDef));
            }
            return NavNode.of(entry, defGroupNode(def));
        }

        var segs = RouteNavUtils.pathSegments(entry.path());

        if (segs.size() <= 1) {
            return NavNode.of(entry, viewIconGenerator.apply(entry));
        }

        for (int i = 0; i < segs.size() - 1; i++) {
            int idx = i;
            var partialPath = String.join("/", segs.subList(0, i + 1));
            var parentPath = i > 0 ? String.join("/", segs.subList(0, i)) : null;
            cache.computeIfAbsent(partialPath, _ -> {
                if (idx == 0) {
                    var defRoot = firstSegToDefRoot.get(segs.getFirst());
                    if (defRoot != null) {
                        return defRoot;
                    }
                }
                var label = RouteNavUtils.routeSegmentLabel(segs.get(idx));
                Supplier<Icon> iconSupplier = null;
                var parent = parentPath != null ? cache.get(parentPath) : null;
                return parent != null
                        ? NavNode.of(label, iconSupplier, parent)
                        : NavNode.of(label, iconSupplier);
            });
        }

        var deepestParentPath = String.join("/", segs.subList(0, segs.size() - 1));
        return NavNode.of(entry, cache.get(deepestParentPath));
    }

    private NavNode defGroupNode(NavGroup def) {
        return defCache.computeIfAbsent(def, d -> {
            var parent = d.parent() != null ? defGroupNode(d.parent()) : null;
            return parent != null
                    ? NavNode.of(d.title(), d.icon(), parent)
                    : NavNode.of(d.title(), d.icon());
        });
    }

}
