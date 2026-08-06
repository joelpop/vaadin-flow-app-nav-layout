package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.server.menu.MenuEntry;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A node in the navigation tree — either a group (non-navigable section) or a
 * leaf (navigable view). The presence of {@link #menuEntry()} distinguishes leaves
 * from groups.
 *
 * <p>Group nodes carry an explicit title and optional icon supplier. Leaf nodes derive
 * their title from {@link RouteNavUtils#leafTitle(MenuEntry)} and their icon from
 * {@code @Menu(icon="collection:name")} if present. External icon generators
 * take priority over the menu-derived icon.
 */
public final class NavNode {

    private final String title;
    private final Supplier<Icon> iconSupplier;
    private final NavNode parent;
    private final MenuEntry menuEntry;

    private NavNode(String title, Supplier<Icon> iconSupplier, NavNode parent, MenuEntry menuEntry) {
        this.title = title;
        this.iconSupplier = iconSupplier != null ? iconSupplier : () -> null;
        this.parent = parent;
        this.menuEntry = menuEntry;
    }

    /** Group node with no parent (top-level section). */
    public static NavNode of(String title, Supplier<Icon> iconSupplier) {
        return new NavNode(title, iconSupplier, null, null);
    }

    /** Group node nested under {@code parent}. */
    public static NavNode of(String title, Supplier<Icon> iconSupplier, NavNode parent) {
        return new NavNode(title, iconSupplier, parent, null);
    }

    /** Leaf node with no parent (top-level view). */
    public static NavNode of(MenuEntry entry) {
        return new NavNode(RouteNavUtils.leafTitle(entry), menuIcon(entry), null, entry);
    }

    /** Leaf node with no parent; uses {@code iconOverride} if non-null, else derives icon from {@code @Menu}. */
    public static NavNode of(MenuEntry entry, Supplier<Icon> iconOverride) {
        return new NavNode(RouteNavUtils.leafTitle(entry), iconOverride != null ? iconOverride : menuIcon(entry), null, entry);
    }

    /** Leaf node nested under {@code parent}. */
    public static NavNode of(MenuEntry entry, NavNode parent) {
        return new NavNode(RouteNavUtils.leafTitle(entry), menuIcon(entry), parent, entry);
    }

    /** Leaf node nested under {@code parent}; uses {@code iconOverride} if non-null, else derives icon from {@code @Menu}. */
    public static NavNode of(MenuEntry entry, Supplier<Icon> iconOverride, NavNode parent) {
        return new NavNode(RouteNavUtils.leafTitle(entry), iconOverride != null ? iconOverride : menuIcon(entry), parent, entry);
    }

    /**
     * Turns {@code @Menu(icon="...")} into an icon supplier, or {@code null} if
     * {@code entry.icon()} is null/empty. {@link Icon}'s own single-{@code String}
     * constructor already parses the {@code "collection:name"} format (and falls back to
     * the {@code vaadin} collection if there's no {@code ":"}), so this doesn't reparse it.
     */
    private static Supplier<Icon> menuIcon(MenuEntry entry) {
        var s = entry.icon();
        return (s == null || s.isEmpty()) ? null : () -> new Icon(s);
    }

    /** Display title for this node. */
    public String title() {
        return title;
    }

    /**
     * Creates and returns a fresh display icon for this node, or empty if none is configured.
     * Each call creates a new {@link Icon} instance — do not cache the result.
     */
    public Optional<Icon> createIcon() {
        return Optional.ofNullable(iconSupplier.get());
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
