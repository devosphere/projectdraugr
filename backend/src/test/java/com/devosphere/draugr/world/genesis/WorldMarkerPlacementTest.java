package com.devosphere.draugr.world.genesis;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every marker stands on ground it names (#156/#161).
 *
 * <p>{@code marker(...)} scans the map for a chunk whose biome the spec accepts, and when it finds none it returns
 * the middle of the map — no biome check, no warning, no failure. So a marker whose ground the terrain never makes
 * does not go missing, which would be noticed; it appears in the right place on the map and means nothing, which
 * is not. A marsh island planted where the generator lays no marsh would read as delivered while being a label on
 * whatever happens to sit at the centre.
 *
 * <p>This is derived geography like the shore, so it needs no database. It is checked across several seeds rather
 * than the default alone, because a spec can be satisfiable in one world and not the next — and a marker that is
 * decoration only for some players is still decoration.
 */
class WorldMarkerPlacementTest {

    private final WorldGenesisService generator = new WorldGenesisService(null, "target/test-world-exports");

    private String biomeAt(WorldGenesisService.GenesisRequest request, int x, int y) throws Exception {
        Method m = WorldGenesisService.class.getDeclaredMethod(
            "terrainAt", double.class, double.class, int.class, int.class, long.class, boolean.class);
        m.setAccessible(true);
        Object cell = m.invoke(generator, (double) x, (double) y,
            request.widthChunks(), request.heightChunks(), request.seed(), true);
        Method biome = cell.getClass().getDeclaredMethod("biome");
        biome.setAccessible(true);
        return (String) biome.invoke(cell);
    }

    private WorldGenesisService.GenesisRequest seeded(long seed) {
        WorldGenesisService.GenesisRequest base = WorldGenesisService.GenesisRequest.mvpDefault();
        return new WorldGenesisService.GenesisRequest(seed, base.widthChunks(), base.heightChunks());
    }

    @Test
    void noMarkerIsPlacedOnGroundItsSpecDoesNotName() throws Exception {
        List<String> stranded = new ArrayList<>();
        for (long seed : new long[]{ WorldGenesisService.GenesisRequest.mvpDefault().seed(), 1L, 7L, 4242L, 99991L }) {
            WorldGenesisService.GenesisRequest request = seeded(seed);
            List<WorldGenesisService.PreviewMarker> markers = generator.markerPlan(request);
            assertEquals(WorldGenesisService.MARKER_SPECIFICATIONS.size(), markers.size(),
                "the plan must place one marker per spec");
            for (int i = 0; i < markers.size(); i++) {
                WorldGenesisService.MarkerSpec spec = WorldGenesisService.MARKER_SPECIFICATIONS.get(i);
                WorldGenesisService.PreviewMarker placed = markers.get(i);
                String actual = biomeAt(request, placed.x(), placed.y());
                if (!Arrays.asList(spec.biomes()).contains(actual))
                    stranded.add("seed " + seed + ": '" + spec.label() + "' wants "
                        + Arrays.toString(spec.biomes()) + " but stands on " + actual
                        + " at (" + placed.x() + "," + placed.y() + ")");
            }
        }
        assertTrue(stranded.isEmpty(),
            () -> "a marker on ground its own spec does not accept is a label, not a place — "
                + stranded.size() + " stranded:\n" + String.join("\n", stranded));
    }

    /**
     * The marsh island specifically, because it is the one whose whole meaning is the ground under it: it makes its
     * chunk arable, and a marsh island that is not in a marsh is farmland handed out somewhere arbitrary.
     */
    @Test
    void theMarshIslandStandsInAMarsh() throws Exception {
        WorldGenesisService.GenesisRequest request = WorldGenesisService.GenesisRequest.mvpDefault();
        List<WorldGenesisService.PreviewMarker> markers = generator.markerPlan(request);
        WorldGenesisService.PreviewMarker island = markers.stream()
            .filter(m -> m.label().equals("Marsh island")).findFirst().orElse(null);
        assertTrue(island != null, "the world must plan a marsh island");
        assertEquals("WETLAND", biomeAt(request, island.x(), island.y()),
            "a marsh island that is not in a marsh is just a field with a misleading name");
    }
}
