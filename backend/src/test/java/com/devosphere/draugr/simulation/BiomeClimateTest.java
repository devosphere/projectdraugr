package com.devosphere.draugr.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #28 regional half: the global sky is felt differently from place to place, driven by the chunk's OWN
 * geography — altitude (lapse rate), latitude, and humidity — not just its biome label.
 *
 * <p>Arg order: {@code at(biome, elevation, moisture, gridY, worldHeightChunks, kind, tempC, windKph)}.
 * A mid map row (gridY = middle) is used unless the test is about latitude, so latitude contributes 0.
 */
class BiomeClimateTest {

    // elevation bands from worldgen: OCEAN<300, WETLAND<380, lowland 380–680, HIGHLAND 680–820, MOUNTAIN>820.
    private static final int PEAK = 950, HIGH = 750, LOWLAND = 500, MOIST = 500;

    @Test void mountainTurnsLowlandRainToSnow() {
        // A mild-lowland rainy front (3°C) is well below freezing up a 950-elevation peak → snow, colder, windier.
        BiomeClimate.Local m = BiomeClimate.at("MOUNTAIN", PEAK, MOIST, 5, 11, "RAIN", 3.0, 20);
        assertEquals("SNOW", m.kind());
        assertTrue(m.temperatureC() < 0, "the peak is below freezing: " + m.temperatureC());
        assertTrue(m.windKph() > 20, "the peak is windier");
    }

    @Test void warmLowlandMeltsSnowToRain() {
        // A snowy front at 6°C stays snow up the cold peak but melts to rain in the warm lowland.
        assertEquals("RAIN", BiomeClimate.at("GRASSLAND", LOWLAND, 400, 5, 11, "SNOW", 6.0, 10).kind());
        assertEquals("SNOW", BiomeClimate.at("MOUNTAIN", PEAK, MOIST, 5, 11, "SNOW", 6.0, 10).kind());
    }

    @Test void forestSheltersWindAndClearStaysClear() {
        BiomeClimate.Local f = BiomeClimate.at("TEMPERATE_FOREST", LOWLAND, 700, 5, 11, "CLEAR", 15.0, 20);
        assertEquals("CLEAR", f.kind(), "clear weather is clear everywhere; only temp/wind shift");
        assertTrue(f.windKph() < 20, "the canopy breaks the wind");
    }

    /** THE #28 refinement: altitude is continuous, not bucketed — a higher mountain is genuinely colder. */
    @Test void higherAltitudeIsColderWithinTheSameBiome() {
        double lowPeak  = BiomeClimate.at("MOUNTAIN", 830, MOIST, 5, 11, "CLEAR", 10.0, 5).temperatureC();
        double highPeak = BiomeClimate.at("MOUNTAIN", 1000, MOIST, 5, 11, "CLEAR", 10.0, 5).temperatureC();
        assertTrue(highPeak < lowPeak - 2.0,
            "a 1000-elevation peak must be materially colder than an 830 one (lapse rate): " + highPeak + " vs " + lowPeak);
    }

    /** Lowlands at or below the reference are NOT cooled by altitude — the lapse only bites going up. */
    @Test void lowlandsAreNotCooledByAltitude() {
        // A wetland at the reference elevation feels the global temperature (± its small biome character), not a lapse.
        double t = BiomeClimate.at("WETLAND", 380, MOIST, 5, 11, "CLEAR", 12.0, 6).temperatureC();
        assertEquals(12.0, t, 0.001, "reference-elevation lowland keeps the baseline temperature");
    }

    /** Latitude: the north of the map is colder than the south for the same front and terrain. */
    @Test void northIsColderThanSouth() {
        double north = BiomeClimate.at("GRASSLAND", LOWLAND, MOIST, 0, 21, "CLEAR", 15.0, 6).temperatureC();
        double south = BiomeClimate.at("GRASSLAND", LOWLAND, MOIST, 20, 21, "CLEAR", 15.0, 6).temperatureC();
        assertTrue(north < south, "the top of the map is colder: north " + north + " vs south " + south);
    }

    /** Humidity (wet-bulb): dry air lets precipitation freeze at a higher air temperature than wet air does. */
    @Test void dryAirFreezesPrecipitationSooner() {
        // The 8.6°C front is felt as ~+1°C up at elevation 750 (lapse). There, a bone-dry chunk turns rain
        // to snow (wet-bulb) while a saturated one stays rain — same temperature, same front, humidity decides.
        assertEquals("SNOW", BiomeClimate.at("HIGHLAND", HIGH, 60,  5, 11, "RAIN", 8.6, 8).kind());
        assertEquals("RAIN", BiomeClimate.at("HIGHLAND", HIGH, 950, 5, 11, "RAIN", 8.6, 8).kind());
    }

    @Test void unknownBiomePassesThroughUnchanged() {
        // Null biome, reference elevation, mid latitude, average moisture → nothing shifts.
        BiomeClimate.Local u = BiomeClimate.at(null, 380, MOIST, 5, 11, "RAIN", 12.0, 15);
        assertEquals("RAIN", u.kind());
        assertEquals(12.0, u.temperatureC());
        assertEquals(15, u.windKph());
    }
}
