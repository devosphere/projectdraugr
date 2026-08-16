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
 * Mortar-and-pestle usefulness regression (dead-craft audit #257, EPIC #123). A stone mortar and pestle were
 * craftable but read by nothing — grinding pounded the same whether or not you had them. executeProcess now
 * biases a grinding yield toward its high end when a mortar and pestle are carried (never gating the work —
 * bare hands still grind — only improving it), so the pair finally earns its keep.
 *
 * <p>Proven over many grinds: with a mortar and pestle a Chronicle yields more pigment from the same ochre
 * than bare-handed. The bias lifts the chance of the high yield from ~50% to ~75%, a wide margin over 120
 * grinds. Skips gracefully without Docker.
 */
@SpringBootTest
class GrindingSetIntegrationTest {

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

    private int pigmentCount(UUID chronicle) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
                "WHERE i.item_key='pigment' AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, chronicle);
    }

    /** Grind pigment {@code runs} times, seeding one measure of ochre each time; returns the pigment yielded. */
    private int grind(UUID chronicle, UUID chunk, Instant now, int runs) {
        int before = pigmentCount(chronicle);
        for (int i = 0; i < runs; i++) {
            items.createCarriedItem(chronicle, "ochre_red", "Red ochre", now, "TEST_SEED");
            items.executeProcess(chronicle, chunk, "grind_pigment", "grind pigment", now);
        }
        return pigmentCount(chronicle) - before;
    }

    @Test
    void aMortarAndPestleYieldsMorePigmentThanBareHands() {
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
        // Bare-handed grinding (pounding) — the baseline yield.
        int bareYield = grind(chronicle, chunk, now, runs);

        // With a stone mortar and pestle to hand — the same ochre, ground finer with less waste.
        items.createCarriedItem(chronicle, "stone_mortar", "Stone mortar", now, "TEST_SEED");
        items.createCarriedItem(chronicle, "stone_pestle", "Stone pestle", now, "TEST_SEED");
        int setYield = grind(chronicle, chunk, now, runs);

        assertTrue(setYield > bareYield,
                () -> "a mortar and pestle must yield more pigment than bare hands over the same grinds (set=" + setYield + ", bare=" + bareYield + ") — the pair must be USED (#257)");

        assertTrue(auditor.inspect().consistent(), () -> "world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
