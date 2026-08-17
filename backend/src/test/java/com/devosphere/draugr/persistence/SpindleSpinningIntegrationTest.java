package com.devosphere.draugr.persistence;

import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drop-spindle usefulness regression (dead-craft audit #257, EPIC #123). A drop spindle was craftable but read
 * by nothing — spinning fleece into yarn drew the thread out the same whether or not you had one. executeProcess
 * now biases the spinning yield toward its high end when a drop spindle is carried (never gating the work — a
 * Chronicle can spin by hand — only improving it), so the spindle finally earns its keep.
 *
 * <p>Proven over many spins: with a drop spindle a Chronicle yields more yarn from the same fleece than by
 * hand. The bias lifts the chance of the high yield, a wide margin over 120 spins. Skips gracefully without Docker.
 */
@SpringBootTest
class SpindleSpinningIntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int yarnCount(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='wool_yarn' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
    }

    /** Spin {@code runs} times, seeding a fleece's worth of wool tufts each time; returns the yarn produced. */
    private int spin(UUID chronicle, UUID chunk, Instant now, int runs) {
        int before = yarnCount(chronicle);
        for (int i = 0; i < runs; i++) {
            for (int t = 0; t < 6; t++) items.createCarriedItem(chronicle, "wool_tuft", "Wool tuft", now, "TEST_SEED");
            items.executeProcess(chronicle, chunk, "spin_wool_yarn", "spin wool into yarn", now);
        }
        return yarnCount(chronicle) - before;
    }

    @Test
    void aDropSpindleYieldsMoreYarnThanSpinningByHand() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject(
                "SELECT id FROM world_chunk WHERE biome='TEMPERATE_FOREST' ORDER BY grid_y, grid_x LIMIT 1", UUID.class);
        jdbc.update("UPDATE world_object SET current_location_id=? WHERE id=?", chunk, chronicle);
        Instant now = ticks.current().simulatedAt();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        int runs = 120;
        // Spun by hand — the baseline yield.
        int byHand = spin(chronicle, chunk, now, runs);

        // With a drop spindle to hand — the same fleece, drawn out more evenly, wasting less.
        items.createCarriedItem(chronicle, "drop_spindle", "Drop spindle", now, "TEST_SEED");
        int withSpindle = spin(chronicle, chunk, now, runs);

        assertTrue(withSpindle > byHand,
                () -> "a drop spindle must yield more yarn than spinning by hand over the same fleece (spindle=" + withSpindle + ", byHand=" + byHand + ") — the spindle must be USED (#257)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
