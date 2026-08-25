package com.devosphere.draugr.ecology;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fish-stretch richness formula (#181/#36): how much a stretch of water holds when full is deterministic for a
 * given water, varies from one water to the next, and never falls below a floor that keeps even a thin reach worth
 * working. A fast unit test of the pure function, so the water survey and the fishing draw-down share one rule.
 */
class FishStockRichnessTest {

    private static final UUID WATER_A = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
    private static final UUID WATER_B = UUID.fromString("00000000-0000-0000-0000-0000000000d4");

    @Test
    void aStretchIsDeterministicForTheSameWater() {
        assertEquals(WildlifeEncounterService.fishStockSeedFor(WATER_A),
                WildlifeEncounterService.fishStockSeedFor(WATER_A),
                "the same water must always read the same fullness — richness is fixed, not random");
    }

    @Test
    void everyStretchLiesWithinTheRichnessBandAndAboveTheFloor() {
        // base 350, factor 0.6 (thin) .. ~1.4 (teeming) -> ~210..490, floored at 180.
        for (int i = 0; i < 40; i++) {
            UUID water = new UUID(i, 13L * i + 5);
            int full = WildlifeEncounterService.fishStockSeedFor(water);
            assertTrue(full >= 180 && full <= 490, "stretch out of band at water " + i + ": " + full);
        }
    }

    @Test
    void differentWaterReadsAsADifferentStretch() {
        int a = WildlifeEncounterService.fishStockSeedFor(WATER_A);
        int b = WildlifeEncounterService.fishStockSeedFor(WATER_B);
        assertTrue(a != b, "two deliberately different waters must not read as the identical stretch (a=" + a + ", b=" + b + ")");
    }
}
