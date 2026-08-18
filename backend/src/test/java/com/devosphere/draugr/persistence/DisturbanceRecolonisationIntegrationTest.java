package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recolonisation regression (EPIC #207, story #212). Ground emptied by migration or decline, once quiet again, is
 * repopulated by dispersal from a neighbouring healthy population of the SAME species — a real spread from a
 * source along a connected, same-biome route, one founder at a time, never spontaneous generation. This closes
 * the response loop so a place recovers rather than staying dead. Proven end to end: an empty site with a healthy
 * same-species neighbour recovers to one founder, a RECOLONISED event is logged, and the source is not depleted.
 * (The no-spontaneous-return case — an empty site with NO source stays empty — is covered by the decline test.)
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class DisturbanceRecolonisationIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void emptyGroundIsRecolonisedByDispersalFromAHealthyNeighbour() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        java.util.Map<String,Object> row = jdbc.queryForMap(
                "SELECT c.id AS here, c.world_id AS world, c.grid_x AS gx, c.grid_y AS gy FROM world_chunk c " +
                "WHERE c.biome='TEMPERATE_FOREST' AND EXISTS (SELECT 1 FROM world_chunk n WHERE n.world_id=c.world_id " +
                "AND n.biome='TEMPERATE_FOREST' AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1) " +
                "ORDER BY c.grid_y, c.grid_x LIMIT 1");
        UUID empty = (UUID) row.get("here"); UUID world = (UUID) row.get("world");
        int gx = (int) row.get("gx"), gy = (int) row.get("gy");
        UUID sourceChunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE world_id=? AND biome='TEMPERATE_FOREST' AND abs(grid_x-?)+abs(grid_y-?)=1 ORDER BY grid_y, grid_x LIMIT 1",
                UUID.class, world, gx, gy);
        Instant base = Instant.parse("2026-06-01T12:00:00Z");
        Timestamp baseTs = Timestamp.from(base);

        // Empty, quiet ground with a persistent (extinct) population row of a species.
        UUID emptySite = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Emptied range',?)", emptySite, empty);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Emptied range',300)", emptySite, world, empty);
        UUID emptyPop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'gray_wolf','CARNIVORE','CREPUSCULAR',0,5,'RESTING',?)", emptyPop, emptySite, baseTs);

        // A healthy population of the SAME species on the connected, same-biome neighbour — the dispersal source.
        UUID srcSite = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Wolf range',?)", srcSite, sourceChunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Wolf range',300)", srcSite, world, sourceChunk);
        UUID srcPop = UUID.randomUUID();
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'gray_wolf','CARNIVORE','CREPUSCULAR',4,5,'RESTING',?)", srcPop, srcSite, baseTs);

        sim.advanceTo(base.plus(Duration.ofHours(1)));

        assertEquals(1, (int) jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, emptyPop),
                "empty ground with a healthy same-species neighbour must be recolonised by a founder (#212)");
        Integer events = jdbc.queryForObject(
                "SELECT COUNT(*) FROM object_transition WHERE object_id=? AND transition_type='RECOLONISED'", Integer.class, emptySite);
        assertTrue(events != null && events >= 1, "recolonisation must be kept in history (#208/#212)");
        assertEquals(4, (int) jdbc.queryForObject("SELECT population_count FROM wildlife_population WHERE id=?", Integer.class, srcPop),
                "the dispersal source's own numbers must not be depleted by seeding the empty ground");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
