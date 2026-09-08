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
            assertEquals(WorldGenesisService.MARKER_SPECIFICATIONS.size() + WorldGenesisService.EDGE_SPECIFICATIONS.size(),
                markers.size(), "the plan must place one marker per spec, edge specs included");
            // Only the plain specs here; the edge specs are appended after them and have their own test below,
            // because their rule has a second half this loop cannot see.
            for (int i = 0; i < WorldGenesisService.MARKER_SPECIFICATIONS.size(); i++) {
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
     * Fast and slow water must be two places, not one. {@code waterCharacterAt} reads the site on the chunk and
     * takes the first it finds, so a stream and a reach sharing a chunk would make that ground arbitrarily one or
     * the other — and the two sites exist precisely to be different waters.
     */
    @Test
    void fastAndSlowWaterAreDifferentStretchesOfRiver() throws Exception {
        for (long seed : new long[]{ WorldGenesisService.GenesisRequest.mvpDefault().seed(), 1L, 7L, 4242L, 99991L }) {
            WorldGenesisService.GenesisRequest request = seeded(seed);
            List<WorldGenesisService.PreviewMarker> markers = generator.markerPlan(request);
            WorldGenesisService.PreviewMarker fast = named(markers, "Fast stream");
            WorldGenesisService.PreviewMarker slow = named(markers, "Slow river reach");
            assertTrue(fast != null && slow != null, "the world must plan both waters at seed " + seed);
            assertEquals("RIVER_BANK", biomeAt(request, fast.x(), fast.y()), "a stream runs in a river channel");
            assertEquals("RIVER_BANK", biomeAt(request, slow.x(), slow.y()), "so does a slow reach");
            assertTrue(fast.x() != slow.x() || fast.y() != slow.y(),
                "fast and slow water share a chunk at seed " + seed + " (" + fast.x() + "," + fast.y()
                    + "), which makes that ground arbitrarily one or the other");
        }
    }

    private WorldGenesisService.PreviewMarker named(List<WorldGenesisService.PreviewMarker> markers, String label) {
        return markers.stream().filter(m -> m.label().equals(label)).findFirst().orElse(null);
    }

    /**
     * An edge marker must stand on ground of its own kind AND touch the other kind — that neighbour is the whole
     * of what makes it an edge. A "forest edge" in the middle of a wood is the right name on ground that does not
     * answer to it, which is exactly what {@code marker()}'s silent centre-of-map fallback would produce.
     */
    @Test
    void everyEdgeMarkerStandsWhereTwoKindsOfGroundMeet() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (long seed : new long[]{ WorldGenesisService.GenesisRequest.mvpDefault().seed(), 1L, 7L, 4242L, 99991L }) {
            WorldGenesisService.GenesisRequest request = seeded(seed);
            List<WorldGenesisService.PreviewMarker> markers = generator.markerPlan(request);
            for (int i = 0; i < WorldGenesisService.EDGE_SPECIFICATIONS.size(); i++) {
                WorldGenesisService.EdgeMarkerSpec spec = WorldGenesisService.EDGE_SPECIFICATIONS.get(i);
                // Edge specs are appended after the main list, so their markers begin at that offset.
                WorldGenesisService.PreviewMarker placed = markers.get(WorldGenesisService.MARKER_SPECIFICATIONS.size() + i);
                String on = biomeAt(request, placed.x(), placed.y());
                if (!Arrays.asList(spec.biomes()).contains(on)) {
                    wrong.add("seed " + seed + ": '" + spec.label() + "' stands on " + on);
                    continue;
                }
                boolean touches = false;
                for (int[] step : new int[][]{ {0,-1}, {0,1}, {-1,0}, {1,0} }) {
                    int nx = placed.x() + step[0], ny = placed.y() + step[1];
                    if (nx < 0 || ny < 0 || nx >= request.widthChunks() || ny >= request.heightChunks()) continue;
                    if (Arrays.asList(spec.besides()).contains(biomeAt(request, nx, ny))) { touches = true; break; }
                }
                if (!touches)
                    wrong.add("seed " + seed + ": '" + spec.label() + "' at (" + placed.x() + "," + placed.y()
                        + ") touches none of " + Arrays.toString(spec.besides()));
            }
        }
        assertTrue(wrong.isEmpty(),
            () -> "an edge that does not touch the other kind of ground is not an edge: " + String.join("; ", wrong));
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
