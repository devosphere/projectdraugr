package com.devosphere.draugr.ecology;

import com.devosphere.draugr.item.PhysicalItemService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A made funnel trap's throat is cut for its quarry (#908): the quarry's size tier and one above, never more. And
 * the quarry is read from the trap's own name, so only a trap named for a species is narrowed at all.
 */
class TrapThroatTest {

    @Test
    void aCrayfishSizedThroatTakesSmallFishButNotACarp() {
        assertTrue(WildlifeEncounterService.throatAdmits("TINY", "TINY"), "crayfish, minnow");
        assertTrue(WildlifeEncounterService.throatAdmits("TINY", "SMALL"), "dace, perch: honest bycatch");
        assertFalse(WildlifeEncounterService.throatAdmits("TINY", "MEDIUM"), "a carp does not fit a crayfish trap");
        assertFalse(WildlifeEncounterService.throatAdmits("TINY", "LARGE"), "nor a sturgeon");
        assertFalse(WildlifeEncounterService.throatAdmits("TINY", null), "an unknown size is never let in");
        assertFalse(WildlifeEncounterService.throatAdmits(null, "TINY"), "a trap with no known quarry size narrows nothing here");
    }

    @Test
    void theQuarryIsWhatTheTrapsNameNames() {
        assertTrue(PhysicalItemService.saidAsASpeciesName("Crayfish trap", "crayfish"));
        assertFalse(PhysicalItemService.saidAsASpeciesName("Crayfish trap", "catfish"), "crayfish is not catfish");
        for (String unnamed : new String[]{"Fish trap", "Basket fish trap", "Woven fish trap"})
            for (String species : new String[]{"crayfish", "catfish", "carp", "minnow", "river_trout", "freshwater_eel"})
                assertFalse(PhysicalItemService.saidAsASpeciesName(unnamed, species),
                    unnamed + " names no species, so it must fish the whole water as before, not for " + species);
    }
}
