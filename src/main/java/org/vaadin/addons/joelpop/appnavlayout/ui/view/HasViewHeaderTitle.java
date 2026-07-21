package org.vaadin.addons.joelpop.appnavlayout.ui.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.theme.lumo.LumoUtility;

/**
 * Implemented by views that want an auto-generated icon+title component in the
 * adaptive view header slot. {@link AppNavLayout} checks for this interface on
 * each navigation and places the result in the {@code viewHeaderSlot} (desktop only).
 *
 * <p>The default implementation calls {@link #getViewHeaderIcon()} and reads {@link PageTitle}
 * from the view class, then appends {@link #getViewHeaderSuffix()} if non-null.
 * Override {@link #getViewHeaderSuffix()} to append a badge or other component
 * after the title.
 */
public interface HasViewHeaderTitle {

    /**
     * Returns a component to append after the title (e.g. a badge or status chip),
     * or {@code null} to omit the suffix slot.
     *
     * <p>Called on every navigation event; return a new instance on each call.
     */
    default Component getViewHeaderSuffix() {
        return null;
    }

    /**
     * Returns an icon for the view header, or {@code null} for no icon.
     *
     * <p><strong>Important:</strong> {@link Icon} is a live DOM component; each
     * call to this method must return a <em>new</em> instance. Returning a cached
     * {@code Icon} will cause it to be silently moved out of its current parent
     * on each navigation event.
     */
    default Icon getViewHeaderIcon() {
        return null;
    }

    /**
     * Returns the component to place in the view header title slot.
     *
     * <p>Called on every navigation event. The default implementation composes an icon (from
     * {@link #getViewHeaderIcon()}), a heading (from {@link PageTitle}), and an optional suffix
     * (from {@link #getViewHeaderSuffix()}) into a horizontal layout. Override to replace the
     * entire title component. Return {@code null} to suppress the slot.
     */
    default Component getViewHeaderTitle() {
        var viewClass = this.getClass();
        var icon      = getViewHeaderIcon();
        var titleAnno = viewClass.isAnnotationPresent(PageTitle.class)
                ? viewClass.getAnnotation(PageTitle.class) : null;
        var suffix    = getViewHeaderSuffix();

        if (icon == null && titleAnno == null && suffix == null) {
            return null;
        }

        var layout = new HorizontalLayout();
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.addClassNames(LumoUtility.Gap.SMALL);

        if (icon != null) {
            icon.addClassName(LumoUtility.IconSize.MEDIUM);
            layout.add(icon);
        }
        if (titleAnno != null) {
            var h2 = new H2(titleAnno.value());
            h2.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.FontWeight.SEMIBOLD,
                    LumoUtility.TextColor.HEADER, LumoUtility.Margin.NONE);
            layout.add(h2);
        }
        if (suffix != null) {
            layout.add(suffix);
        }

        return layout;
    }
}
