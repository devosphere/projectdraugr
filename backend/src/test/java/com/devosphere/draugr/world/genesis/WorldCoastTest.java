package com.devosphere.draugr.world.genesis;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shore is derived geography, so it is proved here without Docker (#157).
 *
 * <p>COAST was the second biome the catalogue believed in and the generator never made — sea beet, samphire and
 * sea buckthorn grow on it, an osprey works it, a bonecrab and a wave roc live on it, and there was nowhere in
 * the world any of them could be. Ground that touches open water IS the shore, so it is derived from the terrain
 * rather than invented, and these are the properties that make what it derives a coastline.
 */
class WorldCoastTest {

    private final WorldGenesisService generator = new WorldGenesisService(null, "target/test-world-exports");
    private final WorldGenesisService.GenesisRequest request = WorldGenesisService.GenesisRequest.mvpDefault();

    private String biomeAt(int x, int y) throws Exception {
        Method m = WorldGenesisService.class.getDeclaredMethod(
            "terrainAt", double.class, double.class, int.class, int.class, long.class, boolean.class);
        m.setAccessible(true);
        Object cell = m.invoke(generator, (double) x, (double) y,
            request.widthChunks(), request.heightChunks(), request.seed(), true);
        Method biome = cell.getClass().getDeclaredMethod("biome");
        biome.setAccessible(true);
        return (String) biome.invoke(cell);
    }

    @Test
    void theWorldHasAShore() throws Exception {
        int coast = 0;
        for (int y = 0; y < request.heightChunks(); y++)
            for (int x = 0; x < request.widthChunks(); x++)
                if ("COAST".equals(biomeAt(x, y))) coast++;
        assertTrue(coast > 0, "the world must have a shore at all — eight catalogue entries have nowhere else to be");
        assertTrue(coast < request.widthChunks() * request.heightChunks() / 8,
            "a shore is an edge, not a third of the map — got " + coast + " chunks");
    }

    @Test
    void everyShoreChunkActuallyTouchesOpenWater() throws Exception {
        for (int y = 0; y < request.heightChunks(); y++) {
            for (int x = 0; x < request.widthChunks(); x++) {
                if (!"COAST".equals(biomeAt(x, y))) continue;
                boolean sea = false;
                for (int dy = -1; dy <= 1 && !sea; dy++) for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int tx = x + dx, ty = y + dy;
                    if (tx < 0 || ty < 0 || tx >= request.widthChunks() || ty >= request.heightChunks()) continue;
                    if ("OCEAN".equals(biomeAt(tx, ty))) { sea = true; break; }
                }
                assertTrue(sea, "a shore at (" + x + "," + y + ") with no water against it is not a shore");
            }
        }
    }

    /**
     * The freshwater marsh must survive the shore. WETLAND here carries reeds, clay, cranberries and freshwater
     * fish; calling the sea-margin ones "coast" would move that whole ecology into salt water.
     */
    @Test
    void theMarshesAreLeftAlone() throws Exception {
        int wetland = 0, mountain = 0;
        for (int y = 0; y < request.heightChunks(); y++)
            for (int x = 0; x < request.widthChunks(); x++) {
                String b = biomeAt(x, y);
                if ("WETLAND".equals(b)) wetland++;
                if ("MOUNTAIN".equals(b)) mountain++;
            }
        assertTrue(wetland >= 40, "the freshwater marsh must not be eaten by the shore — only " + wetland + " left");
        assertTrue(mountain >= 40, "a sea cliff is still a cliff, and that is where the ore is — only " + mountain + " left");
    }

    /** A river mouth reads as river: fresh water is rarer and more useful, and the channel leads inland. */
    @Test
    void aRiverReachingTheSeaStaysARiver() throws Exception {
        java.util.Set<Long> rivers = generator.riverCells(request.widthChunks(), request.heightChunks(), request.seed());
        for (long cell : rivers) {
            int x = (int) (cell >> 32), y = (int) (cell & 0xFFFFFFFFL);
            assertEquals("RIVER_BANK", biomeAt(x, y),
                "the channel at (" + x + "," + y + ") must stay river bank even where it meets the sea");
        }
    }
}
