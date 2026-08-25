package com.devosphere.draugr.item;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seam-richness formula (#181): a deposit's size is deterministic for a given patch of ground, scales with the
 * mineral's commonness, and varies from one ground to the next. A fast unit test of the pure function that decides it,
 * so the survey's richness read and the depletion draw-down rest on the same, predictable rule.
 */
class MineralSeedRichnessTest {

    private static final UUID GROUND_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID GROUND_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Test
    void aSeamIsDeterministicForTheSameGround() {
        int first = PhysicalItemService.mineralSeedFor(GROUND_A, "iron_ore", 0.40);
        int again = PhysicalItemService.mineralSeedFor(GROUND_A, "iron_ore", 0.40);
        assertEquals(first, again, "the same ground must always read the same seam — richness is fixed, not random");
    }

    @Test
    void ironSeamsFallWithinTheirRichnessBand() {
        // iron (rarity 0.40): base 40, times a richness factor 0.6 (poor) .. ~1.5 (rich) -> about 24 .. 60.
        for (int i = 0; i < 40; i++) {
            UUID ground = new UUID(i, 7L * i + 1);
            int seam = PhysicalItemService.mineralSeedFor(ground, "iron_ore", 0.40);
            assertTrue(seam >= 24 && seam <= 60, "iron seam out of band at ground " + i + ": " + seam);
        }
    }

    @Test
    void aRarerMineralFormsSmallerPocketsThanACommonOre() {
        // At the SAME ground the richness factor is not identical across minerals (it keys on the mineral too), so
        // compare bands: common iron's base (40) exceeds rare silver's (28), so silver's whole band sits lower.
        int silver = PhysicalItemService.mineralSeedFor(GROUND_A, "silver_ore", 0.15);
        assertTrue(silver >= 15 && silver <= 44, "silver seam out of its (smaller) band: " + silver);
    }

    @Test
    void differentGroundReadsAsADifferentSeam() {
        int a = PhysicalItemService.mineralSeedFor(GROUND_A, "iron_ore", 0.40);
        int b = PhysicalItemService.mineralSeedFor(GROUND_B, "iron_ore", 0.40);
        assertTrue(a != b, "two deliberately different grounds must not read as the identical seam (a=" + a + ", b=" + b + ")");
    }
}
