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
 * Wooden-mallet usefulness regression (dead-craft audit #257, EPIC #123). A wooden mallet was craftable but
 * read by nothing — a log rived to the same yield of planks whether or not you had a mallet to drive the wedge.
 * executeProcess now biases a riving/splitting yield toward its high end when a wooden mallet is carried
 * (split_planks/rive_shakes/rive_bow_stave/split_wedges): even, controlled blows drive the froe cleaner, so
 * less is lost to run-out and more usable stock comes off the same wood. The axe still does the work — the
 * mallet only wastes less — the same targeted assist a workstation gives.
 *
 * <p>Proven over many splits: with a mallet a Chronicle rives more planks from the same logs than by hand.
 * Skips gracefully without Docker.
 */
@SpringBootTest
class WoodenMalletRivingIntegrationTest {

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

    private int plankCount(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='timber_plank' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
    }

    /** Rive {@code runs} times, seeding an oak log each time; returns the planks produced. */
    private int rive(UUID chronicle, UUID chunk, Instant now, int runs) {
        int before = plankCount(chronicle);
        for (int i = 0; i < runs; i++) {
            items.createCarriedItem(chronicle, "oak_log", "Oak log", now, "TEST_SEED");
            items.executeProcess(chronicle, chunk, "split_planks", "split planks from the log", now);
        }
        return plankCount(chronicle) - before;
    }

    @Test
    void aWoodenMalletRivesMorePlanksThanSplittingByHand() {
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

        // An axe does the splitting (split_planks is AXE-gated) in both cohorts.
        items.createCarriedItem(chronicle, "stone_axe", "Stone axe", now, "TEST_SEED");

        int runs = 120;
        // Rived by hand (axe only) — the baseline yield (2..4 planks a log, averaging near 3).
        int byHand = rive(chronicle, chunk, now, runs);

        // With a mallet to drive the wedge, the yield biases toward its high end.
        items.createCarriedItem(chronicle, "wooden_mallet", "Wooden mallet", now, "TEST_SEED");
        int withMallet = rive(chronicle, chunk, now, runs);

        assertTrue(withMallet > byHand,
                () -> "a wooden mallet must rive more planks than splitting by hand over " + runs + " logs " +
                      "(mallet=" + withMallet + ", hand=" + byHand + ") — the mallet must be USED (#257)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
