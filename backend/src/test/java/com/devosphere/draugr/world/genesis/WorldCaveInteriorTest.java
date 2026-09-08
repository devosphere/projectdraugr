package com.devosphere.draugr.world.genesis;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The inside of the rock is derived geography, so it is proved here without Docker (#158).
 *
 * <p>A cave mouth is the way in; CAVE_INTERIOR is what it is the way into. The rule is deliberately narrow — rock
 * that touches an entrance AND is itself closed to the sky — and both halves earn their place:
 *
 * <ul>
 *   <li>Without "touches an entrance", a cave could form in the middle of a massif with no way in, and the ground
 *       would be unreachable. Movement now refuses to let a Chronicle walk into rock, so an interior with no mouth
 *       beside it would be a chunk of the world nobody could ever stand on.</li>
 *   <li>Without "closed to the sky", every piece of rock beside an entrance becomes cave, and the open summits the
 *       ore lives on go with them.</li>
 * </ul>
 *
 * <p>Checked across several seeds, because a world where the rule happens to behave is not the same as a rule that
 * behaves.
 */
class WorldCaveInteriorTest {

    private final WorldGenesisService generator = new WorldGenesisService(null, "target/test-world-exports");

    private static final long[] SEEDS = { 681_013_497L, 1L, 7L, 4242L, 99991L };

    private String biomeAt(WorldGenesisService.GenesisRequest r, int x, int y) throws Exception {
        Method m = WorldGenesisService.class.getDeclaredMethod(
            "terrainAt", double.class, double.class, int.class, int.class, long.class, boolean.class);
        m.setAccessible(true);
        Object cell = m.invoke(generator, (double) x, (double) y, r.widthChunks(), r.heightChunks(), r.seed(), true);
        Method b = cell.getClass().getDeclaredMethod("biome");
        b.setAccessible(true);
        return (String) b.invoke(cell);
    }

    private WorldGenesisService.GenesisRequest seeded(long seed) {
        var base = WorldGenesisService.GenesisRequest.mvpDefault();
        return new WorldGenesisService.GenesisRequest(seed, base.widthChunks(), base.heightChunks());
    }

    /** The world must actually make caves — a biome nothing generates is worse than no biome. */
    @Test
    void theWorldMakesCavesToGoInto() throws Exception {
        for (long seed : SEEDS) {
            var r = seeded(seed);
            int interior = 0;
            for (int y = 0; y < r.heightChunks(); y++) for (int x = 0; x < r.widthChunks(); x++)
                if ("CAVE_INTERIOR".equals(biomeAt(r, x, y))) interior++;
            assertTrue(interior > 0, "seed " + seed + " made no cave interior at all");
        }
    }

    /**
     * The reachability guarantee. Every interior must have a mouth orthogonally beside it, because movement
     * refuses to let a Chronicle walk into rock from open ground — an interior without a mouth is a piece of the
     * world nobody can ever reach.
     */
    @Test
    void everyChamberHasADoorway() throws Exception {
        List<String> stranded = new ArrayList<>();
        for (long seed : SEEDS) {
            var r = seeded(seed);
            for (int y = 0; y < r.heightChunks(); y++) for (int x = 0; x < r.widthChunks(); x++) {
                if (!"CAVE_INTERIOR".equals(biomeAt(r, x, y))) continue;
                boolean mouth = false;
                for (int[] s : new int[][]{ {0,-1}, {0,1}, {-1,0}, {1,0} }) {
                    int nx = x + s[0], ny = y + s[1];
                    if (nx < 0 || ny < 0 || nx >= r.widthChunks() || ny >= r.heightChunks()) continue;
                    if ("CAVE_MOUTH".equals(biomeAt(r, nx, ny))) { mouth = true; break; }
                }
                if (!mouth) stranded.add("seed " + seed + " (" + x + "," + y + ")");
            }
        }
        assertTrue(stranded.isEmpty(),
            () -> "a chamber with no mouth beside it can never be entered — movement refuses to walk into rock: "
                + stranded);
    }

    /** The cave must not eat the mountain: open rock is where the ore lives. */
    @Test
    void theOpenMountainSurvives() throws Exception {
        for (long seed : SEEDS) {
            var r = seeded(seed);
            int mountain = 0, cave = 0;
            for (int y = 0; y < r.heightChunks(); y++) for (int x = 0; x < r.widthChunks(); x++) {
                String b = biomeAt(r, x, y);
                if ("MOUNTAIN".equals(b)) mountain++;
                if ("CAVE_INTERIOR".equals(b) || "CAVE_MOUTH".equals(b)) cave++;
            }
            assertTrue(mountain > cave,
                "seed " + seed + ": open rock must still outweigh cave (mountain=" + mountain + ", cave=" + cave
                    + ") — obsidian and pumice live on the open summits");
        }
    }

    /** Derived geography must be deterministic, or the same seed stops making the same world. */
    @Test
    void theSameSeedCarvesTheSameCaves() throws Exception {
        var r = seeded(681_013_497L);
        List<String> first = new ArrayList<>(), second = new ArrayList<>();
        for (int y = 0; y < r.heightChunks(); y++) for (int x = 0; x < r.widthChunks(); x++)
            if ("CAVE_INTERIOR".equals(biomeAt(r, x, y))) first.add(x + "," + y);
        for (int y = 0; y < r.heightChunks(); y++) for (int x = 0; x < r.widthChunks(); x++)
            if ("CAVE_INTERIOR".equals(biomeAt(r, x, y))) second.add(x + "," + y);
        assertEquals(first, second, "the same seed must carve the same caves");
        assertTrue(!first.isEmpty(), "and there must be caves to compare");
    }
}
