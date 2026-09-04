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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Story #58 - the last two primitive tools that did no work now do. Proves that a stone maul (its new STRIKING
 * tool_profile the only striking tool in reach) drives a wooden wedge to rive a timber log into fuel: the split
 * succeeds, the log and wedge are spent, the maul remains, and an armful of kindling is in hand. Skips without Docker.
 */
@SpringBootTest
class MaulWedgeSplitKindlingIntegrationTest {

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
    void aMaulAndWedgeSplitALogIntoKindling() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        Instant now = Instant.now();
        items.createCarriedItem(chronicle, "timber_log", "Timber log", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "wooden_wedge", "Wooden wedge", now, "TEST_FIXTURE");
        // The stone maul is the ONLY striking tool in reach, so a success proves its new STRIKING profile is read.
        items.createCarriedItem(chronicle, "stone_maul", "Stone maul", now, "TEST_FIXTURE");

        var split = actions.resolve("split kindling from the log");
        assertEquals("SUCCEEDED", split.outcome(), () -> "the maul must drive the wedge and rive the log: " + split.perception());

        assertTrue(items.hasAtLeast(chronicle, "dry_branch", 6), "an armful of kindling must be in hand");
        assertFalse(items.hasAtLeast(chronicle, "wooden_wedge", 1), "the driven wedge is spent");
        assertFalse(items.hasAtLeast(chronicle, "timber_log", 1), "the log is riven and gone");
        assertTrue(items.hasAtLeast(chronicle, "stone_maul", 1), "the maul is a tool, not consumed");

        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM tool_profile WHERE item_key='stone_maul' AND tool_class='STRIKING')", Boolean.class),
            "the stone maul must be a registered STRIKING tool");
        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
