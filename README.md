# vaadin-flow-app-nav-layout

A Vaadin Flow base layout providing adaptive navigation: a bottom icon bar with secondary tabs on phones, a permanent left-strip rail on portrait tablets, and a drawer-based `SideNav` on desktop. Extends `AppLayout` and wires all nav components automatically from Vaadin's `@Menu`-annotated routes.

## Table of Contents

- [How it works](#how-it-works)
- [Usage](#usage)
- [Configuration reference](#configuration-reference)
- [Nav grouping](#nav-grouping)
- [NavSelector](#navselector)
- [View header integration](#view-header-integration)
- [Supporting types](#supporting-types)
- [Development](#development)
  - [Running the demo](#running-the-demo)
  - [Integration tests](#integration-tests)
- [Publishing to Vaadin Directory](#publishing-to-vaadin-directory)

## How it works

**Phone (touch device)**
- **Bottom tab bar** — up to 4 primary sections shown as icon+label tiles; excess sections overflow into a popover triggered by a `···` (ellipsis) icon
- **Secondary tab bar** — appears below the top header when the current route has sibling routes at depth 2; replaced by a back button at depth 3+

**Portrait tablet**
- **Rail** — a permanent narrow strip on the left showing icons; a swipe-in drawer holds overflow content

**Desktop / landscape tablet**
- **Side navigation drawer** — hierarchical `SideNav` with icon-prefixed group headers, collapsed by default, toggled by a `DrawerToggle` in the header

The nav type re-evaluates dynamically on touch devices whenever the viewport size changes (rotation, split-screen resize), switching components in place without a page reload.

## Usage

Extend `AppNavLayout`, annotate with `@Layout`, and configure in the constructor:

```java
@Layout
public class MainLayout extends AppNavLayout {

    public MainLayout(@Value("${spring.application.name:App}") String appTitle) {
        super(appTitle, NavSelector.defaultSelector());

        setViewIconGenerator(m -> Optional.ofNullable(m.menuClass())
                .map(v -> v.getAnnotation(ViewIcon.class))
                .<Supplier<Icon>>map(a -> a.value()::create)
                .orElse(null));

        setViewTitleGenerator(m -> Optional.ofNullable(m.menuClass())
                .map(v -> v.getAnnotation(PageTitle.class))
                .map(PageTitle::value)
                .orElse(null));

        setViewNavGroupResolver(m -> Optional.ofNullable(m.menuClass())
                .map(v -> v.getAnnotation(MenuGroup.class))
                .map(MenuGroup::value)
                .orElse(null));

        setNavPathMatcher((currentPath, navItemPath) -> {
            var currentSegs = new Location(currentPath).getSegments();
            var navItemSegs = new Location(navItemPath).getSegments();
            return !navItemSegs.isEmpty()
                    && currentSegs.size() >= navItemSegs.size()
                    && currentSegs.subList(0, navItemSegs.size()).equals(navItemSegs);
        });

        addBrandContent(new H1(getAppTitle()));
    }
}
```

All configuration calls take effect immediately, even after the component is attached.

## Configuration reference

| Method | Default | Purpose |
|--------|---------|---------|
| `addBrandContent(Component...)` | — | Logo/title in the header (desktop) or drawer top (mobile) |
| `setUserMenu(Component)` | — | User widget in the header trailing (desktop) or drawer bottom (mobile) |
| `setViewIconGenerator(Function<MenuEntry, Supplier<Icon>>)` | no icon | Icon for each leaf nav item |
| `setViewTitleGenerator(Function<MenuEntry, String>)` | `@Menu#title()` | Label for each leaf nav item |
| `setViewNavGroupResolver(Function<MenuEntry, NavGroup>)` | path-based | Explicit group assignment for a view |
| `setNavPathMatcher(BiPredicate<String, String>)` | `String::equals` | Active-item path matching |
| `setNavGrouper(NavGrouper)` | `PathPrefixNavGrouper` | Full grouping strategy override |
| `setNavNodeRenderer(ComponentRenderer<SideNavItem, NavNode>)` | built-in | Custom desktop `SideNavItem` renderer |

## Nav grouping

`NavGrouper` is a `@FunctionalInterface` with a single method `NavNode nodeFor(MenuEntry)`. It maps each route entry to its position in the nav tree via `NavNode` parent links.

The default implementation, `PathPrefixNavGrouper`, groups routes by their first URL path segment. When a `NavGroup` resolver is also configured (via `setViewNavGroupResolver`), pre-warming entries with explicit groups causes their path siblings to merge into the same group automatically.

To use a fully custom grouping strategy, implement `NavGrouper` and pass it to `setNavGrouper()`.

## NavSelector

`NavSelector` is a `@FunctionalInterface` — `NavType select(DeviceType, Orientation)` — that determines which nav component to render for a given session. Pass it to the `AppNavLayout` constructor.

`NavSelector.defaultSelector()` returns the standard mapping:
- Desktop → `SIDENAV`
- Tablet landscape → `SIDENAV`
- Tablet portrait → `RAIL`
- Phone → `TOUCH`

Supply a custom lambda to override, e.g., to always use `SIDENAV` for testing.

## View header integration

Views can contribute content to the adaptive header slot without coupling to layout internals:

**`HasViewHeaderTitle`** — contributes an icon + title row (desktop only). Override `getViewHeaderIcon()` to supply an `Icon`; the `@PageTitle` value is read automatically. Override `getViewHeaderSuffix()` to append a badge or other trailing component.

**`HasViewHeaderComponent`** — contributes an action component shown on both desktop and mobile (e.g., a search bar or toolbar).

Both interfaces provide no-op defaults; implement only what is needed.

## Supporting types

| Type | Description |
|------|-------------|
| `NavType` | `TOUCH`, `RAIL`, `SIDENAV` — the active nav style |
| `DeviceType` | `PHONE`, `TABLET`, `DESKTOP` — detected from touch capability and screen size |
| `Orientation` | `PORTRAIT`, `LANDSCAPE` — re-evaluated on window resize for touch devices |
| `NavGroup` | Interface: `title()`, `icon()`, `parent()` — metadata for an explicit group node |
| `NavNode` | Immutable tree node: either a navigable leaf (`menuEntry()` present) or a non-navigable group |
| `RouteNavUtils` | Static helpers: `pathSegments`, `normalizedPath`, `routeSegmentLabel`, `leafTitle` |

## Development

### Running the demo

```
mvn jetty:run
```

Starts the test/demo server at http://localhost:8080.

### Integration tests

```
mvn verify -Pit
```

Tests run in headless mode by default. To disable headless mode:

```
mvn verify -Pit -Dplaywright.headed=true
```

## Publishing to Vaadin Directory

You can create the zip package needed for [Vaadin Directory](https://vaadin.com/directory/) using

```
mvn versions:set -DnewVersion=1.0.0 # You cannot publish snapshot versions
mvn clean package -Pdirectory
```

The package is created as `target/{project-name}-1.0.0.zip`

For more information or to upload the package, visit https://vaadin.com/directory/my-components?uploadNewComponent
