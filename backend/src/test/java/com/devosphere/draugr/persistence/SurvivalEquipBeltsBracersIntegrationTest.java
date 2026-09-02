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
 * Story #95 slice 3 - belts and bracers. Proves belts route via "make a X girdle" (around the Java CRAFT_BELT
 * intent) and the left/right bracers are made independently to their own side. Skips without Docker.
 */
@SpringBootTest
class SurvivalEquipBeltsBracersIntegrationTest {

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

    @Test
    void beltsAndPairedBracersAreMadeCorrectly() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", now, "TEST_FIXTURE");
        // A belt (routes past CRAFT_BELT via the 'girdle' phrase).
        var belt = actions.resolve("make a fibre girdle");
        assertEquals("SUCCEEDED", belt.outcome(), () -> "making a fibre belt via 'girdle' must succeed: " + belt.perception());
        assertTrue(items.hasAtLeast(chronicle, "fibre_belt", 1), "a fibre belt must now be in hand");

        // The two bracer sides are made independently to their own side.
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "reed_bundle", "Reed bundle", now, "TEST_FIXTURE");
        var left = actions.resolve("make a left reed bracer");
        assertEquals("SUCCEEDED", left.outcome(), () -> "making a left reed bracer must succeed: " + left.perception());
        var right = actions.resolve("make a right reed bracer");
        assertEquals("SUCCEEDED", right.outcome(), () -> "making a right reed bracer must succeed: " + right.perception());
        assertTrue(items.hasAtLeast(chronicle, "reed_bracer_left", 1), "the left reed bracer must be in hand");
        assertTrue(items.hasAtLeast(chronicle, "reed_bracer_right", 1), "the right reed bracer must be in hand");

        // Each side declares its own FOREARM slot so one item cannot fill both.
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_equipment_compatibility WHERE item_key='reed_bracer_left' AND body_position='FOREARM_LEFT'", Integer.class));
        assertEquals(1, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_equipment_compatibility WHERE item_key='reed_bracer_right' AND body_position='FOREARM_RIGHT'", Integer.class));

        assertEquals(7, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_definition d JOIN item_equipment_compatibility e ON e.item_key=d.item_key WHERE d.equippable AND d.item_key IN " +
            "('fibre_belt','bark_belt','cordage_belt','reed_bracer_left','reed_bracer_right','bark_bracer_left','bark_bracer_right')", Integer.class));

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
