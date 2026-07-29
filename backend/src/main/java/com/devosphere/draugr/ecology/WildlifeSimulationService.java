package com.devosphere.draugr.ecology;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Deterministic aggregate ecology. Individual creatures are materialized only for local encounters. */
@Service
public class WildlifeSimulationService {
    private final JdbcTemplate jdbc;
    public WildlifeSimulationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void advanceTo(Instant now) {
        seedExistingSites(now);
        int hour = now.atZone(ZoneOffset.UTC).getHour();
        jdbc.update("UPDATE wildlife_population SET behavior_state = CASE activity_cycle WHEN 'NOCTURNAL' THEN CASE WHEN ? >= 19 OR ? < 5 THEN 'FORAGING' ELSE 'RESTING' END WHEN 'CREPUSCULAR' THEN CASE WHEN ? BETWEEN 5 AND 8 OR ? BETWEEN 17 AND 20 THEN 'FORAGING' ELSE 'RESTING' END ELSE CASE WHEN ? BETWEEN 7 AND 18 THEN 'FORAGING' ELSE 'RESTING' END END, last_simulated_at = ?", hour, hour, hour, hour, hour, now);
    }

    @Transactional
    void seedExistingSites(Instant now) {
        List<Site> sites = jdbc.query("SELECT id, site_kind FROM ecology_site WHERE site_category = 'WILDLIFE' AND NOT EXISTS (SELECT 1 FROM wildlife_population wp WHERE wp.site_id = ecology_site.id)", (rs, row) -> new Site(rs.getObject(1, UUID.class), rs.getString(2)));
        for (Site site : sites) {
            Profile profile = profileFor(site.kind().toLowerCase());
            jdbc.update("INSERT INTO wildlife_population (id, site_id, species_key, ecological_role, activity_cycle, population_count, carrying_capacity, behavior_state, last_simulated_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'RESTING', ?)", UUID.randomUUID(), site.id(), profile.species(), profile.role(), profile.cycle(), profile.initial(), profile.capacity(), now);
        }
    }

    @Transactional(readOnly = true)
    public List<PopulationView> populations() {
        return jdbc.query("SELECT wp.species_key, wp.ecological_role, wp.activity_cycle, wp.population_count, wp.carrying_capacity, wp.behavior_state, es.site_kind FROM wildlife_population wp JOIN ecology_site es ON es.id = wp.site_id ORDER BY es.site_kind", (rs, row) -> new PopulationView(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getString(6), rs.getString(7)));
    }

    private Profile profileFor(String kind) {
        if (kind.contains("wolf")) return new Profile("gray_wolf", "CARNIVORE", "CREPUSCULAR", 7, 14);
        if (kind.contains("bear")) return new Profile("brown_bear", "OMNIVORE", "CREPUSCULAR", 2, 5);
        if (kind.contains("boar")) return new Profile("wild_boar", "OMNIVORE", "DIURNAL", 12, 28);
        if (kind.contains("deer")) return new Profile("red_deer", "HERBIVORE", "CREPUSCULAR", 24, 55);
        if (kind.contains("elk")) return new Profile("elk", "HERBIVORE", "CREPUSCULAR", 16, 38);
        if (kind.contains("hare")) return new Profile("hare", "HERBIVORE", "NOCTURNAL", 30, 70);
        if (kind.contains("goat")) return new Profile("mountain_goat", "HERBIVORE", "DIURNAL", 14, 32);
        if (kind.contains("beaver")) return new Profile("beaver", "HERBIVORE", "CREPUSCULAR", 6, 14);
        if (kind.contains("otter")) return new Profile("river_otter", "CARNIVORE", "DIURNAL", 5, 12);
        if (kind.contains("fowl")) return new Profile("marsh_fowl", "OMNIVORE", "DIURNAL", 35, 85);
        return new Profile("forest_fox", "OMNIVORE", "CREPUSCULAR", 4, 10);
    }
    private record Site(UUID id, String kind) { }
    private record Profile(String species, String role, String cycle, int initial, int capacity) { }
    public record PopulationView(String speciesKey, String ecologicalRole, String activityCycle, int populationCount, int carryingCapacity, String behaviorState, String siteKind) { }
}
