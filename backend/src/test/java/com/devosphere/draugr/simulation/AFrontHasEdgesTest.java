package com.devosphere.draugr.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #912 — THE FRONT HAS EDGES, AND YOU CAN WALK OUT FROM UNDER IT.
 *
 * <p>A Chronicle on seed 681013497 spawned in a storm, crossed 560 tiles of a 28x20 world, and never left
 * it. {@code world_weather} holds one row per world, so every chunk was inside the only front there was —
 * {@link BiomeClimate} could turn that storm into snow on high ground but could not put a chunk outside it.
 *
 * <p>This holds the footprint itself to account, with no database: that a front covers a believable part of
 * the map rather than all of it, that it is reproducible for a seed, that it MOVES, and that CLEAR and
 * OVERCAST are given no footprint at all because they are not fronts. Where that footprint is then honoured
 * is {@code local_sky(chunk)}'s (V469), and {@code AStormHasEdgesIntegrationTest} holds that end.
 */
class AFrontHasEdgesTest {

    private static final long SEED = 681_013_497L;
    private static final int WIDTH = 28, HEIGHT = 20;

    private WeatherSimulationService.Footprint front(WeatherSimulationService.Kind kind, long day, int hour) {
        return WeatherSimulationService.footprint(kind, SEED, day, hour, WIDTH, HEIGHT);
    }

    /** How many of the world's chunks this footprint covers, by the same distance rule local_sky applies. */
    private double coverage(WeatherSimulationService.Footprint f) {
        if (f == null) return 1.0;
        int under = 0;
        for (int x = 0; x < WIDTH; x++)
            for (int y = 0; y < HEIGHT; y++)
                if (Math.hypot(x - f.centreX(), y - f.centreY()) <= f.radiusChunks()) under++;
        return under / (double) (WIDTH * HEIGHT);
    }

    @Test void clearAndOvercastAreNotFrontsAndHaveNoFootprint() {
        // They are what the sky does when nothing is overhead. A null footprint means the whole world, which
        // is also what an untouched save says — so this is the behaviour V469 leans on twice.
        assertNull(front(WeatherSimulationService.Kind.CLEAR, 0, 12), "a clear sky is not a front");
        assertNull(front(WeatherSimulationService.Kind.OVERCAST, 0, 12), "an overcast sky is not a front");
    }

    @Test void rainStormAndSnowAllGetOne() {
        for (WeatherSimulationService.Kind kind : new WeatherSimulationService.Kind[]{
                WeatherSimulationService.Kind.RAIN, WeatherSimulationService.Kind.STORM, WeatherSimulationService.Kind.SNOW}) {
            WeatherSimulationService.Footprint f = front(kind, 7, 9);
            assertNotEquals(null, f, kind + " is a front and must have a footprint");
            assertTrue(f.radiusChunks() > 0, kind + " must reach somewhere");
        }
    }

    /**
     * THE DEFECT, MEASURED. On no day may a front cover the whole map, or #912 is still true for that day.
     * A storm is the tightest thing the sky does; rain is broader; neither may swallow the world.
     */
    @Test void noFrontEverCoversTheWholeWorld() {
        for (long day = 0; day < 400; day++) {
            for (int hour = 0; hour < 24; hour += 3) {
                for (WeatherSimulationService.Kind kind : new WeatherSimulationService.Kind[]{
                        WeatherSimulationService.Kind.RAIN, WeatherSimulationService.Kind.STORM, WeatherSimulationService.Kind.SNOW}) {
                    double covered = coverage(front(kind, day, hour));
                    assertTrue(covered < 1.0, () -> "a " + kind + " covering every chunk is the bug #912 reports");
                }
            }
        }
    }

    /** And a storm is tighter than rain, as the sky actually behaves. */
    @Test void aStormIsTighterThanRain() {
        assertTrue(front(WeatherSimulationService.Kind.STORM, 3, 6).radiusChunks()
                 < front(WeatherSimulationService.Kind.RAIN, 3, 6).radiusChunks(),
            "a storm is a tighter thing than a day of rain");
    }

    /** Reproducible for a seed: the same world, day and hour always gives the same weather in the same place. */
    @Test void theSameSeedDayAndHourAlwaysGiveTheSameFootprint() {
        for (long day : new long[]{0, 1, 99, 20_000}) {
            for (int hour : new int[]{0, 5, 17, 23}) {
                assertEquals(front(WeatherSimulationService.Kind.STORM, day, hour),
                             front(WeatherSimulationService.Kind.STORM, day, hour),
                    "weather must be reproducible for a seed, or no two readings of one world agree");
            }
        }
    }

    /** A different seed must be a different world's weather, or every world has the same storms in the same places. */
    @Test void anotherSeedPutsTheFrontSomewhereElse() {
        int same = 0, days = 200;
        for (long day = 0; day < days; day++) {
            WeatherSimulationService.Footprint mine = WeatherSimulationService.footprint(
                WeatherSimulationService.Kind.STORM, SEED, day, 12, WIDTH, HEIGHT);
            WeatherSimulationService.Footprint theirs = WeatherSimulationService.footprint(
                WeatherSimulationService.Kind.STORM, SEED + 1, day, 12, WIDTH, HEIGHT);
            if (mine.equals(theirs)) same++;
        }
        assertTrue(same < days / 10, "two seeds share the same storm on " + same + " of " + days + " days");
    }

    /**
     * IT MOVES, which is what makes walking out from under it — or being caught by it — a thing that can
     * happen rather than a fact about where you spawned.
     */
    @Test void theFrontCrossesTheMapOverADay() {
        WeatherSimulationService.Footprint dawn = front(WeatherSimulationService.Kind.STORM, 11, 6);
        WeatherSimulationService.Footprint dusk = front(WeatherSimulationService.Kind.STORM, 11, 18);
        assertNotEquals(dawn.centreX(), dusk.centreX(), "a front that never moves is weather you cannot escape");
        assertTrue(Math.abs(dusk.centreX() - dawn.centreX()) >= 12,
            "twelve hours should carry a front a good part of the way across the map");
    }

    /**
     * A chunk outside the front has somewhere to BE outside it. Averaged over a year of storms, most of the
     * world is not under any one of them — which is the player-facing claim #912 makes.
     */
    @Test void mostOfTheWorldIsOutsideAnyGivenStorm() {
        double total = 0;
        int samples = 0;
        for (long day = 0; day < 365; day++, samples++) total += coverage(front(WeatherSimulationService.Kind.STORM, day, 12));
        double mean = total / samples;
        assertTrue(mean < 0.35, () -> "a storm covers " + Math.round(mean * 100) + "% of the world on average");
        assertTrue(mean > 0.02, () -> "a storm covering " + Math.round(mean * 100) + "% is not weather, it is a puddle");
    }

    /** A bigger world gets a bigger front, so the rule is about the map and not about a magic number. */
    @Test void theFrontScalesWithTheMap() {
        int small = WeatherSimulationService.footprint(WeatherSimulationService.Kind.RAIN, SEED, 5, 12, 28, 20).radiusChunks();
        int large = WeatherSimulationService.footprint(WeatherSimulationService.Kind.RAIN, SEED, 5, 12, 112, 80).radiusChunks();
        assertTrue(large > small * 3, "a map four times as wide should not get the same little front");
    }
}
