package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.simulation.SimulationTickService;
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

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Scout-the-boundary regression (#128/#123: grounded evidence before forced contact). A Chronicle could survey
 * a chunk and read the sign on the ground it stands on (OBSERVE/TRACK), but nothing let it sense a predator in
 * the next chunk before walking into it — so escape planning had no evidence to work from and the "at least one
 * route leads away from hostile territory" contract was unobservable. SCOUT now reads the four boundaries for a
 * carnivore one tile out and reports it directionally (never a map). Proven: with a hunting carnivore placed in
 * the chunk to the east, a scout of the boundary names danger to the east.
 *
 * <p>Skips gracefully without Docker.
 */
@SpringBootTest
class ScoutBoundaryIntegrationTest {

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
    @Autowired ChronicleService chronicles;
    @Autowired ChronicleActionService actions;
    @Autowired com.devosphere.draugr.simulation.SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    @Test
    void scoutingTheBoundaryNamesAPredatorInTheNextChunk() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();

        // Stand the Chronicle on a chunk that has an east neighbour, and fabricate a hunting carnivore there — so
        // the scenario is controlled and never depends on the shared world's seeded populations (which other
        // tests mutate). The 28x20 world always has an interior forest chunk with ground to its east.
        Map<String,Object> row = jdbc.queryForMap(
                "SELECT c.id AS here, c.world_id AS world, c.grid_x AS gx, c.grid_y AS gy FROM world_chunk c " +
                "WHERE c.biome='TEMPERATE_FOREST' AND EXISTS (SELECT 1 FROM world_chunk e WHERE e.world_id=c.world_id AND e.grid_x=c.grid_x+1 AND e.grid_y=c.grid_y) " +
                "ORDER BY c.grid_y, c.grid_x LIMIT 1");
        UUID chronicleChunk = (UUID) row.get("here"); UUID world = (UUID) row.get("world");
        int gx = (int) row.get("gx"), gy = (int) row.get("gy");
        UUID eastChunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE world_id=? AND grid_x=? AND grid_y=?", UUID.class, world, gx + 1, gy);
        java.sql.Timestamp ts = java.sql.Timestamp.from(ticks.current().simulatedAt());
        UUID site = UUID.randomUUID();
        jdbc.update("INSERT INTO world_object (id,object_type,display_name,current_location_id) VALUES (?,'ECOLOGY_SITE','Prowler territory',?)", site, eastChunk);
        jdbc.update("INSERT INTO ecology_site (id,world_id,chunk_id,site_category,site_kind,baseline_abundance) VALUES (?,?,?,'WILDLIFE','Prowler territory',400)", site, world, eastChunk);
        jdbc.update("INSERT INTO wildlife_population (id,site_id,species_key,ecological_role,activity_cycle,population_count,carrying_capacity,behavior_state,last_simulated_at) " +
                "VALUES (?,?,'dire_wolf','CARNIVORE','DIURNAL',3,5,'HUNTING',?)", UUID.randomUUID(), site, ts);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chronicleChunk, chronicle);

        ChronicleActionService.ActionResult r = actions.resolve("I carefully scout the boundary for danger.");
        assertEquals("SUCCEEDED", r.outcome(), () -> "scouting the boundary must succeed: " + r.perception());
        String p = r.perception().toLowerCase(java.util.Locale.ROOT);
        assertTrue(p.contains("to the east"),
                () -> "a scout must name the predator that lies to the east (#128): " + r.perception());
        assertFalse(p.contains("every side"),
                () -> "the boundary must not read all-clear when a predator lies to the east (#128): " + r.perception());

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
