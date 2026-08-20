package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.ecology.WildlifeSimulationService;
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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Clear-cut permanence and recolonisation regression (EPIC #200 forestry — #201 stewardship). Regrowth (#308)
 * lets a worked wood recover, but a stand cut to NOTHING has lost its own seed source: it must not simply grow
 * back, or clear-cutting would carry no cost. Now a cleared stand (quantity 0) stays bare — the real price of
 * clear-cutting — UNLESS a healthy same-species stand on an adjacent chunk seeds it back, when it recolonises to a
 * single sapling and its regrowth clock restarts. Mirrors the wildlife dispersal recolonisation (#207/#212).
 *
 * <p>Proven deterministically: a cleared stand next to a healthy wood comes back; an isolated cleared stand stays
 * bare. Skips gracefully without Docker.
 */
@SpringBootTest
class FloraClearCutRecolonisationIntegrationTest {

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
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private void oak(UUID chunk, int quantity, int capacity, Instant lastHarvested) {
        jdbc.update("INSERT INTO chunk_flora (chunk_id, flora_key, quantity, last_harvested_at, capacity) VALUES (?,?,?,?,?)",
                chunk, "oak", quantity, lastHarvested == null ? null : Timestamp.from(lastHarvested), capacity);
    }

    private int oakQty(UUID chunk) {
        Integer v = jdbc.query("SELECT quantity FROM chunk_flora WHERE chunk_id=? AND flora_key='oak'", rs -> rs.next() ? rs.getInt(1) : null, chunk);
        return v == null ? -1 : v;
    }

    @Test
    void aClearCutStandStaysBareUnlessANeighbourReseedsIt() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        // A chunk with a cardinal neighbour (the seed source), and a third chunk two or more steps away (isolated).
        Map<String,Object> row = jdbc.queryForMap(
                "SELECT c.id here, c.world_id world, c.grid_x gx, c.grid_y gy FROM world_chunk c " +
                "WHERE EXISTS (SELECT 1 FROM world_chunk n WHERE n.world_id=c.world_id AND abs(n.grid_x-c.grid_x)+abs(n.grid_y-c.grid_y)=1) " +
                "ORDER BY c.grid_y, c.grid_x LIMIT 1");
        UUID cleared = (UUID) row.get("here"); UUID world = (UUID) row.get("world");
        int gx = (int) row.get("gx"), gy = (int) row.get("gy");
        UUID source = jdbc.queryForObject("SELECT id FROM world_chunk WHERE world_id=? AND abs(grid_x-?)+abs(grid_y-?)=1 ORDER BY grid_y, grid_x LIMIT 1", UUID.class, world, gx, gy);
        UUID isolated = jdbc.queryForObject("SELECT id FROM world_chunk WHERE world_id=? AND abs(grid_x-?)+abs(grid_y-?)>=3 AND id<>? ORDER BY grid_y, grid_x LIMIT 1", UUID.class, world, gx, gy, cleared);
        Instant base = ticks.current().simulatedAt();

        // Clean slate for oak: only the source stand is healthy.
        jdbc.update("DELETE FROM chunk_flora WHERE flora_key='oak'");
        oak(source, 5, 5, null);                                      // a healthy wood, a seed source
        oak(cleared, 0, 3, base.minus(Duration.ofDays(800)));        // clear-cut, next to the source
        oak(isolated, 0, 3, base.minus(Duration.ofDays(800)));       // clear-cut, with no wood near

        sim.advanceTo(base);

        assertEquals(1, oakQty(cleared), "a cleared stand next to a healthy wood must recolonise from it (#200/#201)");
        assertEquals(0, oakQty(isolated), "an isolated cleared stand must stay bare — clear-cutting is permanent without reseeding (#200/#201)");
        assertEquals(5, oakQty(source), "the seed source itself is unchanged");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
