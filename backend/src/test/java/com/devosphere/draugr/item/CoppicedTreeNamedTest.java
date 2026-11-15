package com.devosphere.draugr.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #906: {@code coppice willow} handed back a Hazel Rod because the coppice handler never heard which tree was
 * named. The words now choose the stand; this pins the pure matcher that hears them.
 */
class CoppicedTreeNamedTest {

    @Test
    void theNamedTreeIsHeard() {
        assertTrue(PhysicalItemService.namesTheTree("coppice willow", "willow"));
        assertTrue(PhysicalItemService.namesTheTree("coppice the willows by the water", "willow"));
        assertTrue(PhysicalItemService.namesTheTree("cut rods from the ashes", "ash"));
        assertTrue(PhysicalItemService.namesTheTree("coppice the birches", "birch"));
        assertTrue(PhysicalItemService.namesTheTree("coppice the walnut", "walnut_tree"));
        assertTrue(PhysicalItemService.namesTheTree("coppice the crab apple trees", "crab_apple_tree"));
    }

    @Test
    void aGeneralWordIsNotATreeAndAWordIsNotFoundInsideAnother() {
        // The head-noun fallback of the species matcher would hear chestnut_tree in "the tree".
        assertFalse(PhysicalItemService.namesTheTree("coppice the tree", "chestnut_tree"));
        assertFalse(PhysicalItemService.namesTheTree("coppice the stand", "willow"));
        assertFalse(PhysicalItemService.namesTheTree("coppice the oakwood", "oak"));
        assertFalse(PhysicalItemService.namesTheTree("coppice the apple", "crab_apple_tree"));
        assertFalse(PhysicalItemService.namesTheTree("coppice the ashlar", "ash"));
    }

    @Test
    void theTreeIsNamedWithoutTheCataloguesSuffix() {
        assertEquals("walnut", PhysicalItemService.treeName("walnut_tree"));
        assertEquals("crab apple", PhysicalItemService.treeName("crab_apple_tree"));
        assertEquals("willow", PhysicalItemService.treeName("willow"));
    }
}
