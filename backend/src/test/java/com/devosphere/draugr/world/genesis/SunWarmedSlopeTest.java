package com.devosphere.draugr.world.genesis;

import com.devosphere.draugr.simulation.BiomeClimate;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ground that faces the sun (#159), proved without Docker because both halves are pure functions.
 *
 * <p>A slope falling away to the south takes the light square-on through the middle of the day instead of
 * glancing off, and it sits out of the north wind behind it. That is why the same hillside is bare on one face
 * and green on the other, and why anyone wintering in hill country camps on the southern shoulder.
 *
 * <p>Two things have to be true for the rule to be worth having, and they are separate questions:
 *
 * <ol>
 *   <li>a south-facing slope must actually read warmer — {@link BiomeClimate} is a pure function, so this is
 *       arithmetic;</li>
 *   <li><b>the world must contain such ground.</b> A rule about terrain the generator never makes is a rule for
 *       nobody, which is the same failure as a marker on ground that does not exist. Elevation is derived
 *       deterministically, so this can be counted here rather than discovered in play.</li>
 * </ol>
 */
class SunWarmedSlopeTest {

    private final WorldGenesisService generator = new WorldGenesisService(null, "target/test-world-exports");

    private static final long[] SEEDS = { 681_013_497L, 1L, 7L, 4242L, 99991L };

    /** The stored elevation of a chunk, as genesis would persist it. */
    private int elevationAt(WorldGenesisService.GenesisRequest r, int x, int y) throws Exception {
        Method m = WorldGenesisService.class.getDeclaredMethod(
            "terrainAt", double.class, double.class, int.class, int.class, long.class, boolean.class);
        m.setAccessible(true);
        Object cell = m.invoke(generator, (double) x, (double) y, r.widthChunks(), r.heightChunks(), r.seed(), true);
        Method elevation = cell.getClass().getDeclaredMethod("elevation");
        elevation.setAccessible(true);
        return (int) elevation.invoke(cell);
    }

    private WorldGenesisService.GenesisRequest seeded(long seed) {
        var base = WorldGenesisService.GenesisRequest.mvpDefault();
        return new WorldGenesisService.GenesisRequest(seed, base.widthChunks(), base.heightChunks());
    }

    /** The same ground, the same weather, one of them facing the sun. */
    @Test
    void aSouthFacingSlopeReadsWarmerThanTheSameGroundFlat() {
        BiomeClimate.Local flat = BiomeClimate.at("HIGHLAND", 700, 500, 10, 20, "CLEAR", 4.0, 10);
        BiomeClimate.Local facing = BiomeClimate.at("HIGHLAND", 700, 500, 10, 20, "CLEAR", 4.0, 10, true);

        assertTrue(facing.temperatureC() > flat.temperatureC(),
            () -> "a slope out of the north wind and square to the light must be warmer (facing="
                + facing.temperatureC() + ", flat=" + flat.temperatureC() + ")");
        assertTrue(facing.temperatureC() - flat.temperatureC() < 5.0,
            "a southern shoulder is a few degrees, not a different climate");
    }

    /** The old reading must be untouched, or this changed the weather everywhere instead of on slopes. */
    @Test
    void groundWithNoAspectReadsExactlyAsBefore() {
        BiomeClimate.Local implicit = BiomeClimate.at("GRASSLAND", 500, 500, 10, 20, "RAIN", 3.0, 12);
        BiomeClimate.Local explicit = BiomeClimate.at("GRASSLAND", 500, 500, 10, 20, "RAIN", 3.0, 12, false);
        assertEquals(implicit.temperatureC(), explicit.temperatureC(), "the flat reading must not have moved");
        assertEquals(implicit.kind(), explicit.kind(), "nor what the front falls as");
        assertEquals(implicit.windKph(), explicit.windKph(), "nor the wind");
    }

    /**
     * The rule must have ground to apply to. Counted from the same elevations genesis persists, using the same
     * test the two callers make in SQL: the chunk to the north stands more than 40 higher.
     */
    @Test
    void theWorldActuallyContainsSouthFacingSlopes() throws Exception {
        for (long seed : SEEDS) {
            var r = seeded(seed);
            int facing = 0;
            for (int y = 1; y < r.heightChunks(); y++)
                for (int x = 0; x < r.widthChunks(); x++)
                    if (elevationAt(r, x, y - 1) > elevationAt(r, x, y) + 40) facing++;

            assertTrue(facing > 0, "seed " + seed + " made no south-facing slope — the rule would be for nobody");
            assertTrue(facing < r.widthChunks() * r.heightChunks() / 2,
                "seed " + seed + " made " + facing + " of " + (r.widthChunks() * r.heightChunks())
                    + " chunks a warm slope; an aspect that covers half the map is not an aspect");
        }
    }
}
