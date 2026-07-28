package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import com.vaadin.flow.component.ComponentEvent;
import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType;

/**
 * Fired whenever {@link AppNavLayout} determines and applies a {@link NavType} — including on
 * first attachment, not just on later changes.
 */
public class NavTypeChangedEvent extends ComponentEvent<AppNavLayout> {

    private final NavType navType;
    private final NavType previousNavType;

    public NavTypeChangedEvent(AppNavLayout source, NavType navType, NavType previousNavType) {
        super(source, false);
        this.navType = navType;
        this.previousNavType = previousNavType;
    }

    /** The newly applied nav type. */
    public NavType getNavType() {
        return navType;
    }

    /** The nav type previously in effect, or {@code null} on first attachment. */
    public NavType getPreviousNavType() {
        return previousNavType;
    }

    /** Returns {@code true} if this is the first nav type ever applied to this layout. */
    public boolean isInitialApplication() {
        return previousNavType == null;
    }
}
