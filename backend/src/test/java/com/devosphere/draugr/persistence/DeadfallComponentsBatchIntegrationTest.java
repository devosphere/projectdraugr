package com.devosphere.draugr.persistence;

import com.devosphere.draugr.action.ChronicleActionService;
import com.devosphere.draugr.audit.PersistentStateAuditor;
import com.devosphere.draugr.chronicle.ChronicleService;
import com.devosphere.draugr.item.PhysicalItemService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story #93 catalogue batch 9 — deadfall trap components. Proves the chain end-to-end: the weight stone and trigger
 * are crafted, then — with no raw stone or branch left in hand — a deadfall is built FROM the components. Setting a
 * trap is deterministic, so the outcome is asserted directly. Skips without Docker.
 */
@SpringBootTest
class DeadfallComponentsBatchIntegrationTest {

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
    @Autowired PersistentStateAuditor auditor;
    @Autowired JdbcTemplate jdbc;

    private int owned(UUID chronicle, String key) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_instance i JOIN world_object w ON w.id=i.object_id " +
            "WHERE i.item_key=? AND w.current_owner_id=? AND w.lifecycle_state='ACTIVE'", Integer.class, key, chronicle);
        return n == null ? 0 : n;
    }

    @Test
    void aDeadfallIsBuiltFromCraftedComponents() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "dry_branch", "Dry branch", now, "TEST_FIXTURE");

        var shape = actions.resolve("shape a deadfall weight stone");
        assertEquals("SUCCEEDED", shape.outcome(), () -> "shaping a deadfall weight stone must succeed: " + shape.perception());
        var carve = actions.resolve("carve a deadfall trigger");
        assertEquals("SUCCEEDED", carve.outcome(), () -> "carving a deadfall trigger must succeed: " + carve.perception());
        assertTrue(owned(chronicle, "deadfall_weight_stone") >= 1 && owned(chronicle, "deadfall_trigger") >= 1,
            "both deadfall components must be in hand");
        // The raw field stone and branch were both consumed crafting the parts — a deadfall now builds from the parts.
        assertEquals(0, owned(chronicle, "field_stone"), "no raw stone left — the deadfall must come from the components");
        assertEquals(0, owned(chronicle, "dry_branch"), "no raw branch left");

        var set = actions.resolve("set a deadfall trap");
        assertEquals("SUCCEEDED", set.outcome(), () -> "building a deadfall from its components must succeed: " + set.perception());
        assertEquals(0, owned(chronicle, "deadfall_weight_stone"), "the deadfall consumes the weight stone");
        assertEquals(0, owned(chronicle, "deadfall_trigger"), "the deadfall consumes the trigger");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
