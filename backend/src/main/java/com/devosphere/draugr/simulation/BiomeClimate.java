package com.devosphere.draugr.simulation;

/**
 * Turns the world's single global sky into the weather actually FELT in a place (GitHub #28, regional
 * half). {@code world_weather} is one synoptic system — the front moving over the whole world — but a
 * mountain is colder and windier than a sheltered forest, and the same front that rains on the lowlands
 * falls as snow up high. This applies each biome's climate character to the global reading: a temperature
 * offset, a wind offset, and a temperature-driven rain↔snow conversion. Pure function; no database.
 *
 * <p>It is deliberately deterministic and read-time: no per-chunk weather rows to store or tick. The
 * global system still drives when fronts arrive and how severe they are; the biome only colours the local
 * experience.
 */
public final class BiomeClimate {

    private BiomeClimate() { }

    /** The weather as felt at a location: possibly a different kind (rain→snow), temperature, and wind. */
    public record Local(String kind, double temperatureC, int windKph) { }

    public static Local at(String biome, String globalKind, double globalTempC, int globalWindKph) {
        double tempOffset;
        int windOffset;
        switch (biome == null ? "" : biome) {
            case "MOUNTAIN"        -> { tempOffset = -9; windOffset = 18; }   // cold, exposed, wind-scoured
            case "HIGHLAND"        -> { tempOffset = -5; windOffset = 10; }   // cool uplands, breezy
            case "OCEAN"           -> { tempOffset = -1; windOffset = 22; }   // mild but the wind comes off the water
            case "GRASSLAND"       -> { tempOffset =  1; windOffset =  8; }   // open, sunnier, nothing to break the wind
            case "WETLAND"         -> { tempOffset =  0; windOffset = -2; }   // humid, still air over the water
            case "TEMPERATE_FOREST"-> { tempOffset = -1; windOffset = -6; }   // sheltered under the canopy
            default                -> { tempOffset =  0; windOffset =  0; }
        }
        double temp = clampTemp(globalTempC + tempOffset);
        int wind = Math.max(0, Math.min(180, globalWindKph + windOffset));

        String kind = globalKind == null ? "CLEAR" : globalKind;
        if ("RAIN".equals(kind) && temp <= 0.0) kind = "SNOW";          // rain freezes to snow up high
        else if ("STORM".equals(kind) && temp <= -1.0) kind = "SNOW";   // a cold storm is a blizzard
        else if ("SNOW".equals(kind) && temp >= 4.0) kind = "RAIN";     // snow melts to rain in the warm lowlands
        return new Local(kind, temp, wind);
    }

    private static double clampTemp(double c) { return Math.max(-40, Math.min(55, Math.round(c * 10) / 10.0)); }
}
