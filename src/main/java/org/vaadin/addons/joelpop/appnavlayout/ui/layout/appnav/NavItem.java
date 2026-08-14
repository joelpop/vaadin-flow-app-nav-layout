package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.popover.PopoverVariant;

/**
 * A single themed icon+label nav item — either a leaf (has somewhere to navigate) or a branch
 * (children added via {@link #addItem}, revealed in a flyout popover triggered by a trailing
 * chevron). This is the same leaf-or-branch duality Vaadin's own {@code SideNavItem} exposes for
 * the desktop drawer's inline expand/collapse, just presented as a popup instead. A leaf never
 * pays for the chevron/popover — both are only materialized the first time {@link #addItem} is
 * actually called.
 *
 * <p>A group {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode} has no {@code
 * menuEntry()} of its own — nothing to navigate to — so a branch's whole row exists only to open
 * its flyout; a leaf's whole row exists only to navigate. The two never compete for the same
 * click.
 */
class NavItem extends Composite<Button> {

    // Row wrapping content and, once reserved, a second cell to its side holding the chevron —
    // kept as a sibling of content, not a child of it, so the chevron gets its own separated box
    // instead of just stacking into content's own icon-over-label column.
    private final Div row;
    private final Div content;
    // Lazily created — either by reserveChevronSpace() (a leaf aligning with a sibling branch in
    // the same list) or addItem() (this item becoming an actual branch). Left null for a NavItem
    // whose list has no branches at all, so a renderer that never uses branches (every renderer
    // but FlyoutRailNavRenderer) gets exactly the same plain, unreserved row it always has.
    private Div chevronBox;
    private FlexLayout childrenColumn;
    private Popover flyout;

    /** Leaf: navigates to {@code viewClass} on click. */
    NavItem(String title, Icon icon, Class<? extends Component> viewClass) {
        var titleSpan = new Span(title);
        titleSpan.addClassName("touch-nav-label");

        // See AbstractTouchNavRenderer's own (removed) navItem comment for why a section with no
        // icon still gets an icon-sized *empty* placeholder here rather than omitting it: Button's
        // height is fit-content around this Div's contents, so leaving it out would make this
        // item's whole row shorter than its icon-bearing siblings, shifting its label down.
        var contentDiv = new Div(titleSpan);
        contentDiv.addClassName("touch-nav-content");
        if (icon != null) {
            icon.setSize("20px");
            contentDiv.addComponentAsFirst(icon);
        }
        else {
            var placeholder = new Div();
            placeholder.addClassName("touch-nav-icon-placeholder");
            contentDiv.addComponentAsFirst(placeholder);
        }
        this.content = contentDiv;

        this.row = new Div(contentDiv);
        row.addClassName("flyout-rail-item-row");
        row.setWidthFull();

        getContent().addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        getContent().addClassName("touch-nav-item");
        if (viewClass != null) {
            // Attached to the whole Button, not just content — clicking anywhere in the row
            // (including, once this becomes a branch, the chevron's own box) does the same thing,
            // matching how Popover's own default target-click-to-open behaves for a branch below.
            getContent().addClickListener(unused -> {
                UI.getCurrent().navigate(viewClass);
                closeEnclosingFlyouts();
            });
        }
    }

    /** Branch: a group node has no {@code menuEntry()}/view of its own. */
    NavItem(String title, Icon icon) {
        this(title, icon, null);
    }

    @Override
    protected Button initContent() {
        return new Button(row);
    }

    void setActive(boolean active) {
        getContent().getElement().getClassList().set("active", active);
    }

    /** Escape hatch for call sites that need the underlying {@link Button} itself — e.g. to pass
     *  it somewhere a pre-existing, public-API method signature specifically requires one. */
    Button asButton() {
        return getContent();
    }

    /**
     * Reserves the chevron zone's width without becoming an actual branch — for a leaf sitting
     * alongside a sibling branch in the same list (root rail or one specific flyout), so every
     * item in that list stays aligned in the same column a branch's does. A no-op if this item is
     * already a branch, which already reserves the same space via {@link #addItem}.
     */
    void reserveChevronSpace() {
        ensureChevronBox();
    }

    /**
     * Turns this item into a branch on first call — materializes the trailing chevron and this
     * item's own flyout {@link Popover} the first time, lazily, so a leaf item never pays for
     * either.
     */
    void addItem(NavItem child) {
        if (childrenColumn == null) {
            ensureChevronBox();
            chevronBox.addClassName("flyout-rail-chevron-box-active");
            var chevron = VaadinIcon.CHEVRON_RIGHT.create();
            chevron.addClassName("flyout-rail-chevron");
            chevronBox.add(chevron);
            ensureBranch();
        }
        childrenColumn.add(child);
    }

    private void ensureChevronBox() {
        if (chevronBox == null) {
            chevronBox = new Div();
            chevronBox.addClassName("flyout-rail-chevron-box");
            row.add(chevronBox);
        }
    }

    private void ensureBranch() {
        childrenColumn = new FlexLayout();
        childrenColumn.addClassName("nav-bar");
        childrenColumn.addClassName("flyout-rail-column");
        // Only a flyout's own content needs a height cap — the rail's own root column gets its
        // height from setSizeFull() against its slot instead, and this class deliberately doesn't
        // touch that.
        childrenColumn.addClassName("flyout-rail-popover-column");
        childrenColumn.setFlexDirection(FlexLayout.FlexDirection.COLUMN);

        flyout = new Popover();
        flyout.addThemeVariants(PopoverVariant.ARROW);
        // Position, not trigger: Popover already opens on click of its target by default, which
        // is all the wiring a branch row needs — unlike a leaf, it has no navigate listener of
        // its own to conflict with.
        flyout.setPosition(PopoverPosition.END_TOP);
        flyout.setTarget(getContent());
        flyout.add(childrenColumn);
    }

    /**
     * Closes every enclosing flyout, not just the nearest one, so navigating away from a leaf
     * nested several branches deep collapses the whole cascade rather than leaving ancestor
     * flyouts open behind it.
     */
    private void closeEnclosingFlyouts() {
        var current = (Component) this;
        while (current.getParent().isPresent()) {
            current = current.getParent().get();
            if (current instanceof Popover popover) {
                popover.close();
            }
        }
    }
}
