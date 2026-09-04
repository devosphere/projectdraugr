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
 * Story #60 - preservation must change safe-life realistically, and never make food immortal. A crock of fermented
 * vegetables is preserved in its own salt brine, so it is tracked on the salted tier and keeps long but not forever.
 * Before this it fell through the preservation map and never spoiled at all. Skips without Docker.
 */
@SpringBootTest
class FermentedFoodKeepsIntegrationTest {

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
    void fermentedVegetablesAreTrackedAndKeepOnTheSaltedTier() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) items.createCarriedItem(chronicle, "wild_berries", "Wild berries", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "ground_salt", "Ground salt", now, "TEST_FIXTURE");

        var fermented = actions.resolve("ferment vegetables");
        assertEquals("SUCCEEDED", fermented.outcome(), () -> "fermenting must succeed: " + fermented.perception());
        assertTrue(items.hasAtLeast(chronicle, "fermented_vegetables", 1), "a crock of fermented vegetables must be in hand");

        // It is tracked food, not immortal: a preservation state exists, on the salted (brine) tier.
        String kind = jdbc.queryForObject(
            "SELECT f.preparation_kind FROM food_preservation_state f JOIN item_instance i ON i.object_id=f.object_id " +
            "WHERE i.item_key='fermented_vegetables' LIMIT 1", String.class);
        assertEquals("SALTED", kind, "fermented vegetables must keep on the salted (brine) tier, not forever");

        Boolean keepsButNotForever = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM food_preservation_state f JOIN item_instance i ON i.object_id=f.object_id " +
            "WHERE i.item_key='fermented_vegetables' AND f.safe_until > now() AND f.safe_until < now() + interval '200 days')", Boolean.class);
        assertEquals(Boolean.TRUE, keepsButNotForever, "it must have a real, finite safe-life ahead of it");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
