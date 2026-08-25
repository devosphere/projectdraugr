package com.devosphere.draugr.item;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The natural-stand density formula (#200/#201): how many trees an uncut wooded chunk holds is deterministic for a
 * given ground, varies from one ground to the next, and never falls below a floor that keeps ordinary felling clear
 * of the ceiling. A fast unit test of the pure function, so the woodland survey and the felling draw-down share one rule.
 */
class WoodlandStandRichnessTest {

    private static final UUID GROUND_A = UUID.fromString("00000000-0000-0000-0000-0000000000e5");
    private static final UUID GROUND_B = UUID.fromString("00000000-0000-0000-0000-0000000000f6");

    @Test
    void aStandIsDeterministicForTheSameGround() {
        assertEquals(PhysicalItemService.natStandFor(GROUND_A), PhysicalItemService.natStandFor(GROUND_A),
                "the same ground must always read the same stand — density is fixed, not random");
    }

    @Test
    void everyStandLiesWithinTheDensityBandAndAboveTheFloor() {
        for (int i = 0; i < 40; i++) {
            UUID ground = new UUID(i, 17L * i + 3);
            int stand = PhysicalItemService.natStandFor(ground);
            assertTrue(stand >= 10 && stand <= 22, "stand out of band at ground " + i + ": " + stand);
        }
    }

    @Test
    void differentGroundReadsAsADifferentStand() {
        int a = PhysicalItemService.natStandFor(GROUND_A);
        int b = PhysicalItemService.natStandFor(GROUND_B);
        assertTrue(a != b, "two deliberately different grounds must not read as the identical stand (a=" + a + ", b=" + b + ")");
    }
}
