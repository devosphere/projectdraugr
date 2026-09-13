package com.devosphere.draugr.ecology;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the water is called (#37): the site that makes it water names it, and dry-looking ground falls back honestly. */
class FreshWaterTest {

    @Test
    void aNamedWaterIsCalledWhatItIs() {
        assertEquals("the still pond", FreshWater.name(List.of("Still pond"), "GRASSLAND"));
        assertEquals("the fast stream", FreshWater.name(List.of("Fast stream", "Freshwater spring"), "RIVER_BANK"),
            "the first row is the open water; the query puts springs and fords after it");
        assertEquals("The slow river reach", FreshWater.capitalised(FreshWater.name(List.of("Slow river reach"), "RIVER_BANK")));
    }

    @Test
    void groundThatIsWaterWithNoNamedSiteFallsBackToItsGround() {
        assertEquals("the river", FreshWater.name(List.of(), "RIVER_BANK"));
        assertEquals("the marsh water", FreshWater.name(List.of(), "WETLAND"));
        assertEquals("the water here", FreshWater.name(null, "GRASSLAND"), "anywhere else keeps the old wording exactly");
    }

    @Test
    void theSiteQueryReadsOnlyWaterOnTheChunkAndPutsSpringsAndFordsLast() {
        String sql = FreshWater.SITE_KINDS_ON_CHUNK;
        assertTrue(sql.contains("chunk_id=?") && sql.contains("site_category='RESOURCE'"),
            "a wildlife site like 'River fishing run' must never be taken for the water's name");
        assertTrue(sql.contains(FreshWater.sites()), "the name must be read from the same definition that makes it water");
        assertFalse(FreshWater.sites().contains("'%pool%'"), "a bare 'pool' would turn a siren's lair into a drink");
    }
}
