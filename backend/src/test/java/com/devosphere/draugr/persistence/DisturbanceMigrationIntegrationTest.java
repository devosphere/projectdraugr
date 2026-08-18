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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Habitat-disturbance migration regression (EPIC #207, story #209 second tier). Where a chunk's disturbance
 * stays heavy, avoidance is not enough: a population shifts its whole range to quieter ground — a real, in-habitat
 * move to a cardinally-connected same-biome neighbour whose own disturbance is low, kept in history, with the
 * population's identity and count preserved. Never a jump into unrelated country, and never a despawn. Proven end
 * to end: a heavily disturbed population is found, after the tick, at a connected same-biome neighbour, with a
 * MIGRATED transition logged and its count intact.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class DisturbanceMigrationIntegrationTest {

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
    void heavilyDisturbedWildlifeMigratesToAConnectedViableNeighbour() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        // A forest chunk that has a same-biome cardinal neighbour to migrate into (a real viable route).
        Map<String,Object> row = jdbc.queryForMap(
                "SELECT c.id AS here, c.world_id AS world, c.grid_x AS gx, c.grid_y AS gy FROM world_chunk c " +
                "WHERE c.biome='TEMPERATE_FOREST' AND EXISTS (SELECT 1 FROM world_chunk n WHERE n.world_id=c.world_id " +
                "AND n.biome='TEMPERATE_FOREST' AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1) " +
                "ORDER BY c.grid_y, c.grid_x LIMIT 1");
        UUID chunk = (UUID) row.get("here"); UUID world = (UUID) row.get("world");
        Instant base = Instant.parse("2026-06-01T12:00:00Z");
        Timestamp baseTs = Timestamp.from(base);

        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Browsing range',?)", site, chunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Browsing range',300)", site, world, chunk);
        UUID pop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','HERBIVORE','DIURNAL',3,5,'FORAGING',?)", pop, site, baseTs);

        // Heavy, sustained disturbance on this ground — past the migration threshold even after a little decay.
        wildlife.recordDisturbance(chunk, "PREDATION", 90, base);

        sim.advanceTo(base.plus(Duration.ofHours(1)));

        // The range has physically shifted to a connected, same-biome neighbour — not the disturbed chunk.
        UUID newChunk = jdbc.queryForObject("SELECT chunk_id FROM ecology_site WHERE id=?", UUID.class, site);
        assertNotEquals(chunk, newChunk, "a heavily disturbed population must shift its range off the disturbed ground (#209)");
        assertEquals("TEMPERATE_FOREST", jdbc.queryForObject("SELECT biome FROM world_chunk WHERE id=?", String.class, newChunk),
                "migration must stay within the same biome — no jump into unrelated country (#207 safeguard)");
        Integer manhattan = jdbc.queryForObject(
                "SELECT abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y) FROM world_chunk n JOIN world_chunk c ON c.id=? WHERE n.id=?",
                Integer.class, chunk, newChunk);
        assertEquals(1, manhattan, "migration must be to a cardinally-connected neighbour, not a teleport (#207 safeguard)");

        // The site's world_object followed its range, and the move is kept in history.
        assertEquals(newChunk, jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, site),
                "the site's world_object must be relocated with its range");
        Integer migrations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='MIGRATED'", Integer.class, site);
        assertTrue(migrations != null && migrations >= 1, "the migration must be kept in immutable history (#208)");

        // Identity and count are intact — migration is movement, never deletion or duplication.
        Integer count = jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, pop);
        assertTrue(count != null && count > 0, "the migrated population must never be despawned (#207 safeguard)");
        Integer copies = jdbc.queryForObject("SELECT COUNT(*) FROM wildlife_population WHERE id=?", Integer.class, pop);
        assertEquals(1, copies, "the population must not be duplicated by migration");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
