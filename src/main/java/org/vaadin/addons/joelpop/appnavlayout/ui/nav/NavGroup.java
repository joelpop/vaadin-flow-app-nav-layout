package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

import com.vaadin.flow.component.icon.Icon;

import java.util.function.Supplier;

/**
 * A named grouping node in the navigation hierarchy.
 * Root groups have a null parent.
 *
 * <p>Implement this interface on an annotation or enum to declare explicit group
 * metadata. Pass a resolver via
 * {@link org.vaadin.addons.joelpop.appnavlayout.ui.layout.AppNavLayout#setViewNavGroupResolver}
 * so the framework can map each view to its group.
 */
public interface NavGroup {

    /** Display title for this group node. */
    String title();

    /**
     * Returns a supplier that creates the icon for this group node, or {@code null}
     * for no icon.
     *
     * <p><strong>Important:</strong> {@link Icon} is a live DOM component; each
     * invocation of the returned supplier must produce a <em>new</em> {@code Icon}
     * instance. Returning a supplier that hands back the same cached instance will
     * cause the icon to be silently moved out of its current parent on each nav rebuild.
     */
    Supplier<Icon> icon();

    /**
     * Returns the parent group, or {@code null} if this is a root group.
     */
    NavGroup parent();
}
