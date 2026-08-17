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
 * Drying-mat usefulness regression (dead-craft audit #257, EPIC #123). A drying mat was craftable
 * (weave_drying_mat) but read by nothing — food dried out to the same yield whether or not you had a mat to
 * spread it on. executeProcess now biases a food-drying yield toward its high end when a drying mat is carried
 * (never gating the work — a Chronicle can dry food by hand — only improving it): spread so air passes above
 * and below, less spoils and more is preserved. The mat suits the drying processes only (fish, meat,
 * mushrooms, herbs), the same targeted assist a workstation gives.
 *
 * <p>Proven over many dryings: with a drying mat a Chronicle preserves more dried mushroom from the same
 * mushrooms than by hand. Skips gracefully without Docker.
 */
@SpringBootTest
class DryingMatIntegrationTest {

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

    private int driedCount(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='dried_mushroom' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
    }

    /** Dry {@code runs} times, seeding four chanterelles each time; returns the dried mushrooms produced. */
    private int dry(UUID chronicle, UUID chunk, Instant now, int runs) {
        int before = driedCount(chronicle);
        for (int i = 0; i < runs; i++) {
            for (int m = 0; m < 4; m++) items.createCarriedItem(chronicle, "chanterelle", "Chanterelle", now, "TEST_SEED");
            items.executeProcess(chronicle, chunk, "dry_mushrooms", "dry the mushrooms", now);
        }
        return driedCount(chronicle) - before;
    }

    @Test
    void aDryingMatPreservesMoreDriedFoodThanDryingByHand() {
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
        // Dried by hand — the baseline yield (2..4 per drying, averaging near 3).
        int byHand = dry(chronicle, chunk, now, runs);

        // With a drying mat, the yield biases toward its high end.
        items.createCarriedItem(chronicle, "drying_mat", "Drying mat", now, "TEST_SEED");
        int withMat = dry(chronicle, chunk, now, runs);

        assertTrue(withMat > byHand,
                () -> "a drying mat must preserve more dried food than drying by hand over " + runs + " dryings " +
                      "(mat=" + withMat + ", hand=" + byHand + ") — the mat must be USED (#257)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
