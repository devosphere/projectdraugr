package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wild flora, batch 3 (story #85) — the variety batch that takes the catalogue to 100 named flora. It proves the two
 * survival uses this batch adds are both functional end-to-end through the public pipeline:
 *   * FOOD — a sow thistle leaf grows, is gathered, and is eaten (nourishment, terminal).
 *   * MEDICINE — a self-heal leaf grows, is gathered, and is pounded into an herbal_poultice (the wound-dressing
 *     material bindWound already reads), so a fresh herb becomes real wound care, not a token.
 * The rest of the batch is gated reachable by the routing/dead-end checks CI enforces. Skips without Docker.
 */
@SpringBootTest
class WildFloraBatch3IntegrationTest {

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
    @Autowired PhysicalItemService items;
    @Autowired SimulationTickService ticks;
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int owned(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }

    private UUID awakenOnGrassland() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        UUID chunk = jdbc.queryForObject("SELECT current_location_id FROM world_object WHERE id=?", UUID.class, chronicle);
        jdbc.update("UPDATE world_chunk SET biome='GRASSLAND' WHERE id=?", chunk); // self-heal and sow thistle both grow in grassland
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        return chronicle;
    }

    @Test
    void aSowThistleLeafIsGatheredAndEaten() {
        UUID chronicle = awakenOnGrassland();

        var gather = actions.resolve("gather the sow thistle leaf here");
        assertEquals("SUCCEEDED", gather.outcome(), () -> "gathering a sow thistle leaf must succeed: " + gather.perception());
        assertTrue(owned(chronicle, "sow_thistle_leaf") >= 1, () -> "gathering yields a sow thistle leaf, got " + owned(chronicle, "sow_thistle_leaf"));

        int before = owned(chronicle, "sow_thistle_leaf");
        var eat = actions.resolve("eat the sow thistle leaf");
        assertEquals("SUCCEEDED", eat.outcome(), () -> "eating the sow thistle leaf must succeed: " + eat.perception());
        assertEquals(before - 1, owned(chronicle, "sow_thistle_leaf"), "eating consumes the leaf — it is a real food");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }

    @Test
    void aSelfHealLeafBecomesAWoundPoultice() {
        UUID chronicle = awakenOnGrassland();

        // gather enough self-heal for a poultice (the process pounds 2 leaves)
        actions.resolve("gather the self heal leaf here");
        actions.resolve("gather the self heal leaf here");
        assertTrue(owned(chronicle, "self_heal_leaf") >= 2,
            () -> "gathering yields self-heal leaves, got " + owned(chronicle, "self_heal_leaf"));

        int poulticesBefore = owned(chronicle, "herbal_poultice");
        int leavesBefore = owned(chronicle, "self_heal_leaf");
        var pound = actions.resolve("pound a self heal poultice");
        assertEquals("SUCCEEDED", pound.outcome(), () -> "pounding a self-heal poultice must succeed: " + pound.perception());
        assertEquals(poulticesBefore + 1, owned(chronicle, "herbal_poultice"),
            "pounding self-heal produces an herbal poultice — a fresh herb becomes real wound care");
        assertEquals(leavesBefore - 2, owned(chronicle, "self_heal_leaf"),
            "the poultice consumes exactly the two leaves it pounds");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
