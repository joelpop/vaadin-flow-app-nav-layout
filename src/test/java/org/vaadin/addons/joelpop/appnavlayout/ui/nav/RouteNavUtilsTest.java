package org.vaadin.addons.joelpop.appnavlayout.ui.nav;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.server.menu.MenuEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteNavUtilsTest {

    private static MenuEntry entry(String path, String title) {
        return new MenuEntry(path, title, null, null, Div.class);
    }

    // ——————————— pathSegments ————————————

    @Test
    void pathSegmentsSplitsOnSlash() {
        assertEquals(List.of("catalog", "products"), RouteNavUtils.pathSegments("catalog/products"));
    }

    @Test
    void pathSegmentsStripsLeadingSlash() {
        assertEquals(List.of("catalog", "products"), RouteNavUtils.pathSegments("/catalog/products"));
    }

    @Test
    void pathSegmentsOfEmptyPathIsEmpty() {
        assertTrue(RouteNavUtils.pathSegments("").isEmpty());
    }

    // ——————————— normalizedPath ————————————

    @Test
    void normalizedPathStripsLeadingSlashFromMenuEntry() {
        assertEquals("catalog/products", RouteNavUtils.normalizedPath(entry("/catalog/products", "Products")));
    }

    @Test
    void normalizedPathLeavesPathWithoutLeadingSlashUnchanged() {
        assertEquals("catalog/products", RouteNavUtils.normalizedPath(entry("catalog/products", "Products")));
    }

    // ——————————— routeSegmentLabel ————————————

    @Test
    void routeSegmentLabelCapitalisesSingleWord() {
        assertEquals("Catalog", RouteNavUtils.routeSegmentLabel("catalog"));
    }

    @Test
    void routeSegmentLabelCapitalisesEachHyphenatedWord() {
        assertEquals("Audit Log", RouteNavUtils.routeSegmentLabel("audit-log"));
    }

    // ——————————— leafTitle ————————————

    @Test
    void leafTitleReturnsMenuEntryTitleAsIs() {
        // MenuConfiguration.getMenuEntries() already resolves this (through @PageTitle, then
        // the class name) before this add-on ever sees it — leafTitle adds no fallback of its own.
        assertEquals("Products", RouteNavUtils.leafTitle(entry("/catalog/products", "Products")));
    }
}
