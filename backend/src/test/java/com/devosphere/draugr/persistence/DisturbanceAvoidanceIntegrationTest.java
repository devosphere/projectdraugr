package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.ecology.WildlifeEncounterService;
import com.devosphere.draugr.ecology.WildlifeSimulationService;
import com.devosphere.draugr.world.genesis.WorldEcologyGenesisService;
import com.devosphere.draugr.world.genesis.WorldGenesisService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Habitat-disturbance avoidance regression (EPIC #207, stories #208/#209, first slice). A place worked hard — a
 * fight or kill, a felled tree — now accrues a measured, decaying, chunk-bounded disturbance, and the wildlife
 * that live there perceive it and quit the ground while it stays disturbed (behaviour FLEEING). It is a
 * transient avoidance re-derived each tick, never a despawn or teleport: once the disturbance decays below the
 * threshold the population returns to its baseline. Proven end to end: a disturbed population flees, and after
 * the disturbance decays over time it no longer flees — with no population deleted or moved.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class DisturbanceAvoidanceIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for this integration test");
        System.setProperty("java.awt.headless", "true");
        postgres.start();
    }

    @AfterAll
    static void stopDatabase() { if (postgres.isRunning()) postgres.stop(); }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired WorldGenesisService worldGenesis;
    @Autowired WorldEcologyGenesisService ecology;
    @Autowired WildlifeSimulationService sim;
    @Autowired WildlifeEncounterService wildlife;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void disturbedWildlifeFleesThenReturnsAsItDecays() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        UUID world = jdbc.queryForObject("SELECT world_id FROM world_chunk WHERE id=?", UUID.class, chunk);
        Instant base = Instant.parse("2026-06-01T12:00:00Z");
        Timestamp baseTs = Timestamp.from(base);

        // Fabricate a quiet browsing herbivore population on this ground (count below the territorial threshold,
        // so its base behaviour is a plain FORAGING/RESTING and only the disturbance cascade can make it flee).
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Browsing range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Browsing range',300)", site, world, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','HERBIVORE','DIURNAL',2,5,'FORAGING',?)", pop, site, baseTs);

        // A fight on this ground marks it — the disturbance rises well past the avoidance threshold.
        wildlife.recordDisturbance(chunk, "PREDATION", 60, base);
        Integer level = jdbc.queryForObject("SELECT disturbance_level FROM chunk_disturbance WHERE chunk_id=?", Integer.class, chunk);
        assertEquals(60, level, "the disturbance must be measured and recorded on the chunk");

        // One hour on, the ground is still disturbed — the wildlife that live here quit it.
        sim.advanceTo(base.plus(Duration.ofHours(1)));
        assertEquals("FLEEING", behaviorOf(pop), "wildlife on freshly disturbed ground must quit it (#207/#209)");

        // Left alone, the disturbance decays over the hours and the ground grows quiet again — the population
        // returns to its baseline rather than staying gone (no despawn, no teleport).
        sim.advanceTo(base.plus(Duration.ofHours(40)));
        Integer decayed = jdbc.queryForObject("SELECT disturbance_level FROM chunk_disturbance WHERE chunk_id=?", Integer.class, chunk);
        assertTrue(decayed < 40, () -> "disturbance must decay when the ground is left alone (was " + decayed + ")");
        assertNotEquals("FLEEING", behaviorOf(pop), "once the disturbance decays the population must return to its baseline, not stay fled");

        // The population still stands — avoidance is transient behaviour, never a deletion.
        Integer count = jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, pop);
        assertTrue(count != null && count > 0, "the disturbed population must never be despawned (#207 safeguard)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    private String behaviorOf(UUID pop) {
        return jdbc.queryForObject("SELECT behavior_state FROM wildlife_population WHERE id=?", String.class, pop);
    }
}
