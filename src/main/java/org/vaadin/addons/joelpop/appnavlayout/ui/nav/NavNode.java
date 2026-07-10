package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.server.menu.MenuEntry;

import java.util.Objects;
import java.util.Optional;

/**
 * A node in the navigation tree — either a group (non-navigable section) or a
 * leaf (navigable view). The presence of {@link #menuEntry()} distinguishes leaves
 * from groups.
 *
 * <p>Group nodes carry an explicit title and optional icon. Leaf nodes derive their
 * title from {@link RouteNavUtils#leafTitle(MenuEntry)} and their icon from
 * {@code @Menu(icon="collection:name")} if present. External icon generators
 * take priority over the menu-derived icon.
 */
public final class NavNode {

    private final String title;
    private final Icon icon;
    private final NavNode parent;
    private final MenuEntry menuEntry;

    private NavNode(String title, Icon icon, NavNode parent, MenuEntry menuEntry) {
        this.title = title;
        this.icon = icon;
        this.parent = parent;
        this.menuEntry = menuEntry;
    }

    /** Group node with no parent (top-level section). */
    public static NavNode of(String title, Icon icon) {
        return new NavNode(title, icon, null, null);
    }

    /** Group node nested under {@code parent}. */
    public static NavNode of(String title, Icon icon, NavNode parent) {
        return new NavNode(title, icon, parent, null);
    }

    /** Leaf node with no parent (top-level view). */
    public static NavNode of(MenuEntry entry) {
        return new NavNode(RouteNavUtils.leafTitle(entry), menuIcon(entry), null, entry);
    }

    /** Leaf node with no parent; uses {@code iconOverride} if present, else derives icon from {@code @Menu}. */
    public static NavNode of(MenuEntry entry, Optional<Icon> iconOverride) {
        return new NavNode(RouteNavUtils.leafTitle(entry), iconOverride.orElseGet(() -> menuIcon(entry)), null, entry);
    }

    /** Leaf node nested under {@code parent}. */
    public static NavNode of(MenuEntry entry, NavNode parent) {
        return new NavNode(RouteNavUtils.leafTitle(entry), menuIcon(entry), parent, entry);
    }

    private static Icon menuIcon(MenuEntry entry) {
        var s = entry.icon();
        if (s == null || s.isEmpty()) return null;
        var parts = s.split(":", 2);
        return parts.length == 2 ? new Icon(parts[0], parts[1]) : null;
    }

    /** Display title for this node. */
    public String title() {
        return title;
    }

    /**
     * Display icon for this node.
     * Group nodes: the icon supplied at construction time.
     * Leaf nodes: derived from {@code @Menu(icon="collection:name")} if present; empty otherwise.
     * External icon generators take priority over this value.
     */
    public Optional<Icon> icon() {
        return Optional.ofNullable(icon);
    }

    /** Parent node, or empty for top-level nodes. */
    public Optional<NavNode> parent() {
        return Optional.ofNullable(parent);
    }

    /** The menu entry for leaf nodes; empty for group nodes. */
    public Optional<MenuEntry> menuEntry() {
        return Optional.ofNullable(menuEntry);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NavNode other)) return false;
        return Objects.equals(title, other.title)
                && Objects.equals(parent, other.parent)
                && Objects.equals(menuEntry, other.menuEntry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, parent, menuEntry);
    }
}
