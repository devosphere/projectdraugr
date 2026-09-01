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

/** Story #95 slice 4 - hand wraps, ankle wraps, sandals. Left/right made independently; a reed pair fills both feet. */
@SpringBootTest
class SurvivalEquipHandsFeetIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll static void startDatabase() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for this integration test");
        System.setProperty("java.awt.headless", "true"); postgres.start();
    }
    @AfterAll static void stopDatabase() { if (postgres.isRunning()) postgres.stop(); }
    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
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
    void pairedHandAndFootWearIsMadePerSide() {
        if (worldGenesis.current() == null) { worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault()); ecology.seed(); }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", now, "TEST_FIXTURE");
        var left = actions.resolve("make a left fibre hand wrap");
        assertEquals("SUCCEEDED", left.outcome(), () -> "left fibre hand wrap must succeed: " + left.perception());
        var right = actions.resolve("make a right fibre hand wrap");
        assertEquals("SUCCEEDED", right.outcome(), () -> "right fibre hand wrap must succeed: " + right.perception());
        assertTrue(items.hasAtLeast(chronicle, "fibre_hand_wrap_left", 1), "left hand wrap in hand");
        assertTrue(items.hasAtLeast(chronicle, "fibre_hand_wrap_right", 1), "right hand wrap in hand");

        // The declared reed pair fills both feet with one item.
        assertEquals(2, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_equipment_compatibility WHERE item_key='reed_sandal_pair' AND body_position IN ('FOOT_LEFT','FOOT_RIGHT')", Integer.class));
        assertEquals(7, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_definition d JOIN item_equipment_compatibility e ON e.item_key=d.item_key WHERE d.equippable AND d.item_key IN " +
            "('fibre_hand_wrap_left','fibre_hand_wrap_right','grass_ankle_wrap_left','grass_ankle_wrap_right','bark_sandal_left','bark_sandal_right','reed_sandal_pair')", Integer.class));

        assertTrue(auditor.inspect().consistent(), () -> "Auditor: " + auditor.inspect().violations());
    }
}
