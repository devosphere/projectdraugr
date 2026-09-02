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

/** Story #96 slice 6 - legs; four left/right shin/knee guard pairs made per side. */
@SpringBootTest
class LeatherEquipLegsIntegrationTest {

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
    void legGuardsAreMadePerSide() {
        if (worldGenesis.current() == null) { worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault()); ecology.seed(); }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        items.createCarriedItem(chronicle, "flint_knife", "Flint knife", now, "TEST_FIXTURE");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "tanned_leather", "Tanned leather", now, "TEST_FIXTURE");
        var left = actions.resolve("make a left leather shin guard");
        assertEquals("SUCCEEDED", left.outcome(), () -> "left leather shin guard must succeed: " + left.perception());
        var right = actions.resolve("make a right leather shin guard");
        assertEquals("SUCCEEDED", right.outcome(), () -> "right leather shin guard must succeed: " + right.perception());
        assertTrue(items.hasAtLeast(chronicle, "leather_shin_guard_left", 1), "left shin guard in hand");
        assertTrue(items.hasAtLeast(chronicle, "leather_shin_guard_right", 1), "right shin guard in hand");

        assertEquals(8, (int) jdbc.queryForObject(
            "SELECT COUNT(DISTINCT d.item_key) FROM item_definition d JOIN item_equipment_compatibility e ON e.item_key=d.item_key WHERE d.equippable AND d.item_key IN " +
            "('rawhide_shin_guard_left','rawhide_shin_guard_right','leather_shin_guard_left','leather_shin_guard_right','rawhide_knee_guard_left','rawhide_knee_guard_right','leather_knee_guard_left','leather_knee_guard_right')", Integer.class));

        assertTrue(auditor.inspect().consistent(), () -> "Auditor: " + auditor.inspect().violations());
    }
}
