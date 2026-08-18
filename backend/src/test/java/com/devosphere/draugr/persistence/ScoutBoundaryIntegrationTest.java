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

        // Find a wildlife population whose chunk has a west neighbour (so the Chronicle can stand due west of it,
        // making the predator lie to the east). The seeded world always carries populations.
        Map<String,Object> row = jdbc.query(
                "SELECT wp.id AS pop, c.world_id AS world, c.grid_x AS gx, c.grid_y AS gy " +
                "FROM wildlife_population wp JOIN ecology_site es ON es.id=wp.site_id JOIN world_chunk c ON c.id=es.chunk_id " +
                "WHERE EXISTS (SELECT 1 FROM world_chunk w WHERE w.world_id=c.world_id AND w.grid_x=c.grid_x-1 AND w.grid_y=c.grid_y) " +
                "LIMIT 1",
                rs -> rs.next() ? Map.of("pop", rs.getObject("pop", UUID.class), "world", rs.getObject("world", UUID.class),
                        "gx", rs.getInt("gx"), "gy", rs.getInt("gy")) : null);
        Assumptions.assumeTrue(row != null, "seeded world must carry a population with a west neighbour");

        UUID popId = (UUID) row.get("pop"); UUID world = (UUID) row.get("world");
        int px = (int) row.get("gx"), py = (int) row.get("gy");
        // Make that population an actively hunting carnivore — the danger the scout must sense.
        jdbc.update("UPDATE wildlife_population SET ecological_role='CARNIVORE', behavior_state='HUNTING' WHERE id=?", popId);
        // Stand the Chronicle due west of it, so the predator lies to the east.
        UUID chronicleChunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE world_id=? AND grid_x=? AND grid_y=?", UUID.class, world, px - 1, py);
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
