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
 * Story #95 slice 1 - head/neck/face survival coverings. Proves "make a leaf sunshade" yields a reachable, equippable
 * item and that all six new coverings are equippable CLOTHING with a real anatomy slot. Skips without Docker.
 */
@SpringBootTest
class SurvivalEquipHeadNeckIntegrationTest {

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
    void aLeafSunshadeIsMadeAndAllCoveringsAreEquippable() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        for (int i = 0; i < 4; i++) items.createCarriedItem(chronicle, "big_leaf", "Broad leaf", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", now, "TEST_FIXTURE");

        var made = actions.resolve("make a leaf sunshade");
        assertEquals("SUCCEEDED", made.outcome(), () -> "making a leaf sunshade must succeed: " + made.perception());
        assertTrue(items.hasAtLeast(chronicle, "leaf_sunshade", 1), "a leaf sunshade must now be in hand");

        // All six new coverings are equippable CLOTHING with a real anatomy slot.
        Integer wired = jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_definition d JOIN item_equipment_compatibility e ON e.item_key=d.item_key " +
            "WHERE d.equippable AND d.category='CLOTHING' AND d.item_key IN " +
            "('leaf_sunshade','reed_headband','bark_headband','grass_cap','fibre_neck_wrap','fibre_face_wrap')", Integer.class);
        assertEquals(6, (int) wired, "all six coverings must be equippable with an anatomy slot");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
