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
 * Long-play disturbance-cycle regression (EPIC #207, story #214). Exercises the whole living-habitat response
 * over a long time advance and asserts the persistence and integrity guarantees the epic demands: identity is
 * kept across migration, no population despawns silently or drifts to an invalid count, history accrues, empty
 * ground recovers only from a real source, and the Persistent State Auditor stays clean at every stage.
 *
 * <p>The arc: a population on heavily disturbed ground migrates to viable country (keeping its identity); the
 * world then advances thirty days, during which the disturbance fully decays and vacated ground is recolonised
 * from a healthy neighbour. Skips gracefully without Docker.
 */
@SpringBootTest
class LongPlayDisturbanceCycleIntegrationTest {

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

    private void assertWorldSound(String stage) {
        assertTrue(auditor.inspect().consistent(), () -> stage + ": Auditor must stay clean: " + auditor.inspect().violations());
        Integer broken = jdbc.queryForObject(
                "SELECT COUNT(*) FROM wildlife_population WHERE population_count IS NULL OR population_count < 0 OR population_count > carrying_capacity", Integer.class);
        assertEquals(0, broken, stage + ": no population may drift to an invalid count");
    }

    private UUID fabricatePopulation(UUID chunk, UUID world, String species, int count, Instant at) {
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Range',300)", site, world, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,?,'CARNIVORE','CREPUSCULAR',?,5,'RESTING',?)", pop, site, species, count, Timestamp.from(at));
        return site;
    }

    @Test
    void theWholeDisturbanceCycleHoldsOverLongPlay() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        java.util.Map<String,Object> row = jdbc.queryForMap(
                "SELECT c.id AS here, c.world_id AS world, c.grid_x AS gx, c.grid_y AS gy FROM world_chunk c " +
                "WHERE c.biome='TEMPERATE_FOREST' AND EXISTS (SELECT 1 FROM world_chunk n WHERE n.world_id=c.world_id " +
                "AND n.biome='TEMPERATE_FOREST' AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1) " +
                "ORDER BY c.grid_y, c.grid_x LIMIT 1");
        UUID c = (UUID) row.get("here"); UUID world = (UUID) row.get("world");
        int gx = (int) row.get("gx"), gy = (int) row.get("gy");
        UUID n = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE world_id=? AND biome='TEMPERATE_FOREST' AND abs(grid_x-?)+abs(grid_y-?)=1 ORDER BY grid_y, grid_x LIMIT 1",
                UUID.class, world, gx, gy);
        Instant base = Instant.parse("2026-06-01T12:00:00Z");
        assertWorldSound("baseline");

        // A population on the ground, and a healthy same-species population on a connected neighbour (both a
        // migration destination and, later, a dispersal source).
        UUID pSite = fabricatePopulation(c, world, "gray_wolf", 4, base);
        UUID pPop = jdbc.queryForObject("SELECT id FROM wildlife_population WHERE site_id=?", UUID.class, pSite);
        fabricatePopulation(n, world, "gray_wolf", 4, base);

        // Phase 1 — heavy disturbance drives the population to migrate. Its identity is kept.
        wildlife.recordDisturbance(c, "PREDATION", 90, base);
        sim.advanceTo(base.plus(Duration.ofHours(2)));
        UUID afterChunk = jdbc.queryForObject("SELECT chunk_id FROM ecology_site WHERE id=?", UUID.class, pSite);
        assertNotEquals(c, afterChunk, "the disturbed population must migrate off the ground");
        assertEquals(4, (int) jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, pPop),
                "migration must keep the population's identity and count");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='MIGRATED'", Integer.class, pSite) >= 1,
                "the migration must be kept in history");
        assertWorldSound("after migration");

        // The ground the population left is now a vacant range (an empty site of that species).
        UUID vacated = fabricatePopulation(c, world, "gray_wolf", 0, base);

        // Phase 2 — thirty days pass with no further disturbance. It fully decays, and the vacant ground is
        // recolonised by dispersal from the healthy neighbour.
        sim.advanceTo(base.plus(Duration.ofDays(30)));
        Integer level = jdbc.query("SELECT disturbance_level FROM chunk_disturbance WHERE chunk_id=?", rs -> rs.next() ? rs.getInt(1) : null, c);
        assertTrue(level == null || level == 0, () -> "left alone, the disturbance must fully decay over thirty days (was " + level + ")");
        assertTrue(jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE site_id=?", Integer.class, vacated) >= 1,
                "vacated ground must be recolonised from the healthy neighbour over long play (#212/#214)");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='RECOLONISED'", Integer.class, vacated) >= 1,
                "the recolonisation must be kept in history");
        // The migrated population still stands somewhere — it was never despawned across the long advance.
        assertTrue(jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, pPop) > 0,
                "the migrated population must persist across long play, never silently despawned");
        assertWorldSound("after long play");
    }
}
