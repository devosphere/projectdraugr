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
 * Story #149 - prep-verb vocabulary. Proves the two verbs that did not previously route now resolve to a real
 * preparation: "braid" lays up cordage, and "abrade" hones a dull edge. Skips without Docker.
 */
@SpringBootTest
class PrepVerbVocabularyIntegrationTest {

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
    void braidAndAbradeAreUnderstoodPrepVerbs() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);
        Instant now = Instant.now();

        // "braid" lays up cordage (routes to twist_cordage, not UNKNOWN).
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "plant_fiber", "Plant fibre", now, "TEST_FIXTURE");
        var braid = actions.resolve("braid a cord");
        assertEquals("SUCCEEDED", braid.outcome(), () -> "braiding must lay up cordage: " + braid.perception());
        assertTrue(items.hasAtLeast(chronicle, "fiber_cordage", 1), "braiding must yield cordage");

        // "abrade" hones a dull edge (routes to REPAIR_ITEM and takes the sharpening branch).
        items.createCarriedItem(chronicle, "flint_knife", "Flint knife", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "sandstone_piece", "Sandstone", now, "TEST_FIXTURE");
        jdbc.update("UPDATE item_instance SET condition_state='WORN' WHERE item_key='flint_knife' AND object_id IN " +
                "(SELECT id FROM world_object WHERE current_owner_id=?)", chronicle);
        var abrade = actions.resolve("abrade the flint knife");
        assertEquals("SUCCEEDED", abrade.outcome(), () -> "abrading a worn edge must hone it: " + abrade.perception());

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
