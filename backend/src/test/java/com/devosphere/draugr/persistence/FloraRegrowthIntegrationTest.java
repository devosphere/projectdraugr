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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Woodland-regrowth regression (EPIC #200 forestry — living stands → harvest → use → regrowth). Felling and
 * gathering depleted chunk_flora but nothing ever regrew it: a wood cut once stayed bare forever, and each
 * species' regrowth_days was a dead-read. Now a stand recovers toward its natural abundance over its regrowth
 * period, so a lightly-worked wood comes back while an over-cut one stays thin.
 *
 * <p>Proven deterministically: a stand cut long ago regrows toward its capacity, a stand cut recently does not
 * yet, and a stand's capacity auto-tracks its own richness. Skips gracefully without Docker.
 */
@SpringBootTest
class FloraRegrowthIntegrationTest {

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

    private void seedStand(UUID chunk, String flora, int quantity, int capacity, Instant lastHarvested) {
        jdbc.update("INSERT INTO chunk_flora (chunk_id, flora_key, quantity, last_harvested_at, capacity) VALUES (?,?,?,?,?)",
                chunk, flora, quantity, lastHarvested == null ? null : Timestamp.from(lastHarvested), capacity);
    }

    private int qty(UUID chunk, String flora) {
        return jdbc.queryForObject("SELECT quantity FROM chunk_flora WHERE chunk_id=? AND flora_key=?", Integer.class, chunk, flora);
    }

    @Test
    void aWoodRecoversTowardItsAbundanceOverItsRegrowthPeriod() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        UUID chunk = jdbc.queryForObject("SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("DELETE FROM chunk_flora WHERE chunk_id=?", chunk);
        Instant base = ticks.current().simulatedAt();

        // An oak stand cut down to one, long ago (two of its 365-day regrowth periods): it recovers toward its
        // natural abundance of three.
        seedStand(chunk, "oak", 1, 3, base.minus(Duration.ofDays(800)));
        // A birch cut to one only ten days ago: far short of its 300-day period, so it has not come back yet.
        seedStand(chunk, "birch", 1, 3, base.minus(Duration.ofDays(10)));
        // A rich pine stand whose capacity was never recorded (default 1): it must auto-track its own richness.
        seedStand(chunk, "pine", 5, 1, null);

        sim.advanceTo(base);

        assertEquals(3, qty(chunk, "oak"), "a wood cut long ago must recover toward its natural abundance (#200)");
        assertEquals(1, qty(chunk, "birch"), "a wood cut only recently must not have regrown yet (#200)");
        assertEquals(5, qty(chunk, "pine"), "a rich stand must keep its abundance");
        assertEquals(5, (int) jdbc.queryForObject("SELECT capacity FROM chunk_flora WHERE chunk_id=? AND flora_key='pine'", Integer.class, chunk),
                "a stand's capacity must auto-track its own richness, so it recovers to that, not a flat cap (#200)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
