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

/** Story #95 slice 7 - limb protection and support. Four left/right pairs on knee/lower leg. */
@SpringBootTest
class SurvivalEquipLimbGuardsIntegrationTest {

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
    void limbGuardsAreMadePerSide() {
        if (worldGenesis.current() == null) { worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault()); ecology.seed(); }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "bark_sheet", "Bark sheet", now, "TEST_FIXTURE");
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", now, "TEST_FIXTURE");
        var left = actions.resolve("make a left bark splint");
        assertEquals("SUCCEEDED", left.outcome(), () -> "left bark splint must succeed: " + left.perception());
        var right = actions.resolve("make a right bark splint");
        assertEquals("SUCCEEDED", right.outcome(), () -> "right bark splint must succeed: " + right.perception());
        assertTrue(items.hasAtLeast(chronicle, "bark_splint_left", 1), "left splint in hand");
        assertTrue(items.hasAtLeast(chronicle, "bark_splint_right", 1), "right splint in hand");

        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM item_equipment_compatibility WHERE item_key='bark_splint_left' AND body_position='LOWER_LEG_LEFT'", Integer.class));
        assertEquals(1, (int) jdbc.queryForObject("SELECT COUNT(*) FROM item_equipment_compatibility WHERE item_key='reed_knee_pad_right' AND body_position='KNEE_RIGHT'", Integer.class));
        assertEquals(8, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_definition d JOIN item_equipment_compatibility e ON e.item_key=d.item_key WHERE d.equippable AND d.item_key IN " +
            "('bark_splint_left','bark_splint_right','fibre_knee_wrap_left','fibre_knee_wrap_right','reed_knee_pad_left','reed_knee_pad_right','bark_shin_guard_left','bark_shin_guard_right')", Integer.class));

        assertTrue(auditor.inspect().consistent(), () -> "Auditor: " + auditor.inspect().violations());
    }
}
