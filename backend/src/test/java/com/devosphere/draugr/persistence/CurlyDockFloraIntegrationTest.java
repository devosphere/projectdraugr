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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story #138 - temperate-forest survival flora. Proves curly dock is catalogued as a gatherable herb whose young leaf
 * is a FOOD green, and that the leaf is terminally useful (eaten). Skips without Docker.
 */
@SpringBootTest
class CurlyDockFloraIntegrationTest {

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
    void curlyDockLeafIsAnEdibleForagedGreen() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // Catalogued: a gatherable herb dropping a FOOD leaf, sourced from the plant.
        assertTrue(jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM flora_definition f JOIN flora_drop d ON d.flora_key=f.flora_key " +
            "JOIN item_definition i ON i.item_key=d.item_key " +
            "WHERE f.flora_key='curly_dock' AND d.item_key='curly_dock_leaf' AND i.category='FOOD')", Boolean.class),
            "curly dock must be a gatherable herb dropping a FOOD leaf");

        // Terminally useful: the leaf is eaten.
        Instant now = Instant.now();
        items.createCarriedItem(chronicle, "curly_dock_leaf", "Curly dock leaf", now, "TEST_FIXTURE");
        assertTrue(items.hasAtLeast(chronicle, "curly_dock_leaf", 1), "the leaf must be in hand before eating");

        var eaten = actions.resolve("eat the curly dock leaf");
        assertEquals("SUCCEEDED", eaten.outcome(), () -> "eating a curly dock leaf must succeed: " + eaten.perception());
        assertFalse(items.hasAtLeast(chronicle, "curly_dock_leaf", 1), "the leaf must be consumed by eating");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
