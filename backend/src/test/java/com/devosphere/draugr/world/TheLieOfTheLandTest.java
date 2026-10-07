package com.devosphere.draugr.world;

import com.devosphere.draugr.action.ChronicleActionService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which way the next ground lies, and what kind of ground this is (#224).
 *
 * <p>The visual context described a place by its biome alone. Two columns that genesis has always maintained —
 * {@code world_chunk.elevation} and {@code moisture} — were read into the service's own query, handed to
 * {@link com.devosphere.draugr.simulation.BiomeClimate}, and then thrown away, so a saturated lowland fen and a
 * dry upland heath were the same WETLAND and the same GRASSLAND. And {@code surroundings} said "moorland is
 * visible from here" without ever saying WHICH WAY to look, though the neighbours are found by their grid offset
 * and the offset was discarded on the way out: a sea to the west and a mountain wall to the east were the same
 * payload. From one real chunk of a seeded world, the view that was being thrown away:
 *
 * <pre>
 *   north  HIGHLAND          728   ABOVE
 *   west   HIGHLAND          733   ABOVE
 *   east   TEMPERATE_FOREST  536   BELOW
 *   south  TEMPERATE_FOREST  538   BELOW
 * </pre>
 *
 * <p>No database: the bands and the bearing are pure functions over numbers the query already had.
 */
class TheLieOfTheLandTest {

    @Test
    void northIsMinusYAndTheRingIsWhole() {
        assertEquals("north", Compass.of(0, -1));
        assertEquals("south", Compass.of(0, 1));
        assertEquals("east", Compass.of(1, 0));
        assertEquals("west", Compass.of(-1, 0));
        assertEquals("northeast", Compass.of(1, -1));
        assertEquals("northwest", Compass.of(-1, -1));
        assertEquals("southeast", Compass.of(1, 1));
        assertEquals("southwest", Compass.of(-1, 1));
        assertNull(Compass.of(0, 0), "no distance at all is no bearing");

        // Every compound a bearing can be is in the ring, or rotating round it silently stops.
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++) {
                String bearing = Compass.of(dx, dy);
                if (bearing == null) continue;
                boolean inRing = false;
                for (String r : Compass.RING) inRing |= r.equals(bearing);
                assertTrue(inRing, bearing + " must be a point of the ring");
            }
        assertEquals("east", Compass.rotate("northeast", true));
        assertEquals("north", Compass.rotate("northeast", false));
        assertEquals("nowhere", Compass.rotate("nowhere", true), "a bearing it does not know comes back unchanged");
    }

    /**
     * The one convention, held by two readers.
     *
     * <p>{@code ChronicleActionService.Direction} parses "go north" into a move and carries its own dx/dy; this
     * class says which way the next ground lies. If the two ever disagree, a place would look one way and feel
     * another — and the warmth model is built on north being {@code grid_y - 1}, in six separate queries. By
     * reflection because the enum is private, which is the same door
     * {@code IntentClassificationRegressionTest} uses to reach {@code classify}.
     */
    @Test
    void theMoveAndTheViewAgreeOnWhichWayIsNorth() throws Exception {
        Class<?> direction = null;
        for (Class<?> c : ChronicleActionService.class.getDeclaredClasses())
            if ("Direction".equals(c.getSimpleName())) direction = c;
        assertNotNull(direction, "ChronicleActionService.Direction is the other reader of this convention");

        Field dxField = direction.getDeclaredField("dx");
        Field dyField = direction.getDeclaredField("dy");
        Field named = direction.getDeclaredField("description");
        dxField.setAccessible(true); dyField.setAccessible(true); named.setAccessible(true);

        int checked = 0;
        for (Object d : direction.getEnumConstants()) {
            int dx = dxField.getInt(d), dy = dyField.getInt(d);
            String says = (String) named.get(d);
            assertEquals(says, Compass.of(dx, dy),
                "Direction." + d + " moves (" + dx + "," + dy + ") and calls it \"" + says + "\"");
            checked++;
        }
        assertEquals(4, checked, "the four cardinals are what a Chronicle can walk");
    }

    @Test
    void theGroundComesInBandsAndEveryHeightHasOne() {
        assertEquals("LOWLAND", VisualContextService.elevationBand(0));
        assertEquals("LOWLAND", VisualContextService.elevationBand(119));
        assertEquals("RISING", VisualContextService.elevationBand(120));
        assertEquals("UPLAND", VisualContextService.elevationBand(536));
        assertEquals("HIGH", VisualContextService.elevationBand(728));

        assertEquals("ARID", VisualContextService.moistureBand(0));
        assertEquals("DRY", VisualContextService.moistureBand(20));
        assertEquals("MOIST", VisualContextService.moistureBand(50));
        assertEquals("WET", VisualContextService.moistureBand(60));
        assertEquals("SATURATED", VisualContextService.moistureBand(80));

        // No height and no wetness falls through a gap: the bands must partition the whole range, or a place
        // would arrive with a null band and the fingerprint would change for no reason anyone could name.
        for (int e = -500; e <= 3000; e += 7) assertNotNull(VisualContextService.elevationBand(e), "elevation " + e);
        for (int m = 0; m <= 100; m++) assertNotNull(VisualContextService.moistureBand(m), "moisture " + m);
    }

    @Test
    void reliefUsesTheSameStepTheWarmthModelDoes() {
        // 40 units, which is what every sun_warmed query in the codebase compares against. A smaller step would
        // call a flat plain "ABOVE" and a larger one would flatten a real scarp.
        assertEquals("ABOVE", VisualContextService.relief(728, 538));
        assertEquals("BELOW", VisualContextService.relief(536, 728));
        assertEquals("LEVEL", VisualContextService.relief(538, 536));
        assertEquals("LEVEL", VisualContextService.relief(576, 536), "exactly 40 up is not yet a slope");
        assertEquals("ABOVE", VisualContextService.relief(577, 536));
        assertEquals("LEVEL", VisualContextService.relief(496, 536), "and exactly 40 down is not yet a drop");
        assertEquals("BELOW", VisualContextService.relief(495, 536));
    }

    @Test
    void theContractStayedAdditive() {
        // A client written against version 1 reads surroundings and must keep working. Both older shapes of the
        // record still construct, and both leave the new fields empty rather than absent.
        VisualContextService.VisualContext v1 = new VisualContextService.VisualContext(
            1, "GRASSLAND", java.util.List.of(), "DAY", "SUMMER", "CLEAR", 18.0, true, "abc");
        assertNull(v1.land(), "the oldest shape knew nothing of the ground's bands");
        assertTrue(v1.nearby().isEmpty());
        assertTrue(v1.surroundings().isEmpty());

        VisualContextService.VisualContext v232 = new VisualContextService.VisualContext(
            1, "GRASSLAND", java.util.List.of(), "DAY", "SUMMER", "CLEAR", 18.0, true,
            java.util.List.of("WETLAND"), "abc");
        assertEquals(java.util.List.of("WETLAND"), v232.surroundings());
        assertNull(v232.land());

        // And the shape from before a skyline could carry anything: bands and bearings, no landmarks.
        VisualContextService.VisualContext v2 = new VisualContextService.VisualContext(
            2, "GRASSLAND", java.util.List.of(), "DAY", "SUMMER", "CLEAR", 18.0, true,
            java.util.List.of("WETLAND"), new VisualContextService.Land("LOWLAND", "WET", false),
            java.util.List.of(new VisualContextService.Nearby("north", "WETLAND", "LEVEL", "ADJACENT")), "abc");
        assertEquals("LOWLAND", v2.land().elevation());
        assertEquals(1, v2.nearby().size());
        assertTrue(v2.landmarks().isEmpty(), "a version-2 payload reports no skyline, and empty is not absent");

        // Pinned, not computed: the point is that a bump is deliberate. 2 was the bands and the bearing; 3 lets the
        // next ground carry what is built or broken on it, which is the last line of #224's contract.
        assertEquals(3, VisualContextService.VERSION, "the skyline is version 3");
    }

    @Test
    void aLandmarkCarriesAKindAndABearingAndNeverAName() {
        // The whole of what makes this tier safe to send. A ruin's identity — which ruin, what it was, what is left
        // in it — is the discovery; its silhouette is only the invitation. So the record has nowhere to put a name,
        // and that is enforced by its shape rather than by a reviewer noticing.
        VisualContextService.Landmark seen = new VisualContextService.Landmark("RUIN", "south", "ADJACENT");
        assertEquals("RUIN", seen.kind());
        assertEquals("south", seen.direction());
        assertEquals("ADJACENT", seen.distance());
        assertEquals(3, VisualContextService.Landmark.class.getRecordComponents().length,
            "a landmark carries a kind, a bearing and a band — a fourth component would be somewhere to leak a name");
        for (var component : VisualContextService.Landmark.class.getRecordComponents())
            assertTrue(java.util.List.of("kind", "direction", "distance").contains(component.getName()),
                "unexpected landmark component: " + component.getName());
    }
}
