package com.devosphere.draugr.simulation;

/**
 * Turns the world's single global sky into the weather actually FELT at a place (GitHub #28, regional
 * half). {@code world_weather} is one synoptic system — the front moving over the whole world — but the
 * same front is colder and windier up a mountain, and falls as snow where the lowland gets rain. This
 * derives the local reading from the CHUNK'S OWN GEOGRAPHY, not just its biome label:
 *
 * <ul>
 *   <li><b>Altitude (elevation)</b> drives temperature through the environmental lapse rate — ~6.5 °C per
 *       km of relief above the lowland reference. A high peak is genuinely colder than a low one, rather
 *       than every MOUNTAIN chunk sharing one flat constant. This is the core of the #28 refinement.</li>
 *   <li><b>Latitude (grid_y)</b> shifts temperature north-to-south: the top of the map is colder, the
 *       bottom warmer, scaled by the map's actual N–S extent.</li>
 *   <li><b>Humidity (moisture)</b> biases the rain↔snow boundary through the wet-bulb effect: dry air lets
 *       precipitation freeze at a higher air temperature; saturated air needs it colder.</li>
 *   <li><b>Biome character</b> adds only the NON-altitude residual — sea damp and exposure, canopy shade
 *       and shelter, open-ground sun and wind — so the altitude cooling is not double-counted in the label.</li>
 * </ul>
 *
 * <p>Pure function, no database, deterministic and read-time: no per-chunk weather rows to store or tick.
 * The global system still decides WHEN a front arrives and how severe it is; the geography decides how that
 * front is experienced here. (Spatial fronts — a rain shadow behind a range, so the KIND itself varies by
 * region — still need a per-region weather model and remain the open half of #28.)
 */
public final class BiomeClimate {

    private BiomeClimate() { }

    /** The weather as felt at a location: possibly a different kind (rain→snow), temperature, and wind. */
    public record Local(String kind, double temperatureC, int windKph) { }

    // --- Altitude (lapse rate) -----------------------------------------------------------------------
    private static final double LAPSE_C_PER_KM = 6.5;        // standard environmental lapse rate
    private static final double METRES_PER_ELEVATION_UNIT = 3.15; // the abstract 0..1000 scale → ~3.15 km of relief
    private static final int    LOWLAND_REFERENCE = 380;     // top of the wetland band = "sea-level-equivalent"; only
                                                             // ground ABOVE this is cooled by altitude (lowlands aren't warmed)
    // --- Latitude ------------------------------------------------------------------------------------
    private static final double LAT_C_PER_CHUNK = 0.5;       // per chunk of N–S distance from the map's middle
    private static final double LAT_C_CAP = 10.0;            // no more than this much either side, however large the map

    public static Local at(String biome, int elevation, int moisture, int gridY, int worldHeightChunks,
                           String globalKind, double globalTempC, int globalWindKph) {
        // 1. Altitude: cool with real height above the lowland reference (never warm below it).
        double metresAboveRef = Math.max(0, elevation - LOWLAND_REFERENCE) * METRES_PER_ELEVATION_UNIT;
        double lapseC = -LAPSE_C_PER_KM * (metresAboveRef / 1000.0);

        // 2. Latitude: north (small grid_y) colder, south warmer, scaled by the map's actual extent.
        double middle = (worldHeightChunks - 1) / 2.0;
        double latC = Math.max(-LAT_C_CAP, Math.min(LAT_C_CAP, (gridY - middle) * LAT_C_PER_CHUNK));

        // 3. Biome character: only the NON-altitude residual (temp nudge + wind), so height isn't counted twice.
        double charC;
        int windOffset;
        switch (biome == null ? "" : biome) {
            case "MOUNTAIN"        -> { charC =  0; windOffset = 18; }  // altitude does the cooling; wind-scoured
            case "HIGHLAND"        -> { charC =  0; windOffset = 10; }  // altitude does the cooling; breezy
            case "OCEAN"           -> { charC = -1; windOffset = 22; }  // damp sea air, nothing to break the wind
            case "GRASSLAND"       -> { charC =  1; windOffset =  8; }  // open, sun-warmed, exposed
            case "WETLAND"         -> { charC =  0; windOffset = -2; }  // humid, still air over the water
            case "RIVER_BANK"      -> { charC = -1; windOffset =  4; }  // running water cools it; the valley funnels wind
            case "TEMPERATE_FOREST"-> { charC = -1; windOffset = -6; }  // shaded and sheltered under the canopy
            default                -> { charC =  0; windOffset =  0; }
        }

        double temp = clampTemp(globalTempC + lapseC + latC + charC);
        int wind = Math.max(0, Math.min(180, globalWindKph + windOffset));

        // 4. Phase, biased by humidity (wet-bulb): dry air freezes precipitation at a higher air temperature.
        //    dryness > 0 when drier than average, < 0 when wetter. Temperature is still the primary driver.
        double dryness = (500 - clampMoisture(moisture)) / 500.0;
        double snowThreshold = dryness * 1.5;              // dry → snow up to +1.5 °C; saturated → only below −1.5 °C
        String kind = globalKind == null ? "CLEAR" : globalKind;
        if ("RAIN".equals(kind) && temp <= snowThreshold) kind = "SNOW";               // rain freezes to snow
        else if ("STORM".equals(kind) && temp <= snowThreshold - 1.0) kind = "SNOW";   // a cold storm is a blizzard
        else if ("SNOW".equals(kind) && temp >= 4.0) kind = "RAIN";                    // snow melts to rain when warm
        return new Local(kind, temp, wind);
    }

    private static double clampTemp(double c) { return Math.max(-40, Math.min(55, Math.round(c * 10) / 10.0)); }
    private static int clampMoisture(int m) { return Math.max(0, Math.min(1000, m)); }
}
