package com.devosphere.draugr.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #28 regional half: the global sky is felt differently by biome — mountains freeze rain to snow, etc. */
class BiomeClimateTest {

    @Test void mountainTurnsLowlandRainToSnow() {
        // A mild-lowland rainy front (3°C) is below freezing up the mountain (3 − 9 = −6°C) → snow.
        BiomeClimate.Local m = BiomeClimate.at("MOUNTAIN", "RAIN", 3.0, 20);
        assertEquals("SNOW", m.kind());
        assertTrue(m.temperatureC() < 0, "mountain is colder");
        assertTrue(m.windKph() > 20, "mountain is windier");
    }

    @Test void warmLowlandMeltsSnowToRain() {
        // A snowy front at 6°C globally stays snow up high but melts to rain in the warm grassland (7°C).
        assertEquals("RAIN", BiomeClimate.at("GRASSLAND", "SNOW", 6.0, 10).kind());
        assertEquals("SNOW", BiomeClimate.at("MOUNTAIN", "SNOW", 6.0, 10).kind());
    }

    @Test void forestSheltersWindAndClearStaysClear() {
        BiomeClimate.Local f = BiomeClimate.at("TEMPERATE_FOREST", "CLEAR", 15.0, 20);
        assertEquals("CLEAR", f.kind(), "clear weather is clear everywhere; only temp/wind shift");
        assertTrue(f.windKph() < 20, "the canopy breaks the wind");
    }

    @Test void unknownBiomePassesThroughUnchanged() {
        BiomeClimate.Local u = BiomeClimate.at(null, "RAIN", 12.0, 15);
        assertEquals("RAIN", u.kind());
        assertEquals(12.0, u.temperatureC());
        assertEquals(15, u.windKph());
    }
}
