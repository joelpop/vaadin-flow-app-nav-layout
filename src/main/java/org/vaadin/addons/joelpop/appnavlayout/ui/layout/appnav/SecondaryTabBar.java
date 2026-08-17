package org.vaadin.addons.joelpop.appnavlayout.ui.layout.appnav;

import org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavNode;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.TabsVariant;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.shared.Registration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The recursive drill-down bar built into {@link NavSlots#headerNav()}: shows one {@link Tab}
 * per direct child of whichever {@link NavNode} group is currently in view — on a real
 * navigation, the active leaf's own immediate parent group; while exploring, whichever group was
 * last selected without navigating — with a {@code [← Back]} button that climbs exactly one
 * level at a time, all the way up to a root's own direct children, however deep the actual nav
 * tree goes. Any {@link NavRenderer} for {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#TOUCH},
 * {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#RAIL}, or
 * {@link org.vaadin.addons.joelpop.appnavlayout.ui.nav.NavType#HEADER} that wants this behavior
 * holds one instance and calls {@link #render} from within its own {@code render}.
 *
 * <p>Builds and owns exactly one component tree — the same shape {@link NavItem} already has
 * ({@code extends Composite<Button>}) — so this is a real {@link Component} too, via
 * {@link Composite}, rather than a plain object that hands its tree to whoever calls
 * {@link #render}. Being an actual component is what lets {@link #addBlurListener} use Vaadin's
 * own {@code ComponentEvent}/{@code fireEvent} machinery directly, the same idiom
 * {@code AppNavLayout.addNavTypeChangedListener} already uses elsewhere in this add-on.
 */
@CssImport("./secondary-tab-bar.css")
class SecondaryTabBar extends Composite<HorizontalLayout> {

    private final boolean autoselect;
    private final boolean centered;

    private NavRenderContext context;
    private Map<NavNode, List<NavNode>> childrenIndex = Map.of();
    private Set<NavNode> activeChain = Set.of();

    // Slot component this bar last attached into — used to detect a NavStrategy tear-down/rebuild
    // (fresh slot instances), since this bar outlives any single NavStrategy build/tearDown cycle.
    private HasComponents attachedSlot;

    private final HorizontalLayout headerNavBar;
    private final Button backButton;
    private final Tabs tabs;
    private final Map<Tab, NavNode> tabTargets = new HashMap<>();

    // The group whose direct children are currently shown as tabs; null hides this bar entirely.
    private NavNode currentGroup;
    // true: currentGroup was reached by exploring (a preview — nothing is ever marked active).
    // false: currentGroup is the real active leaf's own ancestor chain (restore()-computed).
    private boolean exploring;

    /**
     * @param autoselect {@code true} (Vaadin's own {@code Tabs.setAutoselect} vocabulary, read
     *                    the same direction): selecting a tab acts on it immediately, including a
     *                    group tab — which navigates to its own first navigable descendant.
     *                    {@code false}: selecting a group tab only previews its own children
     *                    locally ({@link #explore}, never {@code UI.navigate()}) — used by
     *                    {@link HeaderTabsNavRenderer}, where a group tab should only expand,
     *                    never auto-navigate.
     * @param centered centers the tabs within the row's own remaining space, after the Back
     *                  button — left {@code false} only for a row narrow enough that its tabs
     *                  already read as filling it (e.g. {@link SideRailNavRenderer}'s own
     *                  rail-adjacent header row). Independent of {@code autoselect} — neither
     *                  implies the other; each caller sets both according to its own needs.
     */
    SecondaryTabBar(boolean autoselect, boolean centered) {
        this.autoselect = autoselect;
        this.centered = centered;

        // Rarely a good reason to override Composite's own initContent() — HorizontalLayout has
        // a no-arg constructor, so the default (reflection-based) initContent() already builds
        // it correctly; getContent() in the constructor is all that's needed.
        headerNavBar = getContent();
        headerNavBar.setWidthFull();
        headerNavBar.setAlignItems(FlexComponent.Alignment.CENTER);
        headerNavBar.setVisible(false);
        headerNavBar.setPadding(false);
        headerNavBar.setSpacing(false);
        headerNavBar.addClassName("secondary-tab-bar");

        // Transparent background/no border, and symmetric (rather than the button's own default
        // horizontal-only) padding, come from secondary-tab-bar-back's own CSS (secondary-tab-
        // bar.css), rendering correctly under any theme, or none at all.
        backButton = new Button(VaadinIcon.ARROW_LEFT.create());
        backButton.addClassName("secondary-tab-bar-back");
        backButton.setVisible(false);
        backButton.addClickListener(unused -> handleBack());

        tabs = new Tabs();
        tabs.addThemeVariants(TabsVariant.SMALL);
        if (!autoselect) {
            // Vaadin's own Tabs auto-selects the first tab added to an empty instance by
            // default — rebuildTabs' own removeAll()+add() loop below would otherwise trigger
            // that regardless of the active tab it computes, since it happens independently of
            // (and before) the explicit setSelectedTab call that follows it.
            tabs.setAutoselect(false);
        }
        if (centered) {
            // Centers the tabs within the row's own remaining space, after the Back button, via
            // secondary-tab-bar-tabs's own CSS (secondary-tab-bar.css) — the Back button stays
            // anchored at the row's own start rather than being pulled into the centered group.
            tabs.addClassName("secondary-tab-bar-tabs");
        }
        tabs.addSelectedChangeListener(event -> {
            if (event.isFromClient()) {
                var target = tabTargets.get(event.getSelectedTab());
                if (target != null) {
                    if (!autoselect && childrenIndex.containsKey(target)) {
                        showGroup(target, true);
                        // Rebuilding this bar's own tabs (above) just removed the very tab the
                        // user clicked to get here, which native-blurs it to nothing — the
                        // debounced "focusout" listener below reads that as focus having left
                        // this bar entirely unless something within it reclaims focus first.
                        refocusAfterOwnRebuild();
                    }
                    else {
                        var leafClass = target.menuEntry()
                                .<Class<? extends Component>>map(MenuEntry::menuClass)
                                .orElseGet(() -> RootNavSupport.firstChildOf(target, context));
                        UI.getCurrent().navigate(leafClass);
                    }
                }
            }
        });

        headerNavBar.add(backButton, tabs);

        if (!autoselect) {
            // Removing a focused tab from the DOM (rebuildTabs' own removeAll(), on every
            // explore/back step) blurs it immediately, with relatedTarget always null —
            // indistinguishable, at this instant, from focus genuinely leaving this bar for
            // good. Waiting a frame lets a same-step refocus (see #refocusAfterOwnRebuild) land
            // first, so only a focusout still unresolved a frame later is treated as the real
            // thing, and reads the actual landing spot fresh off document.activeElement rather
            // than the original event's own (by-then-stale) relatedTarget.
            getElement().executeJs("""
                    const bar = this;
                    bar.addEventListener('focusout', () => {
                        requestAnimationFrame(() => {
                            if (!bar.contains(document.activeElement)) {
                                bar.dispatchEvent(new CustomEvent('nav-blur'));
                            }
                        });
                    });
                    """);
            getElement().addEventListener("nav-blur", event -> fireEvent(new BlurEvent(this,
                    event.getEventDataElement("document.activeElement").orElse(null))))
                    .addEventDataElement("document.activeElement");
        }
    }

    void render(NavRenderContext context) {
        this.context = context;
        var headerNav = context.slots().headerNav();
        if (attachedSlot != headerNav) {
            headerNav.add(this);
            attachedSlot = headerNav;
        }
        restore();
    }

    /** Restores this bar to reflect the actually-active route, discarding whatever was being
     *  explored via {@link #explore} or a local drill-down. The level shown is the active
     *  leaf's own immediate parent group — its direct siblings — regardless of how deep that
     *  is; {@link #handleBack} climbs one level at a time from there. */
    void restore() {
        childrenIndex = RootNavSupport.childrenOf(context);
        activeChain = RootNavSupport.activeChainFor(context);
        var activeRoot = RootNavSupport.activeRootFor(context);
        if (activeRoot == null) {
            hide();
            return;
        }
        // activeChain is ordered leaf-first, so the first entry that owns children (a leaf
        // never does — see PathPrefixNavGrouper's own class comment) is the active leaf's own
        // immediate parent; a root-level leaf with no group of its own falls through to
        // activeRoot itself, which showGroup then hides for (having no children either).
        var group = activeChain.stream()
                .filter(childrenIndex::containsKey)
                .findFirst()
                .orElse(activeRoot);
        showGroup(group, false);
    }

    /**
     * Locally previews {@code node}'s own direct children, as if navigated to, but never marks
     * any of them active — nothing has actually been chosen yet, only revealed. Used whenever a
     * group tab is selected elsewhere (a root tab in {@link HeaderTabsNavRenderer}'s own strip,
     * or one of this bar's own group tabs) without committing to a leaf.
     */
    void explore(NavNode node) {
        showGroup(node, true);
    }

    /**
     * Shows {@code group}'s own direct children as tabs. {@code exploring} decides whether any
     * of them get marked active: {@code false} consults {@link #activeChain} (the real current
     * route), {@code true} never marks anything (a preview). Hides this bar entirely if
     * {@code group} turns out to have no children of its own — either a plain leaf with no
     * group at all, or (defensively) a group {@link NavNode} nothing was ever registered under.
     */
    private void showGroup(NavNode group, boolean exploring) {
        var children = childrenIndex.get(group);
        if (children == null || children.isEmpty()) {
            hide();
            return;
        }
        currentGroup = group;
        this.exploring = exploring;
        backButton.setVisible(group.parent().isPresent());
        rebuildTabs(children, !exploring);
        headerNavBar.setVisible(true);
    }

    private void hide() {
        currentGroup = null;
        exploring = false;
        headerNavBar.setVisible(false);
    }

    private void handleBack() {
        // backButton is only visible when currentGroup has a parent (see showGroup) — reachable
        // only in that state, so currentGroup.parent() is guaranteed present here. Preserves
        // exploring as-is: going back changes which level is shown, not whether what's shown is
        // a real, committed location or still just a preview.
        showGroup(currentGroup.parent().orElseThrow(), exploring);
    }

    private void rebuildTabs(List<NavNode> children, boolean markActive) {
        tabs.removeAll();
        tabTargets.clear();
        Tab activeTab = null;
        for (var child : children) {
            var tab = new Tab(child.title());
            tabTargets.put(tab, child);
            tabs.add(tab);
            if (markActive && activeChain.contains(child)) {
                activeTab = tab;
            }
        }
        tabs.setSelectedTab(activeTab);
    }

    /** Keeps focus within this bar's own subtree right after a rebuild that has just replaced
     *  the very tab the user clicked to trigger it (see the "focusout" listener registered in
     *  the constructor for why that matters). Vaadin's own Tabs tracks a single focusable tab
     *  via roving tabindex regardless of which (if any) is selected, so focusing the component
     *  itself is enough to land on one.
     *
     *  <p>Clears {@code focus-ring} on whatever tab that lands on right after: Vaadin's own
     *  {@code FocusMixin} sets that attribute — the one its theme actually keys the visible
     *  ring on, not bare {@code :focus} — whenever a keydown has fired anywhere on the page more
     *  recently than the last mousedown, a page-wide signal with no way to tell it this
     *  particular focus call is a same-click follow-up rather than real keyboard navigation.
     *  Clearing it here undoes exactly that misfire without touching a real keyboard-driven
     *  focus into this bar at any other time. */
    private void refocusAfterOwnRebuild() {
        tabs.getElement().executeJs("""
                const bar = this;
                bar.focus();
                requestAnimationFrame(() => bar.querySelector('[focus-ring]')?.removeAttribute('focus-ring'));
                """);
    }

    /** True if {@code candidate} is this bar's own element, or a descendant of it — lets an
     *  owner ask "is this focus target mine?" without ever being handed the element itself. */
    boolean containsFocusTarget(Element candidate) {
        return isDescendantOf(candidate, getElement());
    }

    private static boolean isDescendantOf(Element candidate, Element ancestor) {
        for (var e = candidate; e != null; e = e.getParent()) {
            if (e.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fired when this bar is blurred (Vaadin's own vocabulary for "lost focus") — carrying
     * whatever element focus moved to, or {@code null} if it left the browser context entirely.
     * Only fired when constructed with {@code autoselect=false}.
     */
    static class BlurEvent extends ComponentEvent<SecondaryTabBar> {
        private final Element relatedTarget;

        BlurEvent(SecondaryTabBar source, Element relatedTarget) {
            super(source, true);
            this.relatedTarget = relatedTarget;
        }

        Element getRelatedTarget() {
            return relatedTarget;
        }
    }

    /** Notifies {@code listener} whenever this bar is blurred. This bar has no knowledge of any
     *  other component's own DOM position — deciding what, if anything, counts as a genuine
     *  blur of a wider explorable area is entirely up to the listener. */
    Registration addBlurListener(ComponentEventListener<BlurEvent> listener) {
        return addListener(BlurEvent.class, listener);
    }
}
