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
 * Story #145 - first hunting traps and field tools. Proves the one missing entry, a distinct bait pouch, is crafted
 * from reachable materials and registered as a potent bait (the rest of the set - snare, deadfall, fish spear, fish
 * trap, tracking-marker MARK action - already exists). Skips without Docker.
 */
@SpringBootTest
class BaitPouchIntegrationTest {

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
    void aBaitPouchIsMixedAndIsAPotentBait() {
        if (worldGenesis.current() == null) {
            worldGenesis.generate(WorldGenesisService.GenesisRequest.mvpDefault());
            ecology.seed();
        }
        ChronicleService.ChronicleSummary summary = chronicles.awaken();
        assertNotNull(summary);
        UUID chronicle = summary.id();
        jdbc.update("UPDATE chronicle_carry_capacity SET sustained_mass_grams=100000000, direct_bulk_ml=100000000, maximum_single_lift_grams=100000000 WHERE chronicle_id=?", chronicle);

        // The rest of the #145 set already exists.
        assertEquals(4, (int) jdbc.queryForObject(
            "SELECT COUNT(*) FROM item_definition WHERE item_key IN ('snare_loop','deadfall_weight_stone','fish_spear','fish_trap')", Integer.class));

        Instant now = Instant.now();
        for (int i = 0; i < 2; i++) items.createCarriedItem(chronicle, "earthworm", "Earthworm", now, "TEST_FIXTURE");
        items.createCarriedItem(chronicle, "wild_berries", "Wild berries", now, "TEST_FIXTURE");

        var made = actions.resolve("make a bait pouch");
        assertEquals("SUCCEEDED", made.outcome(), () -> "mixing a bait pouch must succeed (not routed to sew_leather_pouch): " + made.perception());
        assertTrue(items.hasAtLeast(chronicle, "bait_pouch", 1), "a bait pouch must now be in hand");

        // It is a registered, potent omnivore bait the lure mechanic can deploy.
        Integer potency = jdbc.queryForObject("SELECT potency FROM bait_profile WHERE item_key='bait_pouch' AND draws_role='OMNIVORE'", Integer.class);
        assertNotNull(potency, "the bait pouch must be a registered bait");
        assertTrue(potency >= 25, "the crafted bait must be at least as potent as raw food");

        assertTrue(auditor.inspect().consistent(), () -> "the world must stay Auditor-consistent: " + auditor.inspect().violations());
    }
}
