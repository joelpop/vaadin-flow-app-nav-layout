package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

import com.vaadin.flow.router.Location;
import com.vaadin.flow.server.menu.MenuEntry;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stateless helpers for navigating the route hierarchy.
 * {@code @Menu} titles are leaf-only display names; all structural logic
 * (depth, grouping, prefix matching) uses the {@code @Route} template path.
 */
public final class RouteNavUtils {

    private RouteNavUtils() {}

    /**
     * Returns the path segments of a route path as an immutable list.
     * An empty path (e.g. the root {@code ""}) returns an empty list.
     * Strips a leading {@code /} so both {@code "catalog/products"} and
     * {@code "/catalog/products"} return {@code ["catalog", "products"]}.
     */
    public static List<String> pathSegments(String routePath) {
        var p = routePath != null && routePath.startsWith("/") ? routePath.substring(1) : routePath;
        if (p == null || p.isEmpty()) {
            // Location("").getSegments() returns a single-element list containing an empty
            // string, not an empty list — normalize that here so callers checking isEmpty()/
            // size() get the documented, intuitive result for the root path.
            return List.of();
        }
        return new Location(p).getSegments();
    }

    /**
     * Returns the path from a {@link MenuEntry} with any leading {@code /}
     * stripped, matching the format returned by
     * {@link com.vaadin.flow.router.Location#getPath()}.
     * <p>{@code MenuEntry.path()} returns {@code "/catalog/products"};
     * this method returns {@code "catalog/products"}.
     */
    public static String normalizedPath(MenuEntry entry) {
        var p = entry.path();
        return p != null && p.startsWith("/") ? p.substring(1) : p;
    }

    /**
     * Converts a URL route segment to a human-readable label by capitalising
     * each hyphen-delimited word.
     * <p>{@code "catalog"} → {@code "Catalog"},
     * {@code "audit-log"} → {@code "Audit Log"}.
     */
    public static String routeSegmentLabel(String segment) {
        return Arrays.stream(segment.split("-"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    /**
     * Returns {@link MenuEntry#title()} as the leaf display name — already resolved by
     * {@code MenuConfiguration.getMenuEntries()} itself (falling back through
     * {@code @PageTitle} to the class name), so this doesn't add a fallback of its own.
     */
    public static String leafTitle(MenuEntry entry) {
        return entry.title();
    }

}
