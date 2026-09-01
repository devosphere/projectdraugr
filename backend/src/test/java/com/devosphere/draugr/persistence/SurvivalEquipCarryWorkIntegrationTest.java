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

/** Story #95 slice 5 - carrying and work wear. "make a fibre carry strap" routes around EQUIP('sling'). */
@SpringBootTest
class SurvivalEquipCarryWorkIntegrationTest {

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
    void carryAndWorkWearIsMadeAndEquippable() {
        if (worldGenesis.current() == null) { worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault()); ecology.seed(); }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", now, "TEST_FIXTURE");
        var made = actions.resolve("make a fibre carry strap");
        assertEquals("SUCCEEDED", made.outcome(), () -> "making a fibre carry sling must succeed (not routed to EQUIP): " + made.perception());
        assertTrue(items.hasAtLeast(chronicle, "fibre_sling", 1), "a fibre carry sling must now be in hand");

        assertEquals(8, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_definition d JOIN item_equipment_compatibility e ON e.item_key=d.item_key WHERE d.equippable AND d.item_key IN " +
            "('fibre_sling','reed_back_panel','bark_back_panel','grass_carry_bundle','reed_tool_loop','cordage_tool_belt','simple_knife_sheath','stone_tool_wrap')", Integer.class));

        assertTrue(auditor.inspect().consistent(), () -> "Auditor: " + auditor.inspect().violations());
    }
}
