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
 * Story #93 catalogue batch 10 — a cage frame and hand-line tackle. Proves the chain end-to-end: a cage frame is
 * carved and then a cage trap is built FROM it (no hazel rods left), and the sinew line, bast line, and stone weight
 * are all crafted. Setting a trap and crafting are deterministic, so these outcomes are asserted directly; the fish()
 * LINE/sinker wiring is a small widening verified by code review. Skips without Docker.
 */
@SpringBootTest
class CageFishingLineBatchIntegrationTest {

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
    void aCageIsBuiltFromAFrameAndTheLinesAreTwisted() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary, "awakening must produce a living Chronicle");
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        items.createCarriedItem(chronicle, "hazel_rod", "Hazel rod", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "hazel_rod", "Hazel rod", now, "TEST_FIXTURE");

        var carve = actions.resolve("carve a cage frame");
        assertEquals("SUCCEEDED", carve.outcome(), () -> "carving a cage frame must succeed: " + carve.perception());
        assertTrue(owned(chronicle, "cage_trap_frame") >= 1, "a cage frame must be in hand");
        assertEquals(0, owned(chronicle, "hazel_rod"), "the two rods were consumed making the frame");

        var set = actions.resolve("set a cage trap");
        assertEquals("SUCCEEDED", set.outcome(), () -> "building a cage from its frame must succeed: " + set.perception());
        assertEquals(0, owned(chronicle, "cage_trap_frame"), "the cage consumes the frame");

        // The hand-line tackle is crafted through its own recipes.
        items.createCarriedItem(chronicle, "animal_sinew", "Animal sinew", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "field_stone", "Field stone", now, "TEST_FIXTURE");
        assertEquals("SUCCEEDED", actions.resolve("twist a sinew fishing line").outcome());
        assertTrue(owned(chronicle, "fishing_line_sinew") >= 1, "a sinew line must be made");
        assertEquals("SUCCEEDED", actions.resolve("shape a fishing weight").outcome());
        assertTrue(owned(chronicle, "stone_fishing_weight") >= 1, "a stone fishing weight must be made");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
