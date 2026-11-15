package com.devosphere.draugr.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #931 — which drink phrases name water on the ground. A named source is drunk from even with water in the pack;
 * a phrase that names no source keeps taking what is carried. Pure over text, so no database.
 */
class NamedWaterSourceTest {

    @Test
    void namingTheWaterOnTheGroundIsASource() {
        for (String said : new String[]{
                "drink from the spring", "drink from the stream", "drink from the river", "drink at the well",
                "drink from the catchment", "drink from the walled spring head", "drink at the draw below the sand filter bed",
                "take a drink of water from the stream", "drink some water from the brook", "kneel and drink from the pool",
                "drink from the sea", "drink out of the river"})
            assertTrue(ChronicleActionService.namesAWaterSource(said), () -> "names a source: " + said);
    }

    @Test
    void aDrinkThatNamesNoSourceTakesWhatIsCarried() {
        for (String said : new String[]{
                "drink water", "drink some water", "take a drink of water", "drink", "drink well",
                "drink from my waterskin", "drink from the skin", "drink the boiled water",
                "drink from the cup i filled at the stream", "drink in the shade"})
            assertFalse(ChronicleActionService.namesAWaterSource(said), () -> "names no source: " + said);
    }
}
