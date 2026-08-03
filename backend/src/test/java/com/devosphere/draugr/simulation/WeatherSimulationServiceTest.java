package com.devosphere.draugr.simulation;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the #28 weather rebalance: the seasonal draw is realistic, not the old every-fourth-day storm.
 * Pure distribution check over the deterministic daily draw — no database, no Spring.
 */
class WeatherSimulationServiceTest {

    private final WeatherSimulationService svc = new WeatherSimulationService(null);

    private Map<WeatherSimulationService.Kind, Integer> sample(String season, int days) {
        Map<WeatherSimulationService.Kind, Integer> counts = new EnumMap<>(WeatherSimulationService.Kind.class);
        for (WeatherSimulationService.Kind k : WeatherSimulationService.Kind.values()) counts.put(k, 0);
        long seed = 681_013_497L;
        for (long day = 0; day < days; day++) counts.merge(svc.pickWeather(season, seed, day), 1, Integer::sum);
        return counts;
    }

    @Test void stormsAreRareInEverySeason() {
        int days = 4000;
        for (String season : new String[]{"WINTER", "SPRING", "SUMMER", "AUTUMN"}) {
            double storm = sample(season, days).get(WeatherSimulationService.Kind.STORM) / (double) days;
            assertTrue(storm > 0.03 && storm < 0.12, season + " storm rate should be rare (~7-8%), was " + storm);
        }
    }

    @Test void snowBelongsToWinterAndNeverFallsInSummer() {
        int days = 4000;
        assertEquals(0, sample("SUMMER", days).get(WeatherSimulationService.Kind.SNOW), "summer must never snow");
        double winterSnow = sample("WINTER", days).get(WeatherSimulationService.Kind.SNOW) / (double) days;
        assertTrue(winterSnow > 0.12, "winter should see meaningful snow, was " + winterSnow);
    }

    @Test void summerIsThesunniest() {
        int days = 4000;
        double summerClear = sample("SUMMER", days).get(WeatherSimulationService.Kind.CLEAR) / (double) days;
        double winterClear = sample("WINTER", days).get(WeatherSimulationService.Kind.CLEAR) / (double) days;
        assertTrue(summerClear > winterClear, "summer should be clearer than winter");
        assertTrue(summerClear > 0.4, "summer should be mostly fair, was " + summerClear);
    }

    @Test void theDrawIsStableForAGivenSeedAndDay() {
        assertEquals(svc.pickWeather("AUTUMN", 42L, 100L), svc.pickWeather("AUTUMN", 42L, 100L),
            "same seed + day must always yield the same weather (reproducible, not flicker)");
    }
}
