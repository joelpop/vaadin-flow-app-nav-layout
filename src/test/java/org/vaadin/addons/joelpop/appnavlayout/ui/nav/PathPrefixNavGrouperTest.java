package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.server.menu.MenuEntry;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathPrefixNavGrouperTest {

    private record Group(String title, Supplier<Icon> icon, NavGroup parent) implements NavGroup {}

    private static MenuEntry entry(String path) {
        return new MenuEntry(path, null, null, null, Div.class);
    }

    @Test
    void topLevelEntryHasNoParent() {
        var grouper = new PathPrefixNavGrouper();
        var node = grouper.nodeFor(entry("home"));
        assertTrue(node.parent().isEmpty());
    }

    @Test
    void entriesSharingFirstSegmentShareTheSameParentGroupNode() {
        var grouper = new PathPrefixNavGrouper();
        var products = grouper.nodeFor(entry("catalog/products"));
        var categories = grouper.nodeFor(entry("catalog/categories"));

        assertSame(products.parent().orElseThrow(), categories.parent().orElseThrow(),
                "siblings under the same path prefix must share the same group NavNode instance");
        assertEquals("Catalog", products.parent().orElseThrow().title());
    }

    @Test
    void deeperEntriesNestUnderIntermediateGroups() {
        var grouper = new PathPrefixNavGrouper();
        var node = grouper.nodeFor(entry("catalog/detail/view"));

        var parent = node.parent().orElseThrow();
        assertEquals("Detail", parent.title());
        var grandparent = parent.parent().orElseThrow();
        assertEquals("Catalog", grandparent.title());
        assertTrue(grandparent.parent().isEmpty());
    }

    @Test
    void nodeForIsIdempotentForTheSameEntry() {
        var grouper = new PathPrefixNavGrouper();
        var e = entry("catalog/products");
        assertEquals(grouper.nodeFor(e), grouper.nodeFor(e));
    }

    @Test
    void navGroupResolverAssignsExplicitGroupHierarchy() {
        var root = new Group("Admin", null, null);
        var child = new Group("Users", null, root);
        var grouper = new PathPrefixNavGrouper().setNavGroupDefResolver(e -> child);

        var node = grouper.nodeFor(entry("admin/users/list"));

        var parent = node.parent().orElseThrow();
        assertEquals("Users", parent.title());
        assertEquals("Admin", parent.parent().orElseThrow().title());
    }

    @Test
    void pathSiblingsOfAnExplicitlyGroupedEntryMergeIntoTheSameGroup() {
        // Documented cross-behavior: a NavGroup-annotated view's path siblings merge into
        // the same group automatically, provided the annotated entry is processed first.
        var adminGroup = new Group("Admin", null, null);
        var grouper = new PathPrefixNavGrouper().setNavGroupDefResolver(
                e -> "admin/users".equals(RouteNavUtils.normalizedPath(e)) ? adminGroup : null);

        var explicit = grouper.nodeFor(entry("admin/users"));
        var pathOnly = grouper.nodeFor(entry("admin/settings"));

        assertSame(explicit.parent().orElseThrow(), pathOnly.parent().orElseThrow(),
                "a path-based sibling of an explicitly-grouped view must merge into the same group");
    }

    @Test
    void resetClearsCachedGroupNodes() {
        var grouper = new PathPrefixNavGrouper();
        var before = grouper.nodeFor(entry("catalog/products")).parent().orElseThrow();
        grouper.reset();
        var after = grouper.nodeFor(entry("catalog/products")).parent().orElseThrow();

        assertEquals(before.title(), after.title());
        assertNotSame(before, after, "reset() must clear cached group nodes, not just their content");
    }
}
