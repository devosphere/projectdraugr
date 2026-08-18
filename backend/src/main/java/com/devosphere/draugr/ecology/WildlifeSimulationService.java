package com.devosphere.draugr.ecology;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
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
        jdbc.query("SELECT wp.id,wp.population_count,wp.carrying_capacity,wp.ecological_role,wp.activity_cycle,wp.last_simulated_at,ww.weather_kind FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id JOIN world_weather ww ON ww.world_id=es.world_id FOR UPDATE", rs -> {
            while (rs.next()) {
                UUID id = rs.getObject(1, UUID.class); int population = rs.getInt(2); int capacity = rs.getInt(3); String role = rs.getString(4); String cycle = rs.getString(5); Instant last = rs.getTimestamp(6).toInstant(); String weather = rs.getString(7);
                long intervalHours = reproductionIntervalHours(role);
                long intervals = Math.max(0, Duration.between(last, now).toHours() / intervalHours);
                if (intervals > 0 && population < capacity) {
                    int next = Math.min(capacity, population + (int)Math.min(intervals, capacity - population));
                    jdbc.update("UPDATE wildlife_population SET population_count=?,last_simulated_at=? WHERE id=?", next, Timestamp.from(last.plus(Duration.ofHours(intervals * intervalHours))), id);
                }
                String behavior = behaviorFor(role, hour, weather, cycle);
                jdbc.update("UPDATE wildlife_population SET behavior_state=? WHERE id=?", behavior, id);
            }
            return null;
        });
        applyCascades(now);
    }

    /**
     * The inter-species cascade pass. Base behaviour above is decided per population
     * in isolation; these rules then let populations react to each other and to the
     * world, which is what makes the ecology read as intelligent without any model
     * being asked to think. Each rule is a deterministic set-based update, applied
     * after every population has its base state. See docs/systems/11.2-Behavioral-FSM.md.
     */
    @Transactional
    void applyCascades(Instant now) {
        // Pack cascade — a pack-hunting species that is hunting coordinates instead.
        jdbc.update("UPDATE wildlife_population wp SET behavior_state='PACK_HUNT' FROM wildlife_species ws WHERE ws.species_key=wp.species_key AND ws.pack_hunter AND wp.behavior_state='HUNTING' AND wp.population_count>1");

        // Prey awareness — herbivores in a chunk where a predator is actively hunting
        // stop feeding and watch. Prey do not need to see the predator to sense it.
        jdbc.update("UPDATE wildlife_population prey SET behavior_state='ALERT' " +
            "FROM ecology_site prey_site WHERE prey_site.id=prey.site_id AND prey.ecological_role='HERBIVORE' " +
            "AND prey.behavior_state IN ('FORAGING','RESTING','DRINKING','FEEDING') " +
            "AND EXISTS (SELECT 1 FROM wildlife_population pred JOIN ecology_site pred_site ON pred_site.id=pred.site_id " +
            "  WHERE pred_site.chunk_id=prey_site.chunk_id AND pred.id<>prey.id AND pred.population_count>0 " +
            "  AND pred.ecological_role='CARNIVORE' AND pred.behavior_state IN ('HUNTING','PACK_HUNT','STALKING'))");

        // Scavenger cascade — scavengers converge on a chunk holding a fresh carcass.
        jdbc.update("UPDATE wildlife_population sc SET behavior_state='FEEDING' " +
            "FROM ecology_site site WHERE site.id=sc.site_id AND sc.ecological_role='SCAVENGER' AND sc.population_count>0 " +
            "AND EXISTS (SELECT 1 FROM wildlife_carcass wc JOIN world_object w ON w.id=wc.object_id " +
            "  WHERE w.current_location_id=site.chunk_id AND w.lifecycle_state='ACTIVE' AND wc.remaining_meat_units>0)");

        // Fire fear — a lit fire drives animals off the ground around it. Predators
        // hold at a wary distance rather than pressing a hunt through smoke and light.
        jdbc.update("UPDATE wildlife_population wp SET behavior_state='ALERT' " +
            "FROM ecology_site site WHERE site.id=wp.site_id AND wp.behavior_state IN ('HUNTING','PACK_HUNT','STALKING','FORAGING') " +
            "AND EXISTS (SELECT 1 FROM construction_project cp JOIN world_object w ON w.id=cp.object_id " +
            "  JOIN fire_state fs ON fs.construction_id=cp.object_id " +
            "  WHERE w.current_location_id=site.chunk_id AND fs.active=true)");

        // Territorial defence — a territorial species at or near capacity holds ground
        // rather than ranging, which is what makes intruding on it dangerous.
        jdbc.update("UPDATE wildlife_population wp SET behavior_state='TERRITORIAL' FROM wildlife_species ws " +
            "WHERE ws.species_key=wp.species_key AND ws.territorial AND wp.behavior_state='FORAGING' " +
            "AND wp.population_count >= (wp.carrying_capacity * 3) / 4");

        // Flee window — a population that fled stays fled for two hours, so a chronicle
        // who drove something off finds the ground genuinely quieter for a while.
        jdbc.update("UPDATE wildlife_population SET behavior_state='RESTING' WHERE behavior_state='FLEEING' AND last_simulated_at < ?",
            Timestamp.from(now.minus(Duration.ofHours(2))));

        // Human disturbance decays (#207/#208) — a place worked hard grows quiet again when it is left alone.
        // Only fold in whole hours of decay (4/hour), so frequent ticks do not reset the clock and lose the accrual.
        Timestamp nowTs = Timestamp.from(now);
        jdbc.update("UPDATE chunk_disturbance SET " +
            "disturbance_level = GREATEST(0, disturbance_level - FLOOR(EXTRACT(EPOCH FROM (?::timestamptz - last_updated_at))/3600.0 * 4)::int), " +
            "last_updated_at = ? " +
            "WHERE disturbance_level > 0 AND EXTRACT(EPOCH FROM (?::timestamptz - last_updated_at)) >= 3600", nowTs, nowTs, nowTs);

        // Disturbance avoidance (#207/#209, first response tier) — wildlife that live on ground still marked by a
        // fight, a kill, or felling quit it and range off while it stays disturbed. It is a transient avoidance
        // re-derived each tick, never a despawn or a teleport: once the disturbance decays below the threshold
        // the cascade stops firing and the population returns to its baseline behaviour. Applied last so it wins
        // over the base state and the flee-window recovery above.
        jdbc.update("UPDATE wildlife_population wp SET behavior_state='FLEEING' " +
            "FROM ecology_site site JOIN chunk_disturbance cd ON cd.chunk_id=site.chunk_id " +
            "WHERE site.id=wp.site_id AND wp.population_count>0 AND cd.disturbance_level >= 40");
    }

    @Transactional
    void seedExistingSites(Instant now) {
        List<Site> sites = jdbc.query("SELECT id, site_kind FROM ecology_site WHERE site_category = 'WILDLIFE' AND NOT EXISTS (SELECT 1 FROM wildlife_population wp WHERE wp.site_id = ecology_site.id)", (rs, row) -> new Site(rs.getObject(1, UUID.class), rs.getString(2)));
        for (Site site : sites) {
            Profile profile = profileFor(site.kind().toLowerCase());
            jdbc.update("INSERT INTO wildlife_population (id, site_id, species_key, ecological_role, activity_cycle, population_count, carrying_capacity, behavior_state, last_simulated_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'RESTING', ?)", UUID.randomUUID(), site.id(), profile.species(), profile.role(), profile.cycle(), profile.initial(), profile.capacity(), Timestamp.from(now));
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
    private long reproductionIntervalHours(String role) { return "CARNIVORE".equals(role) ? 24L * 28 : "OMNIVORE".equals(role) ? 24L * 18 : 24L * 10; }
    private String behaviorFor(String role, int hour, String weather, String cycle) {
        if ("STORM".equals(weather)) return "SHELTERING";
        boolean active = switch (cycle) { case "NOCTURNAL" -> hour >= 19 || hour < 5; case "CREPUSCULAR" -> (hour >= 5 && hour <= 8) || (hour >= 17 && hour <= 20); default -> hour >= 7 && hour <= 18; };
        return active ? ("CARNIVORE".equals(role) ? "HUNTING" : "FORAGING") : "RESTING";
    }
    private record Site(UUID id, String kind) { }
    private record Profile(String species, String role, String cycle, int initial, int capacity) { }
    public record PopulationView(String speciesKey, String ecologicalRole, String activityCycle, int populationCount, int carryingCapacity, String behaviorState, String siteKind) { }
}
